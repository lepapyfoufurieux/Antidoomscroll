# Politique de test d'Antiscrolling

*Ce qu'on teste, où, avec quoi, et ce qu'une fonctionnalité doit apporter pour être terminée.*

## Idée directrice

Le code se partage en deux : ce qui **décide** (Kotlin pur, `domain/`) et ce qui **branche Android** (services, activités, Room). Les tests automatiques couvrent la décision. Android se vérifie sur téléphone, avec une recette écrite. Pour que ça tienne quand l'appli grossit, toute nouvelle logique va du côté « décide », et le côté Android reste assez mince pour qu'une recette courte suffise.

## Les quatre niveaux

| Niveau | Quoi | Outil | Quand |
|---|---|---|---|
| 1. Pur | `domain/` : `RuleEngine`, `UsageCalculator`, `StatsCalculator`, enchaînement des frictions | JUnit 4 sur la JVM | `./gradlew test`, à chaque modification |
| 2. Colle avec faux | `QuotaWatcher`, repositories (mémoire avant base, `null` au démarrage à froid) | JUnit 4 + `kotlinx-coroutines-test`, faux `AppDao` écrit à la main | idem |
| 3. Instrumenté | Migrations Room, dès la première migration | `androidTest` + `MigrationTestHelper`, émulateur | avant chaque version qui change le schéma |
| 4. Recette | Services, écrans, permissions, comportement des fabricants | [recette.md](recette.md), sur téléphone | avant chaque envoi en test fermé ou en production |

Volontairement absents : Mockito et MockK (une interface se remplace par un faux de dix lignes), Robolectric (lent, et ne simule pas un service d'accessibilité de façon fiable), tests Compose, objectif de couverture en pourcentage. Ils viendront si un signe de la section « Quand monter d'un cran » apparaît.

## Règles d'écriture

- **Le temps est un paramètre.** `now: ZonedDateTime` pour le moteur, une fonction `now()` injectée pour ce qui boucle. Jamais `ZonedDateTime.now()` ni `Instant.now()` dans du code testé.
- **Fuseau `Europe/Paris`** dans les tests, pour avoir les changements d'heure. Dates de référence : heure d'hiver le 25/10/2026 (3 h → 2 h), heure d'été le 28/03/2027 (2 h → 3 h).
- **Un fichier de test par classe**, même package, dans `app/src/test/`. Noms en français entre backticks qui décrivent le comportement : `` `un accès ne lève pas le quota` ``.
- **Un test, un comportement.** Les valeurs limites d'une même règle peuvent partager un test.
- **Pas d'Android dans `domain/`.** Si un test du niveau 1 a besoin d'un `Context`, c'est le code qui est au mauvais endroit.
- **Données partagées** (heures de référence, règle type) dans `TestData.kt`, seulement quand un deuxième fichier de test en a besoin.

## Ce qu'une fonctionnalité doit apporter

Une fonctionnalité est terminée quand `./gradlew test lint` passe et que les lignes du tableau qui la concernent sont faites.

| La fonctionnalité ajoute… | Tests à écrire |
|---|---|
| Une branche dans `RuleEngine.decide()` | La branche seule. Sa priorité face aux branches voisines (accès en cours + quota atteint → `Block`). La valeur limite (quota atteint pile, plage qui commence maintenant). |
| Une logique liée à l'heure ou au jour | Minuit, les deux changements d'heure, une plage qui chevauche minuit si elle peut exister. |
| Un calculateur | Des séquences d'événements écrites à la main : cas normal, événement manquant, période commencée la veille, journée vide. |
| Un plafond, un compteur, un incrément | 0, 1, la valeur où le plafond s'applique, au-delà. |
| Un état gardé en mémoire puis écrit en base | Niveau 2 : l'état est visible tout de suite, avant l'écriture ; `null` tant que la base n'est pas lue. |
| Une boucle ou une échéance (`QuotaWatcher`, alerte « 5 min ») | Niveau 2 en temps virtuel (`runTest`, `advanceTimeBy`) : la décision change au bon passage, rien ne part écran éteint. |
| Un service, un écran, une permission | Ses scénarios dans [recette.md](recette.md). |
| Un changement de schéma | La migration et son test (niveau 3). |

**Chaque bug trouvé** donne un test qui le reproduit, au niveau le plus bas possible, écrit avant la correction. S'il n'est pas automatisable, un scénario de recette.

## Garder Android mince

C'est ce qui fait passer la politique à l'échelle.

- Un service ou une activité ne décide rien : il traduit un événement Android en appel de fonction pure, puis la réponse en action Android.
- Dès qu'un `when` dans un service dépasse trois branches, ou qu'un bug y est trouvé, il sort dans une fonction pure testée. Premier candidat : le tri des fenêtres de `BlockerAccessibilityService`, quand l'écran partagé ou l'image dans l'image seront traités.
- Ce qui boucle reçoit ses dépendances en paramètres (flows, `now()`, `isInteractive()`) plutôt que l'`AppContainer` entier, pour qu'un test puisse les remplacer.
- L'enchaînement des frictions (`ui/block/steps/`) vit dans une classe pure : étape courante, peut-on continuer, accès accordé. Les composables ne font qu'afficher.

## Garanties par construction

Certaines exigences du Play Store se tiennent mieux dans le build que par un test.

- **Pas d'`INTERNET`**, même si une dépendance l'ajoute : `<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />` dans le manifeste.
- **Pas de lecture du contenu des écrans** : `canRetrieveWindowContent="false"` dans `accessibility_service_config.xml`, relu en recette avant chaque envoi.

## Recette sur téléphone

[recette.md](recette.md) liste les scénarios par fonctionnalité, chacun avec son résultat attendu. Chaque fonctionnalité y ajoute les siens.

- **Pendant le développement** : les scénarios de la fonctionnalité en cours et ceux marqués **critique**, sur la version debug. Les attentes longues y sont raccourcies (accès temporaire de 1 min au lieu de 5, via `BuildConfig.DEBUG`). Minuit et les changements d'heure se testent en changeant l'horloge du téléphone. L'intervalle de 15 s du `QuotaWatcher` ne se raccourcit jamais : c'est lui qu'on vérifie.
- **Avant chaque envoi** en test fermé ou en production : toute la recette, en version release (vraies durées), sur l'émulateur `Antiscrolling_API35` et sur un vrai téléphone, si possible un Xiaomi, Samsung ou Huawei. Aucun émulateur ne reproduit un fabricant qui coupe le service.
- **Avant la première publication**, puis à chaque montée de `targetSdk` : les scénarios **critique** et ceux de la version concernée, sur chaque version du tableau. Un émulateur à la fois : la recette est manuelle, en ouvrir plusieurs ne fait rien gagner.

| Version | Ce qui y change pour l'appli |
|---|---|
| API 28 (Android 9) | minSdk, déverrouillages comptés à partir des événements `KEYGUARD_HIDDEN` |
| API 30 (Android 11) | Les applis installées ne sont visibles qu'à travers les `<queries>` du manifeste |
| API 33 (Android 13) | Il faut demander `POST_NOTIFICATIONS` pour l'alerte « 5 min » |
| API 35 (Android 15) | `targetSdk`, règles les plus strictes pour ouvrir un écran par-dessus une autre appli |

## Ce qu'apporte le test fermé

- Le **rapport de pré-lancement** de la Play Console fait tourner l'appli sur des appareils réels à chaque envoi : plantages, problèmes d'accessibilité, avertissements de sécurité. À lire à chaque version.
- **Android vitals** est la seule source de plantages : sans `INTERNET`, pas de Crashlytics ni d'équivalent. Un plantage qui y apparaît devient un test ou un scénario de recette.

## Quand monter d'un cran

Un outil n'arrive que quand son besoin est là.

| Signe | Réponse |
|---|---|
| Le projet est sur GitHub | Une CI (GitHub Actions) qui lance `./gradlew test lint` à chaque push. |
| La recette complète dépasse 30 min, ou un même scénario casse deux fois | Automatiser ce scénario en test instrumenté UI Automator. Le service s'active avec `adb shell settings put secure enabled_accessibility_services`. Se connecter avec `UiAutomation.FLAG_DONT_SUSPEND_ACCESSIBILITY_SERVICES`, sinon Android met le service en pause pendant le test. Les lancer en parallèle sur les versions du tableau de la recette, avec un groupe d'appareils gérés par Gradle (Gradle Managed Devices). |
| Un écran Compose a une logique qu'on ne peut pas sortir | Un test Compose (`createComposeRule`) pour cet écran-là. |

## Commandes

| Commande | Rôle |
|---|---|
| `./gradlew test` | Niveaux 1 et 2 |
| `./gradlew lint` | Lint Android (permissions, API, accessibilité) |
| `./gradlew connectedAndroidTest` | Niveau 3, émulateur lancé |

# Recette sur téléphone

Scénarios à passer à la main, voir [tests.md](tests.md). Chaque fonctionnalité ajoute sa section. **Critique** : à repasser à chaque modification du service ou de l'écran de blocage.

Préparation : Instagram (ou une autre appli) suivi avec un délai de 15 s, toutes les permissions accordées.

**Durées.** En version debug, l'accès temporaire dure 1 min au lieu de 5 : « 5 min » dans les scénarios veut dire la durée affichée sur le bouton. Pour un quota, poser une limite de quelques minutes. Pour minuit, « le lendemain » ou un changement d'heure, avancer l'horloge du téléphone (Paramètres › Date et heure, heure automatique désactivée), sans toucher au code. Les 15 s entre deux vérifications ne se raccourcissent pas : c'est ce que la recette vérifie. Avant chaque envoi, la recette complète se passe sur la version release, avec les vraies durées.

## Onboarding et permissions

| # | Scénario | Attendu |
|---|---|---|
| O1 | Première ouverture | L'écran d'information et consentement s'affiche avant tout réglage. Rien n'ouvre Paramètres › Accessibilité avant « J'accepte ». |
| O2 | Quitter sans accepter, puis rouvrir | L'écran d'information revient. |
| O3 | Activer le service puis revenir dans l'appli | L'appli détecte le service actif sans redémarrage. |
| O4 | Relire `accessibility_service_config.xml` | `canRetrieveWindowContent="false"`. |

## Blocage et accès « 5 min »

| # | Scénario | Attendu |
|---|---|---|
| B1 **critique** | Ouvrir l'appli suivie | L'écran de blocage s'affiche, attente de 15 s, « Ouvrir 5 min » inactif pendant l'attente. |
| B2 **critique** | « Ouvrir 5 min » après l'attente | L'appli reprend et n'est pas rebloquée aussitôt (pas de boucle). |
| B3 **critique** | Rester dans l'appli après 5 min | Bloquée de nouveau en moins de 20 s. |
| B4 | « Renoncer » ou bouton Retour | Retour à l'écran d'accueil, l'appli n'est plus surveillée. |
| B5 | Accueil pendant l'attente, puis rouvrir | L'attente repart de 15 s. |
| B6 | Ouvrir une appli non suivie, le clavier, le volet de notifications | Aucun blocage. |
| B7 | Écran éteint pendant l'accès, rallumé après expiration | Bloquée au déverrouillage. |
| B8 | Rotation pendant l'attente | Le compte à rebours continue, pas de remise à zéro. |
| B9 | Désactiver l'appli dans la liste pendant qu'elle est ouverte | Plus de blocage au passage suivant. |

## Temps du jour et historique

| # | Scénario | Attendu |
|---|---|---|
| T1 | Service activé, accès à l'utilisation pas encore accordé | L'écran « Autoriser l'accès à l'utilisation » s'affiche, son bouton ouvre la liste des paramètres. Au retour, l'appli passe à « Aujourd'hui » sans redémarrage. |
| T2 | Utiliser une appli quelques minutes, ouvrir « Aujourd'hui » | Elle apparaît avec son temps, à la minute près. Le lanceur et Antiscrolling n'apparaissent pas. |
| T3 | Depuis « Aujourd'hui », utiliser une autre appli quelques minutes puis revenir | Le temps est à jour dès le retour, puis chaque minute. |
| T4 | Le lendemain, ouvrir Antiscrolling | La veille est dans `daily_usage` et `daily_unlocks` (Android Studio › App Inspection › Database Inspector). |
| T5 | Retirer l'accès à l'utilisation dans les paramètres | L'écran T1 revient à l'ouverture suivante. |
| T6 | Appli freinée, ouverte et refusée quelques fois avec des raisons, puis « Aujourd'hui » | Sous l'appli : ses options actives, « N ouvertures, M renoncements » du jour et les raisons (« ×2 » si répétée). Une appli freinée apparaît même sans usage. |

## Limite par jour et alerte

En version debug, la liste des limites propose aussi 2 et 7 min (alerte à 2 min d'usage, blocage à 7).

| # | Scénario | Attendu |
|---|---|---|
| Q1 | Applis › « Options pour l'application » › « Limite : aucune », choisir une durée | La limite s'affiche dans les options. À la première limite (Android 13+), Android demande l'autorisation d'envoyer des notifications. |
| Q2 **critique** | Atteindre la limite en restant dans l'appli | Écran « Le temps prévu pour aujourd'hui est écoulé » en moins de 20 s, sans bouton d'accès. « Fermer » ou Retour ramène à l'accueil. |
| Q3 **critique** | Accès « 5 min » en cours quand la limite tombe | Bloquée quand même. |
| Q4 | Rouvrir l'appli une fois la limite atteinte | Bloquée tout de suite, sans pause ni accès possible. |
| Q5 | Entrer dans les 5 dernières minutes | Notification « <appli> : plus que 5 min », une seule fois dans la journée. |
| Q6 | Notifications refusées, puis atteindre la limite | Pas d'alerte, blocage quand même. |
| Q7 | Écran éteint 2 min juste avant la limite, puis rallumé | Le temps écran éteint ne compte pas. |
| Q8 | Le lendemain (avancer l'horloge) | L'appli repart avec la pause habituelle. |
| Q9 | Repasser la limite à « aucune » | Plus de blocage de limite au passage suivant. |

## Plages horaires et mode focus

En version debug, l'onglet Focus propose aussi un focus de 2 min.

| # | Scénario | Attendu |
|---|---|---|
| P1 | Applis › « Options pour l'application » › « Plages : aucune », ajouter une plage | Elle apparaît dans la liste (« Tous les jours, 22:00 → 07:00 le lendemain »). Sans jour coché, « Ajouter » est inactif. |
| P2 **critique** | Ouvrir l'appli pendant une plage | « Plage de blocage en cours. », sans pause ni accès. Même avec un accès en cours. |
| P3 | Rester dans l'appli quand une plage commence | Bloquée en moins de 20 s. |
| P4 | Plage qui chevauche minuit, ouvrir l'appli le lendemain matin avant la fin | Bloquée. |
| P5 | Supprimer la plage | Pause habituelle au passage suivant. |
| F1 **critique** | Lancer un focus, ouvrir une appli suivie | « Mode focus en cours jusqu'à HH:MM. », sans accès. Une appli non suivie s'ouvre normalement. |
| F2 | Rester sur l'écran de blocage jusqu'à la fin du focus | L'écran passe à la pause habituelle (ou au blocage suivant) en moins de 20 s. |
| F3 | « Arrêter le focus » | Pause habituelle à l'ouverture suivante. |
| F4 | Aucune appli suivie, onglet Focus | Message pour cocher des applis, boutons inactifs. |

## Frictions avancées

Réglages : Applis › « Options pour l'application » sous l'appli cochée. Sur l'émulateur, le clavier virtuel reste caché à cause du clavier du PC : taper sur le clavier du PC, ou `adb shell settings put secure show_ime_with_hard_keyboard 1` puis l'icône clavier en bas à droite.

| # | Scénario | Attendu |
|---|---|---|
| FR1 **critique** | Pause, question, addition et durée activées, ouvrir l'appli | Les étapes s'enchaînent dans cet ordre. « Renoncer » ou Retour à n'importe quelle étape ramène à l'accueil. |
| FR2 | Mauvaise réponse à l'addition ou phrase mal recopiée | « Continuer » reste inactif. Espaces et majuscules ignorés pour la phrase. |
| FR3 | Arriver sur l'étape de la tâche (vrai téléphone) | Le clavier s'ouvre sans toucher le champ ; « OK » du clavier valide une bonne réponse. |
| FR4 | Choisir « 10 min » à l'étape de durée | L'appli s'ouvre, bloquée de nouveau au bout de 10 min. |
| FR5 | Pause croissante (+10 s), ouvrir l'appli une fois puis la rouvrir après l'accès | La 2ᵉ pause dure 10 s de plus. Un renoncement n'allonge pas la pause. Le lendemain, retour à la pause de base. |
| FR6 | Tout désactiver (pause « aucune », pas de question, de tâche ni de durée) | L'appli s'ouvre librement ; limite, plages et focus s'appliquent toujours. |
| FR7 | Rotation pendant l'addition | Mêmes nombres, réponse conservée. |

## Statistiques

| # | Scénario | Attendu |
|---|---|---|
| S1 | Ouvrir et renoncer quelques fois avec des raisons, puis onglet Stats | Ouvertures, renoncements et raisons comptés, y compris une raison donnée avant Retour. |
| S2 | Sans limite sur aucune appli | « Posez une limite… » à la place de la série. |
| S3 | Le lendemain d'une journée sous la limite | Série à 1, 7 derniers jours remplis, déverrouillages en moyenne. |

## Widget et notifications masquées

| # | Scénario | Attendu |
|---|---|---|
| W1 | Réglages d'une appli › « Afficher dans le widget », puis ajouter le widget « Temps restant » | « <appli> / N min restantes » avec une limite, « Limite atteinte » une fois au bout, « N min aujourd'hui » sans limite. Toucher le widget ouvre Antiscrolling. |
| W2 | Utiliser l'appli affichée quelques minutes | Le widget baisse d'une minute environ chaque minute. |
| N1 | Activer « Masquer les notifications » sans l'accès aux notifications | Ouvre Paramètres › Accès aux notifications ; tant qu'il manque, un message rouge le rappelle. |
| N2 **critique** | Focus, plage ou limite en cours, recevoir une notification de l'appli (SMS sur l'émulateur : `adb emu sms send 5551234 test`) | Elle n'apparaît pas, elle est en attente (`dumpsys notification` › Snoozed). Elle revient à la fin du blocage. |
| N3 | Appli seulement freinée (pause), ou notification en cours (musique, appel) | Rien n'est masqué. |

## Profils

| # | Scénario | Attendu |
|---|---|---|
| PR1 | Profils › « Nouveau profil », nommer « Soir » | Le profil s'ouvre sur ses horaires, « Aucune plage ». Dans la liste, il est désactivé. Dans Applis, il a les mêmes applis cochées et les mêmes options que « Par défaut ». |
| PR2 **critique** | Ajouter à « Soir » un horaire qui contient l'heure actuelle, puis l'activer avec son interrupteur | Profils : « Soir » en magenta avec l'étiquette ACTIF. Aujourd'hui : « PROFIL ▸ SOIR ». Applis s'ouvre sur « Soir ». |
| PR3 **critique** | Dans Applis › « Soir », décocher une appli encore cochée dans « Par défaut », puis l'ouvrir | Elle s'ouvre librement. « Par défaut » la garde cochée. |
| PR4 | Supprimer l'horaire de « Soir », rouvrir l'appli | Les réglages de « Par défaut » s'appliquent (pause habituelle). |
| PR5 | Rester dans une appli libre dans le profil actif quand un profil qui la freine commence | Frein ou blocage en moins de 20 s. |
| PR8 | Désactiver « Soir » pendant son horaire | « Par défaut » s'applique au passage suivant. |
| PR9 | Activer un profil sans horaire | Étiquette ACTIF ; il s'applique jusqu'à ce qu'on le désactive. |
| PR6 | Deux profils dont les horaires se chevauchent | Le plus haut dans la liste s'applique. |
| PR7 | Supprimer un profil depuis son écran | Il disparaît de Profils et des choix d'Applis ; Applis revient au profil actif. |

## Style néon

| # | Scénario | Attendu |
|---|---|---|
| UX1 | Ouvrir l'appli, puis une appli freinée | Fond sombre dès le lancement, sans flash blanc. Icônes de la barre d'état claires. |
| UX2 | Appli freinée avec une limite de 7 min (debug), l'utiliser 2 min puis revenir | Aujourd'hui et Applis : sa carte passe entièrement en magenta, « 2m / 7m ». Les autres restent bleues. |
| UX3 | Pause croissante, ouvrir l'appli 1 fois puis 5 fois dans la journée | L'anneau de la pause est bleu à la 1re ouverture, magenta à la 5e. L'en-tête indique « 1re » puis « 5e ouverture aujourd'hui ». |
| UX4 | Lancer un focus | Grille de fond, trait et onglet Focus en magenta ; le titre reste bleu. Retour au bleu à l'arrêt. |
| UX5 | Icônes d'applis sur Android 13+ puis sur l'émulateur API 28 | 13+ : silhouette monochrome teintée (Chrome, Agenda…). API 28 : icône grisée puis teintée. Jamais de case vide une fois chargée. |
| UX6 | Stats, avec plus de temps aujourd'hui qu'hier | Cadre « 7 derniers jours » et total en magenta. Moins qu'hier : bleu. |

## Robustesse

`uiautomator dump` (et tout test UI Automator) **met en pause les autres services d'accessibilité** pendant qu'il tourne : le service est relancé ensuite et ne surveille plus rien jusqu'au prochain changement d'appli. Pour lire l'écran pendant un scénario, prendre une capture (`adb exec-out screencap -p`).

« Forcer l'arrêt » (Paramètres ou `adb shell am force-stop`) **désactive le service d'accessibilité** : c'est Android qui le retire. Pour simuler un démarrage à froid, tuer seulement le processus : `adb shell run-as com.antiscrolling kill -9 <pid>` (pid donné par `adb shell pidof com.antiscrolling`), en version debug.

| # | Scénario | Attendu |
|---|---|---|
| R1 **critique** | Tuer le processus d'Antiscrolling, puis ouvrir l'appli suivie | Bloquée dès la première ouverture (démarrage à froid). |
| R2 | Redémarrer le téléphone, ouvrir l'appli suivie | Bloquée. |
| R3 | Accès « 5 min » accordé, tuer le processus, rouvrir dans les 5 min | Pas de blocage (accès relu depuis la base). |
| R4 | Forcer l'arrêt d'Antiscrolling, puis l'ouvrir | L'écran « Activer le service » s'affiche. |

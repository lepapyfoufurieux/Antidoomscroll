# Antiscrolling : reprendre la main sur son temps d'écran

*Appli Android · brainstorm*

Une appli qui bloque ou freine l'accès aux applis chronophages. Voici les idées de fonctionnalités, les contraintes Android et une première version minimale.

- Idées : **17**
- Dans le MVP : **3**
- Plateforme : **Android**

> Document d'idées de départ. Les choix techniques à jour sont dans [architecture.md](architecture.md).

## Le blocage

- **Liste d'applis bloquées** *(MVP)* : choisir les applis parmi celles installées sur le téléphone.
- **Écran de blocage** *(MVP)* : un écran s'affiche par-dessus l'appli dès qu'on l'ouvre.
- **Blocage total ou quota** : bloquer complètement, ou autoriser par exemple 30 min de TikTok par jour.
- **Plages horaires** : bloqué de 22h à 7h, ou pendant les heures de travail.
- **Mode focus** : un bouton pour tout bloquer pendant 2 heures.

## Les frictions

Plutôt qu'un mur. Souvent plus efficaces qu'un blocage pur, parce qu'on cherche moins à les contourner.

- **Délai d'attente** *(MVP)* : patienter 10 à 30 s avant l'ouverture, le temps de se demander si on en a vraiment envie.
- **Question d'intention** : « Pourquoi tu ouvres cette appli ? » : message précis, ennui ou habitude.
- **Petite tâche** : taper une phrase ou résoudre un calcul avant d'accéder à l'appli.
- **Durée choisie à l'ouverture** : on choisit « 5 min », puis l'appli se bloque à nouveau.
- **Délai croissant** : l'attente s'allonge à chaque ouverture dans la journée.

## Statistiques

- **Temps par appli** *(MVP)* : aujourd'hui et sur la semaine.
- **Déverrouillages et ouvertures** : combien de fois on déverrouille le téléphone et ouvre chaque appli.
- **Séries** : nombre de jours d'affilée sous l'objectif.
- **Résumé hebdo** : comparaison avec la semaine précédente.

## Motivation (bonus)

- **Widget** : le temps restant affiché sur l'écran d'accueil.
- **Alerte avant la limite** : notification « 5 min restantes ».
- **Notifications masquées** : retirer les notifications des applis bloquées.

## Contraintes techniques Android

| Besoin | API Android | Remarque |
|---|---|---|
| Savoir quelle appli est ouverte | `AccessibilityService` ou `UsageStatsManager` | L'accessibilité réagit instantanément mais Google Play examine son usage de près. UsageStats vérifie à intervalles et réagit avec un peu de retard. |
| Afficher l'écran de blocage | Activity par-dessus, ou `SYSTEM_ALERT_WINDOW` | Avec l'accessibilité, lancer une Activity suffit. |
| Mesurer le temps passé | `UsageStatsManager` | L'utilisateur doit donner l'autorisation à la main dans les paramètres. |

> Pour un usage perso (APK installé directement, sans Play Store), le service d'accessibilité est la voie la plus simple et la plus fiable.

## Le MVP proposé

1. Choisir des applis dans la liste de celles installées.
2. Détecter leur ouverture avec le service d'accessibilité et afficher un écran de blocage, avec un délai de 15 s et un bouton « ouvrir quand même 5 min ».
3. Un écran qui affiche le temps passé aujourd'hui par appli.

Stack : Kotlin · Jetpack Compose · DataStore

## Questions ouvertes

- Quelles idées garder en priorité ?
- Blocage dur, ou plutôt des frictions ?
- Appli perso, ou publication sur le Play Store ?

---

*Non vérifié : les règles actuelles de Google Play sur le service d'accessibilité. En cas de publication, ce choix technique peut entraîner un refus.*

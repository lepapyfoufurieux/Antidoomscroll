# Antidoomscroll

An Android app that helps you spend less time on your phone. Pick the apps you want to slow down, and Antidoomscroll puts friction or a hard block in front of them when you open them.

Built with Kotlin and Jetpack Compose (minSdk 28, targetSdk 35). The app's interface is in French.

## Features

- **Blocking**: block an app during set hours, all day, or once its daily time limit is reached.
- **Friction before opening**: a waiting delay that grows with each open, an "why are you opening this?" question, a small task (copy a sentence or solve a sum), and choosing how long you want to stay.
- **Focus mode**: block every tracked app for a set time.
- **Profiles**: separate app settings per profile (work, evening…), each active during its own hours or switched on by hand.
- **Today and stats**: time per app, number of opens and their reasons, unlocks, streaks and a week-over-week summary.
- **Widget and alerts**: a home-screen widget with the time left, a "5 min left" notification, and notifications from blocked apps held until the block ends.

## Privacy

Nothing leaves the phone. The app has no `INTERNET` permission and disables Android backup. The accessibility service only reads which app is in the foreground, never the screen content (`canRetrieveWindowContent="false"`).

## Permissions

- **Accessibility service** (required): detects which app was opened and shows the block screen over it.
- **Usage access** (required): time spent per app and unlocks, from `UsageStatsManager`.
- **Notification access** (optional): holds back notifications from blocked apps.
- **Post notifications** (optional, Android 13+): the "5 min left" alert.

The onboarding explains each one and opens the right settings screen. When installed outside the Play Store, Android 13+ greys out accessibility and notification access until you allow restricted settings in the app's system info.

## Build

Requires JDK 17 and the Android SDK (set `sdk.dir` in `local.properties`, or open the project in Android Studio).

```bash
./gradlew installDebug   # build and install on a connected device or emulator
./gradlew test lint      # unit tests and Android lint
```

## Project layout

A single Gradle module, `app/`, under `com.antiscrolling`:

- `domain/`: pure Kotlin, no Android. `RuleEngine.decide()` is the only place that decides whether an app is allowed, blocked or gets friction.
- `data/`: Room database and repositories, rules kept in memory as `StateFlow`.
- `service/`: the accessibility service, the notification listener and `QuotaWatcher`, which re-checks the rules every 15 s while a tracked app is open.
- `ui/`: Compose screens and the block screen. `widget/`: Glance widget.

Design notes are in French: [architecture.md](architecture.md) (technical design), [tests.md](tests.md) (testing policy) and [recette.md](recette.md) (manual test scenarios on a phone).

## License

[MIT](LICENSE)

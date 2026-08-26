# AURA

**AURA is an open-source, intent-first Android launcher.**

AURA turns the Home screen into a fast command surface for resolving and executing actions rather than making users navigate through layers of app UI.

- Type an intent → AURA resolves it → you act → Android executes.
- No AI layer. No confidence leaks. No hidden provenance.

> **AURA handles intent. Android handles Android.**

AURA deliberately does not duplicate Android's existing strengths such as the notification shade, quick settings, or native permission UI. See [NOTIFICATION PANEL — REJECTED](#notification-panel--rejected) below.

**Status:** Development / Early Open Source — usable as a daily Home on the TECNO CK6 (Android 14) test device, under active development. See [Project status](#project-status).

**License:** [GNU General Public License v3.0](LICENSE)

---

## Architecture

```
L0 — Exact Index (deterministic, in-memory)
L1 — Deterministic Grammar (hand-maintained, 10 families, no ML)
L2 — Semantic Resolution (deterministic synonyms + typo tolerance)
L3 — Deterministic Validation / Action Boundary (exclusive Android execution)
Future L4 — intentionally not implemented
```

**Invariant — the UI never sees resolver internals:**

```
Resolver → Validation → ACT / ASK → explicit user action → Platform Execution
```

- The UI boundary has exactly two outcomes: `ACT` (one dominant, immediately actionable) or `ASK` (an honest candidate group). No `L0Result`, `HighConfidence`, etc. leaks.
- Typing alone never executes. Only an explicit user activation (tap Act, `ACTION` chip, Enter on pre-selected Act, IME Search) may call `L3Validator` → `ValidatedAction` → `AndroidActionExecutor`.
- `L3Validator` is the exclusive gate to Android. Domain/resolver/UI never import `PackageManager`, `ContactsContract`, `AlarmClock`, or `Intent`.

**Package structure**

- `app/src/main/kotlin/com/aura/domain` — pure `CommandState`, `ResolutionOutcome`, `AuraAction`
- `app/src/main/kotlin/com/aura/resolver` — `L0`, `l1/`, `l2/`, `l3/` (pure Kotlin)
- `app/src/main/kotlin/com/aura/platform/android` — `AndroidAppIndexProvider`, `AndroidContactIndexProvider`, `AndroidActionExecutor`, `AuraPrefs` (SharedPreferences), `AuraWidgetHost`, `BatteryMonitor`, `WallpaperAnalyzer`, `WallpaperPicker`, etc. — the only Android boundary
- `app/src/main/kotlin/com/aura/ui` — Compose UI (`home/`, `command/`, `library/`, `components/`) — reads only `CommandState` and design tokens
- `app/src/main/kotlin/com/aura/design` — `AuraTheme`, `AuraColors`, `AuraTokens`, `AuraFocus` — single source of truth
- `app/src/main/kotlin/com/aura/home` — Home layout model (`DockLogic`, `ModuleLogic`, persistence codecs) — pure; also `WallpaperTreatment` (pure brightness→scrim resolver)

See [`docs/PRODUCT.md`](docs/PRODUCT.md) for the authoritative product/roadmap description, [`docs/WALLPAPER.md`](docs/WALLPAPER.md) for the wallpaper model and adaptive dark treatment, and the `design/` package for tokens. The Technical Specification and Design Language are referenced throughout the codebase via `// PRD §` / `// Design Language §` comments and are summarized in `PRODUCT.md`.

---

## Project structure

```
AURA/
├── app/src/main/kotlin/com/aura/   # source
├── app/src/test/kotlin/com/aura/   # 456 JVM unit tests
├── app/src/main/res/               # drawable/mipmap (aura_logo), xml/backup_rules
├── docs/PRODUCT.md                 # product philosophy + roadmap
├── LICENSE                         # GPLv3
├── README.md                       # this file
├── CONTRIBUTING.md
├── CODE_OF_CONDUCT.md
├── SECURITY.md
├── build.gradle.kts / app/build.gradle.kts / settings.gradle.kts
└── gradle.properties
```

---

## Privacy principles

- **Local-first.** Core launcher behavior (intent resolution, app discovery, Home) requires no network.
- **No notification interception.** No `NotificationListenerService`, no listener access flow — Android's shade is left to the OS.
- **No cloud AI / no backend** for core functionality.
- **Contacts are contextual.** `READ_CONTACTS` is requested only when the user first types a contact-dependent action (`call`/`message`/`email`); the app works fully without it. The same pattern is used for `READ_CALENDAR` (Next Event module).
- **Home preferences are local.** `aura_home.xml` is `SharedPreferences` and is explicitly excluded from Android Auto Backup / data extraction (`fullBackupContent` + `dataExtractionRules`). See `docs/PRODUCT.md`: *device-local*.
- **Third-party widget data** remains governed by Android/`AppWidget` providers.
- **No analytics, no monetization.**

This is a factual description of the current code, not a promise that AURA can never access the network — e.g., `SearchPlayStore` opens `play.google.com` via the system browser.

---

## Notification panel — rejected

**NOTIFICATION PANEL — REJECTED** (explicit product decision, not a deferral).

- Android already has a notification system users understand.
- `NotificationListenerService` adds privacy, battery/OEM, and background complexity for little product value.
- AURA specializes in intent, discovery, action, and Home — Android handles notifications.

The codebase contains no `AuraNotificationListenerService`, no `NotificationRepository`, no panel UI. `docs/PRODUCT.md` records the decision.

---

## Build

**Requirements**

- Android Studio / Android SDK (compileSdk 34, minSdk 26, targetSdk 34)
- JDK 21 (`JAVA_HOME=/path/to/jdk21`, e.g. Temurin 21)

```bash
./gradlew assembleDebug      # APK → app/build/outputs/apk/debug/
./gradlew testDebugUnitTest  # 456 JVM tests
./gradlew lint               # Android lint
./gradlew installDebug       # on a connected device / emulator
```

No `local.properties` with `sdk.dir` is committed; create it locally (`sdk.dir=/path/to/android-sdk`) or set `ANDROID_HOME`.

No machine-specific `/home/titan/...` paths are required.

---

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) — read the PRD (`docs/PRODUCT.md`), respect the `Resolver → Validation → ACT/ASK → explicit action → Platform Execution` invariant, keep `Domain`/`Resolver` free of Android APIs, and keep the Design Language tokens authoritative.

---

## Reporting issues

Bug reports should include: Android version, device/OEM, AURA version/commit, reproducible steps, expected vs actual, and relevant logs **without private data** (no contact/message content). See `CONTRIBUTING.md` for the full checklist. Do not paste notification or message content.

Feature requests should explain the user problem, why Android doesn't already solve it, why it belongs in AURA, and privacy/permission implications — AURA's philosophy is subtraction.

---

## Project status

**Development / Early Open Source.**

Implemented:

- Default launcher role (`ROLE_HOME` + `HOME` intent-filter, one-time banner)
- Command Bar (ACT/ASK/Idle/Empty/Error, `ACT/ASK` invariant, no auto-execute)
- L0 exact index, L1 deterministic grammar (10 families), L2 semantic, L3 validation + real `AndroidActionExecutor` (OpenApp/Settings/Timer/Alarm/Dial/Message/Email/Copy/Calculator/Play Store)
- Real app indexing (`PackageManager` off-main-thread + `PackageChangeMonitor`, event-driven, no polling)
- Real contacts indexing (contextual `READ_CONTACTS`)
- App Library (alphabetical, live search with `Normalizer` parity, A–Z rail)
- Home customization: `System/Dark/Light`, `Dynamic` + 6 curated accents (WCAG 4.5:1), wallpaper via `FLAG_SHOW_WALLPAPER` + **adaptive dark scrim** (0.50–0.90 by brightness, with a bottom-weighted gradient for the Command Bar), `Standard/Reduced` motion
- Native modules `NextEvent` (calendar `ContentObserver`, off-`IO`), `Battery` (sticky `ACTION_BATTERY_CHANGED`), `Music` (media-key transport, no `NotificationListener`)
- Android widget host (`AppWidgetHost`/`AppWidgetManager`, discovery `HOME_SCREEN`, bind/configure, `OPTION_APPWIDGET_SIZES` on API 31+, prune on uninstall)

Known limitations: widget resize is responsive via size reporting (no custom drag overlay yet), backup is device-local, no `L4`/AI/cloud. (Wallpaper uses an adaptive dark scrim — calm on dark wallpapers, stronger on bright ones — not a fixed overlay.)

---

## License

**License: [GNU General Public License v3.0](LICENSE)**

AURA is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See [`LICENSE`](LICENSE) for the full text. No warranty.

---

## Acknowledgements

Built with AndroidX Compose, Material3, Kotlin 2.x, and the Android SDK — see `app/build.gradle.kts` for direct dependencies and their licenses.

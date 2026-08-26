# Dependencies

This is a lightweight inventory for contributor awareness, not a legal opinion.
Licenses are as declared by the upstream projects on Maven Central / GitHub.
If you need legal certainty, consult the upstream license text and, if needed,
counsel.

## Direct dependencies (app/build.gradle.kts)

| Dependency | Purpose | Declared license (upstream) | Source |
|---|---|---|---|
| `androidx.compose:compose-bom:2024.09.03` + `androidx.compose.ui:ui`, `ui-tooling-preview` | Compose UI toolkit | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose |
| `androidx.compose.material3:material3:1.3.1` | Material3 components | Apache-2.0 | https://github.com/androidx/androidx |
| `androidx.compose.material:material-icons-extended` | Icons (used for Battery/Music) | Apache-2.0 | https://github.com/androidx/androidx |
| `androidx.activity:activity-compose:1.9.2` | `ComponentActivity` + `setContent`, `rememberLauncherForActivityResult` | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/activity |
| `androidx.core:core-ktx:1.12.0` | KTX extensions (`toBitmap`, `ContextCompat`) | Apache-2.0 | https://github.com/androidx/androidx |
| `androidx.lifecycle:lifecycle-runtime-compose:2.8.6` | `collectAsState`, `LocalLifecycleOwner` | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle |
| `androidx.compose.ui:ui-tooling` (debug) | Preview tooling | Apache-2.0 | same as Compose |
| `androidx.compose.ui:ui-test-manifest` (debug) | Test manifest | Apache-2.0 | same |
| `junit:junit:4.13.2` (test) | JVM unit tests | EPL-1.0 | https://github.com/junit-team/junit4 — **test-only, not shipped in APK** |
| `androidx.test.ext:junit:1.2.1` (androidTest) | Instrumentation test runner | Apache-2.0 | https://github.com/android/android-test |

**Gradle plugins (build-logic, not shipped):**

- `com.android.application:8.7.3` — Android Gradle Plugin, Apache-2.0
- `org.jetbrains.kotlin.android:2.0.21` / `org.jetbrains.kotlin.plugin.compose:2.0.21` — Kotlin, Apache-2.0

## License compatibility notes

- AURA is **GPLv3**. Apache-2.0 (the bulk of AndroidX/Compose/Kotlin) is **compatible with GPLv3** (FSF lists Apache-2.0 as GPLv3-compatible; it is *not* compatible with GPLv2). No GPLv2-only dependency is present.
- `junit:junit:4.13.2` is EPL-1.0. It is a **test-only** dependency (not packaged in `app-debug.apk` / `app-release.apk`), so it does not create a distribution-time incompatibility. If you plan to distribute test artifacts themselves, review EPL-1.0 obligations.
- No dependency is known to be GPL-incompatible for distribution as of this inventory. This is not legal advice — if you add a new dependency, verify its license and note it here.
- Transitive licenses are not enumerated here; run `./gradlew app:dependencies` and check the resolved tree if you need full transitive visibility.

## Adding a dependency

- Justify it in the PR (why platform APIs are insufficient).
- Prefer AndroidX / Kotlin first-party where possible.
- Record it in this file.

## Reproducing

```bash
./gradlew app:dependencies --configuration debugRuntimeClasspath | grep -E "androidx|kotlin|junit"
```

# Contributing to AURA

Thank you for considering a contribution. AURA is intentionally small and opinionated — please read the product context before sending a PR.

## Before you contribute

Read:

- [`docs/PRODUCT.md`](docs/PRODUCT.md) — product philosophy, rejected features (especially Notification Panel), roadmap, and backup semantics
- `app/src/main/kotlin/com/aura/domain/CommandState.kt` and `ResolutionOutcome.kt` — the `ACT/ASK` invariant
- `app/src/main/kotlin/com/aura/resolver/` — `L0`, `l1/`, `l2/`, `l3/` boundaries
- `app/src/main/kotlin/com/aura/design/` — `AuraTheme`, `AuraTokens`, `AuraColors`, `AuraFocus` — tokens are authoritative, no literals in composables

The Technical Specification and Design Language are referenced throughout the codebase as `// PRD §` / `// Design Language §` comments and are summarized in `PRODUCT.md` and `README.md`. If you need the full documents, open an issue.

## Core architecture

```
Resolver
  ↓
Validation (L3)
  ↓
ACT / ASK  (ResolutionOutcome → CommandState)
  ↓
Explicit user action
  ↓
Platform execution (AndroidActionExecutor, platform/android only)
```

## Architecture rules

1. **Domain must not depend on Android APIs.** No `PackageManager`, `ContactsContract`, `Intent`, `Context` in `domain/` or `resolver/`.
2. **Resolver must not execute Android APIs.** Only `platform/android` may call `startActivity`, `AlarmClock`, `WallpaperManager`, etc.
3. **Only the platform execution boundary** (`platform/android/AndroidActionExecutor`, `AuraWidgetHost`, etc.) interacts directly with Android execution APIs.
4. **UI must not bypass `ResolutionOutcome`.** UI reads only `CommandState` (`Idle/Act/Ask/Empty/Error`).
5. **UI must not receive resolver provenance/confidence.** No `L3Result`, `HighConfidence`, etc. in presentation.
6. **L0/L1/L2/L3 must never leak into visual UI states.** No `L0Result` composable.
7. **ASK must never silently execute a candidate.** The user chooses; `L3Validator` decides if the chosen candidate is still valid.
8. **No feature should duplicate Android without a clear product reason.** See `AURA handles intent. Android handles Android.` and the rejected Notification Panel.
9. **Core remains local-first** unless a product decision explicitly changes it. No polling, no background loops, no `NotificationListenerService` for metadata.
10. **New dependencies require justification.** Keep the app small; prefer platform APIs over libraries.
11. **New permissions require explicit product/privacy justification** in the PR and in `AndroidManifest` comments (contextual `READ_CONTACTS`/`READ_CALENDAR` are the precedent; `SET_ALARM` is for `AlarmClock` on TECNO).
12. **Design changes must respect the Design Language.** Use `AuraTheme` tokens (`colors`, `typography`, `spacing`, `radius`, `elevation`, `AuraMotion`), `auraFocusRing`, `48dp` touch targets, TalkBack, `200%` font scale, RTL. No gradients/glass/neon.

## Workflow

```bash
git checkout -b feat/your-topic
# implement — keep resolver pure, keep UI token-driven
./gradlew testDebugUnitTest   # 456 tests must stay green
./gradlew lint
./gradlew assembleDebug
git diff --stat               # inspect — no generated/private files
# push and open PR
```

- Add tests for every fix (pure `*Logic` tests preferred; `app/src/test` is JVM `JUnit4`).
- Do not add `L4`, AI/LLM, cloud, notifications, `Orb`, or unrelated persistence. This is a hardening phase — see `PRODUCT.md`.
- Do not redesign AURA to look like Nova/KLWP.

## Submitting

- Keep PRs small and focused.
- Explain the user problem, why Android doesn't already solve it, and permission/privacy implications.
- Include `Android version / device / AURA commit` for bug reports.
- Strip private data (contacts, messages, notifications) from logs.

## Questions?

Open a GitHub issue — include the same context as a bug report. For design questions, reference the specific token or `// Design Language §` you’re unsure about.

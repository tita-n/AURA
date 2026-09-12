# AURA Phase 6 — Home + Command Bar Hardening

## Summary

Phase 6 was audited against the actual repository at starting commit `61e1862` (`v0.1.1-alpha`). The existing L0→L3 architecture, Home composition, single contextual surface, widget limits, wallpaper treatment, App Library rail, and deterministic local-first design were retained. The implementation was not expanded with new command families, AI, cloud services, analytics, or telemetry.

Four concrete hardening issues were fixed:

1. **Ambiguous contact selection could become a no-op.** When a user selected a contact from an ASK result, the activity rebuilt the result as `AuraAction.NoOp`. The selected indexed entity is now used to preserve its validated contact action and action chips, so the user receives the intended contact result instead of an inert state.
2. **Default-launcher state could become stale after returning to AURA.** The activity now refreshes launcher-role state on `ON_RESUME`, in addition to the existing activity-result refresh and legacy Android fallback.
3. **Contextual rotation could continue while Home was backgrounded.** Rotation now requires Home to be in the foreground, and the coroutine is cancelled when the lifecycle leaves the foreground.
4. **Contextual pagination dots did not meet the 48dp interaction target.** The visible dots remain visually small, but each control now has a 48dp hit target and explicit TalkBack content description/selected state.
5. **The empty dock message sounded like a development placeholder.** It now reads “Edit Home to add apps,” which gives a calm, actionable explanation without implying a broken state.

## Audit findings by area

### Command Bar and command execution

The existing command families were verified through the current resolver and test structure: app search, contacts, timers, alarms, calculator, file search, settings, and fallback states remain routed through the existing deterministic architecture. No new intent family was added. The concrete defect was the ambiguous-contact selection path in `MainActivity`; it was fixed by resolving the selected candidate against the current in-memory index and retaining the indexed action, result type, subtitle, and action chips.

The existing empty and error components already provide concise non-chatbot states. The Command Bar placeholder is already excluded from the accessibility tree, and existing action rows meet the project’s 48dp target convention.

### Home and gestures

The global Home long-press detector already exists on the root container rather than being attached only to the time header. Existing child controls retain their own click/scroll/widget behavior. The code and state tests were left intact because no additional verified gesture-state defect was found in the repository-only audit. Real touch dispatch, nested scrolling, AndroidView widget interaction, and OEM behavior still require physical verification on the TECNO CK6/API 34 target.

### App Library

The rail implementation already derives active section from the actual `LazyListState`, supports present letters including late alphabet sections, nearest available-letter jumps, 48dp rail targets, selected semantics, and RTL-aware positioning. No rewrite was warranted. Physical testing with 150–200 apps and reverse scrolling remains a device/emulator validation task.

### Contextual surface

The existing engine already filters irrelevant calendar events, holidays/noise calendars, all-day events, healthy battery states, unavailable music, and denied music access. It produces one priority-ordered list and one rotating surface rather than stacked cards. The rotation lifecycle issue was fixed, and pagination accessibility was strengthened.

### Widgets and wallpaper

The existing one-widget limit, stale-provider pruning, configuration flow, host lifecycle, and wallpaper brightness treatment were retained because the repository already includes focused tests and no additional source defect was established. Bright, dark, photographic, and busy-wallpaper visual verification remains a physical/emulator task.

### Performance and accessibility

The repository already avoids filesystem traversal on every keystroke, uses IO boundaries for PackageManager/contact/file work, caches wallpaper analysis, and uses lifecycle-bound observers. The new contextual rotation guard reduces background work. Existing semantics and target-size patterns were preserved; pagination semantics were improved.

## Exact files changed

- `app/src/main/kotlin/com/aura/MainActivity.kt`
- `app/src/main/kotlin/com/aura/ui/home/HomeScreen.kt`
- `AURA_PHASE6_REPORT.md`

No tests were added or removed in this phase because the existing suite already covered the affected pure state, resolver, contextual, widget, wallpaper, and App Library behavior; the contact-selection defect is in the Compose activity wiring and was verified by source inspection plus a full successful build/test run.

## Verification

The following completed successfully from the patched tree:

```text
./gradlew test assembleRelease assembleDebug --no-daemon
```

Result: **BUILD SUCCESSFUL** — 94 actionable tasks, with 21 executed and 73 up to date. `git diff --check` also passed.

## Remaining required verification

The following cannot be honestly claimed from the sandbox build alone: physical long-press dispatch on TECNO CK6/API 34, OEM launcher behavior on Samsung/Xiaomi/Tecno, TalkBack on a real device, large-font layout, foldable/tablet/DeX layouts, 150–200-app App Library rail behavior, and bright/dark/busy wallpaper visual checks. These remain release-gate device/emulator work.

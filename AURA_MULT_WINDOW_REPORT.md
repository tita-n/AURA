# AURA Multi-Window and Split-Screen Audit

## Scope

This report audits AURA at starting commit `7184a0e` (`v0.1.2-alpha`) for minimizing apps, returning Home, split-screen, freeform windows, and related Android window-manager behavior.

## Overall finding

AURA was not explicitly opting its launcher activity into Android resizeable-window participation. The manifest declared the launcher activity and `adjustResize` for the keyboard, but did not declare `android:resizeableActivity`. That left AURA dependent on Android/OEM defaults and made multi-window behavior less predictable on devices that require an explicit opt-in.

There is also an important platform boundary: AURA cannot itself minimize arbitrary foreground apps, force two unrelated apps into split-screen, or replace Android Recents. Those operations belong to the system window manager and OEM launcher/task-switcher. A launcher can be a good participant and can launch apps, but it cannot safely or consistently control another app’s task placement without privileged/system-level APIs that normal applications do not have.

## What was wrong

### 1. Missing resizeability declaration

**Severity:** High for multi-window compatibility. `MainActivity` had no explicit `android:resizeableActivity` attribute. AURA therefore did not clearly opt into resizing/freeform behavior on Android versions and OEM shells where manifest participation matters.

**User impact:** On some devices, AURA could be treated as a fixed-size activity or behave inconsistently when the system attempted to resize it. This can affect tablets, desktop/freeform modes, and split-screen transitions.

### 2. No application-level minimize or split-screen control

**Severity:** Platform limitation, not a bug that can be fully fixed inside AURA. The code had no “minimize app” implementation, but Android does not expose a normal launcher API that lets AURA minimize arbitrary apps or force two third-party apps into split-screen. Pressing Home, opening Recents, dragging an app into split-screen, and selecting the second app are system/OEM operations.

**User impact:** AURA can return the user to Home and launch apps, but users must use the device’s Home/Recents/split-screen controls to minimize and arrange apps. This is expected Android behavior, not an AURA-specific failure.

### 3. No picture-in-picture implementation

**Severity:** Not applicable to the launcher’s core role. Picture-in-picture is intended for media/video/navigation activities, not for a home launcher. AURA should not claim to provide PiP for arbitrary apps.

## What was fixed

### Explicit resizeable activity support

`MainActivity` now declares:

```xml
android:resizeableActivity="true"
```

This tells Android and compatible OEM window managers that AURA is allowed to participate in resizeable and freeform activity configurations. It improves behavior for tablets, desktop/freeform modes, and supported multi-window transitions without adding proprietary dependencies or trying to control other apps’ tasks.

A regression test was added to ensure the declaration is not accidentally removed.

## How users should use multiple apps

On Android 12–14 and most OEM skins:

1. Open an app from AURA.
2. Open **Recents** using the system gesture or navigation button.
3. Tap the app icon in the Recents card and choose **Split screen** if the device exposes that option.
4. Select the second app.
5. Drag the divider to resize the two apps.
6. Use the system Home gesture/button to leave split-screen or the Recents controls to change the pair.

AURA remains the starting point and can be reopened as Home. The exact labels and availability vary by Android version, device manufacturer, app resizeability, and orientation.

## What cannot be guaranteed

The following require real-device validation and/or system privileges beyond AURA’s control:

- Whether a particular third-party app supports split-screen.
- Whether Samsung DeX, Xiaomi HyperOS, Tecno HiOS, or another OEM exposes the same Recents menu.
- Whether the launcher itself is allowed to appear as one pane on a specific OEM.
- Whether a device offers freeform windows.
- Whether an OEM task manager kills or recreates AURA during a window transition.
- Automatic pairing of two arbitrary apps into split-screen from a Command Bar command.

AURA should not pretend these behaviors are universally controllable from launcher code.

## Verification

The following completed successfully after the fix:

```text
./gradlew test assembleRelease assembleDebug --no-daemon
```

The new manifest regression test passed with the full suite. Physical split-screen, Recents, tablet, freeform, and DeX behavior still require manual testing on representative devices.

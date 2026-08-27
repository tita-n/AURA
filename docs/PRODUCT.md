# AURA — Product Document

AURA is an intent-first Android launcher. It specializes in:

- **INTENT** — deterministic resolution of what the user wants to do (L0–L3)
- **DISCOVERY** — real app/contact indexing and the App Library
- **ACTION** — validated, real Android execution behind the L3 boundary
- **HOME EXPERIENCE** — a calm, sparse, personal Home

Android handles Android. AURA handles intent.

---

## Product philosophy

1. Premium, calm, personal, fast, intent-first.
2. ACT/ASK invariant is sacred: typing alone never executes; explicit user activation does.
3. One dominant result (ACT) or an honest candidate group (ASK). No confidence leaks.
4. Design Language tokens are authoritative; no new visual language per feature.
5. Local-first, event-driven, no polling loops, no background churn.
6. **AURA should extend Android rather than duplicate Android capabilities that already work well.**

---

## Feature status

| Feature | Status |
|---|---|
| L0 Exact Index | ✅ Shipped |
| L1 Deterministic Grammar (10 families) | ✅ Shipped |
| L2 Semantic Resolution | ✅ Shipped |
| L3 Validation + Real Execution | ✅ Shipped |
| App Library | ✅ Shipped |
| Launcher role (default Home) | ✅ Shipped |
| Contextual READ_CONTACTS | ✅ Shipped |
| Command Bar | ✅ Shipped |
| Design Language / Direction tokens | ✅ Shipped |
| Home editing (edit mode) | ✅ Implemented |
| Dock customization (0–4 apps) | ✅ Implemented |
| Contextual surface (Next Event, Battery, Music) | ✅ Implemented (one rotating surface) |
| Android widget hosting (AppWidgetHost) | ✅ Implemented (single, size-constrained) |
| Deterministic local-time greeting | ✅ Implemented |
| Monochrome app icons | ❌ Deferred — experiment frozen |
| Music media access (optional, music-only listener) | ✅ Optional (narrow, user-granted) |
| App Library A–Z rail + translucent surface | ✅ Implemented |
| Wallpaper cross-device reliability | ✅ Implemented |
| Reduced-motion (calm module animation) | ✅ Implemented |
| Customization (theme, accent, wallpaper entry, animation) | ✅ Implemented |
| Weather module | ❌ CUT — network-dependent; violates local-first |
| **Command Pack 1 (Phase 4A)** | ✅ Implemented — see below |
| &nbsp;&nbsp;Alarm (deterministic time) | ✅ Implemented — `AlarmClock.ACTION_SET_ALARM` |
| &nbsp;&nbsp;Camera launch | ✅ Implemented — system camera intent (no CAMERA permission) |
| &nbsp;&nbsp;Time / Date / Day queries | ✅ Implemented — inline, device-local, no network |
| &nbsp;&nbsp;System Settings (expanded catalog) | ✅ Implemented — closed catalog, `Settings` intents |
| &nbsp;&nbsp;Brightness | ⚠️ Honest fallback — opens Display settings (no `WRITE_SETTINGS`) |
| &nbsp;&nbsp;Reminder | ⚠️ Honest fallback — system calendar event (no reminder API) |
| &nbsp;&nbsp;Screenshot | ❌ Unsupported — Android forbids 3rd-party capture; informs user |

## COMMAND PACK 1 — TIME + DEVICE ACTIONS (Phase 4A)

AURA expands the Command Bar with a small, deterministic set of local actions that Android can
perform on the device. Every new intent flows through the existing L0 → L1 → L2 → L3 pipeline
and is validated by L3 before execution. No AI, no cloud, no new visual states.

| Command | Local? | Android executes? | Permission required | Platform limitation |
|---|---|---|---|---|
| Alarm | Yes | Yes — `AlarmClock.ACTION_SET_ALARM` | None | Day qualifier (tomorrow) is best-effort (API sets next occurrence) |
| Camera | Yes | Yes — system camera intent | None (AURA never requests CAMERA) | AURA does not build a camera; it hands off |
| Time / Date / Day | Yes | Inline result, no execution | None | Optional city→TimeZone is a small offline table; unknown city rejected |
| System Settings | Yes | Yes — `Settings` intents (closed catalog) | None | Catalog is closed; unknown settings rejected |
| Brightness | Yes | No direct set | `WRITE_SETTINGS` (NOT requested) | Falls back to opening Display settings honestly |
| Reminder | Yes | Yes — system calendar event (`ACTION_INSERT`) | None | No reminder API; opens calendar prefilled, never claims creation |
| Screenshot | No | No | — | Android forbids 3rd-party capture without root/MediaProjection/Accessibility — informs user |

**Supported examples**
- `alarm 6:30`, `set an alarm for 6:30`, `wake me at 6:30`, `wake me tomorrow at 6:30am`, `18:30`, `6am`
- `open camera`, `camera`, `launch camera`, `take a photo`, `selfie`
- `what time is it`, `what is today's date`, `what day is it`, `what time is it in Lagos`
- `open Wi-Fi settings`, `open Bluetooth`, `open Display settings`, `open Battery`, `open Sound`,
  `open Apps`, `open Accessibility`, `open Location`, `open Date & time`
- `brightness 70%`, `increase brightness` (→ opens Display settings)
- `remind me to call Mum at 3pm`, `remind me tomorrow at 9am`

**Honesty invariants**
- Screenshot is NEVER faked. AURA reports the platform limitation (Power + Volume Down) and does not claim success.
- Brightness is NEVER silently changed. AURA opens Display settings and explains the permission requirement.
- Reminders are NEVER claimed as created. AURA opens the calendar editor with the details prefilled.
- Time/Date are computed from the device clock with an optional offline city→TimeZone table; unknown cities are rejected, never guessed.

## COMMAND BAR DISCOVERABILITY (Phase 4B)

A small UX phase: make existing Command Bar capabilities *discoverable* without changing any
resolution behavior, execution, or adding new commands. AURA teaches its capabilities through
examples rather than a dedicated onboarding flow.

**Rotating placeholder**
- The old generic "Search anything" is replaced by a rotating set of REAL examples drawn from
  genuinely implemented families: `Call Mum`, `Message Sarah`, `Set a timer for 10 min`,
  `Calculate 15% of 4000`, `Open Spotify`, `Open Wi-Fi settings`, `What time is it`.
- Rotates ~4.5s while Home is visible, the bar is empty, and the user is not typing.
- Pauses on `ON_PAUSE` and resumes on `ON_RESUME` (lifecycle-aware, single delayed coroutine —
  no per-frame timer, no polling, no resolver calls).
- Subtle crossfade; reduced-motion swaps text instantly. Uses existing AURA motion/design tokens.

**Accessibility**
- The rotating placeholder is decorative and hidden from the accessibility tree
  (`clearAndSetSemantics`), so TalkBack never re-announces it every 4s. The field keeps its
  "Command bar" description, 48dp target, dynamic font scaling, and RTL.

**Contextual failed-intent hint**
- Distinct from a normal search miss: if the input strongly resembles a supported command family
  (call / message / timer) but did not resolve, a small, calm hint appears — e.g.
  `Try: Message Sarah` for `message Sarah on`, `Try: Call Sarah` for `call Sarah tomorrow`,
  `Try: Set a timer for 10 min` for `set timer`.
- Deterministic pattern matching only — no AI, network, or analytics. Random text and ordinary
  app-name misses produce no hint. The hint disappears on a successful/ambiguous resolution.

## NOTIFICATION PANEL — REJECTED

**Status: REJECTED as a Panel. A narrow, music-only media listener is OPTIONAL and
user-granted. Notification Phase 1.1 (the full panel/history/subsystem) has been removed
from the active roadmap and from the codebase.**

Reason for rejecting the Panel:

- Android already handles notifications well. Users understand and expect the system shade.
- AURA must not compete with Android's notification shade, quick settings, or native permission UI.
- A full notification subsystem added listener access (privacy risk), battery/OEM risk,
  background complexity, another UI surface — for little unique product value.
- AURA's specialization is intent resolution, app discovery, command execution,
  and the Home experience — not notification interception.

What is explicitly forbidden (enforced by architecture tests):

- No notification panel, no notification history, no grouping/priority logic,
  no notification-specific UI, no swipe-down notification zone.
- Notifications belong to the OS. Android owns notifications; AURA owns the
  contextual Home experience.
- The listener never reads notification content, never stores or transmits anything,
  never renders a notification, and never maintains a list.

What IS allowed — the narrow music bridge:

- Exactly ONE `AuraMediaNotificationListenerService`, music-only, declared with
  `BIND_NOTIFICATION_LISTENER_SERVICE`. Its sole purpose is to act as an *optional*
  technical bridge so AURA can discover other apps' active media sessions and their
  structured metadata (title/artist/album/artwork) for the single Music contextual
  surface.
- It is granted only when the user explicitly enables it in Android's settings, and
  only after AURA explains the scope. It is never requested at install, never because
  AURA became the launcher, and never merely because the Music module was enabled.
- Track metadata comes from the media session / MediaController, never from the
  notification body. The listener filters aggressively to media-related notifications
  and discards everything else immediately.
- Refusing it leaves Music hidden; everything else works normally.

Any future proposal to broaden notification interception beyond this narrow music
bridge requires reversing this document first.

---

## Roadmap

### Phase — HOME EXPERIENCE + CUSTOMIZATION + WIDGET ARCHITECTURE (active)

Goal: make AURA genuinely pleasant to live with every day. Not smarter — more livable.

- **Home edit mode**: long-press empty area → minimal AURA-native edit surface
  (add/remove/rearrange modules & widgets, appearance, dock).
- **Dock customization**: 0–4 installed apps, reorder, remove, tap-to-launch via the
  existing OpenApp execution path. Duplicates not permitted.
- **One contextual surface**: Next Event, Battery, and Music are NOT three separate
  Home boxes. Each *enabled* source (enabled = "allowed to generate context", not
  "permanently occupies Home") feeds a single surface that appears only when something
  matters and, if several things matter at once, shows ONE card that rotates between them
  (subtle auto-rotation; stops under reduced motion or when only one item remains).
  Deterministic priority: **Calendar** (event within 1h / ongoing) > **Battery**
  (≤20% or charging) > **Music** (playing/paused). Pure model in `ContextualEngine`
  (`home/Contextual.kt`) — no ML, no behavioral prediction, no tracking.
  - **Next Event** surfaces a *user* event within 1h or ongoing. Public-holiday and
    birthday calendars, and all-day events, are excluded by deterministic calendar
    metadata (`CalendarRelevance`) — AURA does NOT surface generic holidays as if they
    were personal meetings. Heuristic, documented as such; hide rather than clutter.
  - **Battery** is event-driven (sticky broadcast); hidden at healthy charge.
  - **Music** uses the Android media APIs. The preferred path is `MediaSessionManager` /
    `MediaController` for real playback state + metadata (title/artist/album/artwork) and
    `TransportControls`` for play/pause/skip. A third-party launcher without
    `MEDIA_CONTENT_CONTROL` cannot enumerate other apps' media sessions directly, so AURA
    uses a **narrow, user-granted, music-only** `AuraMediaNotificationListenerService`
    solely as the technical bridge that unlocks `getActiveSessions` + prompt detection.
    That listener never reads notification content — the media session supplies metadata.
    If the user does not grant access, Music falls back to an honest Playing/Paused
    snapshot (no title) via `AudioManager`. State is authoritative Android state; after a
    transport command we wait for the controller callback rather than mutating locally.
- **Monochrome icons**: DEFERRED. The previous experimental AURA monochrome
  transformation produced inconsistent results across applications (a dark circular
  background with a dimmed original icon for many apps) and was rejected rather than
  shipped as a partial visual system. AURA now shows normal application icons. No further
  icon-processing experiment is planned this phase.
- **Third-party widgets**: AppWidgetManager/AppWidgetHost as an advanced optional layer,
  strictly separate from AURA-native modules. No marketplace, no restyling.
- **Layout model**: fixed regions — Time/Presence, optional module/widget region,
  Command Bar (never movable; always reachable), Dock. Default stays sparse.
  Time + Presence + Command Bar + Dock alone is a valid final Home configuration.
- **Customization**: Theme (System/Dark/Light), Accent (Dynamic + curated set),
  Wallpaper (system picker entry), Animation (Standard/Reduced). Explicitly excluded:
  arbitrary fonts, icon marketplaces, free grids, custom animation editors,
  unrestricted color pickers, theme marketplaces.
- **App Library**: the A–Z rail is genuinely coupled to scroll position (the active letter
  follows the list; tapping a missing letter lands on the nearest available section), and the
  sheet renders as a dark, calm, translucent surface over the wallpaper rather than a separate
  bright screen. The greeting in the Time/Presence region is a deterministic local-time phrase
  (Good morning/afternoon/evening/night) — no inference about the user.
- **Wallpaper model**: AURA shows the **system** wallpaper behind a transparent
  window (`FLAG_SHOW_WALLPAPER`) — it never copies or renders the bitmap itself.
  Readability comes from an **adaptive dark scrim** whose alpha is chosen by the
  wallpaper's brightness (Dark 0.50 / Medium 0.70 / Bright 0.82 / VeryBright 0.90),
  drawn as a bottom-weighted vertical gradient so the Command Bar and dock stay
  readable on bright wallpapers. Brightness is estimated cheaply via
  `WallpaperManager.getWallpaperColors()` (API 27+, no bitmap decode) and cached by
  wallpaper id; changes are detected event-driven (broadcast + `ON_RESUME`), never
  polled. Full design in [`WALLPAPER.md`](WALLPAPER.md).
- **Persistence**: SharedPreferences-backed key/value store (`aura_home.xml`). Room deliberately NOT
  introduced — the layout model is a small ordered list plus scalar settings;
  SharedPreferences covers it exactly. Rationale documented in code.
- **Backup**: `allowBackup` remains true, but `aura_home.xml` is explicitly excluded
  via `fullBackupContent` (API 23+) and `dataExtractionRules` (API 31+). AURA Home
  customization is currently device-local. Backup/sync is not yet part of the product.

### Later candidates (not committed)

- Widget resize handles beyond responsive size reporting
- Presence sources beyond time-of-day greeting
- Search inside App Library parity checks

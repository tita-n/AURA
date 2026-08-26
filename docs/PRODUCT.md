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
| Native modules (Next Event, Battery, Music) | ✅ Implemented (contextual, optional) |
| Android widget hosting (AppWidgetHost) | ✅ Implemented (single, size-constrained) |
| Deterministic local-time greeting | ✅ Implemented |
| Monochrome app icons (IconProcessor) | ✅ Implemented |
| App Library A–Z rail + translucent surface | ✅ Implemented |
| Wallpaper cross-device reliability | ✅ Implemented |
| Reduced-motion (calm module animation) | ✅ Implemented |
| Customization (theme, accent, wallpaper entry, animation) | ✅ Implemented |
| Weather module | ❌ CUT — network-dependent; violates local-first |

## NOTIFICATION PANEL — REJECTED

**Status: REJECTED. This is an explicit product rejection, not a deferral.
Notification Phase 1.1 has been removed from the active roadmap and from the codebase.**

Reason:

- Android already handles notifications well. Users understand and expect the system shade.
- AURA must not compete with Android's notification shade, quick settings, or native permission UI.
- The subsystem added Notification Listener access (privacy risk), battery/OEM risk,
  background complexity, another UI surface — for little unique product value.
- AURA's specialization is intent resolution, app discovery, command execution,
  and the Home experience — not notification interception.

Consequences enforced in this repository:

- No `NotificationListenerService`, no listener access flow, no notification panel,
  no notification history, no grouping/priority logic, no notification-specific services.
- Home has no swipe-down notification zone. Notifications belong to the OS.
- Any future proposal to re-introduce notification interception requires reversing this
  document first.

---

## Roadmap

### Phase — HOME EXPERIENCE + CUSTOMIZATION + WIDGET ARCHITECTURE (active)

Goal: make AURA genuinely pleasant to live with every day. Not smarter — more livable.

- **Home edit mode**: long-press empty area → minimal AURA-native edit surface
  (add/remove/rearrange modules & widgets, appearance, dock).
- **Dock customization**: 0–4 installed apps, reorder, remove, tap-to-launch via the
  existing OpenApp execution path. Duplicates not permitted.
- **Native modules**: Next Event (calendar, contextual READ_CALENDAR), Battery
  (sticky broadcast, event-driven), Music (media-key transport, permission-free).
  Modules are *optional and enabled by the user*, but only *visible when relevant*:
  Next Event within 1h or ongoing, Battery ≤20% or charging, Music while playing.
  Relevance is pure and deterministic (see `ModuleRelevance`) — no behavioral inference,
  no tracking. Weather remains CUT. Modules are removable, local-first, event-driven.
- **Monochrome icons**: launcher icons are recolored to the single AURA tone via the
  native adaptive-monochrome layer (API 33+) when present, otherwise an AURA fallback
  luminance→alpha transform — processed off the main thread and cached. No AI, no
  edge detection, no per-device tuning.
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

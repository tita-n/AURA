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
| Home editing (edit mode) | 🚧 This phase |
| Dock customization (0–5 apps) | 🚧 This phase |
| Native modules (Next Event, Battery, Music) | 🚧 This phase |
| Android widget hosting (AppWidgetHost) | 🚧 This phase |
| Customization (theme, accent, wallpaper entry, animation) | 🚧 This phase |
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
- **Dock customization**: 0–5 installed apps, reorder, remove, tap-to-launch via the
  existing OpenApp execution path. Duplicates not permitted.
- **Native modules**: Next Event (calendar, contextual READ_CALENDAR), Battery
  (sticky broadcast, event-driven), Music (media-key transport, permission-free).
  Weather remains CUT. Modules are optional, removable, local-first, event-driven.
- **Third-party widgets**: AppWidgetManager/AppWidgetHost as an advanced optional layer,
  strictly separate from AURA-native modules. No marketplace, no restyling.
- **Layout model**: fixed regions — Time/Presence, optional module/widget region,
  Command Bar (never movable; always reachable), Dock. Default stays sparse.
  Time + Presence + Command Bar + Dock alone is a valid final Home configuration.
- **Customization**: Theme (System/Dark/Light), Accent (Dynamic + curated set),
  Wallpaper (system picker entry), Animation (Standard/Reduced). Explicitly excluded:
  arbitrary fonts, icon marketplaces, free grids, custom animation editors,
  unrestricted color pickers, theme marketplaces.
- **Persistence**: SharedPreferences-backed key/value store. Room deliberately NOT
  introduced — the layout model is a small ordered list plus scalar settings;
  SharedPreferences covers it exactly. Rationale documented in code.

### Later candidates (not committed)

- Widget resize handles beyond responsive size reporting
- Presence sources beyond time-of-day greeting
- Search inside App Library parity checks

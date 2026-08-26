# Wallpaper — design & architecture

AURA is a launcher and shows the user's own wallpaper behind Home. This document
explains how that works and how AURA stays readable without hiding the wallpaper.

## Model

AURA uses the **system wallpaper**, not a copy of it.

- `MainActivity` makes its window **transparent** and sets
  `WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER`. The system then draws the
  wallpaper behind the activity — the same wallpaper every other launcher shows.
- AURA never decodes the wallpaper bitmap to display it. It only estimates the
  wallpaper's **brightness** to choose how dark to make its own overlay.

This keeps AURA honest: the wallpaper is the user's, rendered by Android, and
AURA layers a dark "treatment" on top for legibility. AURA does **not** become a
wallpaper app.

## Adaptive dark treatment

The goal is the combination:

```
USER WALLPAPER  +  AURA-CONTROLLED DARK TREATMENT  =  AURA HOME
```

- **Dark wallpaper** → low treatment (wallpaper shows mostly naturally).
- **Medium wallpaper** → moderate treatment.
- **Bright wallpaper** → strong treatment.
- **Very bright / high-contrast wallpaper** → strongest treatment.

The treatment is a `surfaceBase` scrim drawn in Compose over the transparent
window. It is a **vertical gradient**: the top uses the brightness-derived alpha,
and the bottom is slightly stronger so the Command Bar and dock stay readable on
bright wallpapers. Nothing ever makes the top *lighter* than the chosen alpha, so
contrast is never reduced below the validated floor.

### Brightness → alpha

| Wallpaper class | Scrim alpha (top) | Bottom bonus |
| --- | --- | --- |
| Dark      | 0.50 | +0.12 |
| Medium    | 0.70 | +0.12 |
| Bright    | 0.82 | +0.12 |
| VeryBright| 0.90 | +0.12 |

Alphas are chosen so `textPrimary` still meets WCAG 4.5:1 on a **white** wallpaper.
Darker wallpapers need far less because the effective background is already dark.
The math is asserted in `WallpaperContrastTest` and `WallpaperTreatmentTest`.

### How brightness is estimated (cheap, local-first)

`WallpaperAnalyzer` (in `platform/android`) estimates brightness:

- **API 27+:** `WallpaperManager.getWallpaperColors()` — computed by the system,
  **no bitmap decode**, works for live wallpapers. We average the luminance of the
  available primary/secondary/tertiary colors.
- **Below API 27:** no permission-free sampler exists, so AURA uses a conservative
  "bright" treatment (strong scrim) — still readable, wallpaper still shown.

No bitmap sampling is used because reading the full wallpaper needs a permission
launchers do not hold (`MANAGE_EXTERNAL_STORAGE` / `READ_WALLPAPER_INTERNAL`), and
decoding large wallpapers on every recomposition would be wasteful.

### Caching & refresh (no polling)

- The result is cached by `WallpaperManager.getWallpaperId()` so recompositions and
  repeated calls never re-run the work.
- Wallpaper changes are detected **event-driven**, not polled:
  - a `BroadcastReceiver` for `android.intent.action.WALLPAPER_CHANGED`, and
  - `Lifecycle.Event.ON_RESUME` (the system wallpaper picker is a separate activity,
    so AURA pauses and resumes when the user returns).

## Code location

- `home/WallpaperTreatment.kt` — **pure** (`WallpaperTreatmentResolver`,
  `WallpaperBrightness`, `WallpaperTreatment`). No Android dependencies; fully
  unit-tested. This is where the brightness→alpha mapping lives.
- `platform/android/WallpaperAnalyzer.kt` — Android boundary: samples brightness
  via `WallpaperManager`, caches by wallpaper id, exposes `analyze()` (suspends on
  `Dispatchers.IO`).
- `ui/home/HomeScreen.kt` — draws the adaptive gradient scrim when
  `wallpaperEnabled`.
- `MainActivity.kt` — sets the transparent window + `FLAG_SHOW_WALLPAPER`, wires the
  analyzer, and passes the treatment down.

## Accessibility

- Contrast is validated per brightness class (WCAG 4.5:1 body / 3:1 large).
- No semantics, animation, or layout changes — TalkBack, dynamic font scaling,
  RTL, and reduced motion are unaffected.
- Readability relies on luminance contrast, not color alone (grayscale-safe).

## What this is NOT

- Not a wallpaper store, server, or marketplace.
- Not an AI/image-analysis pipeline.
- Not a permanent black background — the user's wallpaper always shows through.
- Not continuous polling — refresh is event-driven.

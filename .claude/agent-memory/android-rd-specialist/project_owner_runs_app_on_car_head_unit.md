---
name: owner-runs-app-on-car-head-unit
description: Owner uses FastMediaSorter on an in-car Android head unit - low-density wide landscape is a real target config, not a hypothetical
metadata:
  type: project
---

The owner runs FastMediaSorter on an in-car Android head unit and reports UI defects from it (first seen 2026-07-28, the S1258 top-panel icon/label alignment report came from a photo of the car screen). Dark theme, RU locale, permanent landscape, low density, wide screen - roughly `1024x600 @160dpi`.

**Why:** this config is nothing like the S21/S20 FE test phones. At 160dpi a 4px offset lands against a 10px cap height, so a defect that is invisible on a 420dpi phone is glaring in the car. Several width/alignment tickets (S1037, S1049, S1068) were tuned on phone densities only.

**How to apply:**
- Treat low-density wide landscape as a first-class review config for any main-screen or player chrome change, not an edge case.
- Reproduce it on the AVD with `wm size 1024x600` + `wm density 160` (keep rotation 0 - see [[reference_emulator_capture_family_testing]]), then `wm size reset` + `wm density reset` afterwards.
- That geometry is 1024dp wide, so it renders `layout-w600dp/*`, the same bucket that wins over `layout-land/*` on landscape phones - see [[main-top-panels-width-grid]].
- When the owner sends a photo instead of a screenshot, he is on the head unit; ask for the defect location rather than guessing from the blurry photo, then reproduce at that geometry and measure in pixels.

---
name: owner-runs-app-on-car-head-unit
description: Owner uses FastMediaSorter on an in-car Android head unit - low-density wide landscape is a real target config, not a hypothetical
metadata:
  type: project
---

The owner runs FastMediaSorter on an in-car Android head unit and reports UI defects from it (first seen 2026-07-28, the S1258 top-panel icon/label alignment report came from a photo of the car screen). Dark theme, RU locale, permanent landscape, low density, wide screen - roughly `1024x600 @160dpi`. **The head unit runs Android 8** (owner, 2026-08-09) - API 26/27, i.e. exactly the `standard` minSdk floor, so it is also the only real device proving the minSdk-26 path.

**Why:** this config is nothing like the S21/S20 FE test phones. At 160dpi a 4px offset lands against a 10px cap height, so a defect that is invisible on a 420dpi phone is glaring in the car. Several width/alignment tickets (S1037, S1049, S1068) were tuned on phone densities only.

**How to apply:**
- Treat low-density wide landscape as a first-class review config for any main-screen or player chrome change, not an edge case.
- Reproduce it on the AVD with `wm size 1024x600` + `wm density 160` (keep rotation 0 - see [[reference_emulator_capture_family_testing]]), then `wm size reset` + `wm density reset` afterwards.
- That geometry is 1024dp wide, so it renders `layout-w600dp/*`, the same bucket that wins over `layout-land/*` on landscape phones - see [[main-top-panels-width-grid]].
- When the owner sends a photo instead of a screenshot, he is on the head unit; ask for the defect location rather than guessing from the blurry photo, then reproduce at that geometry and measure in pixels.
- **Ask which device before measuring anything.** On S1444 (2026-08-09) a whole measurement round went into the wrong row on a 420dpi phone before he said "on the car head unit" - the config is where these alignment defects live, not the row. Any "labels are off" report defaults to the head-unit geometry first.
- **A native head-unit AVD now exists: `S1444_headunit_api26`** - Android 8.0.0, physical 1024x600 @160dpi (config.ini pinned, not a `wm` override), created 2026-08-09 from the already-installed `system-images;android-26;google_apis_playstore;x86`. No download needed; `avdmanager` needs `$env:JAVA_HOME` pointed at a JDK 17+ (`C:\Program Files\Java\jdk-21.0.11`) or it refuses. Prefer it over `wm size`+`wm density` when the report is platform-sensitive - it proves the API 26 path too.
- **Ask for the installed app version BEFORE spending a measurement round.** The owner picked that over a screenshot as the proof he would fetch (2026-08-10, S1444 quiz) - one number either closes the ticket by update or eliminates a whole branch of explanations, and he cannot always get to the car quickly. He also ruled the fallback: a head-unit report that never produces evidence is closed as not reproducible and archived, not patched blind - a fixed pixel nudge would break alignment on every config that measures correct today.
- **The geometry alone does not reproduce his alignment reports.** On that AVD *and* on Android 13 at the same geometry, the resource-type tab row measured icon-vs-label centre deltas of ≤0.5 px (S1444, build v2.60.8082.309). So when he reports a misalignment, do not assume the config explains it - ask for a screenshot from the unit and the **app version installed there**, since a build older than the fix he is describing is the cheaper explanation.

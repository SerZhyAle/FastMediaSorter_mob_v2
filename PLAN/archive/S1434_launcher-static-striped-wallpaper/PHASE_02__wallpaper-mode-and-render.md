# Phase 02 - Wallpaper mode token, mapping and desktop rendering

**Strategic spec:** [`../S1434_launcher-static-striped-wallpaper.md`](../S1434_launcher-static-striped-wallpaper.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Introduce the `STRIPES` settings token and the `LauncherWallpaper.Stripes` variant, map one to the other through a pure function, and render the variant as a frozen frame that is re-rolled when the launcher comes back to the foreground.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `AudioWaveParticleView.showFrozenFrame()` exists (Phase 01).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 515 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherWallpaper.kt` | Modified | ≤ 70 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherWallpaperManager.kt` | Modified | ≤ 130 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | ≤ 480 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). `AppSettings.kt` measures 503 lines, so Step 02.1 backs it up first.
>
> **Flavor placement.** The manager and the view model already live in the `launcherEnabled` source set, which only `standard` and `noLegal` mount (`app_v2/build.gradle.kts`), matching strategic §3.2. The token and the sealed variant stay in `src/main` because every flavor compiles `AppSettings` and the domain model. No `BuildConfig` flavor guard is introduced.

---

## Steps

### Step 02.1 - Back up `AppSettings.kt` and add the `STRIPES` token

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `AppSettings.kt` to `temp/S1434/AppSettings.<yyyyMMdd-HHmmss>.kt.bak` first. Then add `const val LAUNCHER_WALLPAPER_STRIPES = "STRIPES"` to the companion with a KDoc line naming it the frozen frame of the branded animation, and append it to `LAUNCHER_WALLPAPER_MODES` after `LAUNCHER_WALLPAPER_IMAGE`. Do not reorder the three existing entries.

**Why:**

Strategic §3.2 needs no migration precisely because the mode is a token whose membership in `LAUNCHER_WALLPAPER_MODES` is what the repository checks before falling back to the branded default, and §3.3 records that the new option is appended so no existing list position moves.

**Verification:**

- `Glob` - `temp/S1434/AppSettings.*.kt.bak` matches at least one file.
- `Grep` - `LAUNCHER_WALLPAPER_STRIPES = "STRIPES"` matches exactly once.
- `Grep` - inside the `LAUNCHER_WALLPAPER_MODES` list, `LAUNCHER_WALLPAPER_STRIPES` is the last entry and `LAUNCHER_WALLPAPER_BRANDED` is still the first.

**Status:** `[ ]` not done

---

### Step 02.2 - Add the `Stripes` variant, a pure mapper, and the render branches

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherWallpaper.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherWallpaperManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `LauncherWallpaper.kt` add `data object Stripes : LauncherWallpaper` with a KDoc line stating it is one settled frame of the branded animation, and a companion object carrying `fun fromMode(mode: String, imagePath: String, imageExists: Boolean): LauncherWallpaper`. That function maps `LAUNCHER_WALLPAPER_NONE` to `None`, `LAUNCHER_WALLPAPER_STRIPES` to `Stripes`, `LAUNCHER_WALLPAPER_IMAGE` to `Image(imagePath)` when `imagePath` is non-blank and `imageExists` is true, and every other case - an unknown token, or the image mode with a missing file - to `Branded`; it performs no I/O, the caller probes the file and passes the result in. In the same commit extend both sealed `when` blocks in `LauncherWallpaperManager`: `render` clears the image layer, makes the waves layer visible and calls `wavesLayer.showFrozenFrame()`, and `onStart` gets its own `Stripes` branch. Both files change together because adding a variant to the sealed interface makes those `when` statements non-exhaustive, which is a compile error until the branches exist.

**Why:**

Strategic §7 names the token `when` branching as the highest-probability risk, since a missed branch silently behaves like the branded animation, and its mitigation is a unit test over the mapping, which requires the mapping to be a pure function rather than a lambda inside a view model.

**Verification:**

- `Grep` - `data object Stripes : LauncherWallpaper` matches exactly once.
- `Grep` - `fun fromMode(` matches exactly once in `LauncherWallpaper.kt`.
- `Grep` - `File(` returns zero hits in `LauncherWallpaper.kt`.
- `Grep` - `LauncherWallpaper.Stripes` matches at least twice in `LauncherWallpaperManager.kt`.
- `Grep` - `showFrozenFrame()` matches at least once in `LauncherWallpaperManager.kt`.

**Status:** `[ ]` not done

---

### Step 02.3 - Re-roll the frame only on a real return to the launcher

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherWallpaperManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add a private `returnedFromBackground` flag set in `onStop()` and cleared at the end of `onStart()`. The `Stripes` branch of `onStart()` calls `showFrozenFrame()` only when that flag is set. Comment why: the first foreground edge of a session is already covered by the render that put the layer on screen, so re-rolling there would paint two frames for one appearance.

**Why:**

Strategic §3.3 rules that the new frame is tied to returning after leaving rather than to any foreground callback, because a launcher already on screen never lost focus and therefore never "gets it again", and §2 requires the frame to stay put while the user works with the launcher.

**Verification:**

- `Grep` - `returnedFromBackground` matches exactly three times in `LauncherWallpaperManager.kt` (declaration, set in `onStop`, cleared in `onStart`).
- `Grep` - `showFrozenFrame()` inside `onStart` is guarded by `returnedFromBackground`.

**Status:** `[ ]` not done

---

### Step 02.4 - Delegate the view model mapping to `fromMode`

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Replace the inline `when (settings.launcherWallpaperMode)` block inside the `wallpaper` flow with a single `LauncherWallpaper.fromMode(settings.launcherWallpaperMode, settings.launcherWallpaperImagePath, File(settings.launcherWallpaperImagePath).isFile)` call. Keep `distinctUntilChanged()`, `flowOn(Dispatchers.IO)` and the eager `stateIn` unchanged, and keep the KDoc explaining why the existence probe runs off the main thread.

**Why:**

Strategic §5.2 routes the setting through the view model unchanged, so moving the mapping must not move the disk probe off the IO dispatcher the existing KDoc documents.

**Verification:**

- `Grep` - `LauncherWallpaper.fromMode(` matches exactly once in `LauncherHomeViewModel.kt`.
- `Grep` - `AppSettings.LAUNCHER_WALLPAPER_` returns zero hits in `LauncherHomeViewModel.kt`.
- `Grep` - `flowOn(Dispatchers.IO)` still present in the `wallpaper` flow.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` returns zero hits in every file this phase modified.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

The token is live end to end but unreachable from the UI: `LAUNCHER_WALLPAPER_MODES` now has four entries while the settings row still offers three labels, so `renderWallpaperRow` can select an index the dropdown does not have until Phase 03 adds the fourth label.

---

## Rollback Plan

Revert phase commit(s) - no data migration; a stored `STRIPES` token written by a reverted build degrades to the branded default through the existing unknown-token fallback.

# Phase 02 - Applier branches for the newly presettable fields

**Strategic spec:** [`../S1216_device-profile-preset-matrix-coverage.md`](../S1216_device-profile-preset-matrix-coverage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 5 / 5
**Started:** 2026-07-27
**Completed:** 2026-07-27

---

## Objective

Give an applier branch to every field the strategic spec declares presettable but that the applier cannot currently coerce, gate the launcher fields on build availability, and delete the three dead legacy branches - so Phase 03 can write values that actually take effect.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] The Phase 01 checker output lists the rows still lacking an applier branch - use it as the work queue. It listed 26 missing rows and 14 rows without a branch; that was the queue.
- [x] Working tree is clean or on a feature branch. On `DEBUG-v030`; the tree carries other tickets' WIP, which is normal here, so closure ran with `-ScopeToFile`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt` | Modified | ≤ 430 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ApplyProfilePresetUseCaseTest.kt` | Modified | ≤ 400 |

> `DeviceProfilePresetApplier.kt` is 306 lines today and stays well under the 500-line backup threshold and the 1500-line split threshold after this phase. No new class is introduced - the file keeps its single responsibility (coerce one raw cell onto one field).
>
> **Flavor placement.** No flavor-specific file is added. The launcher gate is consumed through the existing `src/main` capability seam `domain/launcher/LauncherModeContract`, whose real and no-op bindings already live in `src/launcherEnabled/` and `src/launcherDisabled/`. Do not write a `BuildConfig` flavor guard into `src/main` - CLAUDE.md Rule 14 forbids it and `/spec-dev` hard-stops on it.

---

## Steps

### Step 02.1 - Remove the three stale legacy gesture branches

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Delete the `screenshotGestureActionDown`, `screenshotGestureActionRight` and `screenshotGestureActionUp` branches together with the now-unused `toScreenshotGestureActionOrNull` helper if nothing else calls it. These name fields that no longer exist on `AppSettings`; all three CSV rows are empty, so the redirect onto the `LEFT_TOP` band is dead code (CLAUDE.md Rule 20). Keep the `ScreenshotGestureAction` import only if step 02.2 still needs it.

**Verification:**

- `Grep` - `screenshotGestureActionDown` returns zero hits in the file.
- `Grep` - `screenshotGestureActionRight` returns zero hits in the file.
- `Grep` - `screenshotGestureActionUp` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 02.2 - Add the per-zone screenshot gesture branches

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add boolean branches for the four `screenshotGestureZone{LeftTop,LeftBottom,RightTop,RightBottom}Enabled` fields and for the four `screenshotGestureZone*StripVisible` fields. Add `ScreenshotGestureAction` enum branches for the twelve `screenshotGesture{Zone}{Down,Right,Up}` fields, parsing by exact enum name and skipping on an unknown value exactly like the other enum branches. Do NOT add branches for the `screenshotGesturePayload*` fields - Phase 01 registered them as non-presettable pointers.

**Verification:**

- `Grep` - `"screenshotGestureLeftTopDown"` matches exactly once.
- `Grep` - `"screenshotGestureRightBottomUp"` matches exactly once.
- `Grep` - `screenshotGesturePayload` returns zero hits in the file.
- Value equality - the count of `screenshotGestureZone` branch labels in the file is 8 (4 enabled + 4 strip-visible).

**Status:** `[x]` done

---

### Step 02.3 - Add the streams, panel and privacy branches

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add enum branches for `streamsDefaultSort` (`StreamDefaultSort`), `streamsDefaultMediaFilter` (`StreamMediaTypeFilter`), `streamsCatalogRefreshPolicy` (`StreamsCatalogRefreshPolicy`), `streamsDefaultAudioLanguage` and `streamsDefaultSubtitleLanguage` (`StreamTrackLanguage`), following the existing safe-parse shape - keep the current value on an unknown name, never coerce to the enum default. Add boolean branches for `showProgramsPanelInMainWindow`, `showStreamsPanelInMainWindow`, `screenRecordingEnabled`, `resourceTypeTabCollapsed`, `programsPanelCollapsed`, `streamsPanelCollapsed`, and for `secureSensitiveScreens` - strategic §6.2 resolved that a profile may set it in both directions. Add an int branch for `cameraAspectRatio`.

**Verification:**

- `Grep` - `"streamsCatalogRefreshPolicy"` matches exactly once.
- `Grep` - `"secureSensitiveScreens"` matches exactly once.
- `Grep` - `StreamsCatalogRefreshPolicy.valueOf` matches in the file.
- `Grep` - `Log\.d\(` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 02.4 - Add the launcher branches behind the availability seam

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Inject `LauncherModeContract` into the applier and add branches for `launcherDensityFactor` (Float), `launcherTaskbarShowRecents`, `launcherTaskbarShowPinned`, `launcherTaskbarShowTray`, `launcherReplaceSystemStatusArea`, `launcherDesktopLocked` (Booleans) and `launcherWallpaperMode` (String token validated against `AppSettings.LAUNCHER_WALLPAPER_MODES`). Every launcher branch returns the settings unchanged when `isAvailableInBuild` is false, logged through the existing skip helper - a build without the home surface must not persist launcher values (strategic ADR-5). Do not add branches for `launcherRotationHintShown` or `launcherWallpaperImagePath`; Phase 01 registered both as non-presettable. Constructor gains one parameter, so the DI graph and the existing unit tests must be updated in step 02.5.

**Verification:**

- `Grep` - `LauncherModeContract` matches in the file.
- `Grep` - `"launcherDesktopLocked"` matches exactly once.
- `Grep` - `launcherRotationHintShown` returns zero hits in the file.
- `Grep` - `LAUNCHER_WALLPAPER_MODES` matches in the file.
- `Grep` - `BuildConfig` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 02.5 - Extend the applier unit tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/ApplyProfilePresetUseCaseTest.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> The applier constructor gained a `LauncherModeContract` parameter, so every existing construction site in the test must be updated (CLAUDE.md constructor-change rule). Add cases for: an enum branch keeps the current value on an unknown name; a launcher field is ignored when the seam reports the surface unavailable and applied when it reports available; `secureSensitiveScreens` applies in both directions; a `screenshotGesturePayload*` cell is skipped. Note that roughly 26 unit tests in this module fail for reasons predating this ticket - compare against the pre-change baseline rather than requiring a fully green suite.

**Verification:**

- `Grep` - `LauncherModeContract` matches in the test file.
- `Grep` - `secureSensitiveScreens` matches in the test file.
- Value equality - `.\a.ps1 fu` shows no NEW failing test relative to the pre-change baseline.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `Grep` for `Log\.d\(` in every touched `.kt` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` - the applier's constructor signature changed.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Every field Phase 03 will fill now has a coercion path, and the Phase 01 checker's "row without an applier branch" list should contain only registry-backed rows. The launcher rows are safe to fill for all profiles: builds without the home surface drop them at apply time rather than requiring a per-flavor CSV.

---

## Rollback Plan

Revert phase commit(s) - no data migration, no schema change, no user-facing surface. The applier is additive apart from three dead branches whose CSV rows were empty.

**Measured effect of the phase.** Applier branches went from 159 to 196 (+40 added, 3 dead ones removed). The coverage checker now reports zero rows without a branch, so every field Phase 03 will fill has a working coercion path. Unit suite for the applier: 15 tests, 0 failures - one pre-existing case had to move off the removed screenshotGestureActionDown alias onto the real field name.



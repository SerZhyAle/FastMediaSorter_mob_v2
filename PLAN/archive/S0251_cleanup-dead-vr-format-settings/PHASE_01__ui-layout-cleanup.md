# Phase 01 - UI Layout Cleanup (Settings → Media → Video)

**Strategic spec:** [`../S0251_cleanup-dead-vr-format-settings.md`](../S0251_cleanup-dead-vr-format-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-05-19
**Completed:** 2026-05-19

---

## Objective

Strip the three dead VR forced-format controls (`spinnerVrForcedFormat`, `spinnerVrForcedSphericalFormat`, `switchVrRememberFormat`) and the "VR" subgroup header + help icon from `fragment_settings_video.xml` in both portrait and landscape variants. Remove their handlers and observe-block references from `VideoSettingsFragment.kt`. Place the two remaining live switches (`switchPlayerShowFps`, `switchAllowSeparateWindow`) per the `/ui-clarify` decision recorded in step 01.3.

---

## Prerequisites

- [ ] Strategic spec is `Approved` or `Tactical`.
- [ ] `/ui-clarify` decision recorded for Pre-Implementation Blocker (§6.2): placement of `switchPlayerShowFps` and `switchAllowSeparateWindow` after subgroup header removal.
- [ ] Working tree clean or on feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_video.xml` | Modified | < current size |
| `app_v2/src/main/res/layout-land/fragment_settings_video.xml` | Modified | < current size |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt` | Modified | ≤ 250 (currently 319 - shrinks by ~85 lines) |

> Both portrait and landscape layout files exist - landscape parity is MANDATORY (CLAUDE.md Rule 12). Every step that touches the portrait layout MUST mirror the change in the landscape file.

---

## Steps

### Step 01.1 - Remove `layoutVrSettings` block from portrait layout

**Files:** `app_v2/src/main/res/layout/fragment_settings_video.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Open `fragment_settings_video.xml` (portrait variant). Delete the entire `<LinearLayout android:id="@+id/layoutVrSettings" ..>` block (currently lines 175-365 in this file). This block contains: the VR section header with `iconHelpVrSettings`, both spinners (`spinnerVrForcedFormat`, `spinnerVrForcedSphericalFormat`) with their labels and descriptions, the `switchVrRememberFormat` row, the `switchPlayerShowFps` row, and the `switchAllowSeparateWindow` row. The two switches (`switchPlayerShowFps`, `switchAllowSeparateWindow`) are re-added in step 01.3 at the location chosen by `/ui-clarify`. Do not touch the snapshot-format block or any block above it.

**Verification:**

- `Grep -n` for `@\+id/layoutVrSettings` in this file → 0 hits.
- `Grep -n` for `spinnerVrForcedFormat` in this file → 0 hits.
- `Grep -n` for `spinnerVrForcedSphericalFormat` in this file → 0 hits.
- `Grep -n` for `switchVrRememberFormat` in this file → 0 hits.
- `Grep -n` for `iconHelpVrSettings` in this file → 0 hits.
- `Grep -n` for `switchPlayerShowFps` in this file → 0 hits (re-added in step 01.3).
- `Grep -n` for `switchAllowSeparateWindow` in this file → 0 hits (re-added in step 01.3).

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 7/7 PASS. Block `layoutVrSettings` removed from portrait layout. File shrank by ~192 lines.

---

### Step 01.2 - Remove `layoutVrSettings` block from landscape layout

**Files:** `app_v2/src/main/res/layout-land/fragment_settings_video.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> Open the landscape variant. Delete the same `<LinearLayout android:id="@+id/layoutVrSettings" ..>` block (currently lines 91-155 in this file). Mirror the portrait deletion exactly. The two live switches re-appear in step 01.3.

**Verification:**

- `Grep -n` in this file for `@\+id/layoutVrSettings` → 0 hits.
- `Grep -n` for `spinnerVrForcedFormat`, `spinnerVrForcedSphericalFormat`, `switchVrRememberFormat`, `iconHelpVrSettings`, `switchPlayerShowFps`, `switchAllowSeparateWindow` → all 0 hits in this file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 7/7 PASS. Block `layoutVrSettings` removed from landscape layout. Parity with portrait achieved.

---

### Step 01.3 - Re-add `switchPlayerShowFps` and `switchAllowSeparateWindow` inside Video card

**Files:** `app_v2/src/main/res/layout/fragment_settings_video.xml`, `app_v2/src/main/res/layout-land/fragment_settings_video.xml`
**Depends on:** Steps 01.1, 01.2

**Prompt for developer:**

> Owner-resolved placement (§6.2): re-add both switches at the END of the existing Video card content, immediately AFTER the snapshot-format (PNG/JPG) `<LinearLayout>` block but BEFORE the closing root `</LinearLayout>` of `fragment_settings_video.xml`. No new subheader, no new card, no `layoutVrSettings` wrapper. Each switch is a standalone row at the root indent level of the Video card (same indentation as `btnSetDefaultVideoPlayer`).
>
> Mirror the structure in BOTH portrait and landscape variants. Preserve the existing ids (`switchPlayerShowFps`, `switchAllowSeparateWindow`), existing strings (`settings_player_show_fps`, `settings_player_show_fps_desc`, `setting_allow_separate_window`), and the existing structural styling (SwitchMaterial + title TextView + optional description TextView, same margin/gravity attributes as surrounding controls). Both switches must remain focus-reachable from keyboard / D-pad / mouse (CLAUDE.md Rule 17): `focusable="true"`, `clickable="true"`, sensible focus chain. Do not introduce any new strings or arrays - this step only relocates existing controls.
>
> Reference structure (portrait, schematic):
>
> ```xml
> <LinearLayout android:id="@+id/layoutSnapshotResourceSelector" ..> .. </LinearLayout>
> <LinearLayout (frame format PNG/JPG)> .. </LinearLayout>
> <!-- After snapshot block, before closing root: -->
> <LinearLayout (switchPlayerShowFps row)> .. </LinearLayout>
> <LinearLayout (switchAllowSeparateWindow row)> .. </LinearLayout>
> ```

**Verification:**

- `Grep -n` for `switchPlayerShowFps` in portrait layout → exactly 1 hit (the new row).
- `Grep -n` for `switchAllowSeparateWindow` in portrait layout → exactly 1 hit.
- Same predicates for the landscape layout - exactly 1 hit each.
- `Grep -n` in both layouts for `settings_player_show_fps`, `setting_allow_separate_window` → still present in the new locations.
- Visual structural check: parent of the two switches in portrait matches parent in landscape (same container, same indentation level).

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 5/5 PASS. Switches relocated as root-level siblings of the snapshot block in both portrait (multi-line style) and landscape (single-line style). Added `focusable="true"` and `clickable="true"` per CLAUDE.md Rule 17.

---

### Step 01.4 - Drop dead handlers and observe references from `VideoSettingsFragment.kt`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt`
**Depends on:** Steps 01.1, 01.2, 01.3

**Prompt for developer:**

> In `VideoSettingsFragment.kt` perform the following deletions in one pass:
>
> 1. Delete the entire `setupVrSettings()` private function (currently lines 187-249).
> 2. Delete the call site that invokes it inside `setupViews()` (currently lines 117-126: the comment block plus `if (BuildConfig.SUPPORT_VR_PLAYER) { Timber.d(..); setupVrSettings() }`).
> 3. Delete the VR-settings update block inside `observeData()` (currently lines 291-303: the entire `if (BuildConfig.SUPPORT_VR_PLAYER) { .. }` clause, including the resources read for `vr_forced_format_values`, `vr_forced_spherical_format_values`, the two `setSelection` calls, and the two `binding.switch* = settings.*` assignments).
> 4. Keep the `switchPlayerShowFps` and `switchAllowSeparateWindow` listeners and observe-bindings - rebind them as standalone observe statements after step 01.3 layout change. Their listeners that currently live INSIDE `setupVrSettings()` (lines 234-248) must move OUT of `setupVrSettings()` to a new `setupPlayerExtras()` helper (or directly into `setupViews()`), invoked unconditionally (no `BuildConfig.SUPPORT_VR_PLAYER` guard).
> 5. Verify the `import com.sza.fastmediasorter.BuildConfig` import is still required somewhere in the file - if not, remove it. Same for `import timber.log.Timber` (the only use was the `Timber.d("S0250: ..")` in step 2's removed block).

**Verification:**

- `Grep -n "setupVrSettings"` in this file → 0 hits.
- `Grep -n "vrForcedPlatFormat"` in this file → 0 hits.
- `Grep -n "vrForcedSphericalFormat"` in this file → 0 hits.
- `Grep -n "vrRememberFileFormat"` in this file → 0 hits.
- `Grep -n "vr_forced_format_values"` in this file → 0 hits.
- `Grep -n "vr_forced_spherical_format_values"` in this file → 0 hits.
- `Grep -n "switchPlayerShowFps"` in this file → at least 2 hits (listener + observe-block guard / assignment).
- `Grep -n "switchAllowSeparateWindow"` in this file → at least 2 hits.
- `Grep -n "Log\.d\("` in this file → 0 hits (Timber-only policy).
- `Grep -n "SUPPORT_VR_PLAYER"` in this file → 0 hits (gate is gone).
- Module compiles - run `/build` with variants `standardDebug`, `vrDebug`, `noLegalDebug`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Verification 10/10 PASS. Deleted `setupVrSettings()` (63 lines), the VR if-block in `setupViews()`, and the VR if-block in `observeData()`. Created `setupPlayerExtras()` for the two unconditional switch listeners. Added observe-block guard for `switchPlayerShowFps` matching the Material-switch pattern already in use for `switchAllowSeparateWindow`. Removed unused imports `BuildConfig`, `Timber`. File now 232 lines (was 319). Build deferred to Phase Done Criteria.

---

### Step 01.5 - Update dev log entries for the three modified files

**Files:** dev log only
**Depends on:** Steps 01.1 - 01.4

**Prompt for developer:**

> Run `scripts/add_to_dev_log.ps1` once per modified file. The targets:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/fragment_settings_video.xml" "S0251" "Phase 01: remove dead VR forced-format block; relocate 2 live switches per /ui-clarify"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/fragment_settings_video.xml" "S0251" "Phase 01: landscape parity - remove dead VR forced-format block; relocate 2 live switches"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt" "S0251" "Phase 01: drop setupVrSettings + dead observe-block; relocate live switch listeners"
> ```

**Verification:**

- `Grep -n "S0251.*Phase 01"` in `dev/CHANGELOG.md` → at least 3 hits (one per file).

**Status:** `[x] done`

**Step Log:**

- 2026-05-19 - Dev log was recorded per-step (steps 01.1 - 01.4). Total 4 S0251 Phase 01 entries in CHANGELOG (portrait, landscape, fragment, and step-01.3 covered two files → 5 entries).

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly). Variants: `standardDebug`, `vrDebug`, `noLegalDebug`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] On a manual device run of the `vrDebug` build, Settings → Media → Video card no longer shows the three removed controls or the "VR" subgroup header.

---

## Handoff Notes to Next Phase

After Phase 01 completes, `VideoSettingsFragment.kt` no longer reads `settings.vrForcedPlatFormat`, `settings.vrForcedSphericalFormat`, or `settings.vrRememberFileFormat`. The `AppSettings` model still carries those fields, but they are now write-only-via-default - safe to delete in Phase 02. No other source set references these fields directly in the settings UI layer.

---

## Rollback Plan

Revert the three file diffs - no data migration or persisted-state change happened in this phase. DataStore keys and AppSettings fields still exist, so old layouts would still hydrate without error.

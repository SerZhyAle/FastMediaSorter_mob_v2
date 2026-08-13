# Phase 02 — Layout: inline buttons for image-edit commands

**Strategic spec:** [`../S0217_bugfix-image-viewer-toolbar-overflow.md`](../S0217_bugfix-image-viewer-toolbar-overflow.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Add five `ImageButton` views — `btnOpenInSeparateWindowCmd`, `btnCropCmd`, `btnCropToFileCmd`, `btnCompressCopyCmd`, `btnDrawOverlayCmd` — to both the portrait and landscape variants of `activity_player_unified.xml`. Each button mirrors the existing `btnPrintCmd` pattern: 40 dp size, default `visibility="gone"`, tinted via `selector_player_button_tint`, `contentDescription` bound to the matching string resource. No controller hookup yet — views exist purely as scaffolding for Phase 03.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_player_unified.xml` | Modified | +5 lines |
| `app_v2/src/main/res/layout-land/activity_player_unified.xml` | Modified | +5 lines |

> Both files exceed 500 lines — create a timestamped backup in `temp/` before editing each one (rule 5).

---

## Steps

### Step 02.1 — Add 5 inline buttons to portrait `activity_player_unified.xml`

**Files:** `app_v2/src/main/res/layout/activity_player_unified.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> Insert five `ImageButton` entries immediately after `btnPrintCmd` (currently at line ~103) and before `btnUndoCmd`, inside the same horizontal `LinearLayout`. Use the exact same attribute set as `btnPrintCmd`: `android:layout_width="@dimen/player_cmd_button_size"`, `android:layout_height="@dimen/player_cmd_button_size"`, `android:background="?attr/selectableItemBackgroundBorderless"`, `android:visibility="gone"`, `app:tint="@color/selector_player_button_tint"`, `android:scaleType="centerInside"`, `android:padding="@dimen/player_button_padding"`. IDs and resources per row:
>
> | `android:id` | `android:src` | `android:contentDescription` |
> |---|---|---|
> | `@+id/btnOpenInSeparateWindowCmd` | `@drawable/ic_open_in_browse` | `@string/action_open_in_separate_window` |
> | `@+id/btnCropCmd` | `@drawable/ic_crop` | `@string/menu_crop` |
> | `@+id/btnCropToFileCmd` | `@drawable/ic_crop_to_file` | `@string/menu_crop_to_file` |
> | `@+id/btnCompressCopyCmd` | `@drawable/ic_compress` | `@string/menu_compress_copy` |
> | `@+id/btnDrawOverlayCmd` | `@drawable/ic_draw_overlay` | `@string/menu_draw_overlay` |
>
> Order them in the listed sequence. Before editing, copy the file to `temp/activity_player_unified__portrait__pre-S0217-phase02.xml`.

**Verification:**

- `Glob` — `temp/activity_player_unified__portrait__pre-S0217-phase02.xml` exists. expected: 1 file | actual: 1 file
- `Grep -n` — pattern `@\+id/btnOpenInSeparateWindowCmd` in `app_v2/src/main/res/layout/activity_player_unified.xml` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `@\+id/btnCropCmd` in same file matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `@\+id/btnCropToFileCmd` in same file matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `@\+id/btnCompressCopyCmd` in same file matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `@\+id/btnDrawOverlayCmd` in same file matches once. expected: 1 | actual: 1
- Build invariant: `assembleStandardDebug` compiles after this step. expected: BUILD SUCCESSFUL | actual: (deferred to Phase Done)

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 7/7 PASS (backup + 5 IDs grep, build deferred). Files: layout/activity_player_unified.xml (+10 lines incl. comment). Dev log recorded.

---

### Step 02.2 — Mirror 5 inline buttons in landscape `activity_player_unified.xml`

**Files:** `app_v2/src/main/res/layout-land/activity_player_unified.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> Apply the identical insertion in the landscape variant immediately after `btnPrintCmd` (currently at line ~90) and before `btnUndoCmd`. Use the exact same attribute set, IDs, drawables, and content descriptions as Step 02.1. Order matches Step 02.1. Before editing, copy the file to `temp/activity_player_unified__landscape__pre-S0217-phase02.xml`.

**Verification:**

- `Glob` — `temp/activity_player_unified__landscape__pre-S0217-phase02.xml` exists. expected: 1 file | actual: 1 file
- `Grep -n` — pattern `@\+id/btnOpenInSeparateWindowCmd` in `app_v2/src/main/res/layout-land/activity_player_unified.xml` matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `@\+id/btnCropCmd` in same file matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `@\+id/btnCropToFileCmd` in same file matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `@\+id/btnCompressCopyCmd` in same file matches once. expected: 1 | actual: 1
- `Grep -n` — pattern `@\+id/btnDrawOverlayCmd` in same file matches once. expected: 1 | actual: 1
- Build invariant: `assembleStandardDebug` compiles after this step. expected: BUILD SUCCESSFUL | actual: (deferred to Phase Done)

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 7/7 PASS (backup + 5 IDs grep). Files: layout-land/activity_player_unified.xml (+10 lines incl. comment). Dev log recorded.

---

### Step 02.3 — Verify string and drawable resources resolve in all locales

**Files:** none (resource verification only)
**Depends on:** Step 02.2

**Prompt for developer:**

> Confirm the five string keys (`action_open_in_separate_window`, `menu_crop`, `menu_crop_to_file`, `menu_compress_copy`, `menu_draw_overlay`) exist in `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`. Confirm the five drawable resources (`ic_open_in_browse`, `ic_crop`, `ic_crop_to_file`, `ic_compress`, `ic_draw_overlay`) exist in `app_v2/src/main/res/drawable/`. No new resources to add — all five already ship with S0028/S0106/S0107. This step is a guardrail in case earlier specs introduced any gap.

**Verification:**

- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "menu_crop"` — exit code 0. expected: 0 | actual: 0
- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "action_open_in_separate_window"` — exit code 0. expected: 0 | actual: 0
- `Grep` — `<string name="menu_compress_copy"` across `values*/strings.xml` returns 3 hits (EN/RU/UK). expected: 3 | actual: 3
- `Grep` — `<string name="menu_draw_overlay"` across `values*/strings.xml` returns 3 hits. expected: 3 | actual: 3
- `Glob` — each of `ic_open_in_browse.xml`, `ic_crop.xml`, `ic_crop_to_file.xml`, `ic_compress.xml`, `ic_draw_overlay.xml` resolves under `app_v2/src/main/res/drawable/`. expected: 5 files | actual: 5 files

**Status:** `[x]` done

**Step Log:**

- 2026-05-16 — Verification 5/5 PASS (strings EN/RU/UK all green, 5 drawables present). No files modified.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for each of the two layout files via `.\scripts\add_to_dev_log.ps1`.
- [ ] No catalog regen needed yet — Kotlin signatures unchanged.

---

## Handoff Notes to Next Phase

Five `ImageButton` views with IDs `btnOpenInSeparateWindowCmd`, `btnCropCmd`, `btnCropToFileCmd`, `btnCompressCopyCmd`, `btnDrawOverlayCmd` exist in both orientation variants, default `visibility="gone"`. They have no click listeners, no `safeViews` accessors, no `barViewForCommand` mapping. Phase 03 wires all of those.

---

## Rollback Plan

Revert the two layout files from the timestamped backups in `temp/`. No persistent state touched.

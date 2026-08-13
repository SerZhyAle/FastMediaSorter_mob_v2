# Phase 01 — Landscape Layout Fix

**Strategic spec:** [`../S0079_bugfix-file-op-progress-dialog-landscape-npe.md`](../S0079_bugfix-file-op-progress-dialog-landscape-npe.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 1 / 1
**Started:** —
**Completed:** 2026-05-04

---

## Objective

Add the two missing TextViews (`tvOverallPercent` and `tvEta`) to the landscape layout variant of the file-operation progress dialog so that `FileOperationProgressDialog.onCreate()` can `findViewById` them without receiving `null`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. *(N/A — foundation phase)*
- [ ] Working tree is clean or on a feature branch.
- [ ] Verify that `app_v2/src/main/res/layout/dialog_file_operation_progress.xml` (portrait reference) still contains both `tvOverallPercent` (line ≈65) and `tvEta` (line ≈73) inside a horizontal `LinearLayout` between the speed row and `btnCancel`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout-land/dialog_file_operation_progress.xml` | Modified | ≤ 90 |

> Portrait reference (read-only for this phase): `app_v2/src/main/res/layout/dialog_file_operation_progress.xml`.

---

## Steps

### Step 01.1 — Add tvOverallPercent and tvEta to landscape layout

**Files:** `app_v2/src/main/res/layout-land/dialog_file_operation_progress.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In `app_v2/src/main/res/layout-land/dialog_file_operation_progress.xml`, insert the following `LinearLayout` block immediately after the closing tag of the existing horizontal row that contains `tvProgressText` and `tvSpeed` (currently the last element before `btnCancel`):
>
> ```xml
>         <LinearLayout
>             android:layout_width="match_parent"
>             android:layout_height="wrap_content"
>             android:orientation="horizontal">
>
>             <TextView
>                 android:id="@+id/tvOverallPercent"
>                 android:layout_width="0dp"
>                 android:layout_height="wrap_content"
>                 android:layout_weight="1"
>                 android:text=""
>                 android:textSize="@dimen/text_size_small"
>                 android:contentDescription="@string/transfer_overall_progress_desc" />
>
>             <TextView
>                 android:id="@+id/tvEta"
>                 android:layout_width="wrap_content"
>                 android:layout_height="wrap_content"
>                 android:text=""
>                 android:textSize="@dimen/text_size_small"
>                 android:contentDescription="@string/transfer_eta_desc" />
>         </LinearLayout>
> ```
>
> The block mirrors the portrait layout exactly (same IDs, same attributes, same string resources). No other changes in this file.

**Verification:**

- `Grep` — `tvOverallPercent` matches exactly once in `app_v2/src/main/res/layout-land/dialog_file_operation_progress.xml`.
- `Grep` — `tvEta` matches exactly once in `app_v2/src/main/res/layout-land/dialog_file_operation_progress.xml`.
- `Grep` — `transfer_overall_progress_desc` present in the landscape file.
- `Grep` — `transfer_eta_desc` present in the landscape file.
- `Grep` — `btnCancel` still present in the landscape file (no accidental deletion).

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 5/5 PASS. File: `app_v2/src/main/res/layout-land/dialog_file_operation_progress.xml` (+22 LOC → 98 total). `tvOverallPercent` L70, `tvEta` L79, `transfer_overall_progress_desc` L76, `transfer_eta_desc` L84, `btnCancel` L88. Dev log recorded.

---

## Phase Done Criteria

- [x] Step 01.1 above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL in 1m 1s (assembleStandardDebug, exit 0).
- [x] `Grep` for `TODO(phase-01)` returns zero hits in source files.
- [x] Dev log entry added for `app_v2/src/main/res/layout-land/dialog_file_operation_progress.xml`.
- [x] No catalog regen needed — no Kotlin file changed.

---

## Handoff Notes to Next Phase

- Landscape layout now contains all View IDs required by `FileOperationProgressDialog.onCreate()`.
- Phase 02 (docs-catalog-cleanup) can proceed immediately.

---

## Rollback Plan

Revert the single XML change — no data migration, no Kotlin change, no user-facing schema altered.

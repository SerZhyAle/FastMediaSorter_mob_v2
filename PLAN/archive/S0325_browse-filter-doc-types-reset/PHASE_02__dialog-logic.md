# Phase 02 - Dialog Logic

**Strategic spec:** [`../S0325_browse-filter-doc-types-reset.md`](../S0325_browse-filter-doc-types-reset.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-01
**Completed:** 2026-06-01

---

## Objective

Wire `cbFilterOffice` (visibility, initial state, apply-time collection) and `btnResetTypes` (re-check all visible type checkboxes without dismissing) in `BrowseDialogHelper`, gated by the same document-support rule as PDF/Text.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`cbFilterOffice` and `btnResetTypes` exist in both layouts).
- [ ] Backup taken: `BrowseDialogHelper.kt` is 713 LOC (>500) - copy to `temp/BrowseDialogHelper.kt.<timestamp>.bak` before editing (Rule 5).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDialogHelper.kt` | Modified | ≤ 760 |

> No flavor source set involved: Office filter uses the existing `BuildConfig.SUPPORT_DOCUMENTS` product gate already applied to PDF/Text in this file. No new `BuildConfig.SUPPORT_*` flag is introduced.

---

## Steps

### Step 02.1 - Configure Office checkbox visibility and initial state

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDialogHelper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `showFilterDialog`, add a configuration block for `dialogBinding.cbFilterOffice` mirroring the existing `cbFilterPdf` block: visible only when `MediaType.OFFICE_DOCUMENT in allowed && BuildConfig.SUPPORT_DOCUMENTS`, set the parent container visibility the same way, and set `isChecked` from `allTypesSelected || currentFilter?.mediaTypes?.contains(MediaType.OFFICE_DOCUMENT) == true` under the same gate. Insert `Timber.d("S0325: filter dialog opened, office checkbox configured")` once at the start of `showFilterDialog` as the BlockNeedUserTest probe.

**Verification:**

- `Grep` - `cbFilterOffice` matches at least once in the file.
- `Grep` - `MediaType.OFFICE_DOCUMENT` present in the visibility block.
- `Grep -n "Log\.d\("` returns zero hits in the file.
- `Grep` - `Timber.d("S0325:` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-06-01 - Verification 4/4 PASS. expected: cbFilterOffice 1, OFFICE_DOCUMENT present, Log.d 0, S0325 tag 1 | actual: 1, 2, 0, 1. Added Timber import + Office visibility block + BlockNeedUserTest probe.

---

### Step 02.2 - Collect Office type on Apply

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDialogHelper.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In the `btnApplyFilter` click listener, add a line collecting `MediaType.OFFICE_DOCUMENT` into `selectedTypes` when `cbFilterOffice.isChecked && cbFilterOffice.visibility == View.VISIBLE`, matching the existing pattern for PDF/EPUB. No other change to the apply logic - the `allAllowedSelected` / null-filter behavior already handles the new type.

**Verification:**

- `Grep` - `cbFilterOffice.isChecked` present inside the apply collection block.
- `Grep` - `selectedTypes.add(MediaType.OFFICE_DOCUMENT)` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-06-01 - Verification 2/2 PASS. expected: 1, 1 | actual: 1, 1. Office collected on Apply mirroring EPUB pattern.

---

### Step 02.3 - Wire reset-types button

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDialogHelper.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add a `dialogBinding.btnResetTypes.setOnClickListener` that sets `isChecked = true` on every type checkbox whose `visibility == View.VISIBLE` (Image, Video, Audio, GIF, Text, PDF, Epub, Office). Do not touch name/date/size fields and do not dismiss the dialog. This is the "вернуть все галочки" action, distinct from `btnClearFilter` which clears the whole filter and dismisses.

**Verification:**

- `Grep` - `btnResetTypes.setOnClickListener` matches exactly once.
- `Grep` - the listener body references `cbFilterOffice` and `isChecked = true`.
- `Grep` - no `dialog.dismiss()` inside the `btnResetTypes` listener block.

**Status:** `[x] done`

**Step Log:**

- 2026-06-01 - Verification 3/3 PASS. expected: listener 1, refs Office + isChecked=true, NO dismiss | actual: 1, yes, NO_DISMISS. Reset re-checks all visible type checkboxes only.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `build-debug.PS1` BUILD SUCCESSFUL (standardDebug) 2026-06-01.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `Grep -n "Log\.d\("` returns zero hits in `BrowseDialogHelper.kt`.
- [x] Dev log entry added via post-change.
- [x] Catalog regenerated via post-change catalog-sync.

---

## Handoff Notes to Next Phase

Office filter is fully functional end-to-end; reset button re-checks all visible type boxes without dismissing. The `Timber.d("S0325: ...")` probe stays in code until the ticket leaves `BlockNeedUserTest`. Phase 03 finalizes docs, catalog, FEATURES, functionality log.

---

## Rollback Plan

Revert phase commit(s) and restore `temp/BrowseDialogHelper.kt.<timestamp>.bak` - no data migration or persisted surface changed.

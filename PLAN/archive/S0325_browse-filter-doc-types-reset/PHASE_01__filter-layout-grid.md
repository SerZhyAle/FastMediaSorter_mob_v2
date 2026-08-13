# Phase 01 - Filter Layout Grid

**Strategic spec:** [`../S0325_browse-filter-doc-types-reset.md`](../S0325_browse-filter-doc-types-reset.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-01
**Completed:** 2026-06-01

---

## Objective

Add the reset-checkboxes button string and rebuild the type-checkbox grid in both portrait and landscape filter layouts: add an Office checkbox and a reset button, in a compact uniform grid that fits without clipping in both orientations.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] String `media_type_office_documents` already exists in all three locales (verified - reuse it, do not add).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ +1 line |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ +1 line |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ +1 line |
| `app_v2/src/main/res/layout/dialog_filter.xml` | Modified | ≤ 420 |
| `app_v2/src/main/res/layout-land/dialog_filter.xml` | Modified | ≤ 330 |

> Landscape counterpart `res/layout-land/dialog_filter.xml` exists and is edited in the same phase (Rule 12).

---

## Steps

### Step 01.1 - Add reset-checkboxes button string in three locales

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one new string key `filter_check_all_types` for the reset button that re-checks all visible type checkboxes. EN: `Check all types`; RU: `Отметить все типы`; UK: `Позначити всі типи`. Place it next to the existing filter strings (near `filter_by_type`). Use `ё`/`Ё` where applicable. Verify the wording against `docs/COMMUNICATION_POLICY.md` §2 (action label) and §6 tone checklist - it is an in-dialog action CTA, not a destructive reset, so wording must read as "bring all type checkboxes back", distinct from the existing `clear_all`.

**Verification:**

- `Grep` - `name="filter_check_all_types"` matches exactly once in each of the three `strings.xml` files (expected: 3 files, 1 hit each | actual: record).
- Tone: `Strings pass COMMUNICATION_POLICY §6 checklist`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-01 - Verification 2/2 PASS. expected: 3 files × 1 hit | actual: 1/1/1. Added `filter_check_all_types` (EN/RU/UK). Tone OK. Dev log recorded.

---

### Step 01.2 - Rebuild portrait type grid: add Office checkbox + reset button

**Files:** `app_v2/src/main/res/layout/dialog_filter.xml`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the portrait filter layout, rework the "filter by type" block into a compact uniform grid covering all 8 types in 2 columns per row (Image/Video, Audio/GIF, Text/PDF, EPUB/Office), reusing the existing per-cell checkbox+label pattern. Add the Office cell with checkbox id `cbFilterOffice` and label `@string/media_type_office_documents`. In the top action button row, add a `MaterialButton` with id `btnResetTypes`, `TextButton` style, text `@string/filter_check_all_types`, placed before `btnClearFilter` so the destructive "Clear All" stays rightmost-grouped with Cancel/Apply. Keep `minHeight="@dimen/resource_checkbox_min_height"` on each cell so touch targets do not shrink. Ensure focus order follows reading order; checkboxes are focusable/clickable by default.

**Verification:**

- `Grep` - `@+id/cbFilterOffice` matches exactly once (expected: 1 | actual: record).
- `Grep` - `@+id/btnResetTypes` matches exactly once.
- `Grep` - `@string/media_type_office_documents` present in the file.
- `Grep` - `@string/filter_check_all_types` present in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-01 - Verification 4/4 PASS. expected: 1 each | actual: 1/1/1/1. Portrait regrid to 4x2 (EPUB/Office added), btnResetTypes added before btnClearFilter. Dev log recorded.

---

### Step 01.3 - Rebuild landscape type grid: add Office checkbox + reset button

**Files:** `app_v2/src/main/res/layout-land/dialog_filter.xml`
**Depends on:** Step 01.2

**Prompt for developer:**

> In the landscape filter layout, extend the existing 3-column type grid (text-on-checkbox style) to include all 8 types: fill the trailing empty `View` placeholders so the rows read Image/Video/Audio, GIF/Text/PDF, EPUB/Office/(spacer). Add the Office checkbox with id `cbFilterOffice`, `layout_weight="1"`, text `@string/media_type_office_documents`, matching the surrounding checkbox attributes. In the action button row, add the `btnResetTypes` `MaterialButton` (`TextButton` style, text `@string/filter_check_all_types`) before `btnClearFilter`, mirroring the portrait order. Remove only the placeholder `View`s that the Office cell replaces; keep one spacer if the row stays at 3 columns with 8 items.

**Verification:**

- `Grep` - `@+id/cbFilterOffice` matches exactly once.
- `Grep` - `@+id/btnResetTypes` matches exactly once.
- `Grep` - `@string/media_type_office_documents` present in the file.
- `Grep` - `@string/filter_check_all_types` present in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-01 - Verification 4/4 PASS. expected: 1 each | actual: 1/1/1/1. Landscape: cbFilterOffice replaces first spacer in EPUB row (one spacer kept), btnResetTypes added before btnClearFilter. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `build-debug.PS1` BUILD SUCCESSFUL (standardDebug) 2026-06-01.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via post-change.
- [x] String locale audit: post-change strings-audit PASS (EN/RU/UK OK).

---

## Handoff Notes to Next Phase

Both layouts now expose `cbFilterOffice` and `btnResetTypes`. Phase 02 wires their behavior in `BrowseDialogHelper`. The Office checkbox has no logic yet - it is inert until Phase 02.

---

## Rollback Plan

Revert phase commit(s) - layout/string only, no data migration or persisted surface changed.

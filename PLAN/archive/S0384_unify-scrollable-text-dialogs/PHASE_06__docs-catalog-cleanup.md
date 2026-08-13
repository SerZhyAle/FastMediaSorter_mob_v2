# Phase 06 - docs-catalog-cleanup

**Strategic spec:** [`../S0384_unify-scrollable-text-dialogs.md`](../S0384_unify-scrollable-text-dialogs.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all
**Blocks:** none
**Steps done:** 0 / 2

---

## Objective

Mechanical closure: dev changelog for every touched file, catalog regen. No FEATURES change (strategic §8 = "Без изменений").

---

## Steps

### Step 06.1 - Dev changelog

**Prompt for developer:**

> Add one dev-log entry per touched file via `.\scripts\add_to_dev_log.ps1` (target `spec-dev`): new `ScrollableTextDialog.kt`, both `dialog_error_detail` layouts, every migrated caller file, deleted files (`ErrorDialog.kt`, `DialogUtils.kt`, `ErrorDialogHelper.kt`, `ScheduledLogDialog.kt` if removed), deleted layouts, and the three `strings.xml`.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains an `S0384` entry for `ScrollableTextDialog.kt`.

**Status:** `[ ]` not done

---

### Step 06.2 - Catalog sync + functionality log

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Append one functionality-log entry: `add_to_functionality_log.ps1 -Id S0384 -Op CHANGE -Description "All scrollable text/error dialogs now share one component and look"`.

**Verification:**

- catalog_sync exit 0.
- `Grep` - `dev/FUNCTIONALITY.log` contains an `S0384` entry.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] All steps `[x]`.
- [ ] FEATURES trilingual - skipped (strategic §8 = "Без изменений").

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0384`.

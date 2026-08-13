# Phase 04 - docs-catalog-cleanup

**Strategic spec:** [`../S0378_system-info-dialog-compact-actions.md`](../S0378_system-info-dialog-compact-actions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, 02, 03
**Blocks:** none
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Run the mechanical post-change closure: dev changelog for every touched file, catalog regeneration for `app_v2`. No FEATURES update (strategic §8 = "Без изменений").

---

## Prerequisites

- [ ] Phases 01-03 ✅ Done and `standard debug` build green.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` (via script) | Modified | - |
| `dev/CATALOG/app_v2.jsonl` + `.md` (regenerated, gitignored) | Modified | - |

---

## Steps

### Step 04.1 - Dev changelog entries

**Files:** `dev/CHANGELOG.md` (via `add_to_dev_log.ps1`)

**Prompt for developer:**

> Add one dev-log entry per touched file via `.\scripts\add_to_dev_log.ps1` (never edit `dev/CHANGELOG.md` directly): the two new drawables, both layouts, and `ErrorDialog.kt`. Target `spec-dev`, English descriptions referencing S0378.

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains an `S0378` entry for `ErrorDialog.kt` and `dialog_error_detail.xml`.

**Status:** `[ ]` not done

---

### Step 04.2 - Catalog sync

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md` (regenerated)

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (scan + render in one process). These indexes are gitignored - regenerate, do not commit.

**Verification:**

- Script exit code 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] FEATURES trilingual - skipped (strategic §8 = "Без изменений"); UX polish, no new capability.
- [ ] Functionality log: one `CHANGE` entry (user-visible dialog behaviour changed) via `add_to_functionality_log.ps1 -Id S0378 -Op CHANGE`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0378`.

---

## Rollback Plan

Dev log / catalog are append/regenerate only - nothing to roll back.

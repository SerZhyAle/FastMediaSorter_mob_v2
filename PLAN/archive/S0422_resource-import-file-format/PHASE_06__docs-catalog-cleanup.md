# Phase 06 - Docs and catalog cleanup

**Strategic spec:** [`../S0422_resource-import-file-format.md`](../S0422_resource-import-file-format.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all
**Blocks:** -
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Document the new sharing feature for users, regenerate the class catalog, and record the functionality-log entry.

---

## Prerequisites

- [ ] Phases 01-05 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |

---

## Steps

### Step 06.1 - Document the feature (EN/RU/UK)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one feature entry per locale, mirroring the existing section style: share a configured resource by exporting it to a file and importing it on another device via the system «open with», a share, or Settings -> Backup -> Import resources. State that the file contains access passwords and should be shared only with trusted people. Keep EN/RU/UK wording in parity.

**Verification:**

- `Grep` - the new feature sentence present in `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.

**Status:** `[ ]` not done

---

### Step 06.2 - Regenerate the catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to rescan and render. Set `role` + `status` for the new classes (`ResourceShareFormat`, `ResourceShareSerializer`, `ExportResourcesToFileUseCase`, `ResourceImportActivity`) via `dev/CATALOG/scripts/set.ps1`.

**Verification:**

- `Grep` - `ExportResourcesToFileUseCase` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` - `ResourceImportActivity` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

### Step 06.3 - Functionality log

**Files:** (log only)
**Depends on:** Step 06.1

**Prompt for developer:**

> Record the user-visible capability with `pwsh -NoProfile -File scripts/add_to_functionality_log.ps1` as an `ADD`: import/export of resources via a share file. Run this standalone (the script leaves a non-zero `$LASTEXITCODE` even on success).

**Verification:**

- `Grep` - `dev/FUNCTIONALITY.log` contains an `ADD` line mentioning resource import/export.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `dev/CHANGELOG.md` has entries for the docs and catalog changes.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0422`.

---

## Rollback Plan

Docs and catalog only - revert the doc edits; the catalog is regenerated, not committed.

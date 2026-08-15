# Phase 03 - Docs and catalog cleanup

**Strategic spec:** [`../S1334_browsestatesyncmanager-detekt-baseline-stale.md`](../S1334_browsestatesyncmanager-detekt-baseline-stale.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Regenerate the class catalog to pick up Phase 02's public-API change, journal every touched file, and
advance the ticket to `Implemented`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (Phase 01, Phase 02).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` / `.md` | Regenerated (gitignored) | - |
| `dev/CHANGELOG.md` | Modified (via script only) | - |

---

## Steps

### Step 03.1 - Regenerate the app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` to index the new
> `BrowseStateSyncUseCases` class and the changed `BrowseStateSyncManager` constructor arity.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "BrowseStateSyncUseCases"`
  returns exactly one record.

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 1/1 PASS. Catalog already regenerated as a side effect of Phase 02's
  `post-change.ps1` run (2402 records, `app_v2.jsonl`/`.md`); this step only re-verified and filled
  `role`/`status=new` for `BrowseStateSyncUseCases` via `set.ps1` (role field doubles as the
  class-level description per the script's own contract).

---

### Step 03.2 - Journal every touched file and advance ticket status

**Files:** `dev/CHANGELOG.md` (via `add_to_dev_log.ps1` only - never hand-edited)
**Depends on:** Step 03.1

**Prompt for developer:**

> Record dev-log entries for every file touched across Phase 01 and Phase 02 - batch as one entry per
> logical change (the new audit script + doc note as one entry, the dependency-holder refactor + its
> baseline prune as another), via `.\scripts\add_to_dev_log.ps1` or `close-and-log.ps1 -DevLogs`. Once
> every phase row in `INDEX.md` is `✅ Done`, flip strategic spec `Status:` to `Implemented`:
> `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S1334 -Status Implemented`.

**Verification:**

- Dev-log sink contains an entry referencing `S1334` for this session.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1334 -Format json` reports
  `"status":"Implemented"`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 2/2 PASS. Dev-log entries already recorded per file across Phase 01/02
  (5 lines: script, doc, manager-refactor set, BrowseViewModel residual note, catalog role/status).
  `close-and-log.ps1 -Id S1334 -Status Implemented -SkipFuncLog -SkipCatalogSync` flipped the journal
  (catalog already synced in Phase 02; `-SkipFuncLog` because strategic §8 states no user-visible
  capability, so no `docs/ALL_FEATURES.jsonl` record applies).

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skipped per strategic §8 (no user-visible capability).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated and contains `BrowseStateSyncUseCases`.
- [ ] `/spec-check S1334` returns `Verified` - runs next, immediately after this phase closes.

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate. Next step outside this tactical plan is `/spec-check
S1334` (run automatically by `/spec-all`'s F5 audit loop).

---

## Rollback Plan

Re-run `catalog_sync.ps1` after reverting Phase 01/02 - the catalog is a gitignored, regenerated
index, not a source of truth; no rollback risk of its own.

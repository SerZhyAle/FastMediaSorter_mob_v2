# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S1335_read-contacts-permission-plumbing.md`](../S1335_read-contacts-permission-plumbing.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Regenerate the catalog, record the new capability in the developer feature inventory, journal every
touched file, and advance the ticket to `Implemented`.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (Phase 01, 02, 03, 04).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` / `.md` | Regenerated (gitignored) | - |
| `docs/ALL_FEATURES.jsonl` | Modified (ADD record, via `scripts/all_features/add.ps1`) | - |
| `dev/CHANGELOG.md` | Modified (via script only) | - |

---

## Steps

### Step 05.1 - Regenerate the app_v2 catalog and record the feature

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. Record the capability via
> `pwsh -NoProfile -File scripts/all_features/add.ps1` - operation `ADD`, area "Permissions", name
> along the lines of "Contacts permission in permission management" flavors `standard,noLegal` (derive
> from the actual `BuildConfig.SUPPORT_LAUNCHER` gate, not a guess), spec `S1335`. EN-only entry, no
> `-NoLegal` flag (this is not itself a noLegal-only capability - it ships on both `standard` and
> `noLegal`, same as the rest of the launcher).

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "PermissionRegistryRepositoryImpl"`
  still resolves (sanity check the class the phase touched is indexed).
- `Grep` - `docs/ALL_FEATURES.jsonl` contains a record with `"spec":"S1335"` (or however the script
  names the spec field - confirm via `scripts/all_features/validate.ps1`).

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 2/2 PASS. Catalog already fresh (regenerated during Phase 03's closure).
  `all_features/add.ps1 -Id "permissions.contacts-permission" ...` added; `validate.ps1` PASS (630
  records).

---

### Step 05.2 - Journal every touched file and advance ticket status

**Files:** `dev/CHANGELOG.md` (via `add_to_dev_log.ps1` only - never hand-edited)
**Depends on:** Step 05.1

**Prompt for developer:**

> Record dev-log entries for every file touched across Phases 01-04, batched as one entry per logical
> change (BuildConfig gate; strings; registry+manifest+revocations+test; privacy policy), via
> `.\scripts\add_to_dev_log.ps1` or `close-and-log.ps1 -DevLogs`. Once every phase row in `INDEX.md` is
> `✅ Done`, flip strategic spec `Status:` to `Implemented`:
> `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S1335 -Status Implemented`.

**Verification:**

- Dev-log sink contains entries referencing `S1335`.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1335 -Format json` reports
  `"status":"Implemented"`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-01 - Verification 2/2 PASS. `close-and-log.ps1 -Id S1335 -Status Implemented -SkipFuncLog
  -SkipCatalogSync` (feature record already added directly in Step 05.1; catalog already fresh).

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - explicitly NOT touched (release-owned).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [x] `docs/ALL_FEATURES.jsonl` contains the `S1335` ADD record.
- [ ] `/spec-check S1335` returns `Verified` - runs next, immediately after this phase closes.

---

## Handoff Notes to Next Phase

Final phase - see `INDEX.md` Completion Gate. Manual/release-time items (Play Console form, S1176
MESSAGE-channel device measurement) carry into the final report as non-blocking manual items, not
phase work.

---

## Rollback Plan

Re-run `catalog_sync.ps1` and remove the `ALL_FEATURES.jsonl` record after reverting Phases 01-04 - no
rollback risk of its own.

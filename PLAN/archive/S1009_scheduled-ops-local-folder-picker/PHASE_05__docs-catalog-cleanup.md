# Phase 05 - Docs & catalog cleanup

**Strategic spec:** [`../S1009_scheduled-ops-local-folder-picker.md`](../S1009_scheduled-ops-local-folder-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-07-24
**Completed:** 2026-07-24

**Step Log:**

- 2026-07-24 - Steps 05.1-05.3 done via `close-and-log.ps1` + registry check. Catalog scanned+rendered (new: `Migration43To44`, `ResolveLocalFolderResourceUseCase`, `CheckLocalFolderWritableUseCase`, `CleanupHiddenResourceUseCase`, `ScheduledOperationDraft`, `SchedOpPickSide`). `docs/ALL_FEATURES.jsonl` record `scheduled-operations.local-folder-as-scheduled-op-sender-receiver` (flavors standard,lite,photos,legacy,noLegal - all, since `ENABLE_SCHEDULED_OPERATIONS` is a build-type gate). Document-registry `validate` PASS (23 records), `generate -Check` current; `docs/ARCHITECTURE.md` unaffected (no schema-version pin / column enumeration).

---

## Objective

Close the ticket: regenerate the class catalog for the new classes, record the delivered capability in the developer feature inventory, and sync the one affected documentation-registry record. No source behaviour changes here.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done and the project compiles on standard debug.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated (gitignored) | - |
| `docs/ALL_FEATURES.jsonl` | Modified (append one record) | - |
| `dev/CHANGELOG.md` | Modified (via `add_to_dev_log.ps1`) | - |
| `docs/ARCHITECTURE.md` | Modified only if it pins the DB schema version | ≤ per-doc |

> No `docs/FEATURES*.md` edits - that file is `/skill-release`-owned and populated from the `ALL_FEATURES` diff (CLAUDE.md §11). No settings-doc regen - S1009 adds a dialog picker option, not an `AppSettings` setting (Rule 22 not triggered).

---

## Steps

### Step 05.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl` + `.md` (gitignored indexes)
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once so the new classes from earlier phases (`Migration43To44.kt`, and any new hidden-resource use-case/manager introduced in Phase 03/04) are indexed. Fill `role` + `status` for each new class via `dev/CATALOG/scripts/set.ps1` if `catalog_sync` reports them missing. These indexes are gitignored - regenerate, do not commit.

**Verification:**

- `pwsh` - `dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*Migration43To44*"` returns the class.
- `pwsh` - `query.ps1` reports 100% role coverage (no blank `role` on new classes).

**Status:** `[x]` done

---

### Step 05.2 - Record the delivered capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Append one capability record via the closure facade rather than editing the JSONL by hand: `close-and-log.ps1 -FuncOp ADD -FeatArea "Scheduled operations" -FeatName "Local folder as scheduled-op sender/receiver" -FeatFlavors "<read off the actual gate>"`. Read `-FeatFlavors` off reality: S1009 has no `BuildConfig` gate and lives in `src/main`, so it ships in every flavor - list `standard,lite,photos,legacy` (and `noLegal` if that variant is built from the same source). Do NOT copy flavors from a sibling record. EN-only text.

**Verification:**

- `Grep` - `docs/ALL_FEATURES.jsonl` contains a record whose `spec` field is `S1009`.
- `pwsh` - `scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 05.3 - Sync the affected documentation-registry record

**Files:** `docs/ARCHITECTURE.md` (conditional)
**Depends on:** Step 05.2

**Prompt for developer:**

> The document-registry `architecture` record (area `database`) is the only record the schema change touches. Read `docs/ARCHITECTURE.md`; if it pins the Room schema version or enumerates resource columns, update it to reflect version 44 + the `is_hidden` column. If it does not reference the schema at that granularity, no edit is needed - state that explicitly. Then run the registry close-out: `validate.ps1`, `generate.ps1`, `generate.ps1 -Check`.

**Verification:**

- `pwsh` - `scripts/document_registry/validate.ps1` exits 0.
- `pwsh` - `scripts/document_registry/generate.ps1 -Check` exits 0 (generated `DOCS_MAP.md` / `sitemap.xml` in sync).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated; new classes carry `role` + `status`.
- [ ] `docs/ALL_FEATURES.jsonl` has an `S1009` record; `validate.ps1` passes.
- [ ] Document-registry `validate.ps1` + `generate.ps1 -Check` pass.
- [ ] `dev/CHANGELOG.md` has an entry for every modified source file (one logical entry per phase is fine).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next action after this phase: `/spec-check S1009` to flip the strategic spec to `Verified` (device-test gate applies if the picker/migration need on-device confirmation).

---

## Rollback Plan

Catalog and registry regeneration are idempotent - re-run the generators. The single `ALL_FEATURES.jsonl` append is reversible via `scripts/all_features` tooling. No user-facing surface changes in this phase.

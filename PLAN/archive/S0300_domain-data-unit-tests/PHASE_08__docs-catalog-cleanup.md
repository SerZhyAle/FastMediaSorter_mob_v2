# Phase 08 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0300_domain-data-unit-tests.md`](../S0300_domain-data-unit-tests.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all (Phases 01–07)
**Blocks:** none
**Steps done:** 3 / 3
**Started:** 2026-05-29
**Completed:** 2026-05-29

---

## Objective

Close the spec: confirm the inventory is fully resolved, regenerate the class catalog, and record dev-log/changelog entries. No new tests, no production code.

---

## Prerequisites

- [ ] Phases 01–07 ✅ Done.
- [ ] `COVERAGE_INVENTORY.md` exists and every in-scope row is marked covered or explicitly deferred with a reason.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0300_domain-data-unit-tests/COVERAGE_INVENTORY.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated | n/a |
| `dev/CHANGELOG.md` | Modified (via script) | n/a |

> FEATURES is NOT touched - strategic §8 = "Без изменений" (tests add no user-visible capability).

---

## Steps

### Step 08.1 - Finalize coverage inventory

**Files:** `PLAN/S0300_domain-data-unit-tests/COVERAGE_INVENTORY.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Review every in-scope row in `COVERAGE_INVENTORY.md`. Each must be marked covered, or out-of-scope per the cutoff, or explicitly deferred with a one-line reason. No in-scope row may remain unaddressed without a reason. Add a short summary line: counts of in-scope / covered / deferred per phase.

**Verification:**

- `Grep` - no in-scope row lacks a status marker (`expected: 0 unmarked in-scope rows | actual: count`).
- `Grep` - summary line present.

**Status:** `[x]` done

---

### Step 08.2 - Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 08.1

**Prompt for developer:**

> Regenerate the app_v2 catalog so the new test classes are indexed. Run the sync wrapper.

**Verification:**

- Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` - exit 0.
- `Grep` - at least one new `*Test` entry present in `dev/CATALOG/app_v2.jsonl` (`expected: new test classes indexed | actual: present`).

**Status:** `[x]` done

---

### Step 08.3 - Dev-log and changelog closure

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 08.2

**Prompt for developer:**

> Record a dev-log entry summarizing the S0300 test-coverage work. Confirm each phase already recorded its own dev-log entries; add a closing entry referencing the whole spec.

**Verification:**

- Run `pwsh -NoProfile -File scripts/add_to_dev_log.ps1 "PLAN/S0300_domain-data-unit-tests/INDEX.md" "spec-dev" "S0300 domain+data unit coverage complete"` - exit 0.
- `Grep` - `S0300` entry present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 08.*` above is `[x] done`.
- [x] `COVERAGE_INVENTORY.md` shows no unaddressed in-scope rows. `expected: 0 | actual: 0` (`| no | in |` count = 0).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1477 records).
- [x] `dev/CHANGELOG.md` has the S0300 entries (dev-log per phase + closure).
- [x] Ready for `/spec-check S0300`.

**Step Log:**

- 2026-05-29 - Inventory finalized (9 duplicate noLegal rows in the Phase 06 catalog section flipped to `yes`, pointing at Phase 07 coverage; closure summary appended). Catalog regenerated. Final consolidated test compile: `compileStandardDebugUnitTestKotlin` exit 0 + `compileNoLegalDebugUnitTestKotlin` exit 0.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next action: `/spec-check S0300`.

---

## Rollback Plan

Revert the changelog entry; the catalog and inventory are regenerable artifacts. No production code changed.

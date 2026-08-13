# Phase 02 - failure-inventory

**Strategic spec:** [`../S0275_test_suite_triage.md`](../S0275_test_suite_triage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Turn the first runnable baseline into a class-by-class triage sheet and close the obvious fix/delete cases before quarantine infrastructure is introduced.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `temp/S0275_standard_runtime_inventory.md` exists and reflects the latest baseline.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S0275_standard_runtime_inventory.md` | Modified | ≤ 500 |
| `temp/S0275_class_triage.md` | New | ≤ 500 |
| `app_v2/src/test/java/com/sza/fastmediasorter/` | Modified | ≤ 500 per file |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split first.

---

## Steps

### Step 02.1 - Bucket every failing class by first cause

**Files:** `temp/S0275_standard_runtime_inventory.md`, `temp/S0275_class_triage.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Convert the runtime inventory into `temp/S0275_class_triage.md`. For every red class, record one bucket only: `dead-test`, `broken-api`, `framework-runner`, `real-bug`, `flaky`, or `quarantine-candidate`. Include the first failure line and the intended disposition column (`Fix`, `Delete`, `Quarantine`, or `Follow-up`).

**Verification:**

- `Glob` - `temp/S0275_class_triage.md` exists.
- `Grep` - `Bucket:` appears for every recorded class entry.
- `Grep` - `Disposition: pending` returns zero hits.

**Status:** `[ ]` not done

---

### Step 02.2 - Close the immediate fix or delete cases

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/`, `temp/S0275_class_triage.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> For every triage row that is mechanically repairable inside this ticket, fix or delete it immediately. Do not introduce quarantine yet in this phase. Update the triage sheet so every processed class ends with `Disposition: fixed` or `Disposition: deleted`, with a one-line reason.

**Verification:**

- `Grep` - `Disposition: pending` returns zero hits.
- `Grep` - every processed row ends with one of `fixed`, `deleted`, `quarantine-candidate`, or `follow-up`.
- `Grep` - `app_v2/src/test/java/com/sza/fastmediasorter/` contains no stale references to deleted production types for the processed rows.

**Status:** `[ ]` not done

---

### Step 02.3 - Refresh the baseline after immediate repairs

**Files:** `temp/S0275_standard_runtime_inventory.md`, `temp/S0275_class_triage.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> Re-run `:app_v2:testStandardDebugUnitTest -Pchaquopy.enabled=false` and refresh both temp reports. The goal of this step is not full green yet; it is to prove which failures remain after the obvious fix/delete pass so quarantine infrastructure can be scoped correctly.

**Verification:**

- `Grep` - `Post-fix baseline:` present in `temp/S0275_standard_runtime_inventory.md`.
- `Grep` - every still-failing class from the refreshed baseline is present in `temp/S0275_class_triage.md`.
- `Grep` - `Unclassified:` returns zero hits.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] The remaining red classes (if any) are explicitly known and bucketed.
- [ ] No obviously dead or mechanically fixable class remains unlabeled as `fixed` / `deleted` / `quarantine-candidate` / `follow-up`.
- [ ] `dev/CHANGELOG.md` has an entry for every touched file via `add_to_dev_log.ps1` / `post-change.ps1`.
- [ ] If any `app_v2/**/*.kt` file changed: `scripts/catalog_sync.ps1 -Module app_v2` executed.

---

## Handoff Notes to Next Phase

Phase 03 receives the filtered set of genuine quarantine candidates only; everything else must already be fixed or deleted.

---

## Rollback Plan

Revert the processed test-file commits and regenerate the two temp reports from the previous baseline.
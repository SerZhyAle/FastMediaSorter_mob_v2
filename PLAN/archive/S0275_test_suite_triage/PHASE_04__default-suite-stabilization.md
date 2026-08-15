# Phase 04 - default-suite-stabilization

**Strategic spec:** [`../S0275_test_suite_triage.md`](../S0275_test_suite_triage.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Apply the remaining triage decisions until the default `standard` suite is green and quarantine, if non-empty, remains separately runnable.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Phase 03 is ✅ Done.
- [ ] `temp/S0275_class_triage.md` is the live source of truth for remaining classes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S0275_class_triage.md` | Modified | ≤ 500 |
| `app_v2/src/test/java/com/sza/fastmediasorter/` | Modified | ≤ 500 per file |
| `dev/test-quarantine/S0275_standard_unit_quarantine.md` | Modified | ≤ 400 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split first.

---

## Steps

### Step 04.1 - Apply the remaining fix, delete, or quarantine decisions

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/`, `temp/S0275_class_triage.md`, `dev/test-quarantine/S0275_standard_unit_quarantine.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Finish the per-class triage. Every remaining red class must end this step as exactly one of: fixed in place, deleted as dead coverage, or annotated / filtered into `QuarantineLegacyFailure` with a registry entry. No `pending` or silent debt is allowed to survive.

**Verification:**

- `Grep` - `Disposition: pending` returns zero hits in `temp/S0275_class_triage.md`.
- `Grep` - every quarantined class in the triage sheet appears in `dev/test-quarantine/S0275_standard_unit_quarantine.md`.
- `Grep` - every registry entry names an `Sxxxx` owner or explicit `follow-up: none - resolved here`.

**Status:** `[ ]` not done

---

### Step 04.2 - Prove the default suite is green and quarantine remains visible

**Files:** `temp/S0275_class_triage.md`, `dev/test-quarantine/S0275_standard_unit_quarantine.md`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run the default `standard` suite and the quarantine suite separately. Record the exact command, exit code, and outcome in the triage sheet / registry. The target state is: default suite green by exit code; quarantine suite optional but runnable on demand.

**Verification:**

- `Grep` - `Default suite: GREEN` present in `temp/S0275_class_triage.md`.
- `Grep` - `Quarantine suite command:` present in `dev/test-quarantine/S0275_standard_unit_quarantine.md`.
- `Grep` - `Quarantine suite status:` present in `dev/test-quarantine/S0275_standard_unit_quarantine.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `cmd /c ".\gradlew.bat :app_v2:testStandardDebugUnitTest -Pchaquopy.enabled=false"` exits 0.
- [ ] If quarantine is non-empty: `testStandardDebugQuarantineUnitTest` executes and writes XML reports.
- [ ] `temp/S0275_class_triage.md` contains no unresolved class rows.
- [ ] `dev/CHANGELOG.md` has an entry for every touched file via `add_to_dev_log.ps1` / `post-change.ps1`.

---

## Handoff Notes to Next Phase

Phase 05 updates repo guidance, memory, and verification policy to reflect the new green-signal contract.

---

## Rollback Plan

Revert the last failing-class changes and restore the previous quarantine registry snapshot.
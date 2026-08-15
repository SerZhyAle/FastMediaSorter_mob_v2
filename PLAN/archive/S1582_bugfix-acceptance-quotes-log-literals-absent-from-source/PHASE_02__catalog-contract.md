# Phase 02 - Catalog Contract

**Strategic spec:** [`../S1582_bugfix-acceptance-quotes-log-literals-absent-from-source.md`](../S1582_bugfix-acceptance-quotes-log-literals-absent-from-source.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Completed
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-08-11
**Completed:** 2026-08-11

## Objective

Reject an invalid explicit probe literal at catalog write time and remediate existing invalid acceptance notes.

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] The probe audit fixture suite passes.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/update.ps1` | Modified | ≤ 250 |
| `scripts/spec_catalog/update.tests/Run-Tests.ps1` | Modified | ≤ 300 |
| `PLAN/S1417_bugfix-camera-sport-mode-icons.md`, `PLAN/S1478_bugfix-headless-capture-ignores-camera-settings.md` | Modified through catalog CLI | existing |

## Steps

### Step 02.1 - Validate explicit literals before catalog write

**Files:** `scripts/spec_catalog/update.ps1`, `scripts/spec_catalog/update.tests/Run-Tests.ps1`
**Depends on:** Phase 01 complete

**Prompt for developer:**

> When a supplied StatusNote contains `Probe literal:` or `Probe template:`, call the shared matcher before writing the catalog. Reject a missing or foreign-ticket expectation without mutating the record; require named alternative evidence for `Probe none:` and preserve notes without an explicit marker. Add regression coverage for accepted quoted values and rejected absent literals.

**Why:**

> A catalog-wide audit catches accumulated drift, but authors need immediate feedback while the expected signal and source change are still in the same working context.

**Verification:**

- `Grep` - `Probe literal:` is present in `scripts/spec_catalog/update.ps1`.
- `pwsh -NoProfile -File scripts/spec_catalog/update.tests/Run-Tests.ps1` exits 0.
- `pwsh -NoProfile -File scripts/spec_catalog/validate.ps1` exits 0.

**Status:** `[x]` done

### Step 02.2 - Normalize the measured acceptance notes

**Files:** `PLAN/S1417_*.md`, `PLAN/S1419_*.md`, `PLAN/S1478_*.md`, `PLAN/S1579_*.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Use `update.ps1` to add source-backed `Probe literal:` or `Probe template:` values to the two still-BlockNeedUserTest measured notes. Keep surrounding scenario prose and do not invent runtime values. S1419 is already Verified and S1579 is Partial, so neither is an active acceptance-note migration target.

**Why:**

> The new gate must begin from a truthful corpus; otherwise device runners will keep receiving the same misleading instructions despite the checker existing.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-ticket-acceptance-probes.ps1 -Gate` exits 0.
- `Grep` - `Probe literal:` is present in each targeted strategic spec.
- `Grep` - `headless capture lens=` returns zero hits in the S1478 acceptance note.

**Status:** `[x]` done

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Catalog validation passes.
- [x] Dev log entry added for every modified file.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

## Step Log

- 2026-08-11 - Verification 3/3 PASS. Files: `scripts/quality/lib/ticket-acceptance-probes.ps1`, `scripts/spec_catalog/update.ps1`, `scripts/spec_catalog/update.tests/Run-Tests.ps1`. Update regression suite: 21 passed; catalog validation: 0 failures.
- 2026-08-11 - Verification 3/3 PASS. Files: `PLAN/S1417_bugfix-camera-sport-mode-icons.md`, `PLAN/S1478_bugfix-headless-capture-ignores-camera-settings.md`. Gate reports 2 explicit contracts and 0 findings. Phase-boundary audit: P0/P1 none; changes are validation/catalog metadata only.

## Handoff Notes to Next Phase

Explicit literals are prevented at write time and the current catalog is clean.

## Rollback Plan

Revert phase commit(s) - catalog note updates are reversible through `update.ps1`.

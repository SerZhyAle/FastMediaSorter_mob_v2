# Phase 02 - Regression coverage

**Strategic spec:** [`../S1381_doc-drift-gate-coverage-holes.md`](../S1381_doc-drift-gate-coverage-holes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2

## Objective

Prove baseline success, mismatch detection, and exclusion of historical schema values.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/doc-drift/tests/Run-Tests.ps1` | New | ≤ 500 |
| `scripts/doc-drift/tests/fixtures/` | New | ≤ 500 total |

## Steps

### Step 02.1 - Add isolated drift regression scenarios

**Files:** `scripts/doc-drift/tests/Run-Tests.ps1`, `scripts/doc-drift/tests/fixtures/`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a self-contained PowerShell regression runner with fixtures or temporary copies. Assert baseline success, each altered live documentation value fails with its pin identifier, and the historical Room version is not treated as current.

**Why:**

The gate previously reported success while real current values had drifted, so coverage must prove the failure mode as well as the happy path.

**Verification:**

- `pwsh -NoProfile -File scripts/doc-drift/tests/Run-Tests.ps1` exits 0.
- Test output names compile-sdk, target-sdk, and room-schema-version scenarios.

**Status:** `[x]` done - five isolated scenarios passed.

### Step 02.2 - Preserve checker output contract

**Files:** `scripts/check-doc-vs-gradle.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Adjust the checker only if tests expose an output or exit-code ambiguity. Preserve documented success, defect, and bootstrap exit behavior.

**Why:**

The gate is consumed by local checks and CI, so coverage must not repair one blind spot by weakening its stable contract.

**Verification:**

- `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1` exits 0.
- `pwsh -NoProfile -File scripts/quality/assert-doc-pin-drift.ps1 -Gate` exits 0.

**Status:** `[x]` done - checker and quality gate retain their exit contract.

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Regression runner exits 0.
- [x] Phase-boundary audit run - Layer 1 only; no P0/P1 findings.

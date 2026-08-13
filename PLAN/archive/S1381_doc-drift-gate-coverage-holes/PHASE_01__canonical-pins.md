# Phase 01 - Canonical pins

**Strategic spec:** [`../S1381_doc-drift-gate-coverage-holes.md`](../S1381_doc-drift-gate-coverage-holes.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2

## Objective

Expose the three missing live contracts to the declarative documentation drift check.

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/doc-drift/GradleParser.ps1` | Modified | ≤ 500 |
| `scripts/doc-drift/pins.psd1` | Modified | ≤ 500 |

## Steps

### Step 01.1 - Extract live SDK and schema contracts

**Files:** `scripts/doc-drift/GradleParser.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend canonical pin extraction with SDK declarations used by both modules and the current database schema annotation. Return a missing source as an absent canonical value and do not invoke Gradle.

**Why:**

The checker cannot detect drift when the platform and data-contract values have no canonical source outside documentation.

**Verification:**

- `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1 -Pin compile-sdk` exits 0 on the baseline tree.
- `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1 -Pin target-sdk` exits 0 on the baseline tree.
- `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1 -Pin room-schema-version` exits 0 on the baseline tree.

**Status:** `[x]` done - baseline verification passed for all three canonical pins.

### Step 01.2 - Require live documentation declarations

**Files:** `scripts/doc-drift/pins.psd1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Declare required matchers for live compileSdk, targetSdk and Room schema rows. Exclude historical Room version entries while preserving managed-document skips.

**Why:**

The current manifest marks the missing coverage optional, allowing stale current documentation to pass as a skip.

**Verification:**

- `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1` exits 0 and has no SKIP record for the three pins.
- `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1 -VerboseOutput` reports PASS for all three pins.

**Status:** `[x]` done - required live documentation matchers pass without a new SKIP record.

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1` exits 0.
- [x] Phase-boundary audit run - Layer 1 only; no P0/P1 findings.

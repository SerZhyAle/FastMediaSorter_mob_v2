# Phase 02 - Schema pin regression

**Strategic spec:** [`../S1516_room-schema-version-doc-drift.md`](../S1516_room-schema-version-doc-drift.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** -
**Completed:** 2026-08-14

---

## Objective

Require both current Room documents in the existing drift pin and prove that each can fail independently.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/doc-drift/pins.psd1` | Modified | ≤ 30 |
| `scripts/doc-drift/tests/Run-Tests.ps1` | Modified | ≤ 70 |

---

## Steps

### Step 02.1 - Register the operational document in the Room pin

**Files:** `scripts/doc-drift/pins.psd1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the operational documentation as a required `room-schema-version` target with a matcher that captures its explicit Room schema version sentence.

**Why:**

Every current document must be compared with the source declaration, or the same stale value can return silently.

**Verification:**

- `Grep` - `docs/DEV_OPS.md` occurs in the `room-schema-version` pin entry.
- `Grep` - `Room schema version:\\s*` occurs in `scripts/doc-drift/pins.psd1`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Room schema pin now requires DEV_OPS with its explicit version matcher.

---

### Step 02.2 - Cover both required Room documents in the regression suite

**Files:** `scripts/doc-drift/tests/Run-Tests.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Copy the operational document into the sandbox and add a mismatch scenario that proves the Room pin fails for it while retaining the existing requirements-document scenario.

**Why:**

The strategic goal requires evidence that every required current document is protected, rather than a manifest entry that is never exercised.

**Verification:**

- `Grep` - `room-schema-version-dev-ops-mismatch` occurs exactly once in `scripts/doc-drift/tests/Run-Tests.ps1`.
- `Grep` - `docs\\DEV_OPS.md` occurs at least once in `scripts/doc-drift/tests/Run-Tests.ps1`.
- `pwsh -NoProfile -File scripts/doc-drift/tests/Run-Tests.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Regression suite fails independently for each current Room document and passes with both live values.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/check-doc-vs-gradle.ps1 -Pin room-schema-version` exits 0.
- [x] `Grep` for `TODO(phase-02)` returns zero hits in the files listed under Files Touched.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The Room schema pin now requires and tests both current operational documents.

---

## Rollback Plan

Revert phase commit(s) - the checker returns to its previous single-document contract.

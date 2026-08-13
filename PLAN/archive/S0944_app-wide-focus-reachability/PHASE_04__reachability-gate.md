# Phase 04 - Reachability gate

**Strategic spec:** [`../S0944_app-wide-focus-reachability.md`](../S0944_app-wide-focus-reachability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked
**Depends on:** Phase 01
**Steps done:** 0 / 2

> **BLOCKED on research §6.1** (mechanical reachability gate feasibility - instrumented focus-tree walk on TV emulator vs static layout analysis). Do not implement while unchecked.

---

## Objective

Add a repeatable check that flags reachability regressions (unreachable control, focus trap, missing initial focus) so new screens inherit the contract.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-focus-reachability.ps1` | New | ≤ 200 |
| `scripts/post-change.ps1` (advisory registration) | Modified | ≤ 60 |

---

## Steps

### Step 04.1 - Reachability assertion (advisory)

**Prompt for developer:**

> Per §6.1, implement the reachability assertion (static and/or instrumented) reporting unreachable controls, traps, and screens without initial focus; advisory mode + ratchet baseline, matching existing `assert-*` gates.

**Verification:**

- `Glob` - the script exists; runs exit 0 in advisory mode.

**Status:** `[ ]` not done

### Step 04.2 - Wire into post-change (advisory)

**Prompt for developer:**

> Register in `post-change.ps1` as advisory (warn only).

**Verification:**

- `Grep` - referenced in `post-change.ps1`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Steps `[x]`; dev log added.

---

## Rollback Plan

Revert - additive, advisory. No data migration.

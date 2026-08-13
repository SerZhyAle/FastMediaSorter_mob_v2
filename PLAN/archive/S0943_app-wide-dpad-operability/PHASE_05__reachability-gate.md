# Phase 05 - Reachability gate

**Strategic spec:** [`../S0943_app-wide-dpad-operability.md`](../S0943_app-wide-dpad-operability.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked
**Depends on:** Phase 02
**Blocks:** none
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

> **BLOCKED on research §6.3** (mechanical reachability gate feasibility - instrumented focus-tree walk on TV emulator vs static layout analysis). Do not implement while the blocker is unchecked; the gate mechanism below is provisional.

---

## Objective

Add a repeatable check that flags regressions in non-touch operability (undreachable control, focus trap, missing initial focus) so new screens inherit the contract and cannot silently break it.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Research §6.3 Resolved (gate mechanism chosen).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-focus-reachability.ps1` | New | ≤ 200 |
| `scripts/post-change.ps1` (register advisory gate) | Modified | ≤ 60 |

---

## Steps

### Step 05.1 - Reachability assertion script

**Prompt for developer:**

> Per the §6.3 decision, implement the reachability assertion (static layout scan and/or instrumented focus-tree walk) that reports unreachable interactive controls, focus traps, and screens without an initial focus. Start in advisory (non-failing) mode with a ratchet baseline, matching the existing `assert-*` gate conventions.

**Verification:**

- `Glob` - `scripts/quality/assert-focus-reachability.ps1` exists.
- Script runs and exits 0 in advisory mode.

**Status:** `[ ]` not done

### Step 05.2 - Wire into post-change

**Prompt for developer:**

> Register the assertion in `post-change.ps1` as an advisory gate (warn, do not fail) alongside the other quality gates, so touched screens are checked on close.

**Verification:**

- `Grep` - the new gate is referenced in `post-change.ps1`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` is `[x] done`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Regressions in reachability are now caught mechanically; the contract is self-enforcing for new screens.

---

## Rollback Plan

Revert phase commit(s) - script and post-change hook are additive and advisory. No data migration.

# Phase 03 - The refusal names the real blocker, the status shows the pathology

**Strategic spec:** [`../S1448_code-lock-queue-head-starvation.md`](../S1448_code-lock-queue-head-starvation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Replace the unconditional "held by another session" refusal with one branched on the actual blocker, and make `lock-status.ps1` state outright when the queue head belongs to the current lock holder.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/enter-code-lock.ps1` | Modified | ≤ 120 |
| `scripts/utils/lock-status.ps1` | Modified | ≤ 170 |

---

## Steps

### Step 03.1 - Branch the `enter-code-lock.ps1` refusal on the real blocker

**Files:** `scripts/utils/enter-code-lock.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Replace the single unconditional refusal block with two branches, chosen from `Get-AgentLockStatus -Name Code` and `Test-AgentLockTurn`. When the lock file exists and is not stale, keep the current wording and the `Holder:` line. When it does not, print that the lock is free but this session is not the queue head, and name the head's session id, its reason, how many minutes it has waited, and the reservation window from `Get-AgentLockTimings -Name Code`. Never print a `Holder:` line when the status reports `Exists = $false`. Keep exit 4 for both branches and keep the "Your ticket / wait in the background / do lock-free work" lines shared.

**Why:**

Strategic §4 Д3 records an observed refusal that claimed a holder while no lock file existed and printed an empty `Holder: session  (age 0s, reason: '')`, sending the reader to look for a holder that did not exist; strategic ADR-4 requires the message to be built from the blocking fact the code already computed rather than from an assumption.

**Verification:**

- `Grep` - `is held by another session` matches exactly once in `scripts/utils/enter-code-lock.ps1` and sits inside the branch guarded by the lock-exists test.
- `Grep` - a second refusal string naming the queue head is present and mentions `queue`.
- `Grep` - `Holder: session` appears exactly once in the file, inside the lock-exists branch.
- `Grep` - `exit 4` matches in the file and no other exit code is introduced.

**Status:** `[x]` done

---

### Step 03.2 - Flag the head ticket that belongs to the lock holder

**Files:** `scripts/utils/lock-status.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> When `-Queue` is passed, compare each ticket's `sessionId` with `$status.SessionId` from the lock read and attach a boolean `heldByLockHolder` to the ticket. In the text output append ` <- holds the lock` to any such row. In the JSON output add a top-level `headOwnedByHolder` boolean set from the first ticket in the queue. Both must be false, not absent, when no lock is held.

**Why:**

Strategic §2.5 requires the pathology be visible without matching session guids by eye, which strategic §4 Д4 identifies as the only way it can currently be spotted.

**Verification:**

- `Grep` - `heldByLockHolder` matches at least twice in `scripts/utils/lock-status.ps1`.
- `Grep` - `headOwnedByHolder` matches exactly once in that file, inside the `if ($Json)` branch.
- `Grep` - `holds the lock` matches exactly once, inside the text-output queue loop.

**Status:** `[x]` done

---

### Step 03.3 - Record the new field in the script's own header contract

**Files:** `scripts/utils/lock-status.ps1`
**Depends on:** Step 03.2

**Prompt for developer:**

> Extend the `.DESCRIPTION` block to document `heldByLockHolder` / `headOwnedByHolder` and state what a true value means: the queue head is the lock holder's own abandoned ticket, so nobody behind it can advance. Leave the documented exit codes unchanged - this step adds no new failure mode.

**Why:**

CLAUDE.md section 7 requires a script header to describe the contract it actually returns, and a diagnostic field nobody knows about does not satisfy strategic §2.5.

**Verification:**

- `Grep` - `headOwnedByHolder` matches inside the comment-based help block at the top of the file.
- `Grep` - `Exit code: 0` still matches exactly once, proving the exit contract is untouched.

**Status:** `[x]` done

---

## Step Log

- 2026-08-07 - Step 03.1 Verification 4/4 PASS. The refusal now branches on `$holder.Exists -and -not $holder.Stale`; the free-lock branch names the head's session, ticket, wait and the 3-minute reservation window; `exit 4` is unchanged for both branches.
- 2026-08-07 - Step 03.2 Verification 3/3 PASS. `heldByLockHolder` attached per ticket, `headOwnedByHolder` emitted once in the `if ($Json)` branch, `<- holds the lock` suffix in the text loop.
- 2026-08-07 - Step 03.3 Verification 2/2 PASS. `headOwnedByHolder` documented in the comment-based help; `Exit code: 0` still matches once.
- 2026-08-07 - Ran both output modes against the live lock: text exit 0, JSON exit 0 carrying `"heldByLockHolder":false` and `"headOwnedByHolder":false` while this session held the lock and the head belonged to a sibling - the correct answer, and the one the old output could not give.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Code -Queue -Json` exits 0 and the payload contains `headOwnedByHolder`.
- [x] `pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Code -Queue` exits 0 in text mode.
- [x] `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` passes for both edited scripts.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13).

---

## Handoff Notes to Next Phase

Both diagnostic surfaces now report the queue-head/holder relationship, which is what Phase 05's scenario asserts against rather than re-deriving from raw ticket files.

---

## Rollback Plan

Revert both scripts. Neither writes state, so a rollback leaves nothing behind.

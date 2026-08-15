# Phase 05 - A scenario harness that proves the fairness claims

**Strategic spec:** [`../S1448_code-lock-queue-head-starvation.md`](../S1448_code-lock-queue-head-starvation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Add a repeatable harness that simulates two sessions against a throwaway queue directory and asserts the starvation, heartbeat and backwards-compatibility claims without touching the live `temp/CODE.QUEUE`.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/test-agent-lock-queue.ps1` | New | ≤ 260 |

---

## Steps

### Step 05.1 - Create the harness with an isolated queue root

**Files:** `scripts/utils/test-agent-lock-queue.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Write a scenario script that dot-sources `agent-lock.ps1` and then overrides `$Script:AgentLockRepoRoot` with a fresh sandbox directory under `temp/S1448/sandbox-<yyyyMMdd-HHmmss>/`, so every lock file and queue directory the run creates lives there. Simulate a second session by setting `$env:CLAUDE_CODE_SESSION_ID` around each call and restoring the caller's value in a `finally`. Print one `PASS` / `FAIL` line per assertion and a final verdict line. Document the exit codes in the header: 0 all assertions passed, 1 at least one failed, 2 the sandbox could not be prepared. Delete the sandbox on a passing run and keep it on a failing one.

**Why:**

Strategic §7 names "правится та самая механика, которой правящая сессия пользуется прямо сейчас" as the highest-probability risk and prescribes a scenario run in a separate queue directory rather than on the live one, because a sibling session is holding real tickets throughout this work.

**Verification:**

- `Glob` - `scripts/utils/test-agent-lock-queue.ps1` exists.
- `Grep` - `$Script:AgentLockRepoRoot` is assigned exactly once in that file, after the dot-source line.
- `Grep` - `CLAUDE_CODE_SESSION_ID` matches at least twice (set and restore).
- `Grep` - the header lists exit codes 0, 1 and 2.

**Status:** `[x]` done

---

### Step 05.2 - Assert the fairness and heartbeat claims

**Files:** `scripts/utils/test-agent-lock-queue.ps1`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add these assertions, each independent and each printing its own verdict line: after session A acquires the lock, no ticket of session A remains in the queue; with session B's ticket already queued, session A releasing and immediately re-acquiring does not take the turn ahead of B; a ticket whose `lastSeenAt` is fresh survives a `Get-AgentLockQueue` sweep even when its `transcriptPath` points at a non-existent file; a ticket whose `enqueuedAt` is older than `TicketCeilingMinutes` is evicted regardless of a fresh `lastSeenAt`.

**Why:**

These are strategic §11 criteria 1, 2, 3 and 4 stated as executable checks; criterion 4 in particular guards ADR-3, where an over-eager heartbeat would turn the starvation around instead of removing it.

**Verification:**

- `Grep` - the file contains four distinct assertion labels covering ticket retirement, queue order, heartbeat survival and ceiling eviction.
- Run - `pwsh -NoProfile -File scripts/utils/test-agent-lock-queue.ps1` exits 0 and prints four `PASS` lines.

**Status:** `[x]` done

---

### Step 05.3 - Assert that a pre-change ticket still works

**Files:** `scripts/utils/test-agent-lock-queue.ps1`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add an assertion that hand-writes a ticket file in the old shape - `schema` 1, no `lastSeenAt`, no `turnGrantedAt` - into the sandbox queue and confirms `Get-AgentLockQueue` returns it, orders it by `seq`, and does not delete it as malformed.

**Why:**

Strategic ADR-5 requires the ticket format to change additively because the fix lands in a tree where parallel sessions already hold tickets of the old shape, and strategic §11 criterion 9 states that requirement as a checkable claim.

**Verification:**

- `Grep` - the file writes a ticket literal containing `"schema":1` (or the equivalent hashtable) and asserts on it.
- Run - `pwsh -NoProfile -File scripts/utils/test-agent-lock-queue.ps1` exits 0 and prints five `PASS` lines.

**Status:** `[x]` done

---

## Step Log

- 2026-08-07 - Step 05.1 Verification 4/4 PASS. Harness overrides `$Script:AgentLockRepoRoot` once after the dot-source, swaps `CLAUDE_CODE_SESSION_ID` per simulated session and restores the caller's value in `finally`, and documents exit codes 0/1/2.
- 2026-08-07 - Step 05.2 first run FAILED assertion 1 - a defect in the scenario, not in the product code. It forced two tickets and then acquired with the second, so the head was a different ticket and the acquire correctly refused. Rewritten to acquire with the HEAD ticket while a sibling ticket of the same session lingers, which is the state strategic §0 actually recorded (#1, #3, #5 for one session). The failure is kept in this log because it is the evidence the harness discriminates rather than rubber-stamps.
- 2026-08-07 - Step 05.2 Verification 2/2 PASS after the rewrite.
- 2026-08-07 - Step 05.3 Verification 2/2 PASS.
- 2026-08-07 - Full run: 5 assertions, 5 PASS, `expected: 0 | actual: 0 failures`, exit 0, sandbox removed. `lock-status.ps1 -Name Code -Queue` immediately afterwards showed the live lock and queue unchanged.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/utils/test-agent-lock-queue.ps1` exits 0 with every assertion `PASS`.
- [x] `temp/CODE.QUEUE` is byte-for-byte untouched by the harness run - the sandbox path is the only thing written.
- [x] `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` passes for the new script.
- [x] Dev log entry added for the file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13).

---

## Handoff Notes to Next Phase

The harness is the evidence Phase 06 cites in the documentation update and that `/spec-check` re-runs, so it must stay runnable with no arguments.

---

## Rollback Plan

Delete the new script. Nothing else depends on it.

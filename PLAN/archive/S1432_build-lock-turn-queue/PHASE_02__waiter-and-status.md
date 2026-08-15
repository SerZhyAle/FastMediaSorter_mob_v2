# Phase 02 - Detached waiter and queue visibility

**Strategic spec:** [`../S1432_build-lock-turn-queue.md`](../S1432_build-lock-turn-queue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Deliver the "your turn" signal: a short-lived waiter process that takes a ticket, blocks until its turn and exits, plus a read-only view of who holds the resource and who is waiting.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved - 02 is.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/wait-for-lock-turn.ps1` | New | ≤ 180 |
| `scripts/utils/lock-status.ps1` | Modified | ≤ 200 |
| `scripts/utils/clear-agent-lock.ps1` | Modified | ≤ 120 |

---

## Steps

### Step 02.1 - Write the waiter script

**Files:** `scripts/utils/wait-for-lock-turn.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/utils/wait-for-lock-turn.ps1` with parameters `-Name <Build|Code>`, `-Reason <string>`, `-WaitTimeoutSeconds` (default 3600), `-PollSeconds` (default 5). On start it enqueues a ticket, prints the granted position immediately, then polls `Test-AgentLockTurn` until the turn arrives and exits 0. The ticket stays in place on exit - the caller inherits it, protected by the reservation window - and the script never acquires the lock itself. Exit 2 on timeout and exit 3 when the ticket was evicted while waiting, removing the ticket in both cases.

**Why:**

Research artifact 02 concludes that the exit of a detached background process is the only channel through which an external event can return an agent to work, which forces the waiter to exit rather than hold - and the strategic §5.1 reservation is what keeps the inherited turn safe across that exit.

**Verification:**

- `Glob` - `scripts/utils/wait-for-lock-turn.ps1` exists.
- `Grep` - `New-AgentLockTicket` and `Test-AgentLockTurn` both called in that file.
- `Grep` - `exit 2` and `exit 3` both present.
- Run `pwsh -NoProfile -File scripts/utils/wait-for-lock-turn.ps1 -Name Build -Reason "S1432 smoke"` on a free lock - exits 0 in under 10 seconds.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4/4 PASS. Smoke run exited 0 in 9 s with `position 1`; the ticket survived the exit as designed (`queue left: 1`). The outcome marker was written in the same step rather than in 02.2 - a waiter that exits without leaving its verdict is not a working waiter, so splitting them would have shipped a broken intermediate.

---

### Step 02.2 - Leave a machine-readable outcome marker

**Files:** `scripts/utils/wait-for-lock-turn.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Before every exit path write a marker containing `outcome` (`granted` / `timeout` / `evicted`), `seq`, `sessionId`, `reason`, `waitedSeconds` and `decidedAt`. The marker is per session - `temp/<NAME>.TURN-<sessionId>.json` - because two agents waiting on the same queue would otherwise overwrite each other's verdict; the waiter prints its marker path on start. Document in the script header that the caller must read this file rather than trust the background task's reported exit code, and state the reason in one line. List the exit codes in the header per the reachable-exit-code rule in CLAUDE.md section 7.

**Why:**

The exit code a background task reports reflects the last command of the launch line rather than the script's own verdict, which has already turned a refused build into an apparently green one - research artifact 02 records this and requires an unambiguous marker the agent reads after waking.

**Verification:**

- `Grep` - `TURN.json` present in `scripts/utils/wait-for-lock-turn.ps1`.
- `Grep` - `granted`, `timeout` and `evicted` all present in that file.
- Run the waiter once on a free lock, then `Glob` - `temp/BUILD.TURN-<sessionId>.json` exists and contains `"outcome":"granted"`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. Marker read back verbatim: `{"outcome":"granted","lockType":"Build","seq":1,...,"waitedSeconds":6}`. Written by write-then-rename so a reader never catches a half-written verdict.

---

### Step 02.3 - Show the queue in the status query

**Files:** `scripts/utils/lock-status.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a `-Queue` switch printing the holder followed by the ordered waiting list - position, session id, reason, age - and this session's own position when it holds a ticket. Include the same list under a `queue` property when `-Json` is passed. Keep the existing exit-code contract unchanged: a populated queue is a normal answer, not a failure.

**Why:**

Strategic §11 requires the state of both queues - holder, waiting list and order - to be readable with one command, and the existing status query is the command every skill already calls for exactly that question.

**Verification:**

- `Grep` - `\[switch\]\$Queue` present in `scripts/utils/lock-status.ps1`.
- Run `pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build -Queue` - exit code 0.
- Run `pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build -Queue -Json` - output parses as JSON and carries a `queue` property.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. Text mode marks this session's own ticket with `>` and prints position plus waiting time; JSON mode carries `queue` and `myPosition`. Exit code stays 0 whether the queue is empty or not - a populated queue is an answer, not a failure.

---

### Step 02.4 - Let the clearing tool empty the queue

**Files:** `scripts/utils/clear-agent-lock.ps1`
**Depends on:** Step 02.3

**Prompt for developer:**

> Extend `clear-agent-lock.ps1` so it also removes the queue for the named lock. Without `-Force` it evicts only stale tickets and reports how many survived; with `-Force` it deletes the whole queue directory alongside the lock file. Print the number of tickets removed in both modes.

**Why:**

Strategic §2 goal 6 requires a stuck queue to resolve without human intervention, and the manual clearing tool is the escape hatch for the case the automatic eviction cannot cover - leaving it lock-only would strand tickets behind a cleared lock.

**Verification:**

- `Grep` - `Get-AgentLockQueueDir` referenced in `scripts/utils/clear-agent-lock.ps1`.
- Run `pwsh -NoProfile -File scripts/utils/clear-agent-lock.ps1 -Name Build` on a free lock - exit code 0 and output names the ticket count.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 2/2 PASS. Default mode reported `0 stale ticket(s) evicted, 1 still waiting` and left the live ticket alone; `-Force` reported `1 ticket(s) removed` and emptied the queue. Both exited 0.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Waiter run on a free lock exits 0 and writes a `granted` marker.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Catalog regeneration not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

**Phase-boundary audit (2026-08-06).** Layers 1-2; Layers 3-4 not applicable.

- **No P0/P1.** The abandoned-head case that looked like one is already bounded: a waiter that is granted its turn and then dies leaves its ticket at the head, but the head only owns the resource for its reservation window (5 min for Build, 3 for Code) - after that the next caller reports `head reservation expired` and proceeds. The ticket itself is swept later by the ceiling.
- **P2 - accepted with a note.** When a head's reservation does expire, every remaining waiter becomes eligible at once and they race for the lock. The race is resolved atomically by `FileMode.CreateNew`, so at most one wins; this is a deliberate degradation to the pre-queue behaviour for the abandonment case only, not the normal path.
- **P3 - accepted.** Turn markers (`temp/<NAME>.TURN-<sessionId>.json`) are never garbage-collected. There is at most one per session per lock and each run overwrites its own, so the file count is bounded by the number of sessions that ever waited.

---

## Handoff Notes to Next Phase

An agent can now run the waiter as a background task and be woken by its exit. The ticket survives that exit and is protected by the reservation window, which is the property Phase 03 and Phase 04 rely on when they acquire the lock after waking.

---

## Rollback Plan

Delete `scripts/utils/wait-for-lock-turn.ps1` and revert the two modified scripts - the queue core from Phase 01 stays inert without them.

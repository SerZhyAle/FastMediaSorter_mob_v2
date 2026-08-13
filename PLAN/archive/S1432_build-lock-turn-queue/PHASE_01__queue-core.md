# Phase 01 - Queue core in the shared lock library

**Strategic spec:** [`../S1432_build-lock-turn-queue.md`](../S1432_build-lock-turn-queue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 6 / 6
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Give `scripts/utils/agent-lock.ps1` a third state between free and held: an ordered queue of tickets whose owner is an agent session, with eviction and a head-of-queue reservation. No caller changes yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none.
- [ ] Strategic §6 research items blocking this phase are Resolved - 01 and 02 are.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/BUILD.LOCK` free while editing - the file under edit is dot-sourced by every gradle entry point.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/agent-lock.ps1` | Modified | ≤ 700 |

> The file is 380 lines today and crosses 500 during this phase, so Step 01.1 takes the backup required by CLAUDE.md Rule 5 before the first edit.

---

## Steps

### Step 01.1 - Back up the library and define the queue layout

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `scripts/utils/agent-lock.ps1` to `temp/S1432/agent-lock.ps1.<yyyyMMdd-HHmmss>.bak` before the first edit. Then add `Get-AgentLockQueueDir -Name <Build|Code>` returning `<repo>/temp/<NAME>.QUEUE` and creating it on demand, resolved from the same `$Script:AgentLockRepoRoot` the lock path uses so linked worktrees share one queue. Document the ticket file name as `<seq:0000>__<sessionId>.json` and the ticket body fields in the file header: `seq`, `lockType`, `sessionId`, `host`, `pid`, `reason`, `enqueuedAt`, `transcriptPath`, `schema`.

**Why:**

Strategic §3.2 requires the solution to work in a single working tree shared by all agents, and the existing repo-root resolution is what makes every linked worktree see one lock - the queue has to inherit that same resolution or two worktrees would each keep a private queue and neither would order the other.

**Verification:**

- `Glob` - a `temp/S1432/agent-lock.ps1.*.bak` file exists.
- `Grep` - `function Get-AgentLockQueueDir` matches exactly once in `scripts/utils/agent-lock.ps1`.
- `Grep` - `transcriptPath` present in the header comment block.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. Backup `temp/S1432/agent-lock.ps1.20260806-120555.bak`; `Get-AgentLockQueueDir` added; header documents the ticket schema. Dev log batched at phase close. Note: `BUILD.LOCK` was held by a concurrent session (`check-standard-fast.ps1`, pid 37688) during this edit - the exact contention this ticket addresses.

---

### Step 01.2 - Issue tickets with an atomic sequence number

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `New-AgentLockTicket -Name <Build|Code> -Reason <string>` returning the created ticket object. Derive the next sequence number as one past the highest existing ticket in the queue directory, then create the ticket file with `[System.IO.File]::Open(..., CreateNew, Write)` exactly as `Enter-AgentLock` already does - on `IOException` (another session claimed the same number) recompute and retry, up to 50 attempts, then throw. Stamp `sessionId` from `$env:CLAUDE_CODE_SESSION_ID` and resolve `transcriptPath` once here by locating `<sessionId>.jsonl` under `$env:USERPROFILE/.claude/projects`, storing the resolved full path in the ticket.

**Why:**

Strategic §7 names the race where two simultaneous enqueues take the same number and leave the order undefined; the create-new file mode is the atomic test-and-set the library already relies on for the lock itself. Resolving the transcript path once at enqueue time satisfies the §3.2 constraint that a queue check reads small files only - a recursive scan of the whole projects tree on every two-second poll would not.

**Verification:**

- `Grep` - `function New-AgentLockTicket` matches exactly once.
- `Grep` - `CreateNew` matches at least twice in the file (lock acquire plus ticket issue).
- `Grep` - `CLAUDE_CODE_SESSION_ID` present in `scripts/utils/agent-lock.ps1`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS (`New-AgentLockTicket` x1, `CreateNew` x4, session id referenced) plus a PowerShell parse check of the whole file. `Get-AgentSessionTranscriptPath` resolves the transcript once at enqueue time.

---

### Step 01.3 - Read a ticket's liveness from its owning session

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `Get-AgentTicketLiveness -Ticket <object> -StaleMinutes <int>` returning one of `self`, `foreign-live`, `foreign-stale`, `undetermined`, mirroring the verdict vocabulary of `scripts/spec_catalog/spec-next-session.ps1`. Read the last-write time of the ticket's stored `transcriptPath`; when that path is missing or unreadable, fall back to the ticket's own `enqueuedAt`. Treat an absent `$env:CLAUDE_CODE_SESSION_ID` as `undetermined` and never let that verdict evict anyone.

**Why:**

Research artifact 01 establishes that a waiter process exits before the work begins, so process liveness cannot identify a live queue member, and that a live agent session appends to its transcript every turn - which makes the transcript write time the only available heartbeat.

**Verification:**

- `Grep` - `function Get-AgentTicketLiveness` matches exactly once.
- `Grep` - `foreign-live` and `foreign-stale` both present in the file.
- `Grep` - `undetermined` present in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. `Get-AgentTicketLiveness` returns the four verdicts; `undetermined` is explicitly documented as never grounds for eviction.

---

### Step 01.4 - Evict dead and expired tickets

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Add `Remove-StaleAgentLockTickets -Name <Build|Code>` deleting every ticket whose liveness is `foreign-stale` or whose age exceeds the per-resource ticket ceiling. Add `Get-AgentLockQueue -Name <Build|Code>` returning the surviving tickets ordered by `seq`, and have it call the eviction first so every reader sees a self-cleaning queue. Skip malformed ticket files by deleting them, matching how the library already treats a torn lock file.

**Why:**

Strategic §7 rates an abandoned session holding the head of the queue as the highest-probability risk, since without eviction one closed session stops every other agent permanently - a worse failure than the contention the queue is meant to fix.

**Verification:**

- `Grep` - `function Remove-StaleAgentLockTickets` matches exactly once.
- `Grep` - `function Get-AgentLockQueue` matches exactly once.
- Run `pwsh -NoProfile -Command ". ./scripts/utils/agent-lock.ps1; Get-AgentLockQueue -Name Build | Measure-Object | Select-Object -ExpandProperty Count"` - exit code 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. Live exercise: issued a ticket (`seq=1`, transcript resolved), queue read back 1, removed, read back 0. `Get-AgentLockTimings` was introduced here rather than in Step 01.6 because eviction needs its ceilings - Step 01.6 was rewritten accordingly.

---

### Step 01.5 - Reserve the lock for the head of the queue

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** Step 01.4

**Prompt for developer:**

> Add `Test-AgentLockTurn -Name <Build|Code> [-Ticket <object>]` returning an object with `IsMyTurn`, `Position`, `HeadSessionId` and `Reason`. It is my turn when the queue is empty, or when the head ticket belongs to this session, or when the head ticket's reservation window has expired. Then make `Enter-AgentLock` consult it before the create-new acquire: a caller that is not the head and faces a live head must not take a free lock, and must report `Acquired = $false` with `BlockedBy = 'queue-head'` in the returned status. Preserve the current behaviour exactly when the queue directory is empty, so an existing caller that never enqueues is unaffected.

**Why:**

Strategic §5.1 states that the ownership of the resource must move from a race to the head of the queue, and §7 records that the turn is otherwise lost precisely in the gap between the signal and the start of work - a free lock grabbed by a latecomer in that gap makes the ordering fictional.

**Verification:**

- `Grep` - `function Test-AgentLockTurn` matches exactly once.
- `Grep` - `BlockedBy` present in `scripts/utils/agent-lock.ps1`.
- Run `pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build` - exit code 0 and output still names the lock state (no regression with an empty queue).

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS plus a live fairness probe: with a foreign live ticket at the head and `BUILD.LOCK` free, `Enter-AgentLock` returned `Acquired=False, BlockedBy=queue-head`; after evicting that ticket the same call reported `IsMyTurn=True`. The reservation window is stamped on the head at the moment the lock goes free (`turnGrantedAt`), not at enqueue time - otherwise a waiter queued behind a 20-minute build would lose its reservation before ever being offered the resource.

---

### Step 01.6 - Give each resource its own timings

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** Step 01.5

**Prompt for developer:**

> The timings table itself was introduced in Step 01.4, which needed its ceilings. Finish the job here: route the pre-existing staleness literals through it. `Get-AgentLockStatus` currently hardcodes 60 minutes for `Build` and 10 for `Code` in its own default - replace that branch with `Get-AgentLockTimings -Name $Name` so no minute value lives outside the table.

**Why:**

Strategic §5.3 requires the eviction and reservation spans to be parameters rather than constants because a long release build and a short edit have different reasonable limits, and research artifact 01 flags the existing 45-minute session threshold as deliberately generous and wrong for code edits.

**Verification:**

- `Grep` - `AgentLockTimings` (or the chosen table name) matches at least four times in the file.
- `Grep -c` - literal `45` appears at most once in `scripts/utils/agent-lock.ps1`.
- Run `pwsh -NoProfile -Command ". ./scripts/utils/agent-lock.ps1; (Get-AgentLockTimings -Name Code).ReservationMinutes"` - prints `3`, exit code 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. `Get-AgentLockStatus` now reads its default staleness from the timings table; the literal `45` survives in exactly one place - the table itself - after the leftover default in `Get-AgentTicketLiveness` was made a mandatory parameter.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build` and `-Name Code` both exit 0 with an empty queue present.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for `scripts/utils/agent-lock.ps1`.
- [x] Catalog regeneration not applicable - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

**Phase-boundary audit (2026-08-06).** Layers 1 and 2 of `docs/CODE_AUDIT_PROTOCOL.md`; Layers 3-4 not applicable (no listeners, no Room).

- **P1 - fixed in phase.** `Set-AgentTicketTurnGranted` wrote the reservation stamp in place, and the sweeper deletes a ticket it cannot parse - so a reader catching a half-written ticket could evict the very queue head being stamped. Fixed two ways: the stamp is now write-then-rename (`Move-Item -Force`, an atomic replace on NTFS), and an unparsable ticket is only deleted after it has stayed unreadable for 60 seconds.
- **P3 - accepted.** `Test-AgentLockTurn` re-reads the queue directory on every poll. With a handful of small files per queue this is cheaper than any caching scheme would be to keep correct, and the strategic §3.2 budget is "small file reads only", which it satisfies.
- No P0/P2 findings. File is 655 lines, inside the phase budget and far from the 1500 ceiling.

---

## Handoff Notes to Next Phase

The library can now issue a ticket, order the queue, evict the dead and answer "is it my turn". Nothing calls any of it yet: `Enter-AgentLock` behaves exactly as before while the queue directory is empty, which is the invariant Phase 02 and Phase 03 build on.

---

## Rollback Plan

Restore `scripts/utils/agent-lock.ps1` from the Step 01.1 backup and delete `temp/BUILD.QUEUE` / `temp/CODE.QUEUE`. No caller depends on the new functions until Phase 02.

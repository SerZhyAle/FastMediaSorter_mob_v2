# Phase 01 - Acquiring the lock retires the session's tickets

**Strategic spec:** [`../S1448_code-lock-queue-head-starvation.md`](../S1448_code-lock-queue-head-starvation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Close the starvation loop: a session that takes `CODE.LOCK` leaves no ticket of its own behind, and a caller without a ticket can no longer inherit the turn from its own abandoned queue head.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done - none.
- [x] Strategic §6 research items blocking this phase are Resolved - the single item is Resolved.
- [x] Working tree is clean or on a feature branch.
- [x] `temp/CODE.QUEUE` inspected before editing (`lock-status.ps1 -Name Code -Queue`) so a pre-existing sibling ticket is not mistaken for a regression introduced here.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/agent-lock.ps1` | Modified | ≤ 900 |
| `scripts/utils/enter-code-lock.ps1` | Modified | ≤ 120 |

> `agent-lock.ps1` is 771 LOC, above the 500-LOC threshold - Step 01.1 takes the mandatory timestamped backup before any edit.

---

## Steps

### Step 01.1 - Back up `agent-lock.ps1` before editing

**Files:** `temp/S1448/`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `scripts/utils/agent-lock.ps1` to `temp/S1448/agent-lock.<yyyyMMdd-HHmmss>.ps1` before making any edit in this phase. Create `temp/S1448/` if absent.

**Why:**

CLAUDE.md Rule 5 requires a timestamped backup before editing a file over 500 LOC, and this file is the machinery every concurrent session depends on - a bad intermediate state stops every agent in the tree at once, which strategic §7 records as the highest-probability risk of this ticket.

**Verification:**

- `Glob` - `temp/S1448/agent-lock.*.ps1` matches at least one file.

**Status:** `[x]` done

---

### Step 01.2 - Sweep every ticket of the acquiring session on a successful acquire

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `Remove-AgentSessionTickets -Name <Build|Code> -SessionId <string>` to `agent-lock.ps1`: it deletes every queue ticket whose `sessionId` equals the argument and returns the count removed. Call it from `Enter-AgentLock` at the point where the lock file has just been written successfully, resolving the session id the same way `New-AgentLockTicket` does (`CLAUDE_CODE_SESSION_ID`, falling back to `pid-$PID`). It replaces the current single-ticket `Remove-Item` on `$Ticket.path`; keep removing `$Ticket` explicitly as well, so a caller whose ticket carries a different session id is still retired. Removal stays best-effort - a failed delete must not fail the acquire.

**Why:**

Strategic §4 Д1 traces the starvation to `Enter-AgentLock` retiring only the ticket handed to it, which leaves the ticket from the session's previous step sitting on the queue head while that same session holds the lock; strategic §2.1 requires that a session holding the lock own no queue head.

**Verification:**

- `Grep` - `function Remove-AgentSessionTickets` matches exactly once in `scripts/utils/agent-lock.ps1`.
- `Grep` - `Remove-AgentSessionTickets` matches at least twice in that file (declaration plus the call inside `Enter-AgentLock`).
- `Grep` - the call site sits after the `$stream.Close()` of the lock-file write and before the `return [pscustomobject]@{ Acquired = $true`.

**Status:** `[x]` done

---

### Step 01.3 - Stop a ticket-less caller inheriting the turn from its own session's head

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> In `Test-AgentLockTurn`, delete the `elseif ([string]$head.sessionId -eq $sessionId)` branch that returns `IsMyTurn = $true` with reason `head of queue (this session)`. A caller that passes no ticket now falls through to the lock and reservation checks like any other. Leave the ticket-based head comparison (`[int]$head.seq -eq [int]$Ticket.seq`) and the empty-queue short-circuit untouched.

**Why:**

Strategic ADR-1 rules that the turn belongs to a ticket rather than to a session, because a session can hold the lock and a queue ticket at the same time - the exact state strategic §4 Д1 observed, in which this branch handed the session its next turn straight back and no sibling ever reached the head.

**Verification:**

- `Grep` - `head of queue \(this session\)` returns zero hits in `scripts/utils/agent-lock.ps1`.
- `Grep` - `[int]$head.seq -eq [int]$Ticket.seq` still matches exactly once in that file.
- `Grep` - `queue is empty` still matches exactly once in that file.

**Status:** `[x]` done

---

### Step 01.4 - Make `enter-code-lock.ps1` take its ticket before it asks for the lock

**Files:** `scripts/utils/enter-code-lock.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Restructure the acquire path to mirror `Enter-BuildLockOrExit`: call `New-AgentLockTicket -Name Code -Reason $Reason` first (it already reuses the session's existing ticket rather than issuing a second one), then pass that ticket to every `Enter-AgentLock` call on both the immediate and the `-Wait` path. Remove the later, second `New-AgentLockTicket` call. On a failed non-waiting acquire leave the ticket in the queue - the caller needs the place it just earned - and keep removing it only when `-Wait` ran out of time.

**Why:**

Strategic §5.1.1 requires enqueue-first here so that the ticket the acquire retires is the session's own, and so that a session releasing and immediately re-requesting the lock queues behind whoever was already waiting rather than ahead of them, which strategic §2.2 states as a goal.

**Verification:**

- `Grep` - `New-AgentLockTicket` matches exactly once in `scripts/utils/enter-code-lock.ps1`.
- `Grep` - `Enter-AgentLock` calls in that file all carry `-Ticket $ticket`; zero hits for an `Enter-AgentLock -Name Code -Reason $Reason` line with no `-Ticket`.
- `Grep` - the `New-AgentLockTicket` line precedes the first `Enter-AgentLock` line by file order.

**Status:** `[x]` done

---

### Step 01.5 - Recognise a re-entrant acquire instead of queueing behind our own lock

**Files:** `scripts/utils/enter-code-lock.ps1`
**Depends on:** Step 01.4

**Prompt for developer:**

> Before taking a ticket, read `Get-AgentLockStatus -Name Code`. When the lock exists, is not stale, and its `SessionId` equals this session's id, print that the lock is already held by this session and exit 0 without enqueueing. Mirror the re-entrancy guard `Enter-BuildLockOrExit` already applies for `BUILD.LOCK`. Record the re-entrant exit in the script's `.EXIT CODES` block.

**Why:**

Found by the Phase 01 boundary audit: a session that already holds the lock and asks again enqueues itself behind its own lock, exits 4, and leaves that ticket on the queue head - which is the exact state strategic §2.1 forbids, arriving through a second door than the one Step 01.2 closed.

**Verification:**

- `Grep` - `Get-AgentLockStatus -Name Code` matches at least twice in `scripts/utils/enter-code-lock.ps1` (the pre-existing Build notice reads `-Name Build`, so both Code reads are new or moved).
- `Grep` - the re-entrancy branch precedes the `New-AgentLockTicket` call by file order.
- `Grep` - `.EXIT CODES` block mentions the already-held case under exit 0.
- Run - calling the script twice in a row from one session returns exit 0 both times and leaves `temp/CODE.QUEUE` with no ticket of this session.

**Status:** `[x]` done

---

## Step Log

- 2026-08-07 - Step 01.1 Verification 1/1 PASS. `temp/S1448/agent-lock.20260807-031440.ps1` (40378 bytes).
- 2026-08-07 - Step 01.2 Verification 3/3 PASS. `Remove-AgentSessionTickets` declared at agent-lock.ps1:320, called at :656, between the lock-file `$stream.Close()` and the `Acquired = $true` return at :665.
- 2026-08-07 - Step 01.3 Verification 3/3 PASS. `head of queue (this session)` 0 hits; ticket-seq comparison 1 hit; `queue is empty` 1 hit. Removed the `$sessionId` local the deleted branch orphaned (Rule 20).
- 2026-08-07 - Step 01.4 Verification 3/3 PASS. One `New-AgentLockTicket` call at enter-code-lock.ps1:58, preceding both `Enter-AgentLock` calls (:60, :68), each carrying `-Ticket $ticket`.
- 2026-08-07 - **Live confirmation of goal §2.2 on the real queue.** After releasing `CODE.LOCK` and immediately re-requesting it, the new code refused and queued this session at position 2, behind sibling session `d4d1793a` (`/spec-all S1447 gate fixes`) which had been waiting at the head. The pre-S1448 code would have granted the turn straight back through the session-id branch removed in Step 01.3.
- 2026-08-07 - The same run reproduced strategic §4 Д3 verbatim: `enter-code-lock: CODE.LOCK is held by another session` plus `Holder: session  (age 0s, reason: '')` while `lock-status -Json` reported `"Exists":false`. Fixed in Phase 03.
- 2026-08-07 - `post-change.ps1 -ChangeType Script -ScopeToFile` over both files: `post-change: PASS`, exit 0. Its release step correctly left the sibling's `CODE.LOCK` in place (owner check).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Code -Queue` runs and exits 0 with both files edited.
- [x] `pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1` acquires on a free lock and leaves `temp/CODE.QUEUE` with no ticket of this session - proven at 03:18:54 once the queued turn arrived: `acquire-exit=0` followed by `Code queue: empty`, with this session's ticket #2 swept by `Remove-AgentSessionTickets`. The first attempt could not test it, because sibling session `d4d1793a` legitimately owned the head and the acquire correctly refused.
- [x] `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` passes for both edited scripts - `expected: 0 | actual: 0`, exit 0.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13).

---

## Handoff Notes to Next Phase

Every acquire path for both locks now carries a ticket, and the turn is decided by ticket identity alone. Phase 02 can therefore treat the ticket as the single carrier of a waiter's liveness without a second, session-level path to keep in sync.

---

## Rollback Plan

Restore `scripts/utils/agent-lock.ps1` from the Step 01.1 backup and revert `enter-code-lock.ps1`. No file format changed in this phase, so a rollback needs no queue cleanup.

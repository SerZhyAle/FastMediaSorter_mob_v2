# Phase 02 - A waiting ticket proves its own liveness

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

Give a queued ticket a heartbeat written by its own polling waiter, so a session that waits exactly as the contract demands is no longer evicted for being silent - while the absolute ticket ceiling stays untouched.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.
- [x] The Step 01.1 backup of `agent-lock.ps1` exists under `temp/S1448/`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/agent-lock.ps1` | Modified | ≤ 900 |
| `scripts/utils/wait-for-lock-turn.ps1` | Modified | ≤ 150 |

---

## Steps

### Step 02.1 - Add a heartbeat stamp writer for a ticket

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `Set-AgentTicketHeartbeat -Ticket <object>` to `agent-lock.ps1`. It sets `lastSeenAt` on the ticket to the current unix-ms value and persists it with the same write-then-rename sequence `Set-AgentTicketTurnGranted` already uses: serialise without the `path` property, write to `"$($Ticket.path).tmp-$PID"`, then `Move-Item -Force` onto the ticket path. Unlike `Set-AgentTicketTurnGranted` it overwrites an existing value on every call. Swallow write failures - the next poll retries.

**Why:**

Strategic ADR-2 states that liveness must be proved by an action rather than inferred from silence, and the only process that can prove a waiter is alive is the waiter's own poll loop; without a place to record that proof the signal has nowhere to go.

**Verification:**

- `Grep` - `function Set-AgentTicketHeartbeat` matches exactly once in `scripts/utils/agent-lock.ps1`.
- `Grep` - `lastSeenAt` matches at least once in that file.
- `Grep` - `Move-Item -LiteralPath $staging` matches at least twice in that file (the existing turn-granted writer plus this one).

**Status:** `[x]` done

---

### Step 02.2 - Prefer the ticket's own heartbeat over the session transcript

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> In `Get-AgentTicketLiveness`, resolve `$lastSeen` from `$Ticket.lastSeenAt` first when that field is present, before consulting `$Ticket.transcriptPath`, and keep `enqueuedAt` as the final fallback. Document the precedence order in the function's comment block. Do not touch `Remove-StaleAgentLockTickets`'s separate `$ageMinutes -gt $timings.TicketCeilingMinutes` test.

**Why:**

Strategic §4 Д2 shows a correctly-waiting session evicted at the 15-minute session-staleness threshold precisely because waiting produces no transcript writes, and strategic ADR-3 requires that the absolute ceiling remain un-extendable so a genuinely abandoned head cannot hold the queue forever.

**Verification:**

- `Grep` - `lastSeenAt` matches inside the `Get-AgentTicketLiveness` body.
- `Grep` - `$ageMinutes -gt $timings.TicketCeilingMinutes` still matches exactly once in `scripts/utils/agent-lock.ps1`.
- `Grep` - `transcriptPath` still matches inside `Get-AgentTicketLiveness`, proving the transcript remains a fallback rather than being removed.

**Status:** `[x]` done

---

### Step 02.3 - Stamp the heartbeat on every poll of the waiter

**Files:** `scripts/utils/wait-for-lock-turn.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Call `Set-AgentTicketHeartbeat -Ticket $ticket` once per iteration of the wait loop, immediately after the still-queued check confirms the ticket survives, and once before the loop starts. Extend the `.DESCRIPTION` block to state that the poll doubles as the ticket's liveness heartbeat.

**Why:**

Strategic §2.3 requires that a session waiting through the background waiter not be evicted as silent, and the poll loop is the only recurring event this session produces while it waits.

**Verification:**

- `Grep` - `Set-AgentTicketHeartbeat` matches at least twice in `scripts/utils/wait-for-lock-turn.ps1`.
- `Grep` - one `Set-AgentTicketHeartbeat` call sits between the `$stillQueued` guard and the `Test-AgentLockTurn` call inside the `while` loop.
- `Grep` - `heartbeat` matches in the file's comment header.

**Status:** `[x]` done

---

## Step Log

- 2026-08-07 - Step 02.1 Verification 3/3 PASS. `Set-AgentTicketHeartbeat` declared once at agent-lock.ps1:418; `Move-Item -LiteralPath $staging` now matches twice (:409 turn-granted, :444 heartbeat).
- 2026-08-07 - Step 02.2 Verification 3/3 PASS. `lastSeenAt` read at :266-267 inside `Get-AgentTicketLiveness`, ahead of `transcriptPath` at :269; the ceiling test at :322 is untouched.
- 2026-08-07 - Step 02.3 Verification 3/3 PASS. `Set-AgentTicketHeartbeat` called at wait-for-lock-turn.ps1:92 (before the loop) and :114 (between the still-queued guard and `Test-AgentLockTurn`); header documents the heartbeat at :21.
- 2026-08-07 - Functional proof in an isolated sandbox (`$Script:AgentLockRepoRoot` overridden): a ticket written without `lastSeenAt` gained `"lastSeenAt":1786065677997` after one stamp, and `Get-AgentLockQueue` returned it rather than sweeping it.
- 2026-08-07 - The live `wait-for-lock-turn.ps1` run could not show the field: it timed out against this session's own lock and removed its ticket on the way out, which is correct behaviour and no evidence either way. The sandbox probe is the evidence; Phase 05 turns it into a standing assertion.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/utils/wait-for-lock-turn.ps1 -Name Code -Reason "S1448 heartbeat check" -WaitTimeoutSeconds 20` runs to a verdict and writes `temp/CODE.TURN-<sessionId>.json`.
- [x] A ticket file written during that run contains a `lastSeenAt` field.
- [x] `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` passes for both edited scripts.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13).

---

## Handoff Notes to Next Phase

The ticket schema now carries an optional `lastSeenAt`; every reader must still accept a ticket without it, which Phase 05 proves explicitly.

---

## Rollback Plan

Restore `agent-lock.ps1` from the Step 01.1 backup and revert `wait-for-lock-turn.ps1`. Tickets carrying `lastSeenAt` stay readable by the reverted code, since the field is additive and unread there.

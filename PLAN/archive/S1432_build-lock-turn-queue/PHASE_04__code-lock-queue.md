# Phase 04 - Code lock gets an owner, a heartbeat and a queue

**Strategic spec:** [`../S1432_build-lock-turn-queue.md`](../S1432_build-lock-turn-queue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Turn the code lock from a blind ten-minute advisory into an owned, queued resource: stamped by session, alive while its session is alive, released only by its owner, and entered through the queue.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `temp/CODE.LOCK` free - this phase rewrites its acquire and release paths.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/agent-lock.ps1` | Modified | ≤ 700 |
| `scripts/utils/enter-code-lock.ps1` | Modified | ≤ 140 |
| `scripts/utils/exit-code-lock.ps1` | Modified | ≤ 60 |

---

## Steps

### Step 04.1 - Stamp the code lock with its owning session

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Extend the lock body written by `Enter-AgentLock` with `sessionId`, `transcriptPath` and `schema: 2`, keeping every existing field. In `Get-AgentLockStatus`, judge a `Code` lock stale when its owning session is stale by the `Get-AgentTicketLiveness` verdict, or when its age passes the per-resource ceiling - whichever comes first. A lock written before this change carries no `sessionId`; treat that shape as wall-clock-only exactly as today, so an old file is still read correctly and still expires.

**Why:**

Strategic §3.2 requires the lock format to grow compatibly so a record written by the previous version still reads and still expires, and research artifact 01 shows the code lock is the one holder with no liveness signal at all - which a queue behind it would turn from a nuisance into a stall.

**Verification:**

- `Grep` - `schema` present in the lock body construction in `scripts/utils/agent-lock.ps1`.
- `Grep` - `sessionId` referenced inside `Get-AgentLockStatus`.
- Run `pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Code -Json` against a hand-written pre-change lock file - exit code 0 and a `status` of `held` or `stale`, never an error.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. A hand-written schema-1 lock read back as `status=held, sessionId=''`, exit 0 - old files still parse and still expire by the clock. A freshly taken lock carries the session id. Audit correction applied in the same step: a schema-2 lock whose owner is ALIVE no longer expires by the clock at all (proven at age 2400 s, `stale=False`), because expiring a working session by wall time would hand its turn to the next agent mid-edit; a lock whose owner has gone quiet still goes stale (same age, `stale=True`).

---

### Step 04.2 - Release only the lock this session owns

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Change `Exit-AgentLock -Name Code` from an unconditional delete to an owner check: remove the file only when its `sessionId` matches this session, or when it carries no `sessionId` at all (a pre-change file), or when the recorded owner is already stale. Otherwise leave it and print one line naming the owner. Keep the acquire and release in different processes - that asymmetry is the existing design and does not change.

**Why:**

Strategic §5.1 makes the code lock the head of a queue, and an unconditional release lets one agent's closure delete another agent's live lock - which under a queue hands the turn to the wrong session rather than merely losing an advisory hint.

**Verification:**

- `Grep` - `Exit-AgentLock` body contains a `sessionId` comparison.
- `Grep -c` - the unconditional `Remove-Item` in the `Code` branch no longer appears without a preceding owner check (verified by reading the function).
- Run `pwsh -NoProfile -File scripts/utils/exit-code-lock.ps1` with a foreign live lock present - the file survives and the output names the owner.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. With a foreign live holder the release printed `CODE.LOCK belongs to session foreign-session-abc .. leaving it in place` and the file survived; releasing our own lock still works. Acquire and release stay in different processes, unchanged.

---

### Step 04.3 - Enter the code lock through the queue

**Files:** `scripts/utils/enter-code-lock.ps1`
**Depends on:** Step 04.2

**Prompt for developer:**

> Rewrite the held-lock branch: instead of printing "proceeding is allowed" and exiting 0, enqueue a ticket, print the position and the holder, and exit 4 to mean "queued, not yet your turn", naming the waiter command the caller should run in the background. Add `-Wait` for callers that prefer to block inline. Keep the existing behaviour when the lock is free - acquire and exit 0 - and keep the informational notice about a live build lock.

**Why:**

Strategic §1 records that the code lock does not refuse at all today, so a second agent simply edits the same files, and §2 goal 4 accepts serialised editing as the deliberate price of one shared tree without divergence.

**Verification:**

- `Grep` - `exit 4` present in `scripts/utils/enter-code-lock.ps1`.
- `Grep` - `wait-for-lock-turn.ps1` named in that file's output text.
- `Grep` - the script header lists exit codes 0 and 4.
- Run the script twice from two processes - the second prints a position and exits 4.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4/4 PASS. Against a foreign holder the entry printed `queued at position 1`, named the holder's session and age, printed the exact background waiter command and the list of lock-free work, and exited 4. `lock-status -Name Code -Queue` then showed the ticket marked as ours.

---

### Step 04.4 - Keep automatic release honest

**Files:** `scripts/utils/exit-code-lock.ps1`
**Depends on:** Step 04.2

**Prompt for developer:**

> Update the header of `exit-code-lock.ps1` to state that release is now owner-checked and that a foreign live lock is left alone. Confirm by reading `scripts/post-change.ps1` around its `Exit-AgentLock -Name Code` call that the closure path needs no change, and record that finding in the step evidence rather than editing the file for its own sake.

**Why:**

Strategic §5.1 requires the lock to be taken for the duration of an edit rather than a whole ticket, and the automatic release inside the closure facade is what makes that lifetime hold in practice - it must keep working unchanged under the new owner check.

**Verification:**

- `Grep` - `owner` present in the header of `scripts/utils/exit-code-lock.ps1`.
- `Grep` - `Exit-AgentLock -Name Code` still matches exactly once in `scripts/post-change.ps1`.
- Run `pwsh -NoProfile -File scripts/utils/exit-code-lock.ps1` on a free lock - exit code 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. `post-change.ps1` needs no change - its single `Exit-AgentLock -Name Code` call now inherits the owner check. One defect fixed while here: the script printed "released" even when it had deliberately left a foreign lock in place, which is a false completion claim; it now reports which of the two actually happened.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Two concurrent code-lock entries produce one holder and one queued ticket with a position.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

**Phase-boundary audit (2026-08-06).** Layers 1-2.

- **P1 - found and fixed in phase.** The first cut kept the wall clock as an additional expiry for schema-2 locks, so an agent editing for longer than ten minutes would have had its own lock declared stale while it was still working - and the queue would then have handed the turn to the next agent mid-edit, causing exactly the collision this ticket exists to prevent. Liveness now wins outright: a live owner keeps the lock however long the edit runs, a quiet owner goes stale, and the clock only applies when there is no session id to ask about.
- **P2 - accepted.** Session liveness is read from a transcript write time, so a session that is alive but genuinely idle for longer than the staleness window looks gone. The window (15 min for Code) is longer than any gap between an agent's tool calls while it is working.
- No P0 findings.

---

## Handoff Notes to Next Phase

Both locks are now ordered resources with the same vocabulary. What remains is the human half: the rule that tells an agent what to do while it waits, and the documentation that describes the new states.

---

## Rollback Plan

Revert the three scripts. The lock body gains fields that older code ignores, so a rollback leaves no unreadable file behind.

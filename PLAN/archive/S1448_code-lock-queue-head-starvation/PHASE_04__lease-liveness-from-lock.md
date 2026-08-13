# Phase 04 - A held lock proves the lease is alive

**Strategic spec:** [`../S1448_code-lock-queue-head-starvation.md`](../S1448_code-lock-queue-head-starvation.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - independent subsystem
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Stop a spec-ticket lease expiring under a session that is demonstrably working that very ticket, by accepting lock ownership as a liveness signal and by refreshing the lease on the owner's own operations.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done - none.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/spec_catalog/ticket-lease.ps1` | Modified | ≤ 340 |

---

## Steps

### Step 04.1 - Treat a lock reason naming the ticket as proof of liveness

**Files:** `scripts/spec_catalog/ticket-lease.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> In `Get-LeaseLiveness`, before returning a `foreign-stale` verdict, read `Get-AgentLockStatus -Name Code` and `Get-AgentLockStatus -Name Build`. If either reports a non-stale lock whose `SessionId` equals the lease's `sessionId` and whose `Reason` contains the lease's ticket id, return `foreign-live` instead. Read each lock at most once per call.

**Why:**

Strategic §4 Д5 records `spec-next-preflight.ps1` offering S1436 as unleased while the owning session held `CODE.LOCK` with reason `/spec-dev S1436 step 06.2` - had a sibling trusted that, two sessions would have written one ticket at once, which is exactly what the S1437 lease exists to prevent.

**Verification:**

- `Grep` - `Get-AgentLockStatus` matches at least once in `scripts/spec_catalog/ticket-lease.ps1`.
- `Grep` - `foreign-live` matches inside the `Get-LeaseLiveness` body.
- `Grep` - the ticket id is matched against the lock reason (a `-match` or `.Contains` against `$Lease.id`).

**Status:** `[x]` done

---

### Step 04.2 - Refresh the lease when its owner touches it

**Files:** `scripts/spec_catalog/ticket-lease.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add a `lastSeenAt` unix-ms field to the lease payload written by `Write-LeaseFile`, and rewrite it whenever a verb runs for a lease this session owns. Make `Get-LeaseLiveness` prefer `lastSeenAt` over the transcript when present, keeping `claimedAt` as the last fallback, and leave the 480-minute `TicketCeilingMinutes` test judging `claimedAt` so the absolute ceiling cannot be extended. A lease file written before this change carries no `lastSeenAt` and must still be read.

**Why:**

Strategic ADR-2 makes an action the liveness signal rather than silence, and ADR-3 keeps the absolute ceiling un-extendable; strategic §5.3 additionally requires the ticket format to stay additive so leases written by a running sibling session keep working.

**Verification:**

- `Grep` - `lastSeenAt` matches at least three times in `scripts/spec_catalog/ticket-lease.ps1` (write, refresh, read).
- `Grep` - `TicketCeilingMinutes` still matches exactly once and still compares against a value derived from `claimedAt`.
- Run - `pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Status -Json` exits 0 against the current lease directory, which still holds pre-change lease files.

**Status:** `[x]` done

---

## Step Log

- 2026-08-07 - Step 04.1 Verification 3/3 PASS. `Test-LeaseOwnerHoldsLock` reads `Get-AgentLockStatus` for Code then Build, matches `$lock.Reason` against the escaped ticket id, and upgrades a `foreign-stale` verdict to `foreign-live`.
- 2026-08-07 - Step 04.2 Verification 3/3 PASS. `lastSeenAt` written by `Write-LeaseFile`, refreshed by `Update-LeaseHeartbeat` on every verb, read first by `Get-LeaseLiveness`; the 480-minute ceiling still judges `claimedAt`.
- 2026-08-07 - Backwards compatibility observed directly: the pre-change `temp/SPEC-TICKET.LEASES/S1448.json` (written at claim time, no `lastSeenAt`) was read, refreshed in place and reported without error. `-Verb Status` exit 0, `-Verb List -Json` exit 0.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Status` exits 0 and lists the live leases including this session's own S1448 lease.
- [x] `pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb List -Json` exits 0.
- [x] `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` passes for the edited script.
- [x] Dev log entry added for the file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13).

---

## Handoff Notes to Next Phase

The lease and the queue now share one liveness vocabulary: an explicit stamp first, an external signal second, the creation time last.

---

## Rollback Plan

Revert the script. Lease files carrying `lastSeenAt` remain readable by the reverted code, which simply ignores the field.

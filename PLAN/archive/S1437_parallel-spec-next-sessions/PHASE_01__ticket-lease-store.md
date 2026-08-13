# Phase 01 - Ticket lease store

**Strategic spec:** [`../S1437_parallel-spec-next-sessions.md`](../S1437_parallel-spec-next-sessions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Introduce a ticket-lease registry with atomic claim, owner-checked release, liveness-based expiry and a holder-map report; no picker or catalog behaviour changes yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/` is gitignored, so new lease files are never committed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/agent-lock.ps1` | Modified | ≤ 880 |
| `scripts/spec_catalog/ticket-lease.ps1` | New | ≤ 320 |

> Backup / split thresholds: `scripts/utils/agent-lock.ps1` is 844 LOC - over the 500 LOC line, so step 01.1 carries an explicit backup sub-step per CLAUDE.md Rule 5.

---

## Steps

### Step 01.1 - Add the lease timing row to the shared timings table

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up `scripts/utils/agent-lock.ps1` to `temp/S1437/` with a timestamped name first - the file is 844 LOC.
> Add a third row `SpecTicket` to `$Script:AgentLockTimings` alongside `Build` and `Code`, with `SessionStaleMinutes = 45` matching the round-state window, and `TicketCeilingMinutes = 480` as the liveness-independent ceiling. `LockStaleMinutes` and `ReservationMinutes` do not apply to a lease - set them to `0` and note in a comment that the lease has no lock file and no queue reservation.
> Widen the `[ValidateSet('Build', 'Code')]` on `Get-AgentLockTimings` to accept `SpecTicket`. Do not widen the ValidateSet on any other function - `Enter-AgentLock`, `Exit-AgentLock`, `Get-AgentLockStatus`, `New-AgentLockTicket` and `Test-AgentLockTurn` stay two-valued, because a lease has no lock file for them to operate on.

**Why:**

ADR-6 places the lease window at the round-state value rather than the code-lock value, because a lease covers a whole ticket including a release-scale build and an over-tight window would hand a live session's ticket to a sibling mid-work - the failure S1396 was written to stop. The numbers go in the existing table rather than beside the new script because that table is deliberately the single source for every minute value in this family.

**Verification:**

- `Grep` - `SpecTicket` matches inside the `$Script:AgentLockTimings` literal in `scripts/utils/agent-lock.ps1`.
- `Grep` - `ValidateSet('Build', 'Code', 'SpecTicket')` present on `Get-AgentLockTimings`.
- `Grep` - `ValidateSet('Build', 'Code')` still returns at least 5 hits in the file (the untouched functions).
- Run `pwsh -NoProfile -Command ". ./scripts/utils/agent-lock.ps1; (Get-AgentLockTimings -Name SpecTicket).SessionStaleMinutes"` - prints `45`, exit 0.
- `Glob` - a timestamped backup of `agent-lock.ps1` exists under `temp/S1437/`.

**Status:** `[x]` done

---

### Step 01.2 - Create the lease script with Claim and Release

**Files:** `scripts/spec_catalog/ticket-lease.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `scripts/spec_catalog/ticket-lease.ps1` with a `-Verb` parameter over `Claim`, `Release`, `List`, `Sweep`, `Status`, plus `-Id <Sxxxx>`, `-Reason <string>`, `-Json` and `-StaleMinutes` (default from `Get-AgentLockTimings -Name SpecTicket`). Dot-source `scripts/utils/agent-lock.ps1` for `Get-AgentTicketLiveness` and the timings - do not restate the liveness rule.
> Store one file per lease at `temp/SPEC-TICKET.LEASES/<Sxxxx>.json`, resolving the directory from `$PSScriptRoot` and creating it when absent. Each file carries `schema`, `id`, `sessionId`, `host`, `pid`, `reason`, `claimedAt` (unix ms) and `transcriptPath` resolved once at claim time via the helper already in `agent-lock.ps1`.
> `Claim` creates the file with `[System.IO.File]::Open($path, [System.IO.FileMode]::CreateNew, ...)` and treats the resulting `IOException` as "lost the race", not as an error. Re-claiming a lease this session already owns succeeds and is idempotent. Claiming one held by a stale session sweeps it first, then claims.
> `Release` removes the lease only when its `sessionId` matches this session or the lease is already stale; releasing a live foreign lease is refused, and releasing an absent lease succeeds silently.
> Give the script an exit-code contract in its header and honour it: `0` claimed/released, `1` error, `3` claim lost to a live foreign session, `4` release refused - a live foreign session owns it. Write the losing holder's identity to stdout as JSON under `-Json` so a caller reads the verdict from the payload rather than the exit code.

**Why:**

ADR-2 makes the claim an atomic file creation because a check-then-write leaves a gap between two disk calls, and that gap is exactly how two sessions take one ticket. ADR-1 puts the registry in the temp area rather than the catalog journal because a lease is machine-local and dies with its session, while the journal is version-controlled and permanent. The owner check on release exists so a finishing session cannot free a sibling's live ticket.

**Verification:**

- `Glob` - `scripts/spec_catalog/ticket-lease.ps1` exists.
- `Grep` - `FileMode\]::CreateNew` matches at least once.
- `Grep` - `Get-AgentTicketLiveness` matches - the liveness rule is consumed, not re-implemented.
- `Grep` - no function in this file compares a transcript write time against a threshold: the only liveness *rule* is the one in `agent-lock.ps1`. A thin adapter that reshapes a lease into that helper's input and returns its verdict unchanged is expected and is not a second copy. (Predicate corrected 2026-08-06: the original `function Get-.*Liveness` grep also matched the delegating adapter `Get-LeaseLiveness`, which contains no rule at all.)
- `Grep` - the header lists exit codes `0`, `1`, `3`, `4`.
- Run `pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Claim -Id S9999 -Reason "smoke"` twice - first exit 0, second exit 0 (idempotent same-session re-claim); then `-Verb Release -Id S9999` exit 0.

**Status:** `[x]` done

---

### Step 01.3 - Sweep stale leases on every read

**Files:** `scripts/spec_catalog/ticket-lease.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add an internal sweep that deletes every lease whose liveness is `foreign-stale`, or whose age exceeds `TicketCeilingMinutes` regardless of liveness, and run it as the first action of `List`, `Status` and `Claim`. Expose it as `-Verb Sweep` for manual use.
> Give an unreadable lease file a 60-second grace window before treating it as corrupt and deleting it, so a file caught mid-write is not mistaken for garbage.
> A lease whose liveness is `undetermined` - no session id in the environment - is never swept.

**Why:**

Strategic goal 3 requires a session that stopped showing signs of life to return its ticket without human intervention, and §5.1 places that cleanup on whoever reads next rather than on a separate watchdog, matching the existing queue design. The ceiling exists on top of liveness for the case §7 names: a session still writing its transcript while the ticket itself was abandoned.

**Verification:**

- `Grep` - `foreign-stale` matches in `ticket-lease.ps1`.
- `Grep` - `TicketCeilingMinutes` matches in `ticket-lease.ps1`.
- `Grep` - `undetermined` matches, guarding against sweeping when ownership cannot be judged.
- Hand-write a lease file with a `sessionId` that owns no transcript and a `claimedAt` older than the ceiling, run `-Verb List`, confirm the file is gone and exit 0.

**Status:** `[x]` done

---

### Step 01.4 - Report the holder map

**Files:** `scripts/spec_catalog/ticket-lease.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Implement `-Verb Status` printing one line per live lease: ticket id, owning session id, host, how long ago that session last showed signs of life, and the claim reason. Mark this session's own leases distinctly. `-Json` emits the same set as an array.
> `-Verb List` emits ids only, one per line under plain output and a JSON array under `-Json`, so a caller can feed it straight into an exclusion list.
> Both verbs exit 0 when no lease is held and print an explicit "no leases held" line rather than nothing.

**Why:**

Strategic goal 6 and criterion 8 require one command that shows which session holds which ticket. The two shapes are split because `Status` answers a human and `List` feeds Phase 02's exclusion set, and one format cannot serve both without a caller parsing prose.

**Verification:**

- Run `pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Status` with no leases held - exit 0, output contains `no leases held`.
- Claim `S9998`, run `-Verb Status`, confirm the output names `S9998` and the current session; run `-Verb List -Json` and confirm it parses as a JSON array containing `S9998`; release it.
- `Grep` - `-Verb` help text in the header names all five verbs.

**Status:** `[x]` done

---

### Step 01.5 - Register the script in the cheatsheet source

**Files:** `scripts/spec_catalog/ticket-lease.ps1`
**Depends on:** Step 01.4

**Prompt for developer:**

> Give `ticket-lease.ps1` the comment-based help block the cheatsheet generator reads - `.SYNOPSIS`, `.DESCRIPTION`, a `.PARAMETER` entry per parameter and at least two `.EXAMPLE` blocks covering `Claim` and `Status`. Match the header shape already used by `scripts/utils/wait-for-lock-turn.ps1`, including the exit-code list.

**Why:**

`scripts/quality/assert-script-cheatsheet-sync.ps1` rebuilds `docs/SCRIPT_CHEATSHEET.md` from these headers and fails the closure gate when a script's help is missing or stale, so a script without the block cannot pass Phase 06.

**Verification:**

- `Grep` - `.SYNOPSIS`, `.DESCRIPTION` and `.EXAMPLE` each match in `ticket-lease.ps1`.
- Run `pwsh -NoProfile -File scripts/utils/help.ps1 -Name ticket-lease.ps1` - exit 0, prints the synopsis.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Status` exits 0 - no gradle build applies, this phase touches no Kotlin.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

`ticket-lease.ps1 -Verb List` is the exclusion source Phase 02 consumes. Claim is atomic and losing a claim is a normal outcome carrying exit 3, not an error - Phase 05 builds its retry loop on that. Lease timings live in `$Script:AgentLockTimings.SpecTicket`; no caller hardcodes a minute value.

---

## Rollback Plan

Delete `scripts/spec_catalog/ticket-lease.ps1`, revert the `SpecTicket` row and the widened ValidateSet in `agent-lock.ps1`, and remove `temp/SPEC-TICKET.LEASES/`. Nothing else consumes the store until Phase 02.

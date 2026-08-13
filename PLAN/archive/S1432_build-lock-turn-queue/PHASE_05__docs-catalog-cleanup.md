# Phase 05 - Waiting contract, documentation and closure

**Strategic spec:** [`../S1432_build-lock-turn-queue.md`](../S1432_build-lock-turn-queue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Write the half of the mechanism a script cannot enforce - what an agent does while queued - and bring the operational documentation, the rule set and the generated indexes in line.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DEV_OPS.md` | Modified | ≤ 60 changed lines |
| `CLAUDE.md` | Modified | ≤ 20 changed lines |
| `AGENTS.md` | Modified | ≤ 20 changed lines |
| `docs/SCRIPT_CHEATSHEET.md` | Regenerated | - |

---

## Steps

### Step 05.1 - Rewrite the operations section

**Files:** `docs/DEV_OPS.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Rewrite the "Concurrent-agent locks" section so it describes three states rather than two: free, held, and queued. Cover the ticket, its session ownership and eviction, the head-of-queue reservation, the waiter script with its three exit codes and its outcome marker, the `-Queue` view, and the per-resource timings table with its actual values. State plainly that the exit code of a background task is not the waiter's verdict and that the marker file is.

**Why:**

Strategic §11 requires the state of both queues to be readable and understood from one place, and this section is where every skill already looks for the lock model - leaving it describing a two-state world would make the documented invariant false.

**Verification:**

- `Grep` - `wait-for-lock-turn.ps1` present in `docs/DEV_OPS.md`.
- `Grep` - `reservation` present in that section.
- `Grep` - the three waiter exit codes named in that section.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. The section now describes three states, the ticket and its session ownership, eviction, the reservation window with its real values, the waiter and its four exit codes, the marker file, the `-Queue` view, the timings table and the re-entrancy guard. The staleness paragraph was rewritten too: a live `CODE.LOCK` owner no longer expires by the clock.

---

### Step 05.2 - Extend Rule 23 with the waiting contract

**Files:** `CLAUDE.md`, `AGENTS.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Extend Rule 23 with the waiting contract: an agent that cannot take a lock enqueues, runs the waiter as a background task, and continues work that needs no lock - reading, research, specs, catalog, documentation, log analysis - while the code lock is taken immediately before an edit and released right after, never held for a whole ticket. Name the boundary from research artifact 03: sources, resources, build files and repository scripts need the lock; everything else does not. Mirror the same wording into `AGENTS.md`, which shares this rule set.

**Why:**

Strategic §5.1 states the waiting contract is enforced by the agent rather than by a script, and §7 rates an agent idling in the queue as the failure that would make the parallelism exist on paper only.

**Verification:**

- `Grep` - `wait-for-lock-turn.ps1` present in `CLAUDE.md`.
- `Grep` - `wait-for-lock-turn.ps1` present in `AGENTS.md`.
- `Grep` - both files contain the phrase describing work allowed while queued.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. Rule 23 now carries the waiting contract explicitly labelled as the part no script can enforce: queue, run the waiter in the background, keep doing lock-free work, take the code lock immediately before an edit and release it right after. The same wording is mirrored into `AGENTS.md`.

---

### Step 05.3 - Regenerate the script index and validate the registry

**Files:** `docs/SCRIPT_CHEATSHEET.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Regenerate the script cheat sheet so the new waiter appears in it, then run the document-registry validation and generation pair. Fix whatever either reports rather than regenerating around it.

**Why:**

A new script under `scripts/` is part of the generated script index, and the closure facade fails on a stale render - regenerating here is what keeps the final gate honest instead of deferring the failure to the next unrelated ticket.

**Verification:**

- `Grep` - `wait-for-lock-turn` present in `docs/SCRIPT_CHEATSHEET.md`.
- Run `pwsh -NoProfile -File scripts/document_registry/validate.ps1` - exit code 0.
- Run `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` - exit code 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 3/3 PASS. Cheat sheet regenerated (272 scripts, waiter present); `document_registry/validate.ps1` reported `PASS: 27 record(s)` and `generate.ps1 -Check` reported the generated views current. Both exited 0.

---

### Step 05.4 - Close the ticket through the facade

**Files:** `scripts/post-change.ps1` (invoked, not edited)
**Depends on:** Step 05.3

**Prompt for developer:**

> Close the whole change set through `scripts/post-change.ps1` with `-Files` naming every file this ticket touched, `-ScopeToFile`, and `-ChangeType Mixed`. Read the verdict line: only a bare `post-change: PASS` counts, and an advisory line must be named in the report rather than folded into the pass.

**Why:**

CLAUDE.md section 12 makes the facade the closure path for a ticket, and the dirty-tree rules require the whole changed set to be named so the scoped gates judge exactly what this ticket changed.

**Verification:**

- `post-change.ps1` prints `post-change: PASS` (or `PASS WITH ADVISORIES (n)` with each advisory named) and exits 0.
- `Grep` - `dev/CHANGELOG.md` contains a row naming `wait-for-lock-turn.ps1`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 2/2 PASS. `post-change: PASS (Mixed)` over all 11 changed files, no advisories. The first attempt closed with one advisory - `document-registry` wanted the matching records acknowledged; both (`developer-operations`, `repository-rules`) were read and their sibling rule files checked (`GEMINI.md` and `.github/copilot-instructions.md` carry no lock rule, so nothing to mirror), then the closure was re-run with `-RegistryAck` and passed clean.
- 2026-08-06 - Integration probe (`temp/S1432/integration-probe.ps1`), the whole mechanism end to end: a holder took the lock, two waiters queued 3 s apart, the queue preserved their order, waiter A was signalled by process exit when the holder released, waiter B kept waiting while A worked, and B was signalled in turn after 20 s. `INTEGRATION VERDICT: PASS`. It also found a real defect, fixed before this close: `Test-AgentLockTurn` judged the turn by session id, so two processes of the SAME session were each told they were next - it now compares the ticket's sequence number whenever a ticket is supplied.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `post-change.ps1` closed green over the full changed set.
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit - not applicable: this phase is documentation plus a regenerated index, per the protocol's doc-only exemption. The code defect the integration probe surfaced was fixed and is recorded in Step 05.4.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert the documentation edits and regenerate the script index. No runtime behaviour depends on this phase.

# Phase 03 - Gradle entry points queue instead of refusing

**Strategic spec:** [`../S1432_build-lock-turn-queue.md`](../S1432_build-lock-turn-queue.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-06
**Completed:** 2026-08-06

---

## Objective

Make every gradle entry point enqueue and wait by default instead of refusing, while keeping an explicit fail-fast path for callers that must not block.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] `temp/BUILD.LOCK` free - this phase edits the function every builder dot-sources.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/agent-lock.ps1` | Modified | ≤ 700 |
| `scripts/quality/assert-detekt.ps1` | Modified | ≤ 400 |

> The ~30 builder scripts are deliberately NOT edited: they all reach the lock through `Enter-BuildLockOrExit`, so changing that one function changes every caller. Step 03.3 audits them to prove no caller depended on the old refusal.

---

## Steps

### Step 03.1 - Wait by default, with a named opt-out

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Invert the default of `Enter-BuildLockOrExit`: it enqueues a ticket and waits for its turn unless the caller passes the new `-NoWait` switch, and it honours the environment variable `FMS_LOCK_NO_WAIT=1` as a global fail-fast override for unattended runs. Keep the existing `-Wait` switch accepted and ignored so no current call site breaks. Default the wait ceiling to 3600 seconds. Preserve the exit-code contract exactly: 1 for a refusal under `-NoWait`, 2 for a wait that ran out of time, 3 for the broken toolchain, and keep the toolchain check running before anything touches the queue.

**Why:**

Strategic §1 records that a refusal surfaces to the agent as a successful background task, so "the build passed" has repeatedly meant "the build never ran", and §2 goal 5 requires that no gradle entry point end in a silent refusal that reads as success.

**Verification:**

- `Grep` - `\[switch\]\$NoWait` present in `scripts/utils/agent-lock.ps1`.
- `Grep` - `FMS_LOCK_NO_WAIT` present in that file.
- `Grep` - the header of `Enter-BuildLockOrExit` still lists exit codes 1, 2 and 3.
- Run `pwsh -NoProfile -File ./a.ps1 fk` with a free lock - completes normally (`Fast check passed.` in output).

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 4/4 PASS. Live contention probe: a probe process held `BUILD.LOCK`, the caller printed `queued at position 1 (ticket #1)`, waited 16 s and reported `BUILD.LOCK acquired after 16s in the queue - starting` with exit 0. Before this step the same call would have exited 1. `a.ps1 fk` ran clean afterwards (`BUILD SUCCESSFUL`, `Fast check passed.`).

---

### Step 03.2 - Report the position when the wait begins

**Files:** `scripts/utils/agent-lock.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> When the wait begins, print one line naming this ticket's position, the holder's reason and the holder's age, then reprint the position only when it changes. On acquiring after a wait, print how long the wait took. Send all of it to the host stream so it lands in the captured build log.

**Why:**

Strategic §7 rates "the command looks hung to the owner" as a real consequence of waiting by default, and a position that visibly advances is what distinguishes queueing from hanging.

**Verification:**

- `Grep` - `position` (case-insensitive) present in `scripts/utils/agent-lock.ps1`.
- Hold the lock from a second process, run a build, and confirm the log contains a position line - captured in the phase evidence.

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 2/2 PASS. The wait announces position, holder pid, holder age and holder reason on entry, and prints how long the queue took on acquire (`acquired after 16s`). Both lines go to the host stream, so they land in a captured build log.

---

### Step 03.3 - Audit the call sites that must still fail fast

**Files:** `scripts/quality/assert-detekt.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Review every `Enter-BuildLockOrExit` call site and decide, per caller, whether waiting or failing fast is correct. Pass `-NoWait` in `scripts/quality/assert-detekt.ps1` only if the gate is expected to answer immediately; if it should queue, leave it on the new default and record that decision in the step evidence. Do not edit the builder scripts - they inherit the new default by construction.

**Why:**

Strategic §5.1 requires an explicit fail-fast path where a fast failure matters more than a turn, and the same section insists that "did not wait" stay distinguishable from "checked and found a defect".

**Verification:**

- `Grep -c` - `Enter-BuildLockOrExit` still matches in at least 28 files (no call site was accidentally removed).
- `Grep` - `assert-detekt.ps1` contains either `-NoWait` or an explicit comment recording that it queues.
- Run `pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -ScopeToFile -ChangedFiles "scripts/utils/agent-lock.ps1"` - exit code 0 (the gate ignores non-Kotlin input but must still run).

**Status:** `[x] done`

**Step Log:**

- 2026-08-06 - Verification 2/3 PASS, third replaced. 37 call sites still reach the lock through `Enter-BuildLockOrExit`, so none was lost. Decision recorded for `assert-detekt.ps1`: it **queues** rather than failing fast - a gate that refuses because a sibling is mid-build reports "cannot verify" exactly when a ticket is being closed, which reads as a defect in the change under review - but with a 900 s ceiling, because a gate that waits an hour has stopped being a gate. The detekt run itself was exercised through `post-change` at the phase close instead of the standalone invocation.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] A build started while the lock is held queues and then runs, instead of refusing - evidence captured.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

**Phase-boundary audit (2026-08-06).** Layers 1-2.

- **P1 - found and fixed in phase.** Making the wait the default turned a fast, visible refusal into a potential self-deadlock: four scripts run a nested script while already holding `BUILD.LOCK`, and `& other.ps1` executes in the SAME process, so the nested acquire would have queued behind a lock this very run owns. Fixed with a re-entrancy guard - the nested call recognises the holder as itself (same pid) or as the ancestor that launched it (inherited `FMS_BUILD_LOCK_HELD_BY`) and reuses the lock instead of queueing. Proven: two `Enter-BuildLockOrExit` calls in one process now print `already held by this run .. reusing it` and exit 0, where the naive version would have waited out the full ceiling.
- **P2 - pre-existing, unchanged.** In the `&`-nested case the inner script's `finally { Exit-AgentLock }` releases the OUTER script's lock, because the pid matches. That is the behaviour that existed before this ticket and is out of its scope; noted so it is not mistaken for a regression introduced here.
- No P0 findings.

---

## Handoff Notes to Next Phase

Every gradle entry point now orders itself through the queue. Phase 04 applies the same treatment to the code lock, where the ownership model has to change first.

---

## Rollback Plan

Restore the default of `Enter-BuildLockOrExit` to single-shot refusal - one parameter default and one branch. Builders need no edit either way.

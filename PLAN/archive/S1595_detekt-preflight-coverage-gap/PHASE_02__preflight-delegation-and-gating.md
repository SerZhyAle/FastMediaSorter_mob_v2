# Phase 02 - Preflight delegation and gating

**Strategic spec:** [`../S1595_detekt-preflight-coverage-gap.md`](../S1595_detekt-preflight-coverage-gap.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-12
**Completed:** 2026-08-12

---

## Objective

Make the cheap step delegate to the real analyser, keep the existing lexical scan as the degraded
fallback, measure whether the scoped verdict matches the whole-module verdict, and only then let
the step abort a closure before the expensive gate starts.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6.5 is measured by Step 02.2 before Step 02.3 runs.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/detekt-preflight.ps1` | Modified (314 LOC - under the backup threshold) | ≤ 420 |
| `scripts/post-change.ps1` | Modified (890 LOC - **backup required**, see Step 02.3) | ≤ 40 changed |

---

## Steps

### Step 02.1 - Delegate to the runner, keep the lexical scan as degraded fallback

**Files:** `scripts/quality/detekt-preflight.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Call `detekt-scoped.ps1` first. On its exit 0 report PASS naming the whole configured rule set
> rather than the three rule names in the current message. On its exit 1 print the findings it
> produced and return 1 under `-Gate`. On its exit 2 fall back to the existing lexical scan, print
> a clearly-worded DEGRADED banner naming why the analyser could not run, and return 0 regardless
> of what the lexical scan found - print those findings but never block on them. Keep the current
> parameters working, including `-IgnoreBaseline` and `-Json`.

**Why:**

Strategic ADR-1 replaces emulation with the real verdict, but the lexical scan still has value as
a fallback when the analyser cannot start, and strategic ADR-3 forbids blocking on the degraded
path - a lexical finding is exactly the false positive class that `research/01` measured, so it
must never abort a closure.

**Verification:**

- `Grep` - `detekt-scoped.ps1` is invoked from `detekt-preflight.ps1`.
- `Grep` - the PASS message no longer enumerates `MaxLineLength / ImportOrdering / MagicNumber` as the checked set.
- Run on a file with a fresh `ReturnCount` violation - exit 1 under `-Gate`, and `ReturnCount` appears in the output.
- Simulate an unresolvable classpath - output carries the DEGRADED banner and the exit code is 0.

**Status:** `[x]` done

---

### Step 02.2 - Measure scoped-versus-whole-module divergence

**Files:** `temp/S1595/divergence.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Pick a set of changed `.kt` files that currently produces at least one new finding. Run
> `assert-detekt.ps1 -Gate -ChangedFiles <set>` and `detekt-scoped.ps1 -ChangedFiles <set>` over
> the same set, and record both finding lists. Report three counts: findings both produce,
> findings only the whole-module run produces, and findings only the scoped run produces. Record
> the result in `temp/S1595/divergence.md` and write the verdict into strategic §6 item 5.

**Why:**

Strategic §6.5 is Open and strategic §7 names scoped-run divergence as the risk that would make a
blocking step reject a legitimate closure - so the flip in Step 02.3 is conditional on this
measurement, and extra findings in the scoped run are the disqualifying direction.

**Verification:**

- `Glob` - `temp/S1595/divergence.md` exists and names both commands with their exit codes.
- Strategic §6 item 5 `Статус:` is no longer `Open`.

**Status:** `[x]` done

---

### Step 02.3 - Flip the step from advisory to blocking

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> `scripts/post-change.ps1` is 890 LOC, so first copy it to
> `temp/S1595/post-change.ps1.<yyyyMMdd-HHmmss>.bak`. Then change the `detekt-preflight` step from
> `Invoke-AdvisoryStep` to `Invoke-Step` so a non-zero exit aborts the closure. Leave it positioned
> before the detekt job starts. Do this only if Step 02.2 found no findings unique to the scoped
> run; if it found any, leave the step advisory, record why in the Blockers Log and mark this step
> `[DEFERRED]`.

**Why:**

Strategic ADR-2 is the step that actually recovers the round-trip: the cheap step already runs
before the expensive gate, but being advisory it does not stop it, so today a finding it catches
is still paid for in full. `Invoke-Step` exits immediately on a non-zero child, which is what
prevents the gradle job from ever starting.

**Verification:**

- `Glob` - a timestamped `post-change.ps1` backup exists under `temp/S1595/`.
- `Grep` - `Invoke-Step "detekt-preflight"` present; `Invoke-AdvisoryStep "detekt-preflight"` absent.
- Run `post-change.ps1 -ChangeType Kotlin` over a file with a fresh violation - the run ends before `[detekt-gate]` appears in the output.

**Status:** `[x]` done

---

### Step 02.4 - Keep the step off the build lock and inside the foreground budget

**Files:** `scripts/quality/detekt-preflight.ps1`
**Depends on:** Step 02.3

**Prompt for developer:**

> Confirm no `BUILD.LOCK` acquisition exists anywhere on the preflight path, and measure the
> step's wall clock on a representative changed set. Record the measured seconds in the script
> header next to the existing cost note.

**Why:**

Strategic §3.2 makes both properties hard constraints: a cheap step that queues on the build lock
inherits exactly the wait it exists to avoid, and one that leaves the foreground budget becomes a
second expensive gate.

**Verification:**

- `Grep` - neither `agent-lock.ps1` nor `Enter-BuildLock` appears on the preflight path.
- Measured wall clock recorded in the header and under the foreground threshold.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] No app build required - no Kotlin, resources or build files touched.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

**Measured outcome of this phase.** `post-change.ps1 -ChangeType Kotlin` over a file carrying live
findings ended at `[detekt-preflight] FAIL (3124 ms)`, naming all 13 findings with rule, line and
message; `[detekt-gate]` never appeared in the run. That is the round-trip this ticket exists to
remove, demonstrated end to end rather than argued.

**Divergence measurement (Step 02.2), for the record.** Whole-module gate 14 findings in 24 s,
scoped run 14 findings, same eight rules and same line numbers, zero unique to either side.

---

## Handoff Notes to Next Phase

The closure facade now fails fast on a real detekt finding in the changed set. Phase 04 documents
the new behaviour; the cheatsheet entry for `detekt-preflight.ps1` is stale from Step 02.1 onward
because its parameter block and header changed.

---

## Rollback Plan

Restore `Invoke-AdvisoryStep` in `post-change.ps1` and revert `detekt-preflight.ps1` to its
lexical body. No data migration and no user-facing surface is involved.

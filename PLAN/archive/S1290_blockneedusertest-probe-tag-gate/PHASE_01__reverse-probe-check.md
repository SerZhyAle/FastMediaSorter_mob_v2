# Phase 01 - Reverse probe check

**Strategic spec:** [`../S1290_blockneedusertest-probe-tag-gate.md`](../S1290_blockneedusertest-probe-tag-gate.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Teach the gate that already reads the catalogue and the sources to report the other direction: a ticket in `BlockNeedUserTest` with no active probe, with an allow-list of explained exceptions.

---

## Prerequisites

- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-no-ticket-logs.ps1` | Modified | ≤ 290 |
| `scripts/quality/blockneedusertest-probe-baseline.txt` | New | ≤ 30 |

---

## Steps

### Step 01.1 - Collect the ids that actually carry a probe

**Files:** `scripts/quality/assert-no-ticket-logs.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> While the existing scan walks each reconstructed Timber call, record the ticket id of every call recognized as a live probe into a new set. Take the id from the probe match, not from the generic id match, so only a real `Timber.d("Sxxxx:` opener counts. Record it whether or not the probe was judged allowed, because a stale probe still proves the tag exists in source and its own finding already reports the status mismatch.

**Why:**

Strategic §5.1 needs a set of ids that actually have a probe to difference against the catalogue set, and §6.2 requires it to come from the multi-line call reconstruction rather than a per-line grep, which a wrapped probe would defeat.

**Verification:**

- `Grep` - a probe-id set is populated inside the existing call loop, from the `$probeRx` match.
- `Grep` - no second traversal of the source tree was added.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Reverse check added to the existing gate: probe-id set from the multi-line call reconstruction, allow-list at scripts/quality/blockneedusertest-probe-baseline.txt. Measured 10 BlockNeedUserTest, 8 probes, 2 excused (S1606, S1620 - both say 'no .kt touched' in their own status notes). Proven: clean tree -Gate exit 0; S1606 unexcused -> named, exit 1; stale-probe fixture for a non-BlockNeedUserTest id -> named, exit 1; tree restored exit 0. a.ps1 fg PASS.

---

### Step 01.2 - Add the allow-list and the missing-probe report

**Files:** `scripts/quality/assert-no-ticket-logs.ps1`, `scripts/quality/blockneedusertest-probe-baseline.txt`
**Depends on:** Step 01.1

**Prompt for developer:**

> After the scan, difference the `BlockNeedUserTest` set against the probe-id set. Read `scripts/quality/blockneedusertest-probe-baseline.txt`, one `Sxxxx  <reason>` record per line, `#` for comments, and drop every listed id from the difference. Report what remains as its own finding class, one line per ticket, and add its count to the value the `-Gate` exit decision reads. Extend the summary line so both directions are visible in one read. Seed the baseline from the gate's own audit output, giving each entry the reason the strategic §6.4 measurement records: the ticket changes tooling or documentation rather than Kotlin, so a probe has nowhere to live.

**Why:**

Strategic ADR-1 chose an allow-list over a ratchet counter because both current gaps have a legitimate named reason, and a counter would record them as anonymous debt while the whole point is that the number moves only with an explanation.

**Verification:**

- `Glob` - `scripts/quality/blockneedusertest-probe-baseline.txt` exists and every entry carries a reason.
- Running the gate with `-Gate` on the current tree exits 0.
- Removing one id from the baseline makes the same run exit 1 and name that ticket.
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Gate` - exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Reverse check added to the existing gate: probe-id set from the multi-line call reconstruction, allow-list at scripts/quality/blockneedusertest-probe-baseline.txt. Measured 10 BlockNeedUserTest, 8 probes, 2 excused (S1606, S1620 - both say 'no .kt touched' in their own status notes). Proven: clean tree -Gate exit 0; S1606 unexcused -> named, exit 1; stale-probe fixture for a non-BlockNeedUserTest id -> named, exit 1; tree restored exit 0. a.ps1 fg PASS.

---

### Step 01.3 - Prove the three detection directions

**Files:** `scripts/quality/assert-no-ticket-logs.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Prove each strategic §11 criterion against the real gate rather than by reading the code. For criterion 1, temporarily drop a `BlockNeedUserTest` id from the baseline and confirm it is named. For criterion 2, confirm the same mechanism is what would have caught S1279. For criterion 3, confirm the pre-existing stale-probe finding still fires. Restore the tree afterwards and re-run to confirm exit 0. Record the observed output of each in the Step Log.

**Why:**

Strategic §11 states all four criteria as observable behaviour of a run, and CLAUDE.md section 12 forbids claiming a gate works without citing the run that proves it.

**Verification:**

- Step Log records the observed exit code and named ticket for each of the three directions.
- Final run on the restored tree exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Reverse check added to the existing gate: probe-id set from the multi-line call reconstruction, allow-list at scripts/quality/blockneedusertest-probe-baseline.txt. Measured 10 BlockNeedUserTest, 8 probes, 2 excused (S1606, S1620 - both say 'no .kt touched' in their own status notes). Proven: clean tree -Gate exit 0; S1606 unexcused -> named, exit 1; stale-probe fixture for a non-BlockNeedUserTest id -> named, exit 1; tree restored exit 0. a.ps1 fg PASS.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] No build required - this phase touches no compiled source.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] The gate still reports the original forbidden-log direction unchanged.
- [x] Dev log entry added for every file in "Files Touched".
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The invariant is enforced in both directions from one catalogue read and one source walk. Exceptions are named, not counted.

---

## Rollback Plan

Revert the gate change and delete the baseline file - no other script reads either.

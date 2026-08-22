# Phase 02 - Baseline and build

**Strategic spec:** [`../S1640_vr-unpaired-surface-and-player-registrations.md`](../S1640_vr-unpaired-surface-and-player-registrations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Turn the four removals into a measurable result: the gate's baseline drops by exactly four through the gate's own ratchet, and the `vr` source set still compiles.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/listener-symmetry-baseline.txt` | Modified | 1 |

> `scripts/quality/assert-listener-symmetry.ps1` is deliberately absent from this table: strategic §11 criterion 5 requires the gate's own file to stay untouched.

---

## Steps

### Step 02.1 - Lower the baseline with the gate's own ratchet

**Files:** `scripts/quality/listener-symmetry-baseline.txt`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the listener-symmetry check in its ratchet mode, without the gate switch, so it recomputes the current count and writes the lower number itself. Read the printed transition and confirm the drop is exactly four. Never edit the number by hand and never pass a changed-file scope on this run - the baseline is a project-wide figure.

**Why:**

Strategic §9 ADR-4 refuses a hand-written baseline because editing the number by hand is indistinguishable from fitting it to the desired result, while the ratchet lowers it by exactly what actually left the tree.

**Verification:**

- Run `pwsh -NoProfile -File scripts/quality/assert-listener-symmetry.ps1 -UpdateBaseline` - the baseline reaches `102`. Corrected 2026-08-14: the ratchet mode is the `-UpdateBaseline` switch, not the bare run, which only reports.
- `Read` - `scripts/quality/listener-symmetry-baseline.txt` contains `102`.
- Run `pwsh -NoProfile -File scripts/quality/assert-listener-symmetry.ps1 -Gate` - exit 0.
- Run `pwsh -NoProfile -File scripts/quality/assert-listener-symmetry.ps1 -List` - no file under `app_v2/src/vr` is listed.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Baseline lowered by the gate's own ratchet to 102, exactly the four removed registrations, and no file under app_v2/src/vr is listed any more. It took two runs and the reason is worth keeping: the first ratchet stopped at 103 because the counter reads comment text as well as code, and the comment introduced in step 01.4 contained the literal token removeListener - a phantom paired removal that left the file reporting an imbalance of one in the opposite direction (add 1 vs remove 2). Rewording the comment to name the paired removal without the API token brought the count to its true value. Predicates: baseline file reads 102, -Gate exit 0, -List shows no vr file.

---

### Step 02.2 - Compile the vr source set

**Files:** none - verification only
**Depends on:** Step 02.1

**Prompt for developer:**

> Compile the flavor that carries these four files, using the repository's flavor-scoped Kotlin compile target rather than a full assemble. Record the exit code. A failure here is a code defect in Phase 01, not a build-system problem, because no other flavor mounts this source set.

**Why:**

Strategic §3.2 restricts the change to the `vr` flavor and states that these files exist nowhere else, so a compile of any other flavor would prove nothing about them.

**Verification:**

- Run `pwsh -NoProfile -File ./a.ps1 fk -Flavor Vr` - exit 0.
- `Grep` - no `TODO` or commented-out registration left behind in the four files.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Compiled the flavor that carries these files: .\a.ps1 fk -Flavor Vr, BUILD SUCCESSFUL in 1m 45s, exit 0. Predicates: zero TODO hits and zero commented-out registrations across the four files. Phase-boundary audit: no P0/P1 - the two removals sit at terminal boundaries only, no teardown line moved, and the one behaviour question found while reading (an unqualified release() binding to the player rather than the controller) was parked as S1662 rather than changed here.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - the `vr` flavor compile in step 02.2 is that proof.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: no - two private fields only.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Restore the previous baseline number and revert Phase 01 - the gate refuses a raise, so the baseline must be restored in the same change as the code, never after it.

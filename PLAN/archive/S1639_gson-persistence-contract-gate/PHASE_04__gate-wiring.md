# Phase 04 - Gate wiring

**Strategic spec:** [`../S1639_gson-persistence-contract-gate.md`](../S1639_gson-persistence-contract-gate.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Make the gate run on its own, without anyone remembering to call it: registered in the fast-gate batch and in the closing facade, within the stated time budget.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-fast-gates.ps1` | Modified | ≤ 300 |
| `scripts/post-change.ps1` | Modified | ≤ 1000 |
| `scripts/quality/assert-gson-persistence-contract.ps1` | Modified | ≤ 660 |
| `scripts/quality/gate-recovery-hints.psd1` | Modified | ≤ 160 |

Table corrected 2026-08-14. The `post-change.ps1` budget of 600 was never reachable - the file already stood at 980 lines before this ticket touched it, and it ends the phase at 998, inside CLAUDE.md Rule 2. Two files were missing from the plan: the gate script itself, because the batch invokes every child with `-Gate` and a `CmdletBinding` script that does not declare the switch fails to bind, and the recovery-hint registry, because `assert-gate-hints-sync` refuses a facade gate label it has no hint for.

---

## Steps

### Step 04.1 - Register the gate in the fast-gate batch

**Files:** `scripts/quality/assert-fast-gates.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add the gate to the batch's gate table and to the header comment listing what the batch covers. Measure the run and record the measured wall time in the step's own dev-log entry. If it exceeds the strategic budget of a few seconds, do not register it here: move it to the pre-release set instead and note the measurement in the strategic spec's risk row.

**Why:**

Strategic §3.2 caps the gate at single-digit seconds because it joins a closing facade where static analysis already dominates the cost, and strategic §7 pre-agrees the fallback so a slow gate does not silently tax every close.

**Verification:**

- `Grep` - the gate script name appears in the batch's gate table.
- Run `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` - exit 0 and the new gate appears in its output.
- The measured wall time for the new gate is recorded in the dev-log entry for this step.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Registered in the fast-gate batch table with -Quiet and in its changed-files-aware list, and named in the batch header. Measured wall time 3053 ms inside the batch (batch total 60.7 s, exit 0, 27 gates green), against a strategic budget of a few seconds - it sits mid-pack, well under assert-source-gates at 15.8 s. Getting there took two changes to the gate itself: a literal keyword prefilter before the declaration regex and ReadAllLines in place of Get-Content, which took a standalone run from 7.29 s to 3.04 s. The gate also gained -Gate and -Quiet switches, because the batch invokes every child with -Gate and a CmdletBinding script without it would fail to bind.

---

### Step 04.2 - Register the gate in the closing facade

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add the gate to the facade's gate list with an applicability rule that skips it when no changed file is a Kotlin source or an obfuscation rules file, matching how the other source-specific gates declare themselves. Wire it as a failing gate, not an advisory one, and forward the changed-file set when the caller scoped the run.

**Why:**

Strategic §2 goal 1 requires refusal before the change is closed rather than at release time, and the facade is the only place every change passes through, so a gate registered anywhere else would be one nobody runs.

**Verification:**

- `Grep` - the gate name appears in the facade's gate list with a skip reason string.
- Run the facade against a documentation-only file set - the new gate reports SKIP with its reason.
- Run the facade against a Kotlin file set - the new gate reports PASS or FAIL, not SKIP.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Registered in the closing facade as a FATAL gate with a path-based applicability rule (a changed .kt or a proguard-*.pro) and a named skip reason, plus a recovery hint in gate-recovery-hints.psd1 - assert-gate-hints-sync now reads 32 labels against 32 hints. Scoping is deliberately asymmetric: a Kotlin-only change forwards its .kt files, while any obfuscation-rules edit is judged project-wide, because a changed keep rule can unpin any model in its module and a scoped set holding no source would collect no serialization point and report green for nothing. Predicates: doc-only set reports SKIP with the reason; Kotlin set reports PASS in 14226 ms, not SKIP.

---

### Step 04.3 - Prove the gate refuses a real regression

**Files:** `scripts/quality/assert-gson-persistence-contract.ps1`
**Depends on:** Step 04.2

**Prompt for developer:**

> Prove the gate on the tree rather than by reading it. Temporarily remove one wire-name annotation from a durable model, run the gate, confirm it reports the partial-pinning violation naming that property, then restore the annotation and confirm the gate returns to green. Record both exit codes. Leave no modification behind.

**Why:**

Strategic §11 criterion 2 requires that a model losing its pinning causes a refusal without editing the gate, and per CLAUDE.md section 12 a gate that has never refused anything is an untested claim rather than a proven guard.

**Verification:**

- The removal run exits 1 and names the removed property.
- The restore run exits 0.
- `Grep` - the model file is byte-identical to its state before the step, with the annotation present.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Proved on the tree under CODE.LOCK. Removed the @SerializedName from BrowseFileTransferSource.displayName: the gate exited 1 and reported 'annotated-partial com.sza.fastmediasorter.ui.browse.transfer.BrowseFileTransferSource - unannotated: displayName', which is the partial kind rather than the missing-annotation one. Restored from the backup: exit 0, SHA256 identical to the pre-probe hash, annotation present. Phase-boundary audit: no P0/P1 - the facade forwards only .kt paths and the gate normalizes separators itself, so a backslash path cannot slip past the scoping.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - not applicable, this phase adds no compiled source.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable, no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The gate now runs unattended in both runners and has demonstrably refused a real regression. Phase 05 only records it in the documents that must not drift from it.

---

## Rollback Plan

Revert the phase commit - the gate stays on disk and callable by hand, but stops running automatically.

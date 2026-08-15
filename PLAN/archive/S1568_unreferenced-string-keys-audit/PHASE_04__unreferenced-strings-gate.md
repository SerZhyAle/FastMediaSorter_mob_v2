# Phase 04 - Ratchet gate against new unreferenced keys

**Strategic spec:** [`../S1568_unreferenced-string-keys-audit.md`](../S1568_unreferenced-string-keys-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-12
**Completed:** 2026-08-12

---

## Objective

Turn the cleanup into a standing rule: a gate that fails when a name appears in `values/strings.xml` that nothing under `app_v2/src` references, with the held-back names recorded as the only allowed exceptions.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/S1568/hold-back.txt` still present from Phase 03.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-unreferenced-strings.ps1` | New | ≤ 170 |
| `scripts/quality/assert-unreferenced-strings-baseline.txt` | New | one line per held-back name |
| `scripts/quality/assert-fast-gates.ps1` | Modified | ≤ +6 |

---

## Steps

### Step 04.1 - Add the gate

**Files:** `scripts/quality/assert-unreferenced-strings.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the gate on the shape of `scripts/quality/assert-string-format.ps1`: dot-source the Phase 01 liveness library, take `-Gate`, `-UpdateBaseline`, `-List`, `-Quiet`, `-Module` defaulting to `app_v2` and `-File` defaulting to `strings.xml`, and read `scripts/quality/assert-unreferenced-strings-baseline.txt` as the set of names allowed to be unreferenced.
> Baseline parsing ignores blank lines and treats everything from `#` onward as the reason comment, so the Phase 03 hold-back file is accepted verbatim.
> Fail under `-Gate` only on a name that is unreferenced and absent from the baseline; a baseline name that has since become referenced or been deleted is reported as ratchet slack, never a failure.
> Exit 0 when no new name was found, 1 under `-Gate` when at least one was, 2 when the module, resource directory or strings file cannot be read - a gate that could not look must not report a pass.
> State the exit codes in the header, and use `Write-Error <msg> -ErrorAction Continue` before any `exit N` where N is not 1, per CLAUDE.md section 7.

**Why:**

Strategic goal 4 asks the ticket to leave behind a mechanical check rather than a one-off cleanup, because the 397 names accumulated precisely while nothing was measuring, and strategic §3.1 asks that each kept name show the basis on which it was kept.

**Verification:**

- `Glob` - `scripts/quality/assert-unreferenced-strings.ps1` exists.
- `Grep` - `android-string-liveness.ps1` matches, proving the gate shares Phase 01's definition of a reference rather than recounting.
- `Grep` - the `.NOTES` block names exit codes 0, 1 and 2.
- Run `pwsh -NoProfile -File scripts/quality/assert-unreferenced-strings.ps1 -List` - expected exit code 0, output naming exactly the held-back names.
- Run `pwsh -NoProfile -File scripts/quality/assert-unreferenced-strings.ps1 -Gate -Module app_v2 -File no-such-file.xml` - expected exit code 2, never 0.
- Run `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - expected exit code 0.

**Status:** `[x]` done

---

### Step 04.2 - Seed the baseline from the hold-back list

**Files:** `scripts/quality/assert-unreferenced-strings-baseline.txt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Copy `temp/S1568/hold-back.txt` to `scripts/quality/assert-unreferenced-strings-baseline.txt`, keeping every `# <reason>` comment intact, and add a header comment naming S1568 and the command that regenerates the finding set.
> If Phase 03 held nothing back, commit the file with only the header, so the gate has a baseline to read and a future addition has an obvious home.
> Verify the seeded baseline is exact rather than generous: run the gate with `-Gate` and confirm it passes, then delete one line from the baseline temporarily and confirm it fails, restoring the line afterwards.

**Why:**

Strategic §3.1 requires each kept name to carry a visible basis, and `temp/` is gitignored, so the hold-back reasons written in Phase 03 exist nowhere durable until this file lands in the repository.

**Verification:**

- `Glob` - `scripts/quality/assert-unreferenced-strings-baseline.txt` exists.
- `Grep` - `S1568` matches in the header comment.
- Its non-comment line count equals the line count of `temp/S1568/hold-back.txt`.
- Run `pwsh -NoProfile -File scripts/quality/assert-unreferenced-strings.ps1 -Gate` - expected exit code 0.
- With one baseline line removed, the same command exits 1 and names the removed key; after restoring the line it exits 0 again.

**Status:** `[x]` done

---

### Step 04.3 - Register the gate in the fast-gates batch

**Files:** `scripts/quality/assert-fast-gates.ps1`
**Depends on:** Step 04.2

**Prompt for developer:**

> Add `assert-unreferenced-strings.ps1` to the gate table in `scripts/quality/assert-fast-gates.ps1` with `-Quiet`, following the existing entries, and name it in the `.DESCRIPTION` gate list with a one-line purpose and the ticket id.
> Measure the added wall-clock cost by running `.\a.ps1 fg` before and after; the gate walks the same source tree the other lexical gates walk, so if it costs more than a couple of seconds, register it through the single-walk umbrella `scripts/quality/lib/source-scan.ps1` instead of as a standalone entry.
> Do not add it to `post-change.ps1` separately - `assert-fast-gates.ps1` is the batch that closure already runs.

**Why:**

Strategic goal 4 requires the check to be mechanical, and CLAUDE.md section 3 records that an ungated rule in this repository is followed between 1 and 8 percent of the time while a gated one is followed nearly always - a gate nobody runs is prose.

**Verification:**

- `Grep` - `assert-unreferenced-strings.ps1` matches in both the gate table and the `.DESCRIPTION` block of `scripts/quality/assert-fast-gates.ps1`.
- Run `pwsh -NoProfile -File ./a.ps1 fg` - expected exit code 0, and its output names the new gate.
- The measured `a.ps1 fg` wall clock after the change is recorded in the step notes together with the before figure.
- `Grep` - `assert-unreferenced-strings` returns zero hits in `scripts/post-change.ps1`, proving it was not double-registered.

**Status:** `[x]` done

---

## Step Log

- 2026-08-12 - Step 04.1 DONE. Created `scripts/quality/assert-unreferenced-strings.ps1` on the shape of `assert-string-format.ps1`, dot-sourcing the Phase 01 library rather than recounting. Verified all three exit paths: `-List` prints the 7 baselined names with their reasons, `-Gate` exits 0, and `-File no-such-file.xml` exits **2** rather than 0 - a gate that could not look must not report a pass.
- 2026-08-12 - Design note worth keeping: the baseline is an allowlist of NAMES, not a count. A count-ratchet would admit one new dead key each time another was deleted, which is exactly how a ratchet quietly stops ratcheting. Naming them also makes the file the durable answer to strategic §3.1 - the basis on which each surviving unreferenced name was kept.
- 2026-08-12 - Step 04.2 DONE. Baseline seeded verbatim from `temp/S1568/hold-back.txt`, so the seven reasons written during the cleanup are now committed rather than living in gitignored `temp/`. Proved the baseline is exact and not generous: with `passthrough_capture_timeout` deleted from it the gate exits **1** and names that key; restored, it exits 0 again.
- 2026-08-12 - Step 04.3 DONE. Registered in `assert-fast-gates.ps1` with `-Quiet` and named in its `.DESCRIPTION`. Measured cost 1.6-1.9 s inside a ~61 s batch, so no move to the single-walk umbrella was warranted. Not added to `post-change.ps1` separately - `assert-fast-gates` is what closure already runs, and double-registering would pay for it twice.
- 2026-08-12 - `a.ps1 fg` initially exited 1, on `assert-memory-budget`, not on this gate. Unrelated pre-existing failure, fixed inline per CLAUDE.md §3.1; see the Phase 03 audit note. Final state: `assert-fast-gates: PASS (all fast gates green)`, exit 0, with `assert-unreferenced-strings.ps1 PASS (1946 ms)` among them.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - not applicable, this phase touches no module source. `/build` skipped.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for the phase via `scripts/post-change.ps1`.
- [x] `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` run so the gate reaches `docs/SCRIPT_CHEATSHEET.md`.
- [x] Phase-boundary audit run - Layer 1 only, scripts and one baseline file. The gate shares the Phase 01 library rather than recounting, fails closed on cannot-verify, and was proved to fail on a real unbaselined name. No P0/P1.

---

## Handoff Notes to Next Phase

The gate is the ticket's durable product. Phase 05 documents it and nothing more - if the gate does not run inside `a.ps1 fg`, the cleanup of Phase 03 will silently refill over the next months, which is exactly how the 397 names accumulated.

---

## Rollback Plan

Revert the `assert-fast-gates.ps1` entry and delete the gate and its baseline. The Phase 03 deletions are unaffected; only the standing check goes away.

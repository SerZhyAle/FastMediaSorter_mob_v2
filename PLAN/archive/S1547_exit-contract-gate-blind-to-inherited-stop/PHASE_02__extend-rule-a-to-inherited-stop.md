# Phase 02 - Extend rule A to inherited Stop

**Strategic spec:** [`../S1547_exit-contract-gate-blind-to-inherited-stop.md`](../S1547_exit-contract-gate-blind-to-inherited-stop.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Make the gate treat a file that receives `$ErrorActionPreference = 'Stop'` through a one-level literal dot-source exactly as it treats a file that sets the mode itself.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `temp/CODE.LOCK` acquired before the edit and released right after.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-exit-contract.ps1` | Modified | ≤ 360 |

---

## Steps

### Step 02.1 - Add the dot-source resolver and the mode probe

**Files:** `scripts/quality/assert-exit-contract.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two helpers above the scan loop. The first takes a file's lines and returns whether any non-comment line assigns `'Stop'` to `$ErrorActionPreference` - the existing condition-1 test, lifted so both the scanned file and a sourced library go through the same predicate. The second takes a file's lines plus its directory and returns the resolved paths of its one-level dot-sources, recognising only two literal forms: `. (Join-Path $PSScriptRoot '<relative>')` and `. "$PSScriptRoot<separator><relative>"`. A dot-source whose path comes from a variable returns nothing. Memoise the per-library verdict in a hashtable keyed by resolved full path, so a library shared by many callers is read once per run.

**Why:**

Strategic §5.1 makes the mode a property of the file together with what it dot-sources, and requires the path to be literal - a computed path has no statically known value, and guessing there costs more than the miss; §3.2 caps the price at one read per library per run, because the gate sits in the fast-checks battery.

**Verification:**

- `Grep` - the file declares both helper functions exactly once each. PASS - `Test-SetsStopMode` and `Get-DotSourcedPaths`, plus `Test-LibrarySetsStop` carrying the memo.
- `Grep` - the memo hashtable is declared once, outside the `foreach ($f in $files)` loop. PASS - `$script:stopModeMemo` sits above `$files`.
- The gate parses and runs: `assert-exit-contract.ps1 -Gate` over the repository - `expected: 0 | actual: 0`.

**Status:** `[x]` done

---

### Step 02.2 - Wire the resolver into condition 1 and name the source in the report

**Files:** `scripts/quality/assert-exit-contract.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Replace the inline condition-1 loop with a call to the mode predicate; when it returns false, resolve the file's one-level dot-sources and apply the predicate to each, stopping at the first library that runs under `Stop` and remembering its repository-relative path. Skip the file only when neither the file nor any resolved library sets the mode. Carry that library path on each finding this file produces, and when a finding was reached through inheritance, print the source after the existing message so a reader can see why the rule applied to a file with no assignment of its own.

**Why:**

Strategic §1 names the reported `PASS` as the actual damage - the gate did look at the catalog tooling and reported it clean - and §5.1 requires a finding reached by inheritance to say where the mode came from, since otherwise the reader cannot tell why a file with no assignment was judged at all.

**Verification:**

- `Grep` - the old inline `foreach ($l in $lines)` condition-1 loop is gone from the file. PASS - replaced by the predicate call plus the resolver loop.
- `Grep` - the findings object carries a field for the inheriting library. PASS - `Inherited`, printed as `runs under Stop inherited from <path>`.
- `assert-exit-contract.ps1 -Gate` over the repository - `expected: 0 | actual: 0`.
- Positive control on a sandbox fixture whose only `Stop` comes from a sourced library: `expected: 1 | actual: 1`, and the report named the library.
- Negative control, same fixture with the dot-source path assembled into a variable first: `expected: 0 | actual: 0`.

**Status:** `[x]` done

---

### Step 02.3 - Correct the header's condition-1 wording

**Files:** `scripts/quality/assert-exit-contract.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Rewrite the `.DESCRIPTION` bullet that reads "the file sets $ErrorActionPreference = 'Stop'" so it states the condition the gate now applies: the file sets the mode itself, or receives it from a library it dot-sources at one level with a literal path. Add one sentence recording that the wording was corrected because it was true only for a file with no dot-sources, and name S1547.

**Why:**

Strategic §2 goal 4 requires the header to stop asserting the narrower condition; the old sentence read as a deliberate design choice rather than a blind spot, which is why the defect survived review for as long as it did.

**Verification:**

- `Grep` - the header no longer contains the bare phrase `the file sets $ErrorActionPreference`. PASS - condition 1 now reads "assigns the mode itself, or dot-sources a library that does".
- `Grep` - the header mentions `dot-source` and `S1547`. PASS - both, in the paragraph recording why the wording was corrected.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - not applicable, no Kotlin touched.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry deferred to Phase 03 - one entry per logical change, not per file.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Two deltas worth naming: the shared predicate now skips `#` comment lines, which the old inline loop did not, so a commented-out assignment no longer counts as setting the mode - it can only shrink the flagged set, and the set was already empty. Block comments are still not parsed, which over-blocks rather than under-blocks and matches the gate's existing stance.

---

## Handoff Notes to Next Phase

Condition 1 is now a shared predicate applied to the file and to its one-level libraries, and every finding records the library it inherited the mode from. Phase 03 asserts both halves - the defect flagged, the legitimate shapes spared - in the regression suite.

---

## Rollback Plan

Revert the phase's hunks in `assert-exit-contract.ps1` - the gate returns to its previous condition-1 test and no other script changes.

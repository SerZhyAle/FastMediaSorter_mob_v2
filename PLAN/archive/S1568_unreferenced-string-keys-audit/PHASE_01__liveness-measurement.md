# Phase 01 - Reproducible liveness measurement

**Strategic spec:** [`../S1568_unreferenced-string-keys-audit.md`](../S1568_unreferenced-string-keys-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-11
**Completed:** 2026-08-11

---

## Objective

Add one shared library that decides whether a string resource name is referenced inside a single module, plus a report CLI over it, so the dead-key list stops depending on who counted and how.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none, this is the foundation phase.
- [ ] Strategic §6 research items blocking this phase are Resolved - all three Resolved 2026-08-11.
- [ ] Working tree is clean or on a feature branch.
- [ ] `PLAN/S1568_unreferenced-string-keys-audit/research/01__deadness-method-and-risk-subsets.md` read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/lib/android-string-liveness.ps1` | New | ≤ 180 |
| `scripts/utils/audit-unreferenced-strings.ps1` | New | ≤ 160 |
| `scripts/quality.tests/android-string-liveness.Tests.ps1` | New | ≤ 200 |

> No Kotlin, no resources and no layout files in this phase, so the landscape-parity and flavor-placement rules do not apply to it.

---

## Steps

### Step 01.1 - Add the shared liveness library

**Files:** `scripts/quality/lib/android-string-liveness.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a dot-sourceable library exposing three functions over one module.
> `Get-DeclaredResourceNames -ResDir <path> -File <basename>` returns an ordered map of name to kind for every `<string>`, `<plurals>` and `<string-array>` declared in that one file, so a caller can tell the three kinds apart afterwards.
> `Get-ReferencedResourceNames -SrcRoot <path>` walks `<module>/src` once, reads each `.kt`, `.java` and `.xml` file once, and returns a `HashSet[string]` of every name matched by `R.(string|plurals|array).<name>` or `@(string|plurals|array)/<name>`, with a trailing `(?![A-Za-z0-9_])` guard so `foo` does not match `foo_bar`.
> `Get-UnreferencedResourceNames -Module <name> -File <basename>` composes the two and returns the declared entries whose name is absent from the referenced set.
> The walk root is `<module>/src`, never `<module>/src/main` and never the repository root.
> Follow the header and dot-source conventions of the sibling `scripts/quality/lib/android-string-format.ps1`, and state the exit-code contract of any script that consumes it rather than exiting from the library itself.

**Why:**

Strategic ADR-1 fixes liveness at module scope because the app and watch modules are separate resource namespaces with no dependency between them, and a scan spanning both reports a name as alive when it is only alive in the other module - 15 names are exposed to that error and `test_connection` already demonstrated it.

**Verification:**

- `Glob` - `scripts/quality/lib/android-string-liveness.ps1` exists.
- `Grep` - `function Get-DeclaredResourceNames`, `function Get-ReferencedResourceNames` and `function Get-UnreferencedResourceNames` each match exactly once.
- `Grep` - `string-array` matches in the declaration regex, proving all three resource kinds are parsed.
- `Grep` - `R\.\(\?:string\|plurals\|array\)` or the equivalent alternation matches, proving all three reference kinds are matched.
- `Grep` - the reference walk root is assigned `Join-Path $Module 'src'`, not `src/main`, proving the scan covers every source set. The declared-names path is `src/main/res/values` and must stay so: `strings.xml` exists only in the main source set, so the two paths differ by design.

**Status:** `[x]` done

---

### Step 01.2 - Add the report CLI

**Files:** `scripts/utils/audit-unreferenced-strings.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create a CLI that dot-sources the Phase 01.1 library and prints the unreferenced names of one strings file.
> Parameters: `-Module` defaulting to `app_v2`, `-File` defaulting to `strings.xml`, `-Format` accepting `text` or `json` and defaulting to `text`, and `-OutFile` writing the report instead of printing it.
> Text output prints one `<kind>\t<name>` line per unreferenced name, sorted by name, then a summary line carrying the declared count, the referenced count, the scanned file count and the unreferenced count.
> Exit 0 when the scan completed regardless of how many names it found, exit 2 when the module, the resource directory or the strings file does not exist - the count is the report, not a verdict.
> Pass `-NoProfile` in every usage example in the header, and list the exit codes the script actually returns, per CLAUDE.md section 7.

**Why:**

Strategic goal 1 requires a way to obtain the list that does not depend on who ran it and how, and strategic §5.3 requires the same measurement to serve any strings file and any module so the next audit of input keys, settings keys or the watch module needs no second script.

**Verification:**

- `Glob` - `scripts/utils/audit-unreferenced-strings.ps1` exists.
- `Grep` - `android-string-liveness.ps1` matches, proving the CLI consumes the library rather than reimplementing the scan.
- Run `pwsh -NoProfile -File scripts/utils/audit-unreferenced-strings.ps1 -Module app_v2 -File strings.xml` - expected exit code 0.
- The summary line of that run reports `declared=3234` and `unreferenced=397`, matching the planning re-measurement in INDEX.md. A different declared count is acceptable only if `values/strings.xml` changed meanwhile; a different unreferenced count with an unchanged declared count is a defect in the library.
- The report contains the line for kind `plurals` and name `sync_interval_hours`, proving non-`<string>` kinds are measured. Match the literal tab with `grep "sync_interval_hours" <report> | cat -A` and read `plurals^Isync_interval_hours$`; the `Grep` tool's `\t` does not match a literal tab in this file.
- Re-run the difference with the walk root forced to `app_v2/src/main`: the dead count must rise sharply, and the gap is the population of names alive only through a non-main source set. This is the direct proof that the scan is not flavor-blind, and it replaces the weaker prefix check that was written here at planning time. That check asserted no `launcher_` / `cast_` / `screen_capture_` / `screen_recording_` / `screenshot_` name may appear in the report, which is wrong: a family can be mostly alive and still hold individually dead members, and five `launcher_*` names are exactly that.

**Measured 2026-08-11:** 397 dead scanning every source set, 619 scanning `src/main` alone, so 222 names are alive only through a flavor, feature or test source set. A main-only scan would delete all 222.
- Run the same command with `-Module wear` - expected exit code 0 or 2, never an unhandled exception.

**Status:** `[x]` done

---

### Step 01.3 - Add fixture-based regression tests for the library

**Files:** `scripts/quality.tests/android-string-liveness.Tests.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add tests over a temporary fixture tree built inside the test, mirroring the harness shape of `scripts/quality.tests/check-device-profile-presets.Tests.ps1`: a hand-rolled `Assert-Equal` counter, a sandbox under `temp/S1568/`, removed at the end, and `exit 0` on all-pass / `exit 1` on any failure.
> This is not Pester and there is no discovery runner - `scripts/quality.tests/Run-Tests.ps1` is itself a test file for a different subject, and nothing aggregates `*.Tests.ps1`. The new file is invoked directly, so do not edit `Run-Tests.ps1`.
> Cover six cases: a `<string>` referenced only from a flavor source set is alive; a `<string>` referenced only from `src/main` is alive; a `<string>` referenced from another module is dead; a `<plurals>` referenced through `R.plurals.` is alive; a `<string-array>` referenced through `@array/` is alive; and a name that is a strict prefix of a referenced name is dead.
> Add a seventh case for the alternation-ordering trap recorded in the Step Log: a `<string-array>` declaration must report kind `string-array`, never `string`.

**Why:**

Strategic §11 criterion 2 requires the measurement to agree with a hand re-check including keys alive only through a flavor, and strategic goal 4 asks for a mechanical check rather than a one-off cleanup - a test over the library is what keeps the Phase 04 gate honest after the cleanup has erased the evidence it was built from.

**Verification:**

- `Glob` - `scripts/quality.tests/android-string-liveness.Tests.ps1` exists.
- `Grep` - `flavor`, `plurals` and `string-array` each match at least once in test names.
- Run `pwsh -NoProfile -File scripts/quality.tests/android-string-liveness.Tests.ps1` - expected exit code 0, final line reporting `fail: 0` over at least 7 cases.
- Run `pwsh -NoProfile -File scripts/quality.tests/Run-Tests.ps1` - expected exit code 0, proving the unrelated sibling suite still passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - not applicable, this phase touches no module source. `/build` skipped.
- [x] `Grep` for `TODO(phase-01)` returns zero hits in `scripts/` - the single hit is this criterion's own text.
- [x] Dev log entry added for the phase via `scripts/post-change.ps1` (three closures, final one `post-change: PASS`).
- [x] `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` run - 308 scripts; `assert-script-cheatsheet-sync.ps1` exits 0, clearing the advisory the two earlier closures carried.
- [x] `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` exits 0 - 0 unreachable exit sites, 0 silent scripts, 0 reasonless exits.
- [x] Phase-boundary audit run - no P0/P1 findings. See Audit note below.

## Phase-boundary audit (2026-08-11)

Layer 1 only: `Files Touched` is three PowerShell files, so the lifecycle, memory-ownership and Room layers have no surface here.

- Responsibility split holds: the library measures, the CLI reports, the gate in Phase 04 will judge. No third copy of the reference regex exists.
- Every exported function is consumed - `Get-DeclaredResourceNames` and `Get-ReferencedResourceNames` by both the composed function and the tests - so nothing landed as dead weight (CLAUDE.md Rule 20).
- Both `$RepoRoot` derivations were checked by hand against their own nesting depth: `scripts/quality/lib` needs three `Split-Path` hops, `scripts/utils` needs two. Both correct, and both are the kind of silent-wrong-path defect that would have made every count meaningless.
- `Get-ReferencedResourceNames` reads one file at a time rather than slurping the tree, so the 3892-file walk stays flat in memory.
- No P0/P1. One P3 noted and deliberately not acted on: the walk is re-run per process, so a caller needing several measurements in one session should hold the returned `HashSet` rather than call again. Phase 02 depends on exactly this and states it in Step 02.3.

---

## Step Log

- 2026-08-11 - Step 01.1 DONE. Created `scripts/quality/lib/android-string-liveness.ps1` (172 LOC) with the three functions. Predicate 5 was mis-specified at planning time and was corrected in place before the run: it demanded zero `src/main` hits, but the declared-names path legitimately reads `src/main/res/values` because `strings.xml` exists only in the main source set. The predicate now pins the reference **walk root** to `Join-Path $Module 'src'` (line 151), which is the constraint that actually matters. Smoke run over the real tree: `declared=3234 referenced=4245 scanned=3892 unreferenced=397` (396 `string` + 1 `plurals`), reproducing the INDEX planning baseline exactly.
- 2026-08-11 - Step 01.2 DONE. Created `scripts/utils/audit-unreferenced-strings.ps1` (108 LOC). Run over `app_v2`/`strings.xml`: exit 0, `declared=3234 referenced=4245 scanned=3892 unreferenced=397`, matching the INDEX baseline. `-Module wear` exits 0 and reports `declared=96 referenced=81 scanned=82 unreferenced=15`, satisfying strategic §5.3 - the same script serves another module with no edit.
- 2026-08-11 - Step 01.2 predicate correction, and the most load-bearing finding of the phase. The planned check "no `launcher_` / `cast_` / `screen_capture_` / `screen_recording_` / `screenshot_` name appears in the report" FAILED with 5 hits, all `launcher_*`. Investigated rather than waived: an independent grep over `app_v2/src` + `wear/src` confirms all 5 have zero references, and other `launcher_*` names ARE referenced from `app_v2/src/launcherEnabled` and are correctly absent from the report, so the scan is not blind to that source set. The families are mostly alive and hold individually dead members; the predicate was wrong, not the scan. Replaced with the check that actually tests the property: forcing the walk root to `app_v2/src/main` yields 619 dead against 397, so **222 names are alive only through a non-main source set** and a main-only scan would delete every one of them. Research artifact 01 estimated 216 for this figure and flagged it as not hand-verified; 222 is the re-measured value.
- 2026-08-11 - Step 01.3 DONE. Created `scripts/quality.tests/android-string-liveness.Tests.ps1`: 17 cases, `fail: 0`, exit 0; `Run-Tests.ps1` still exits 0. Two planning assumptions were wrong and were corrected in the step before writing it: the harness is not Pester but a hand-rolled `Assert-Equal` counter, and `Run-Tests.ps1` is itself a test file for the changed-files normalizer rather than a discovery runner - nothing in the repository aggregates `*.Tests.ps1`, so the new file is run directly and `Run-Tests.ps1` was not edited.
- 2026-08-11 - Two first-run failures, both in the test's own arithmetic rather than the library. Declared count is 9, not the 10 I wrote. Scanned file count is 6, not 5, because `values/strings.xml` itself lives under `src` and is walked like any other XML - which is correct and now commented in the test: a declaration is not a reference, but a `<string-array>` whose `<item>` is `@string/foo` is one, and only walking the values files finds it.
- 2026-08-11 - Note for Step 02.2: the declaration alternation orders `string-array` before `string` deliberately. `<string\b` matches `<string-array` because the `g`/`-` junction is a word boundary, so the naive ordering mislabels every array as a string. The planning-time hand measurement had this bug; it did not change the count because the file's only `<string-array>` is alive.

---

## Handoff Notes to Next Phase

`Get-ReferencedResourceNames` is the single source of truth about liveness for the rest of this ticket. Phase 02 must call it rather than keep the private per-key scan inside `set-android-string.ps1`, and Phase 04 must call it rather than recount. The one-walk shape matters: the private scan re-enumerates 3892 files per key, which is why Phase 02 also introduces a batch path.

---

## Rollback Plan

Delete the three new files. Nothing else references them until Phase 02, and no shipped resource or source file changed.

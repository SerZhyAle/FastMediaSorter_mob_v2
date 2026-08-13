# Phase 02 - Removal tool reaches every kind and every key in one pass

**Strategic spec:** [`../S1568_unreferenced-string-keys-audit.md`](../S1568_unreferenced-string-keys-audit.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-12
**Completed:** 2026-08-12

---

## Objective

Make `-Action remove` share Phase 01's liveness source, delete `<plurals>` and `<string-array>` blocks as well as `<string>`, and accept a key list so 397 removals cost one source-tree walk instead of 397.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `scripts/quality.tests/Run-Tests.ps1` passes before any edit, so a later failure is attributable to this phase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/set-android-string.ps1` | Modified | ≤ 820 |
| `scripts/quality.tests/set-android-string-remove.Tests.ps1` | New | ≤ 220 |

> `set-android-string.ps1` is 713 LOC, above the 500-LOC threshold of CLAUDE.md Rule 5. Step 02.1 carries the backup sub-step. The 820-LOC budget stays well inside the 1500-LOC limit of Rule 2.

---

## Steps

### Step 02.1 - Delegate the reference scan to the shared library

**Files:** `scripts/utils/set-android-string.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Back up the file first: copy `scripts/utils/set-android-string.ps1` to `temp/S1568/set-android-string.ps1.<yyyyMMdd-HHmmss>.bak` per CLAUDE.md Rule 5.
> Dot-source `scripts/quality/lib/android-string-liveness.ps1` next to the existing `android-string-format.ps1` dot-source, and rewrite `Get-KeyReferences` so it answers from that library instead of its own `Get-ChildItem` walk and its own regex.
> Keep the return shape exactly as callers expect it today: an array of `path:line` strings, because `Show-KeyReferences` prints them and the `remove` and `rename` branches count them.
> If the library returns names rather than locations, extend the library with a location-returning overload rather than keeping a second regex here - one definition of a reference, per strategic ADR-2.
> Do not weaken the current behaviour: the scan root stays `<module>/src`, all three resource kinds stay matched, and `remove` still exits 3 on a referenced key without `-Force`.

**Why:**

Strategic ADR-2 requires the removal path and the audit to answer from one source of truth about liveness, because today they are different functions and a divergence puts the shorter of the two in charge of an irreversible deletion.

**Verification:**

- `Glob` - the timestamped backup exists under `temp/S1568/`.
- `Grep` - `android-string-liveness.ps1` matches exactly once in `scripts/utils/set-android-string.ps1`.
- `Grep` - `Get-ChildItem -Path $srcRoot -Recurse` returns zero hits, proving the private walk is gone.
- Run `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action remove -Key app_name -DryRun` - expected exit code 3, and the printed reference list is non-empty.
- Run the same command for a name from the Phase 01 report, with `-DryRun` - expected exit code 0 and a "would remove" line per locale that holds it.

**Status:** `[x]` done

---

### Step 02.2 - Remove `<plurals>` and `<string-array>` blocks, not only `<string>`

**Files:** `scripts/utils/set-android-string.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> The `remove` branch builds one regex matching `<string name="...">..</string>` only, so a `<plurals>` or `<string-array>` name is reported as "not found in any locale" and silently survives.
> Resolve the kind first by locating the declaring block, then delete the whole element of that kind including its `<item>` children, still line-anchored and still byte-preserving - no reserialization through `[xml]`.
> Apply the same fix to `rename` so the two branches cannot drift, and to `Invoke-Audit`, whose key union counts `<string name=` only and would therefore report no change after a `<plurals>` deletion.
> Update the header `.DESCRIPTION` text for `remove`, `rename` and `audit` to say all three kinds are covered.

**Why:**

Strategic §3.2 records that plural forms and arrays have no check in the removal tool today and need separate attention, and the measurement in INDEX.md found one genuinely dead `<plurals>` name, `sync_interval_hours`, which the current regex cannot delete at all.

**Verification:**

- `Grep` - `plurals` and `string-array` each match inside the `remove` branch of `scripts/utils/set-android-string.ps1`.
- `Grep` - `<string name=` inside `Invoke-Audit` is replaced by an alternation covering all three kinds.
- Run `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action remove -Key sync_interval_hours -DryRun` - expected exit code 0, and the output names a locale file rather than printing "not found in any locale".
- Run `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action remove -Key selected_n_files -DryRun` - expected exit code 3, proving a referenced `<plurals>` is now protected rather than invisible.

**Status:** `[x]` done

---

### Step 02.3 - Accept a key list and add regression tests for the branch

**Files:** `scripts/utils/set-android-string.ps1`, `scripts/quality.tests/set-android-string-remove.Tests.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Add `-KeyList <path>` to `remove`: read one key per line, ignore blank lines and lines starting with `#`, build the reference index once through the Phase 01 library, then process every key against that one index.
> Per key the existing contract is unchanged - a referenced key is refused and skipped, an absent key is reported, a dead key is deleted from every locale on disk.
> Print a closing summary of removed, refused and absent counts, and exit 3 if any key was refused, 0 otherwise, so a caller cannot mistake a partially refused batch for a clean run.
> Reject `-Key` and `-KeyList` supplied together rather than silently preferring one.
> Add Pester tests over a temporary fixture tree covering: a referenced key refused inside a batch while its neighbours are still removed, a `<plurals>` removed across locales, a key present in a translated locale but absent from `values/` swept without error, and the exit code being 3 when at least one key was refused.

**Why:**

Strategic §6.2 requires the cleanup to run now and to hold `CODE.LOCK` for a short step rather than a whole ticket, which a per-key invocation cannot satisfy: the current scan re-enumerates 3892 source files for every key, so 397 keys mean 397 full walks of the tree while the lock is held and S1420 waits behind it.

**Verification:**

- `Grep` - `KeyList` matches in the `param(` block and in the `remove` branch of `scripts/utils/set-android-string.ps1`.
- `Grep` - the `.NOTES` exit-code block names 3 for a refused batch, per CLAUDE.md section 7 exit-contract rule.
- `Glob` - `scripts/quality.tests/set-android-string-remove.Tests.ps1` exists.
- Run `pwsh -NoProfile -File scripts/quality.tests/Run-Tests.ps1` - expected exit code 0 with zero failures.
- Run `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - expected exit code 0.
- Write a two-line list holding `app_name` and one name from the Phase 01 report to `temp/S1568/probe-keys.txt`, run `remove -KeyList temp/S1568/probe-keys.txt -DryRun` - expected exit code 3, summary reporting 1 refused and 1 would-remove.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - not applicable, this phase touches no module source. `/build` skipped.
- [x] `Grep` for `TODO(phase-02)` returns zero hits in `scripts/`.
- [x] Dev log entry added for the phase via `scripts/post-change.ps1`.
- [x] `pwsh -NoProfile -File scripts/utils/help.ps1 -Generate` run - `-KeyList` now appears in `docs/SCRIPT_CHEATSHEET.md`.
- [x] No strings file was modified in this phase. The originally planned predicate - an empty `git diff --stat` over `app_v2/src/main/res` - is not usable here and was corrected: the tree is permanently dirty because S1420 is translating the corpus in parallel, so that diff shows 53 files and proves nothing about this phase. The evidence used instead is positive and specific: `access_denied` and `sync_interval_hours`, the two keys driven through `-DryRun` removals, are both still declared in `values/strings.xml`, and the audit still reports `declared=3234 unreferenced=397`, unchanged from before the phase.
- [x] File size 799 LOC, inside the 820 budget and far inside the 1500 limit of CLAUDE.md Rule 2.
- [x] `assert-exit-contract.ps1` exits 0 after adding the batch exit path.
- [x] Phase-boundary audit run - one P1 found and fixed inside the phase. See Audit note below.

## Phase-boundary audit (2026-08-12)

Layer 1 plus a targeted correctness pass, since this phase arms an irreversible deletion. No Android surface, so the lifecycle, memory and Room layers do not apply.

- **P1, found and fixed in-phase:** the unrolled `HashSet` return described in the Step Log. Silent on the real repository, fatal on an empty result, and it quietly turned the batch's membership test into a linear scan. Fixed at the source with the comma operator and pinned by three assertions. Recorded here rather than deferred, per the protocol's rule that P0/P1 are never carried into the next phase.
- Duplication check: `Remove-KeyFromLocales` is now the single definition of removing a key, and the kind list has exactly one home. Before this phase there were three separate spellings of "a reference" across the audit, the removal gate and the rename branch.
- Blast-radius check on the widened `rename` and `audit` branches: both were previously `<string>`-only, so widening them cannot break a caller that worked before - it can only stop under-reporting. `audit`'s EN count moves 4868 because it now sees plurals and arrays, which is the corrected number, not a regression.
- The refusal path still fails closed: `remove` exits 3 on any reference, `-Force` waives only the refusal and never the scan, and the batch exits 3 if even one key was refused.
- No P0. No P2/P3 left open.

---

## Step Log

- 2026-08-12 - Step 02.1 DONE. Backed up `set-android-string.ps1` to `temp/S1568/set-android-string.ps1.20260812-000104.bak`. `Get-KeyReferences` now delegates to the library; its private `Get-ChildItem` walk and its private regex are gone (0 hits). The library gained `Get-ResourceReferenceLocations` plus `New-ResourceReferencePattern`, and the kind list is now written once as `$script:AndroidResourceReferenceKinds` and consumed by both the bulk and the per-name pattern - the divergence strategic ADR-2 forbids is now structurally impossible rather than merely discouraged. Verified: `app_name` exits 3 with 8 located hits, `access_denied` exits 0 with 13 locale removals.
- 2026-08-12 - Note on the reference-vs-declaration spelling, which is a live trap: references read `R.array.` / `@array/` while the declaration reads `<string-array>`. Conflating the two makes arrays unreachable from one side or the other, so the library keeps two patterns over one kind list.
- 2026-08-12 - Step 02.2 DONE. Added `Get-KeyRemovalRegex`, which resolves the declaring element's kind through the library and builds the deletion regex for that kind. `rename` and `Invoke-Audit` widened to the same three-kind alternation so the branches cannot drift; `audit` now reports EN `count=4868`. Verified on the real file before trusting any of it: the dead `<plurals> sync_interval_hours` matches a 147-char block, its two `<item>` children come out with it, declarations drop 3234 -> 3233, zero residue, and the result still parses as XML. `selected_n_files` (a referenced `<plurals>`) now exits 3 - previously it was not merely unprotected but invisible.
- 2026-08-12 - Step 02.3 DONE. Added `-KeyList` with one index build per batch, per-key refusal, a removed/refused/absent summary, and exit 3 on any refusal. `-Key` plus `-KeyList` is refused rather than silently resolved. Extracted `Remove-KeyFromLocales` so the single and batch paths share one definition of removing a key. Real-repo batch probe: `would remove=2 refused=1 absent=1 of 4`, exit 3, index built exactly once.
- 2026-08-12 - **Bug found by the new tests, fixed at the source.** The first sandbox run exited 1 with "The property 'Count' cannot be found on this object". Cause: `Get-ReferencedResourceNames` returned a `HashSet`, and PowerShell unrolls a returned collection into the pipeline, so every caller received an `Object[]`. Two consequences, both silent on the real repository and therefore invisible without a test: `.Contains` degraded from O(1) to a linear scan over 4245 names, and an EMPTY result unrolled to `$null` so `.Count` threw under StrictMode. Fixed with the comma operator on both return paths in the library, and pinned by three new assertions that the returned object is still a `HashSet` - including for an empty result, which is the case that crashed. This is exactly the class of defect a dry run cannot surface, because the real tree never produces an empty set.
- 2026-08-12 - Two planning assumptions in Step 02.3 were wrong and were corrected in place: the tests are not Pester (same harness note as Phase 01), and the sibling suite `Run-Tests.ps1` covers an unrelated subject, so it is run as a non-regression check rather than as the home of these cases.

---

## Handoff Notes to Next Phase

`remove -KeyList` is the only sanctioned way to execute the cleanup. A hand edit of `values/strings.xml` would leave the same name behind in up to twelve locale files, and strategic §7 records that no existing gate notices that orphan - `check_strings_localized.ps1` builds its key universe from the strict locales and has no surplus check.

---

## Rollback Plan

Restore `scripts/utils/set-android-string.ps1` from the Step 02.1 backup under `temp/S1568/` and delete the new test file. No resource file changed in this phase, so nothing user-facing needs reverting.

# Phase 02 - Honest verdicts from the closure facade

**Strategic spec:** [`../S1338_agent-process-overhaul.md`](../S1338_agent-process-overhaul.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 05, Phase 06
**Steps done:** 8 / 8
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Make `scripts/post-change.ps1` and `scripts/quality/assert-fast-gates.ps1` judge the whole change and report the result truthfully: scope every gate to the full changed-file set, refuse invalid arguments, print PASS only when nothing failed, publish an exit-code contract, and run the gates before the mutating steps.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done - the baseline report exists, so this phase's effect is measurable.
- [ ] `temp/BUILD.LOCK` is free (`pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build`).
- [ ] `pwsh -NoProfile -File scripts/utils/enter-code-lock.ps1 -Reason "S1338 phase 02"` acquired.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/post-change.ps1` | Modified | ≤ 700 |
| `scripts/quality/assert-fast-gates.ps1` | Modified | ≤ 180 |
| `scripts/quality/assert-detekt.ps1` | Modified | ≤ 230 |
| `CLAUDE.md` | Modified | n/a |
| `docs/SCRIPT_CHEATSHEET.md` | Modified (generated) | n/a |

> `scripts/post-change.ps1` is 581+ lines - back it up to `temp/S1338/` before the first edit (Rule 5).

---

## Steps

### Step 02.1 - Back up the facade before editing

**Files:** `scripts/post-change.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `scripts/post-change.ps1` to `temp/S1338/post-change.ps1.<yyyyMMdd-HHmmss>.bak` before any edit. CLAUDE.md Rule 5 requires a timestamped backup for any file over 500 lines.

**Verification:**

- `Glob` - `temp/S1338/post-change.ps1.*.bak` matches at least one file.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 1/1 PASS. `temp/S1338/post-change.ps1.20260731-201822.bak` written before the first edit; source was 581 lines, over the Rule 5 threshold. `CODE.LOCK` acquired for the phase.

---

### Step 02.2 - Accept the whole changed set

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a `[string[]]$Files` parameter to the `param()` block alongside the existing mandatory `[string]$File`. When `-Files` is supplied, use the full set everywhere the script currently passes the single `$File`: the `catalog-sync` call and every gate invocation that accepts `-ChangedFiles`. When `-Files` is absent, fall back to `@($File)` so every existing caller keeps working unchanged. Make `-File` non-mandatory only if `-Files` is supplied, using two parameter sets, so neither can be omitted together.

**Verification:**

- `Grep` - `\[string\[\]\]\s*\$Files` matches in `scripts/post-change.ps1`.
- `Grep` - `ParameterSetName` matches in `scripts/post-change.ps1`.
- Run `pwsh -NoProfile -File scripts/post-change.ps1 -File "docs/DEV_OPS.md" -Target "S1338" -Description "arg smoke" -ChangeType Doc` - exit code 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. Two parameter sets: `Single` (mandatory `File`) and `Multi` (mandatory `Files`), confirmed by `Get-Command -ParameterSets`. A resolved `$changedFiles` now drives every consumer - `catalog_sync.ps1` and all six gate pass-through sites - so no gate can judge a narrower slice than the verdict claims. `$File` survives as the primary file for the dev-log line and the single-file gates that take no changed-set parameter. Backward-compatibility smoke with `-File` alone: `post-change: PASS (Doc, 4309 ms)`, exit 0.
- 2026-07-31 - Note on the omitted-argument case. There is deliberately no `DefaultParameterSetName`, so invoking with neither `-File` nor `-Files` fails to resolve a parameter set and the script refuses at bind time. That is the wanted behaviour - the step required that neither can be omitted together - and a bind refusal can never be mistaken for a green verdict.

---

### Step 02.3 - Reject file arguments that do not exist

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 02.2

**Prompt for developer:**

> Before running any step, validate every entry of the resolved file set: each must be a non-empty string, must not contain an unexpanded shell variable marker (`$` or `%`), and must exist on disk relative to the repo root. On any violation print the offending argument and exit 2 - "could not verify", never PASS. An unexpanded shell variable currently produces a full green verdict certifying nothing.

**Verification:**

- `Grep` - `Test-Path` matches inside the new validation block in `scripts/post-change.ps1`.
- Run `pwsh -NoProfile -File scripts/post-change.ps1 -File '$UNSET/x.kt' -Target "S1338" -Description "bad arg" -ChangeType Kotlin` - exit code is 2 and stdout contains neither `PASS` nor `post-change: PASS`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. Validation runs before any step: rejects an empty entry, a path containing `$` or `%` (unexpanded shell variable), and a path that does not resolve against the repo root. Both probes exit **2** with `post-change: CANNOT VERIFY` and no `PASS` anywhere in the output - `$UNSET/x.kt` reported as an unexpanded shell variable, `scripts/does-not-exist.ps1` as not found. Exit 2 rather than 1 is the load-bearing detail: nothing was inspected, so the caller must be able to tell this from "found a defect".

---

### Step 02.4 - Print PASS only when every gate passed

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 02.3

**Prompt for developer:**

> Track a run-level verdict across all steps. `Invoke-AdvisoryStep` currently prints an advisory line and never influences the outcome, so under `-ScopeToFile` a downgraded ratchet gate can find a real defect while the script still prints `post-change: PASS`. Record advisory findings in a counter and change the final line to one of three verdicts: `post-change: PASS` (nothing failed, nothing advisory-flagged), `post-change: PASS WITH ADVISORIES (<n>)` listing each advisory gate that found something, or `post-change: FAIL`. Only the first prints the bare word PASS on its own.

**Verification:**

- `Grep` - `PASS WITH ADVISORIES` matches in `scripts/post-change.ps1`.
- `Grep` - the literal `post-change: PASS` is emitted from exactly one code path guarded by the zero-advisory condition.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. `Invoke-AdvisoryStep` now appends to `$script:AdvisoryFindings` on both the non-zero-exit path and the gate-error path, which previously vanished entirely. The verdict has two emission sites: `PASS WITH ADVISORIES (n)` in yellow listing each gate, and the bare `post-change: PASS` inside the `else` branch guarded on a zero advisory count. Smoke on a clean Doc change: `post-change: PASS (Doc, 2359 ms)`, exit 0 - the bare word still appears when it is actually earned.

---

### Step 02.5 - Publish the exit-code contract

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 02.4

**Prompt for developer:**

> Add a `.NOTES` block to the script header enumerating the exit codes it actually returns, matching the shape already used by `scripts/quality/assert-detekt.ps1`: 0 every gate passed, 1 a gate failed, 2 could not verify (invalid arguments, missing tooling, gate timeout). Make the code paths match the contract - the argument-validation failure from step 02.3 and any "gate could not run" condition must return 2, not 1, so callers can distinguish "found a defect" from "did not look". Follow CLAUDE.md Rule 7: use `Write-Error $msg -ErrorAction Continue` before `exit 2` so the exit is reachable.

**Verification:**

- `Grep` - `Exit codes:` matches in the header comment of `scripts/post-change.ps1`.
- Run `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1 -Path scripts/post-change.ps1 -Gate` - exit code 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. `.NOTES` block added to the header enumerating 0 / 1 / 2 and stating why a caller must distinguish 1 from 2. The step-02.3 validation path already returns 2 through `Write-Error -ErrorAction Continue` followed by `exit 2`, so the code is reachable. Gate: 0 unreachable exit sites, 0 silent scripts.

---

### Step 02.6 - Run the gates before the mutating steps

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 02.5

**Prompt for developer:**

> Move the two mutating steps - `dev-log` (currently the first step) and `catalog-sync` - to run after the whole gate block instead of before it. Keep the detekt thread job kicked off early so its wall clock still overlaps the lexical gates; only the mutations move. A failed closure must leave no changelog row and must not have run catalog-sync. This also makes the facade idempotent: re-running after fixing a gate failure produces exactly one changelog row, not two.

**Verification:**

- `Grep -n` - the line invoking `add_to_dev_log.ps1` has a greater line number than the line invoking `assert-neuroslop.ps1`.
- `Grep -n` - the line invoking `catalog_sync.ps1` has a greater line number than the line invoking `assert-listener-symmetry.ps1`.
- Run the facade against a file with a deliberate neuroslop violation; confirm exit code 1 and that `dev/CHANGELOG.md` gained no row.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. `dev-log` moved from first step to line 629 and `catalog-sync` to line 639, both after the whole gate block; `assert-neuroslop` is invoked at 439 and `assert-listener-symmetry` at 517, so both ordering predicates hold by line number. Negative proof: a probe layout carrying `android:background="#FF0000"` closed as `neuroslop-gate FAIL - child exit code 1`, exit **1**, and `dev/CHANGELOG.md` stayed at 23,864 lines across the run - expected: no new row | actual: no new row. The `finally` block still releases `CODE.LOCK` on both paths, so a failed closure leaves nothing held.
- 2026-07-31 - The mutating steps now sit after the `finally` that releases `CODE.LOCK`, so the lock is released a few hundred ms before the changelog row is written. Deliberate: the two remaining steps write a journal line and a gitignored index, never source, so nothing a concurrent editor could collide with.

---

### Step 02.7 - Forward `-ChangedFiles` to every gate that accepts it

**Files:** `scripts/quality/assert-fast-gates.ps1`
**Depends on:** Step 02.6

**Prompt for developer:**

> In the `$gates` table, five entries accept `-ChangedFiles` and are today invoked with no arguments, so they scan project-wide and go red on any other ticket's in-flight drift: `assert-flavor-flags-not-growing`, `assert-neuroslop`, `assert-public-mutable-flow`, `assert-deprecated-pm-flags`, `assert-listener-symmetry`. Forward the script's own `-ChangedFiles` parameter to those five, conditionally on it actually being supplied - copy the pattern the detekt branch already uses. A release or CI run invoked without `-ChangedFiles` must keep the strict project-wide judgement.

**Verification:**

- `Grep -c` - `'-ChangedFiles'` appears at least 6 times in `scripts/quality/assert-fast-gates.ps1` (five gates plus detekt).
- Run `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1 -ChangedFiles scripts/post-change.ps1` - exit code 0 on a tree whose Kotlin drift belongs to other tickets.
- Run `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` with no arguments - project-wide judgement still applied (unchanged behaviour).

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. A `$changedFilesAware` list names the five gates and the loop appends `-ChangedFiles` only when the caller supplied it, so the no-argument call path is byte-identical to before. Scoped run over `scripts/post-change.ps1`: **exit 0**, all thirteen gates green. Project-wide run with no arguments: **exit 0**, same thirteen gates, strict judgement retained.
- 2026-07-31 - The scoped run first failed on `assert-no-ticket-logs` - `AnimatedImageDecoder.kt:110` carried `Timber.d("S1317: ..")` while S1317 sits at `Tactical`, not `BlockNeedUserTest`. A stale probe is removed on sight (CLAUDE.md section 2), so it was deleted inline rather than parked; that gate takes no `-ChangedFiles` and would have gone on failing every closure in the repo. `Timber` stays imported - `Timber.w` on the adjacent catch still uses it.
- 2026-07-31 - **Defect found by this step, not by the plan.** Forwarding the set as separate array elements (`@('-ChangedFiles') + $changedFiles`) is unbindable: `pwsh -File` binds only the first element to a `[string[]]` parameter and rejects the rest, so the first real multi-file closure died with "A positional parameter cannot be found that accepts argument 'scripts/quality/assert-fast-gates.ps1'". Every forwarding site in both callers now passes one comma-joined argument, which every consumer already comma-splits (`Expand-ChangedFiles`, `Measure-ChangedFileGrowth`). It had never fired because `-File` alone always produced a one-element set. Proof that the join is not merely bindable but actually judged per file: a hardcoded-colour probe placed as the **third** CSV element was reported as `layout-hardcoded-colors: new occurrences 1`, exit 1.

---

### Step 02.8 - Make a detekt failure self-diagnosing

**Files:** `scripts/quality/assert-detekt.ps1`, `CLAUDE.md`
**Depends on:** Step 02.7

**Prompt for developer:**

> Two changes. First, in the diff-scoped branch of `assert-detekt.ps1` the Checkstyle XML has already been parsed into findings but only the file path is printed - emit `file:line:column - <ruleId> - <message>` for each matched finding instead, so a failure does not cost a second gradle round-trip to diagnose. Second, in `scripts/post-change.ps1` drop the `-Quiet` argument from the `ticket-log-audit` invocation, which suppresses exactly the File:Line list needed to fix it. Then correct the `-ScopeToFile` bullet in CLAUDE.md section 12 so it describes what the script does after this phase - the gates are scoped to the whole `-Files` set, not to exactly one file.

**Verification:**

- `Grep` - `ruleId` or an equivalent rule-name variable matches in the diff-scoped print block of `scripts/quality/assert-detekt.ps1`.
- `Grep` - `ticket-log-audit` invocation in `scripts/post-change.ps1` no longer carries `-Quiet`.
- `Grep` - CLAUDE.md section 12 no longer contains the phrase `diff-scope the detekt gate to `-File``.

**Status:** `[x] done`

**Step Log:**

- 2026-07-31 - Verification 3/3 PASS. A new `Get-DetektFindings` in `lib/detekt-report.ps1` returns File / Line / Column / RuleId / Message per finding; `Get-DetektFindingFiles` now delegates to it and keeps its exact Ok/Files/Reason contract - the S1077 regression suite passes **14/14** unchanged. The scoped branch prints `file:line:column - RuleId - message`, sorted by file then line, so a failure no longer costs a second gradle round-trip to read. `ticket-log-audit` lost `-Quiet`, which was suppressing precisely the File:Line list a fix needs.
- 2026-07-31 - CLAUDE.md section 12 rewritten as two bullets: the scoping bullet now says name the whole set (`-Files "a,b"`) and describes what each gate class actually does since S0848/S0850 - the count-ratchet gates judge a FATAL per-file delta, they are not "downgraded to advisory"; only the three repo-wide re-render gates are. The second bullet publishes the verdict vocabulary and the 0/1/2 exit codes in the always-loaded preamble, which is where the trust in the verdict comes from.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `help.ps1 -Generate` re-run - 248 scripts written to `docs/SCRIPT_CHEATSHEET.md`; `assert-script-cheatsheet-sync.ps1 -Gate` reports "in sync", exit 0.
- [x] `assert-fast-gates.ps1` - exit **0** both scoped (`-ChangedFiles`) and project-wide (no arguments); all thirteen gates green in each mode.
- [x] `assert-exit-contract.ps1 -Gate` - exit **0**, 0 unreachable exit sites, 0 silent scripts.
- [x] Dev log entries added, one per logical change, through the facade rather than by hand.
- [x] Phase-boundary audit run - see below.
- [x] `CODE.LOCK` released - `post-change.ps1` releases it from its own `finally`, which is why the closure run is also the release.

### Phase-boundary audit

- **P1 - fixed.** `-ChangedFiles` was forwarded as separate array elements to child scripts invoked through `pwsh -File`, which binds only the first and rejects the rest. Every multi-file closure would have died at the first scoped gate. Found by running the facade for real rather than by re-reading the diff; fixed at all eight forwarding sites plus `catalog_sync.ps1`.
- **P1 - fixed.** The changelog row was written before `catalog-sync`, so a run that failed in that step still left a row claiming the change had closed, and a re-run added a second. The row is now the last step of the whole facade, which makes "there is a row" equivalent to "the closure passed". The step only asked for the mutations to move after the gates; ordering them against each other was the part that actually delivered the idempotence the step claimed.
- **P2 - recorded for phase 04.** detekt's diff-scope is file-granular; see the handoff note.
- **P3 - fixed inline.** A stale `Timber.d("S1317: ..")` probe against a `Tactical` ticket, removed on sight per CLAUDE.md section 2.
- No P0. Nothing in this phase touches product code, a user-facing surface or the build graph.

---

## Handoff Notes to Next Phase

**For phase 04: detekt's diff-scope is file-granular, and that is now visible.** Closing this phase touched one line in `AnimatedImageDecoder.kt` - deleting a stale S1317 probe - and the scoped detekt gate then attributed **ten** findings to the closure. All ten belong to S1317's in-flight rewrite of that file: `git show HEAD:` returns a 199-line file whose lines 104-108 are a KDoc block, against a working copy where the same region is decode code. A one-line deletion cannot introduce `ArgumentListWrapping` at line 106. The gate is not wrong - it judged the file it was given - but "changed file" is a coarser unit than "changed lines", so any edit inside another ticket's WIP file inherits that ticket's findings. Phase 04's single-pass runner should carry line-range attribution, or the scoped detekt verdict will keep charging one ticket for another's debt. Recorded here rather than parked: it is phase 04's own subject matter, not an out-of-scope finding.

The facade now judges the whole change and its verdict can be trusted. Phases 04, 05 and 06 add and remove gates behind that facade; each must keep the three-verdict contract and the 0/1/2 exit codes intact. Any new gate added later is wired into `assert-fast-gates.ps1` with conditional `-ChangedFiles` pass-through if it supports the parameter.

---

## Rollback Plan

Restore `scripts/post-change.ps1` from the `temp/S1338/` backup taken in step 02.1 and revert the two gate scripts. No data migration, no user-facing surface, no build configuration changed.

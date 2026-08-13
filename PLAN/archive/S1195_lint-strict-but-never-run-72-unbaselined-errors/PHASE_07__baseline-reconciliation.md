# Phase 07 - Baseline reconciliation

**Strategic spec:** [`../S1195_lint-strict-but-never-run-72-unbaselined-errors.md`](../S1195_lint-strict-but-never-run-72-unbaselined-errors.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Completed:** 2026-07-31
**Depends on:** Phase 02, 03, 04, 05, 06
**Blocks:** Phase 08
**Steps done:** 4 / 4

---

## Objective

Spec §5 step 4 and §6.2. This is the phase §7 warns about, so its ordering constraint is absolute: **the baseline is touched only after every detector has been refined and every live finding triaged.** A blanket `updateLintBaseline` at any earlier point would bury the real `MainThreadIo` and context-leak findings, which is the risk the strategic spec rates "high".

Three distinct pieces of work that must not be conflated:

1. **Stale entries.** Lint's `LintBaselineFixed` hint reports 76 entries listed but no longer found, and the detector refinements in Phases 02-04 will add many more - most of the 205 `UiContextLeak` and 148 `PlayerNotReleased` entries were written against the `Manager` heuristic and the `Player` substring, and stop matching once those are gone. Removing them is cheap and mechanical.
2. **New entries** for the findings Phases 05 and 06 deliberately kept, each with a recorded reason.
3. **Selective re-triage** of the surviving filtered findings, per §6.2: project-detector entries and P0/P1 categories only. Style entries are explicitly not reviewed.

---

## Prerequisites

- [ ] Phases 02-06 all `✅ Done`, with their re-measurement numbers recorded.
- [ ] `temp/CODE.LOCK` acquired.
- [ ] Timestamped backup of `app_v2/lint-baseline.xml` (2808 entries) under `temp/S1195/`.
- [ ] Handoff notes from Phases 02-06 collected - they carry the justification text for every entry this phase writes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/lint-baseline.xml` | Modified | shrinks |
| `temp/S1195/baseline-triage.md` | New | working record, not a deliverable |

---

## Steps

### Step 07.1 - Establish the post-refinement truth

**Files:** none - measurement only
**Depends on:** - start of phase

**Prompt for developer:**

> Full `:app_v2:lintStandardDebug` under `temp/BUILD.LOCK`, output to `temp/S1195/phase07-pre.log`. This is the first run where every detector is refined and every live finding triaged, so it is the only trustworthy input to a baseline decision.
>
> From `app_v2/build/reports/lint-results.xml` record three numbers: live errors by rule; filtered-by-baseline count; and the full `LintBaselineFixed` unmatched-type list. Compare against the 2026-07-29 starting point in `INDEX.md` and state the delta per rule. Any live error that is not in a phase handoff note is unexplained - stop and triage it rather than baselining it.

**Verification:**

- All three counts recorded here with the log path.
- Every live error traced to a phase handoff note or freshly triaged.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Two runs were needed, because the first one surfaced findings no handoff note covered
  and the step's own rule is to triage those rather than baseline them.
- Run A, `temp/S1195/phase06-lint.log` - 19 live errors, 90 filtered by baseline, 434 baseline entries
  unmatched. Only 2 of the 19 (`RepeatOnLifecycleWrongUsage`) were covered by a handoff note, so the
  remaining 17 were triaged here. Full working record: `temp/S1195/baseline-triage.md`.
- Triage outcome - **13 of the 17 were not baseline material at all**:
  - `UiContextLeak` 4 - false positives. INDEX called them "new true positives"; they are four
    `@Singleton` classes holding an unqualified `Context`, which `core/di/AppModule.kt:70` binds to the
    application context. `SingletonComponent` has no other `Context` binding, so no Activity can reach
    them. Fixed by annotating the four injection sites `@param:ApplicationContext` - the project's own
    convention, no behaviour change, and the invariant becomes visible where it is read.
  - `PlayerNotReleased` 6 - false positives from two detector defects Phase 02 did not model: a Kotlin
    file facade (`<File>Kt`) being credited with a player its top-level extension function stores on the
    receiver, and a `MediaController` freed through `releaseFuture(ListenableFuture<MediaController>)`,
    where the owned type sits in the type argument. Detector fixed per ADR-1 rather than baselined;
    three tests added, `:lint-rules:test` 30/30, `temp/S1195/phase07-flr.log`.
  - `MainThreadIo` 3 of 7 - one real (`IntegrationTestViewModel`: `viewModelScope.launch` with no
    dispatcher is `Main.immediate`) and two confined two call levels out, where Phase 04's escape hatch
    deliberately resolves only one. Fixed by confining the write and by declaring
    `ReceiveShareActivity.extractAndCacheFiles` `suspend`, which is simply true of it - not by widening
    the rule into call-graph territory.
- Remaining 4 (`ReceiveShareActivity` teardown deletes) are real, need a scope that outlives the
  Activity, and belong to a file this ticket does not otherwise touch - parked as **S1324** per
  CLAUDE.md §3.1 and baselined with that id as the reason.
- Run B, `temp/S1195/phase07-pre2.log`, report mtime 01:27:56 - the trustworthy input to the baseline
  decision, taken with every detector refined and every live finding dispositioned. Errors **19 -> 7**:
  `UiContextLeak` 4 -> 0, `PlayerNotReleased` 6 -> 1, `MainThreadIo` 7 -> 4 (exactly the S1324 four),
  `RepeatOnLifecycleWrongUsage` 2 -> 2.
- The one `PlayerNotReleased` survivor corrected the fix, not the diagnosis. `StreamPlaybackHelper.kt`
  is the same facade shape as its four siblings but also declares six `private const val` constants,
  which compile to static fields - and the first cut of `isFileFacade()` demanded *zero* fields. A
  `const val` is inlined at every use site and cannot hold a player, so the test became "every field is
  a compile-time constant" (`computeConstantValue() != null`). The guard survives: a top-level `val`
  holding a real object is not constant, which is what
  `testPlayerReleaseDetectorFlagsTopLevelPropertyOwner` asserts. `:lint-rules:test` 30/30 after the
  refinement, `temp/S1195/phase07-flr2.log`.

**Detekt state of this phase's edits.** `assert-detekt -Gate -ChangedFiles` over all 24 touched files
reports findings in 8 of them. Attribution, by reading each one rather than by counting:

- Genuinely introduced here, both fixed: a blank line before the closing brace left by removing
  `CompanionConfigImportActivity`'s companion object, and `StreamsActivity`'s import block, where a
  pre-existing `domain.streams` / `domain.model` inversion was reordered while the file was open.
- Everything else is pre-existing debt whose line numbers moved: the `CameraCaptureActivity:391`
  wrapping cluster, `DeliveredNativeLibraryLoader:117/139`, `ReceiveShareActivity:305/309`,
  `StreamsActivity:913-916`, `MainViewModel:115/117`. None of those lines was edited by this ticket;
  `-ChangedFiles` scopes to the whole file rather than to the changed hunks, so a shifted finding reads
  as new. `PlayerViewModel`'s `LongParameterList` is the S0826 signature resurface: the constructor
  gained one parameter, so its baseline signature no longer matches.

---

### Step 07.2 - Remove stale entries

**Files:** `app_v2/lint-baseline.xml`
**Depends on:** Step 07.1

**Prompt for developer:**

> Delete the baseline entries lint reports as no longer found. Work from the `LintBaselineFixed` unmatched-type list plus a diff of the current report against the baseline; do not regenerate the file wholesale, because a regeneration would also swallow anything Step 07.1 flagged as unexplained.
>
> Expect the project-detector entries to dominate the removals - the `Manager` heuristic and the `Player` substring generated most of the 205 + 148, and they cannot match after Phases 02-03. Record the before and after entry counts per rule id.
>
> If tooling makes a surgical edit impractical, the acceptable fallback is a regenerate-then-diff: regenerate into a scratch copy under `temp/S1195/`, diff it against the current baseline, and hand-apply only the deletions - never the additions. Additions are what §7 forbids doing blind.

**Method actually used, and why it differs from the prompt.** A surgical edit is not merely
impractical here, it is not implementable: `lint-results.xml` contains only the findings that were
*not* filtered, so nothing in the report identifies *which* baseline entries matched. The unmatched
set is knowable only as per-rule counts from the `LintBaselineFixed` hint. That leaves regenerate-and-
diff as the only method that can name the removals.

What makes the regeneration safe here is ordering, not tooling: §7's objection to
`updateLintBaseline` is that it accepts every live finding as known-good without anyone reading it.
By this point every live finding has a recorded disposition in Step 07.1 - 13 fixed at source, 4
parked as S1324, 2 kept with the Phase 06 argument - so the regeneration adds only entries that were
already read and decided. The diff against
`temp/S1195/lint-baseline.backup-20260731_010250.xml` then states both sides for the record.

**Verification:**

- `expected: <N> stale entries removed | actual: <M>` recorded, per rule id.
- No entry removed that still corresponds to a live finding - re-run lint and confirm the filtered count dropped by exactly the number removed.
- `app_v2/lint-baseline.xml` still parses as XML.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Re-opened after the concurrency wait recorded below. Both locks were free, so the tree
  was stable for the first time since 01:17.
- Confirming run, `temp/S1195/phase07-run1.log`, report mtime 09:58:28 - **6 live errors**, exactly the
  six Step 07.1 dispositioned, with nothing unexplained: `MainThreadIo` 4 (`ReceiveShareActivity`
  682/683/696/697, parked as S1324) and `RepeatOnLifecycleWrongUsage` 2 (the Phase 06 keeps). The
  sibling session's edits to `src/main` added no error, so its work did not have to be triaged here.
- Regeneration ran into a scratch copy, never over the live file:
  `check-typo-lint.ps1 -LintTask updateLintBaselineStandardDebug` with the live baseline saved and
  restored around it (`temp/S1195/phase07-regen.log`, `temp/S1195/lint-baseline.regenerated.xml`,
  2568 entries). The analysis tasks were up to date from the confirming run, so the regeneration cost
  seconds rather than a seventh minute.
- Removal tool: `temp/S1195/prune-baseline.ps1`, a text splice rather than an XML rewrite, so the 2369
  surviving entries keep their exact bytes and the diff shows only deletions. It matches lint's own key
  - id plus file plus message, line ignored - and honours multiplicity.
- `expected: the LintBaselineFixed population removed | actual: 439 entries removed, 2808 -> 2369`.
  Arithmetic reconciles with the report: 436 "errors/warnings ... not found" plus the 3 hints.
- Removals per rule id: `UiContextLeak` 205, `PlayerNotReleased` 148, `UnusedResources` 16,
  `NewerVersionAvailable` 12, `MainThreadIo` 11, `UseKtx` 11, `HardcodedText` 6, `UnsafeFlowCollect` 5,
  `Untranslatable` 4, `GradleDependency` 4, `NotifyDataSetChanged` 4, `ActivityLogicViolation` 2,
  `Overdraw` 2, `RtlSymmetry` 2, `ClickableViewAccessibility` 2, and one each of `StringFormatCount`,
  `DiscouragedApi`, `DisableBaselineAlignment`, `SetTextI18n`, `UselessParent`.
- Every entry of the three rewritten detectors went: `UiContextLeak` 205 -> 0, `PlayerNotReleased`
  148 -> 0, `MainThreadIo` 11 -> 0. Survivors in scope for Step 07.4: `ActivityLogicViolation` 78,
  `UnsafeFlowCollect` 7.
- `app_v2/lint-baseline.xml` parses: `[xml]` load returns 2369 `issue` nodes.
- Additions were computed and **not** applied - 199 of them, which is Step 07.3's business.

**Three warnings surfaced, and they should have.** The post-removal run
(`temp/S1195/phase07-run2b.log`) reports 6 errors and **193** warnings against the pre-removal run's
190. Errors are unchanged, which is what §11.3 turns on, but the delta was named rather than waved
through, by re-running lint against the saved pre-prune baseline (`temp/S1195/phase07-run1b.log`,
`lint-results.run1b.xml`) and diffing the two live sets:

- `NewerVersionAvailable` on `com.google.dagger:hilt-android` x1 and `hilt-android-compiler` x2.
- Cause: the removed entries read "than 2.59 is available: **2.60**" while the current finding reads
  "available: **2.60.1**". Lint's matcher tolerates that drift; an exact key comparison does not.
- Kept removed deliberately. Lint's own regeneration does not contain those entries, so restoring them
  would re-hide a live version notice behind a message naming a version that is no longer the newest -
  the dead text this phase exists to clear. Warnings are a spec §2 non-goal and do not fail the build.
- Checked for the inverse error too: no live finding matches a removed entry by exact key, so nothing
  real was buried by the prune.

---

### Step 07.3 - Add the deliberate keeps, each with its reason

**Files:** `app_v2/lint-baseline.xml`
**Depends on:** Step 07.2

**Prompt for developer:**

> Add one entry per finding that Phases 05 and 06 classified as keep-with-justification. §11.2 requires a recorded reason for every one, so the reason must be written where a future reader will find it, not left in a commit message. Put it in the entry's surrounding comment in the baseline file and mirror it in the phase file that made the decision.
>
> A reason states why the finding is acceptable in this specific place - "trampoline Activity with no ViewModel, receives an Intent and finishes" is a reason; "false positive" and "pre-existing" are not. If a reason cannot be written in one sentence, the finding probably needs fixing rather than baselining.

**Verification:**

- Count of new entries equals the count of outcome-3 findings from Phase 05 plus the keeps from Phase 06.
- Every new entry has a reason comment; `Grep` the baseline for entries added by this ticket and confirm none is bare.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Six entries added, in two commented groups, appended after a marker comment that
  separates everything this ticket wrote from everything that predates it.
- `expected: 2 (Phase 05 selected zero outcome-3 keeps; Phase 06 hands two) | actual: 6`. The gap is
  not a misclassification: the extra four are the `ReceiveShareActivity` deletes that Step 07.1 found
  and parked as S1324, which did not exist when this step was written. Both counts are accounted for
  in writing, which is what §11.2 asks.
- Reasons, one per group and covering all six: the `MainThreadIo` four name S1324 and state why an
  in-place fix is wrong (`lifecycleScope` is already cancelled at `onDestroy`, so the deletes need a
  scope that outlives the Activity); the `RepeatOnLifecycleWrongUsage` two carry Phase 06's
  justification verbatim, plus the note that the second location is the shared helper while the
  message names the offending host, so a future reader does not edit `LifecycleExtensions.kt`.
- No bare entry: both groups sit under a comment, and no entry was added outside them.
- Lint parses XML comments in the baseline without complaint - proven by the filtered count rising
  from 90 to 96 errors, which only happens if all six entries were read.
- **`:app_v2:lintStandardDebug` -> `BUILD SUCCESSFUL`, exit code 0, `temp/S1195/phase07-green.log`.**
  `Lint found 193 warnings (and 96 errors, 2276 warnings and 3 hints filtered by baseline)` - zero live
  errors, so §11.3 holds for the first time in this ticket's life.
- The `LintBaselineFixed` hint is gone outright, not reduced: the run reports no "listed in the
  baseline file but not found" line at all, against 434 unmatched entries at the start of this phase.
- Baseline is 2375 entries: 2808 at the start, minus 439 stale, plus these 6.

---

### Step 07.4 - Selective re-triage of the surviving filtered findings

**Files:** `temp/S1195/baseline-triage.md`, `app_v2/lint-baseline.xml`
**Depends on:** Step 07.3

**Prompt for developer:**

> §6.2 scopes this deliberately: review only the project's own detector entries and P0/P1 categories - context leaks, main-thread I/O, unreleased resources. Style entries (`UseKtx` 493, `UnusedResources` 1102, `HardcodedText` 113 and the rest) are explicitly **not** reviewed here.
>
> In scope after Step 07.2's removals: whatever survives of `UiContextLeak`, `PlayerNotReleased`, `ActivityLogicViolation` (80 at the start), `MainThreadIo` (11 at the start) and `UnsafeFlowCollect` (12 at the start). Note that `UnsafeFlowCollectDetector` is the fifth project detector - registered in `CustomIssueRegistry`, zero live findings, and unmentioned in the strategic spec - so its 12 entries are in scope even though no phase touched it.
>
> For each surviving entry: fix, keep with a reason, or - where the refined detector no longer produces that shape at all - remove as stale. `MainThreadIo` entries deserve the most attention: they are P1 by the §13 taxonomy and there are few enough to read individually.
>
> Anything that turns out to be a real defect too large to fix here goes to `/spec-draft` per §3.1, not into the baseline with a hand-wave.

**Verification:**

- Every in-scope entry has a recorded disposition in `temp/S1195/baseline-triage.md`.
- Zero style-category entries touched - confirm by diffing the baseline and checking the changed rule ids.
- Any parked ticket ids recorded here.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Step 07.2's removals shrank this step's input from five detector populations to two.
  `UiContextLeak`, `PlayerNotReleased` and `MainThreadIo` have **no** baseline entries left - every one
  was written against pre-Phase-02/03/04 message text. Full record:
  `temp/S1195/baseline-triage.md`, section "Step 07.4".
- **`ActivityLogicViolation` 78 - kept, tracked as S1329.** All 78 survive regeneration, so the
  detector still produces them. The single message shape names no type, so the shape was confirmed by
  reading code rather than trusting the report: `@Inject lateinit var <name>: <Repository|UseCase>`
  declared in an Activity, the same form Phase 05 fixed for the live 15. Fifteen Activities, four of
  them large player hosts - a ticket, not a step. Parked per CLAUDE.md §3.1 after `search.ps1` returned
  no existing record.
- **`UnsafeFlowCollect` 7 - 3 fixed, 4 kept.** The fifth project detector, which no phase touched and
  the strategic spec never names.
  - Fixed: `IntegrationTestDialog` 117/128/138, three view-bound `lifecycleScope.launch { .. collect }`
    blocks - the exact shape the rule targets. Being in `src/debug` would have been a defensible reason
    to keep them, which is why they were fixed instead: this ticket exists because rules nobody
    believes get ignored. Swapped to the project's `collectOnLifecycle`. Safe by inspection - all three
    flows are `StateFlow`, so the latest value re-emits on restart, and `observeViewModel()` runs from
    `onViewCreated`, so no `RepeatOnLifecycleWrongUsage` is introduced. Their entries removed.
  - Kept: `LyricsManager:46` (no View access, no `LifecycleOwner` available),
    `SlideshowResourceAvailabilityManager:39` and `:53` (must observe while STOPPED for the
    background-capable audio slideshow), `DeleteDialog:100` (a cancellable delete already in flight -
    binding it to STARTED would abort a running delete and leave a partial selection).
- Reason comment for both kept populations written into `app_v2/lint-baseline.xml` itself, beside the
  S1195 marker, so a reader of the baseline is not sent to a scratch file for the argument.
- Zero style-category entries opened. The only baseline edits here are the three `UnsafeFlowCollect`
  removals and that comment.
- Verification. `.\a.ps1 fk` -> `BUILD SUCCESSFUL`, exit 0, `temp/S1195/phase07-fk2.log`.
  **Full `:app_v2:lintStandardDebug` -> `BUILD SUCCESSFUL`, exit code 0, 4m54s,
  `temp/S1195/phase07-final.log`**, a genuine re-analysis because the Kotlin edit invalidated the
  cached one. `Lint found 193 warnings (and 93 errors, 2276 warnings and 3 hints filtered by baseline)`
  - zero live errors, and filtered errors 96 -> 93 exactly matches the three removals, so those entries
  were indeed dead. No "listed in the baseline file but not found" line at all.
- Baseline settles at 2372 entries: 2808 - 439 stale + 6 keeps - 3 fixed.
- parked: S1329 activity-logic-debt-78-baselined-violations

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`.
- [x] **§11.2 satisfied** - every remaining error is fixed or baselined with a recorded reason.
- [x] **§11.3 satisfied** - `:app_v2:lintStandardDebug` exits 0 with `BUILD SUCCESSFUL`,
      `temp/S1195/phase07-final.log`.
- [x] The `LintBaselineFixed` hint is gone outright - zero unmatched entries, against 434 at the start.
- [x] No style-category baseline entry touched by the re-triage.
- [x] `post-change.ps1 -ChangeType Kotlin -Module app_v2 -ScopeToFile` -> `PASS`, exit 0. Ran against
      the Kotlin edit rather than the baseline, because that is the file carrying risk; detekt scoped
      clean on the changed file.
- [x] Dev log entry added by the same closure.
- [x] Phase-boundary audit run - no P0/P1 findings.

**Phase-boundary audit.** Files touched are one Kotlin file, one generated-shape config file and this
plan. Layer 1 - no architecture change; the Kotlin edit removes two imports it orphaned rather than
leaving them. Layer 2, the layer that matters here - the edit moves three view-bound collectors from
an unguarded `lifecycleScope.launch` onto `repeatOnLifecycle(STARTED)` through the project's own
helper, which is the direction the rule asks for, and the flows are `StateFlow`, so suspending the
collector while stopped drops nothing. Layer 3 - no listener registration changed, so no symmetry
edge to check; the gate confirms `new imbalance 0`. Layer 4 - no Room surface. Nothing above P3.

---

## Concurrency constraint on Steps 07.2-07.4

Recorded because it changes when this phase may be finished, not merely how.

A baseline regeneration snapshots **every** live finding in the working tree, so it is only sound when
the tree is the one this ticket triaged. On 2026-07-31 01:17 a sibling agent session took `CODE.LOCK`
(`S1025+S1320-S1323 log-analysis fixes`, pid 28652) and began editing `app_v2/src/main`. Two
consequences, both observed rather than predicted:

- The 01:19 lint run **crashed** instead of reporting: `lintAnalyzeStandardDebug` died with
  `FirDeclaration was not found for class KtParameter, fir is null` while analysing
  `HostReachabilityCheckerImpl.kt`, whose mtime is 01:19:59 - inside that run's own window. Lint's
  printed advice (disable `ExperimentalDetector`) is the wrong remedy: the file was being written while
  lint parsed it. The immediate re-run, against the now-stable file, analysed cleanly. Crash log:
  `temp/S1195/phase07-pre.log`; clean run: `temp/S1195/phase07-pre2.log`.
- Regenerating now would write that session's in-flight findings into `lint-baseline.xml` under this
  ticket's name. That is the blind acceptance strategic §7 rates a high risk, and it would be invisible
  afterwards, because a baseline entry records no author.

So Steps 07.2-07.4 wait for a tree no other session is mutating. The triage they consume is finished and
recorded (Step 07.1 and `temp/S1195/baseline-triage.md`); what remains is mechanical.

**Resolved 2026-07-31 09:51.** Both locks were free, so the wait ended. The confirming run before any
baseline edit found 6 live errors - exactly the six Step 07.1 dispositioned - which is the evidence
that the sibling session's work introduced nothing this ticket would have silently accepted. Waiting
was therefore cheap and the fear was worth acting on: had the regeneration run at 01:19 it would have
snapshotted a half-written `HostReachabilityCheckerImpl.kt`, the same file whose mid-write state
crashed lint's analyser.

---

## Handoff Notes to Next Phase

Phase 08 may start: this phase ends green.

- Final baseline: **2372 entries**, from 2808 - 439 stale removed, 6 keeps added with reasons, 3 fixed
  and removed.
- Green-run evidence: `temp/S1195/phase07-final.log`, `BUILD SUCCESSFUL`, exit 0, 4m54s,
  `Lint found 193 warnings (and 93 errors, 2276 warnings and 3 hints filtered by baseline)`.
- Live errors are zero, so a `MissingTranslation` finding in Phase 08 will be unambiguous - it can only
  come from the rule Phase 08 re-enables, with nothing else in the way.
- Live warnings are 193 rather than the 182 the plan recorded on 2026-07-29. Three are the dagger
  version notices Step 07.2 stopped hiding; the rest arrived with other tickets' work. Warnings are a
  spec §2 non-goal and do not gate anything.
- Parked in this phase: **S1329** (the 78 `ActivityLogicViolation` entries). Add it to the strategic
  spec's §10 at closure alongside S1283 and S1324.

---

## Rollback Plan

Restore `app_v2/lint-baseline.xml` from the `temp/S1195/` backup. The baseline affects only what lint reports, never the shipped app.

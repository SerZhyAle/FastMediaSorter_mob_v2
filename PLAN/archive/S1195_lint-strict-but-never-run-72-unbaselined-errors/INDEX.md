# Tactical Plan: S1195 - lint-strict-but-never-run-72-unbaselined-errors

**Strategic spec:** [`../S1195_lint-strict-but-never-run-72-unbaselined-errors.md`](../S1195_lint-strict-but-never-run-72-unbaselined-errors.md)
**Feature:** Make `:app_v2:lintStandardDebug` green and believable again - refine the project's own detectors, then triage what survives
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 60
**Status:** Tactical
**Phases:** 8 / 8 done
**Last updated:** 2026-07-31

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Measured Baseline (2026-07-29, 19:56, 6m50s)

Every number below is read from `app_v2/build/reports/lint-results.xml` produced by that run, not from the strategic spec's §1 prose. The spec's §1 census was taken 2026-07-25 with `MissingTranslation` temporarily enabled and is superseded here; see "Corrections to the strategic spec" below.

Errors, 75 total (this is what `abortOnError = true` fails on):

- `UiContextLeak` 44 - project detector
- `ActivityLogicViolation` 15 - project detector
- `PlayerNotReleased` 6 - project detector
- `UseAppTint` 4 - platform, not mentioned anywhere in the spec
- `MainThreadIo` 2 - project detector
- `MissingPermission` 2 - platform
- `RepeatOnLifecycleWrongUsage` 2 - platform, not mentioned anywhere in the spec

The project's own detectors are 67 of 75 errors (89 %). Warnings, 182, are out of scope per spec §2 non-goals.

Baseline state, `app_v2/lint-baseline.xml`, 2808 entries:

- 448 errors + 2281 warnings + 3 hints filtered from this run.
- 76 entries listed but no longer found - lint emits the `LintBaselineFixed` hint naming them. Unmatched types include `UiContextLeak` (3), `PlayerNotReleased` (3), `UnsafeFlowCollect` (5), `ActivityLogicViolation` (1), `MainThreadIo` (1).
- Project-detector entries in the file: `UiContextLeak` 205, `PlayerNotReleased` 148, `ActivityLogicViolation` 80, `UnsafeFlowCollect` 12, `MainThreadIo` 11 - 456 total. The live findings are the unbaselined residue of a much larger population.

---

## Evidence Behind the Detector Refinements

Recorded here so each phase starts from measurement, not from the report text the spec §4 complains about.

### `UiContextLeak` - 44 findings, zero of them real

Split by which `isSingleton` / `isViewModel` branch admitted the class:

- 33 admitted **only** by `node.name?.endsWith("Manager") == true` (`UiContextLeakDetector.kt:15`). Every one is an Activity-scoped `ui/**/helpers/*Manager.kt` holding a `View`, `Activity`, `FragmentActivity`, `Fragment` or `ViewGroup` - the exact shape CLAUDE.md Rule 3 mandates. Three of them (`BrowseVrCinemaLaunchManager`, `ResourceVrCinemaLaunchManager`, `StandaloneVrCinemaLaunchManager`) declare `@ActivityContext` explicitly, so the activity scoping is deliberate and annotated.
- 11 admitted by a real `@Singleton`. **All 11 carry a Hilt `@ApplicationContext` or `@param:ApplicationContext` qualifier** and are safe. They are flagged only because the escape hatch at `UiContextLeakDetector.kt:33-34` is a *field-name* comparison against `appContext` / `applicationContext`, and every one of these fields is named `context`.

Consequence that drives Phase 03: fixing both defects takes `UiContextLeak` from 44 to **zero** live findings. The rule would then catch nothing at all, because its type matching is literal - `typeName.endsWith(".View")` never matches `TextView`, `RecyclerView` or `PlayerView`, and `.endsWith(".Activity")` never matches `AppCompatActivity`. Closing that false negative is not optional polish; it is the only thing that leaves a rule worth keeping.

Two further defects found while measuring:

- `typeName.contains("android.view.View")` sweeps in nested types. `LauncherEditModeManager.kt:72` is flagged for `private val dragListener = View.OnDragListener { .. }`, whose canonical text is `android.view.View.OnDragListener`.
- Reported locations are unreliable for documented fields: `CameraOverlayRotationManager.kt:13` and `StreamsControlsPlacementManager.kt:34` point at comment lines, `CameraCaptureSessionManager.kt:150` at a KDoc opener. `context.getLocation(field)` on a Kotlin light element starts at the doc comment. Triage must read the class, never the reported line.

### `PlayerNotReleased` - 6 findings, mechanism sharper than "substring"

The substring diagnosis is right but incomplete, and the incompleteness matters for the fix. `node.fields` on a Kotlin light class includes **synthetic** fields whose type carries the enclosing class's fully-qualified name, so a class whose own name contains `Player` flags itself with no player-typed property anywhere:

- `PlayerTextureFrameCapture.kt:7` is `internal object` with **zero properties**. Its synthetic `INSTANCE` field is typed `..helpers.PlayerTextureFrameCapture`.
- `S0981OpenInPlayerDefaultOff.kt:11` has no player field either. Its `private companion object` yields a synthetic `Companion` field typed `..migration.S0981OpenInPlayerDefaultOff.Companion`.

The remaining four are ordinary substring hits on real fields, none of which own a player:

- `VideoPlayerDependencies.kt:24` - `data class VideoPlayerHostDependencies`, a DTO, via `playerCallback: VideoPlayerManager.PlayerCallback`.
- `DefaultAppsDialogFragment.kt:20` - via `defaultPlayerSettingsManager: DefaultPlayerSettingsManager`.
- `FastMediaSorterApp.kt:46` - via `dagger.Lazy<..data.migration.S0981OpenInPlayerDefaultOff>`.
- `AudioServiceController.kt:406` - the **nested** `data class FutureRequest(val future: ListenableFuture<MediaController>)`. `visitClass` evaluates nested classes in isolation, so the outer controller's release logic is invisible to the nested DTO. Nested-class scoping is a third distinct defect alongside the substring match and the `hasReleaseCall` check.

### `ActivityLogicViolation` - 15 findings, all real

Every one is `@Inject lateinit var <name>: <Repository|UseCase>` declared directly in an Activity. The detector requires both an `@Inject` annotation and a forbidden type, which is precisely CLAUDE.md Rule 3. No detector change is planned.

`dagger.Lazy<XUseCase>` does count, and should: `field.type.canonicalText` includes the type argument, so `dagger.Lazy<..SaveTextNoteUseCase>` matches. Confirmed empirically - `BrowseActivity` and `PlayerActivity` `Lazy<..Repository>` fields sit in the baseline as `ActivityLogicViolation` entries. Wrapping in `Lazy` defers construction (Rule 18); it does not make an Activity to UseCase reference legal.

The live 15 are the unbaselined residue of 95 total (15 live + 80 baselined). Phase 05 fixes the 15; the 80 are Phase 07's selective re-triage.

### `MainThreadIo` - 2 findings, one of each

- `PhotoVideoStandaloneActivity.kt:400` is a **false positive**. The enclosing `stageBitmapForPrint` is invoked at line 374 as `val staged = withContext(Dispatchers.IO) { stageBitmapForPrint(bitmap) }`. The detector walks UAST parents from the call node and stops at the enclosing function body, so it never sees a caller's confinement. Intra-procedural analysis only.
- `PrintDispatchActivity.kt:141` is a **true positive**. `dispatchText()` is invoked from `onPostResume()` at line 71 with no coroutine anywhere in the chain, so `file.readText()` runs on the main thread.

### `RepeatOnLifecycleWrongUsage` - a gate-coverage finding, not a lint finding

Both findings sit inside `lifecycleScope.launch { repeatOnLifecycle(..) { .. } }`. Lint's complaint is *where the call is made from*: "Wrong usage of repeatOnLifecycle from `CameraSettingsDialogFragment.onStart`" and "from `PhotoVideoStandaloneActivity.onStart`". Calling `repeatOnLifecycle` inside `onStart` relaunches a collector on every lifecycle restart.

`scripts/quality/assert-unsafe-collect.ps1` cannot see this shape, and not because of its ratchet. Its per-launch predicate ends with `if ($body -match '(repeatOnLifecycle|flowWithLifecycle)') { return $false }` - a body containing `repeatOnLifecycle` is **explicitly exempted**. The gate tests for the presence of the guard and never for the call site. This is structural blindness, so it is a `/spec-draft` candidate rather than in-scope work here (Phase 06 records it and reports).

Note the attribution: the second finding's location is `LifecycleExtensions.kt:39`, the project's own sanctioned `collectOnLifecycle` helper, but the message names `PhotoVideoStandaloneActivity.onStart` as the offender. Lint attributes through the inlined helper. `PhotoVideoStandaloneActivity.onStart` (line 1157) does not call `collectOnLifecycle` directly, so triage must trace the call chain. Do not edit `LifecycleExtensions.kt` on the strength of the reported location.

### The test suite is itself a gate that never runs

`lint-rules/src/test/java/com/sza/fastmediasorter/lint/CustomLintRulesTest.kt` exists with five tests. `:lint-rules:test` is invoked by nothing:

- CI `verify` runs `lintStandardDebug testStandardDebugUnitTest assembleStandardDebug`.
- `.\a.ps1 fu` maps to `check-standard-fast.ps1 -Mode Unit`, which resolves to `:app_v2:testStandardDebugUnitTest`.
- No other script or workflow references `:lint-rules:test`.

This is the same failure mode the ticket exists to fix, one level down, and it is why Phase 01 comes before any detector edit: without a running test task, "covered by a test" is an unverifiable claim.

---

## Corrections to the Strategic Spec

Fold these into the spec during Phase 08 closure so the record matches measurement.

1. §1 census is wrong and incomplete. It reports "66 standing errors" broken down as `UiContextLeak` 43, `ActivityLogicViolation` 13, `PlayerNotReleased` 6, `MainThreadIo` 2, `MissingPermission` 2. Current truth is 75, and the spec never mentions `UseAppTint` (4) or `RepeatOnLifecycleWrongUsage` (2) at all. The slug's "72" was closer than the prose's "66".
2. §4.1 states the detector "caught the word Player in a comment and in the name of the boolean setting `linkAutoDownloadOpenInPlayer`". It reads neither comments nor field names - only `field.type.canonicalText`. The line 11 location is the class KDoc because `getLocation` on a `UClass` starts at the doc comment. The actual trigger for that file is the synthetic `Companion` field carrying the enclosing class's FQN.
3. §1 counts only four project detectors. There is a fifth, `UnsafeFlowCollectDetector`, registered in `CustomIssueRegistry` with 12 baseline entries and zero live findings. It is in scope for §6.2's baseline re-triage.
4. §3.2 records the full run as 10m43s. The 2026-07-29 run took 6m50s.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | detector-test-harness | - | ✅ Done | 3/3 | [PHASE_01__detector-test-harness.md](PHASE_01__detector-test-harness.md) |
| 02 | player-release-detector | 01 | ✅ Done | 4/4 | [PHASE_02__player-release-detector.md](PHASE_02__player-release-detector.md) |
| 03 | ui-context-leak-detector | 01 | ✅ Done | 5/5 | [PHASE_03__ui-context-leak-detector.md](PHASE_03__ui-context-leak-detector.md) |
| 04 | main-thread-io-detector | 01 | ✅ Done | 4/4 | [PHASE_04__main-thread-io-detector.md](PHASE_04__main-thread-io-detector.md) |
| 05 | activity-logic-triage | - | ✅ Done | 4/4 | [PHASE_05__activity-logic-triage.md](PHASE_05__activity-logic-triage.md) |
| 06 | platform-residue-triage | - | ✅ Done | 4/4 | [PHASE_06__platform-residue-triage.md](PHASE_06__platform-residue-triage.md) |
| 07 | baseline-reconciliation | 02, 03, 04, 05, 06 | ✅ Done | 4/4 | [PHASE_07__baseline-reconciliation.md](PHASE_07__baseline-reconciliation.md) |
| 08 | missing-translation-and-closure | 07 | ✅ Done | 5/5 | [PHASE_08__missing-translation-and-closure.md](PHASE_08__missing-translation-and-closure.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Ordering follows spec §5 and ADR-1: detectors are refined before their findings are triaged, and no baseline is regenerated before triage. Phases 02, 03 and 04 are independent of each other once 01 lands, and each is a self-contained landable unit per §7's mitigation. Phases 05 and 06 touch only app code and depend on nothing.

---

## Pre-Implementation Blockers

None blocking. Two scheduling facts:

- Phase 08's precondition - marking the 27 `src/debug/res/values/strings_debug.xml` strings `translatable="false"` - is owned by **S1280**, in flight as of 2026-07-29. Phase 08 verifies that precondition and never performs it. S1195 owns only the `app_v2/build.gradle.kts` edit and its comment.
- A full `lintStandardDebug` run costs about 7 minutes and takes `temp/BUILD.LOCK`. Phases 02-04 must lean on `:lint-rules:test` for iteration and spend a full lint run only at the phase boundary.

---

## Completion Gate

Mapped one-to-one onto the four strategic criteria in §11.

- [x] All phases show ✅ Done.
- [x] **§11.1** - no project detector produces a false positive on the sample it was checked against.
      `:lint-rules:test` 30/30, every enumerated finding gone or justified, each refinement fenced by a
      removed-false-positive and a retained-true-positive case.
- [x] **§11.2** - every remaining error is fixed or baselined with a recorded reason. The six entries
      this ticket added sit under reason comments in `lint-baseline.xml` itself, and the two kept
      pre-existing populations (`ActivityLogicViolation` 78, `UnsafeFlowCollect` 4) are recorded there
      too.
- [x] **§11.3** - `:app_v2:lintStandardDebug` -> `BUILD SUCCESSFUL`, exit 0, full run,
      `temp/S1195/phase08-lint.log`, zero errors with `MissingTranslation` back on.
- [ ] **§11.4** - MANUAL-REQUIRED. The `verify` job needs a push to `main`, which is owner-gated. All
      four of its commands are green locally; evidence in Phase 08 Step 08.5.
- [x] `dev/CHANGELOG.md` has an entry per logical change (batched, not per file).
- [x] `docs/ALL_FEATURES.jsonl` - **deliberately not written**. Every area in that file is a user-facing
      product surface and it feeds the public showcase; enforced lint rules are build plumbing. Reasoned
      in Phase 08 Step 08.5.
- [ ] `/spec-check S1195` returns `Verified`, and advances the strategic spec's `Status:`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/8 done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` only when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked` and add a bullet to the Blockers Log. If the whole spec is blocked, set the journal status with `-StatusNote`.
5. All done: run `/spec-check S1195`.

---

## Blockers Log

- (empty)

---

## Measurement After Phases 01-04

Full `lintStandardDebug`, 2026-07-29 21:42, log `temp/S1195/phase04-lint-final.log`.

Errors **75 -> 40**. Warnings unchanged at 182, as the non-goal in spec §2 intends.

| Rule | Before | After | Reading |
|------|-------:|------:|---------|
| `UiContextLeak` | 44 | 4 | All 44 known false positives gone; the 4 survivors are new true positives |
| `ActivityLogicViolation` | 15 | 15 | Untouched by design - Phase 05 owns it |
| `PlayerNotReleased` | 6 | 6 | All 6 known false positives gone; a different 6, all residual false positives |
| `MainThreadIo` | 2 | 7 | Both known findings resolved; the rise is baseline staleness, see below |
| `UseAppTint` | 4 | 4 | Platform - Phase 06 |
| `MissingPermission` | 2 | 2 | Platform - Phase 06 |
| `RepeatOnLifecycleWrongUsage` | 2 | 2 | Platform - Phase 06 |

An intermediate run at 21:27 measured 48 errors with `PlayerNotReleased` at 14. Measuring against real code exposed two false-positive patterns the unit tests had not modelled - a factory returning its player, and a release through a differently-named local - which were fixed and re-measured. Both patterns now have their own test cases. The 21:27 numbers are superseded.

### Why two counts rose, and why it is not a regression

**A lint baseline entry matches on the issue's message text.** All three rewritten detectors changed their message wording, so every previously-filtered finding for those rules stopped matching its baseline entry and resurfaced as live. This is the mechanism, confirmed by file: the 7 live `MainThreadIo` sit in `IntegrationTestViewModel.kt` and `ReceiveShareActivity.kt`, which are exactly the files already carrying `MainThreadIo` baseline entries, and neither of the two findings Phase 04 targeted is among them.

Consequence for Phase 07, which should be treated as its main input: **the 456 project-detector baseline entries are now largely unmatchable and must be regenerated, not edited.** The `LintBaselineFixed` hint list is no longer a useful signal for these five rules either.

This also means the ticket's headline number cannot be driven to zero by triage alone. Phase 07 has to regenerate the baseline against the rewritten detectors first, and only then triage what genuinely survives.

---

## Change Log

- 2026-07-29 - Initial tactical plan authored by `/spec-tech`, against the 2026-07-29 19:56 lint run.

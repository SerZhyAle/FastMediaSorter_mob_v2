# Phase 05 - ActivityLogicViolation triage

**Strategic spec:** [`../S1195_lint-strict-but-never-run-72-unbaselined-errors.md`](../S1195_lint-strict-but-never-run-72-unbaselined-errors.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Completed:** 2026-07-31
**Depends on:** none - app-code only, independent of the detector phases
**Blocks:** Phase 07
**Steps done:** 4 / 4

---

## Objective

`ActivityLogicDetector` is sound and is **not** changed in this phase. It requires both an `@Inject` annotation and a type containing `Repository` / `UseCase` / `DataSource` / `Dao` / `Database`, which is exactly CLAUDE.md Rule 3. All 15 live findings are real violations. This phase fixes app code.

`dagger.Lazy<XUseCase>` does count, and should. `field.type.canonicalText` includes the type argument, so `dagger.Lazy<..SaveTextNoteUseCase>` matches - confirmed by the `Lazy<..Repository>` fields of `BrowseActivity` and `PlayerActivity` sitting in the baseline as `ActivityLogicViolation` entries. `Lazy` defers construction (Rule 18); it does not make an Activity to UseCase reference legal. No detector change is warranted on that account.

The 15 live findings are the unbaselined residue of 95 (15 live, 80 baselined). This phase fixes the 15; Phase 07 decides what to do about the 80.

---

## The 15 findings

- `ui/cameracapture/CameraCaptureActivity.kt:104` - `settingsRepository: SettingsRepository`
- `ui/cameracapture/CameraCaptureActivity.kt:109` - `resourceRepository: ResourceRepository`
- `ui/companionimport/CompanionConfigImportActivity.kt:44` - `importUseCase: ImportCompanionConfigUseCase`
- `ui/companionimport/qr/CompanionQrShareActivity.kt:30` - `settingsRepository: SettingsRepository`
- `ui/launcher/LauncherHomeActivity.kt:71` - `queryAppShortcuts: QueryAppShortcutsUseCase` (`launcherEnabled` source set)
- `ui/launcher/LauncherHomeActivity.kt:74` - `startAppShortcut: StartAppShortcutUseCase` (`launcherEnabled`)
- `ui/main/MainActivity.kt:229` - `saveCapturedMedia: SaveCapturedMediaUseCase`
- `ui/main/MainActivity.kt:266` - `observePinnedStreamSources: ObservePinnedStreamSourcesUseCase`
- `ui/main/MainActivity.kt:276` - `unpinStreamSource: UnpinStreamSourceUseCase`
- `ui/player/PlayerActivity.kt:869` - `saveTextNoteUseCase: SaveTextNoteUseCase`
- `widget/PhotoCaptureLaunchActivity.kt:32` - `settingsRepository: SettingsRepository`
- `widget/PhotoCaptureLaunchActivity.kt:34` - `saveCapturedMedia: SaveCapturedMediaUseCase`
- `screencapture/ScreenVideoRecordingConsentActivity.kt:28` - `settingsRepository: SettingsRepository` (`screenCapture` source set)
- `ui/streams/StreamsActivity.kt:113` - `streamTrackPreferenceUseCase: StreamTrackPreferenceUseCase`
- `ui/streams/StreamsActivity.kt:118` - `streamResumeStateRepository: StreamResumeStateRepository`

Two of them sit in non-`main` source sets (`launcherEnabled`, `screenCapture`), so verification must compile the variants that include them, not only `standardDebug`.

---

## Prerequisites

- [ ] `temp/CODE.LOCK` acquired.
- [ ] `MainActivity.kt` is near the ~1500 LOC ceiling - check its current size before adding anything, and prefer moving the dependency out over moving code in.
- [ ] Timestamped backup under `temp/S1195/` for every touched file over 500 LOC (`MainActivity.kt`, `PlayerActivity.kt`, `StreamsActivity.kt` at minimum).

---

## Steps

### Step 05.1 - Classify each of the 15 before changing anything

**Files:** this plan file
**Depends on:** - start of phase

**Prompt for developer:**

> For each of the 15, read the Activity and record which remedy applies. There are only three legitimate outcomes, and picking one per finding up front prevents fifteen ad-hoc decisions:
> 1. **Move to the ViewModel** - the Activity already has one, the dependency belongs behind it. The default and the preferred outcome.
> 2. **Move to a helper Manager** - the Activity has no ViewModel or the dependency serves one narrow flow. Follows the `NounVerbManager` convention and Rule 3's own wording.
> 3. **Keep, with a written justification** - only where the Activity is a trampoline with no UI and no ViewModel (`PhotoCaptureLaunchActivity`, `CompanionConfigImportActivity`, `ScreenVideoRecordingConsentActivity` are candidates: they exist to receive an Intent, do one thing and finish). For these, the outcome is a baseline entry with the reason recorded, not a code change - and the reason belongs in this file and in the baseline entry's context, not only in a commit message.
>
> Note the two documented exceptions already in the code: `CompanionQrShareActivity.kt:30` carries an S1045 comment explaining it does not extend `BaseActivity` and reads the setting directly. `CameraCaptureActivity.kt:104` carries an S0766 comment stating the host owns the location source. Existing comments are requirements (Rule 8) - do not silently override either; if the remedy contradicts the comment, the comment must be updated in the same change with the new rationale.

**Verification:**

- All 15 have a recorded outcome (1, 2 or 3) with a one-line reason in this file.
- Every outcome-3 choice names why the Activity has no ViewModel or Manager to delegate to.

**Classification (2026-07-30):**

- `CameraCaptureActivity.settingsRepository` - 2, existing capture helpers already own the narrow capture flow.
- `CameraCaptureActivity.resourceRepository` - 2, existing capture helpers already own the narrow destination-label flow.
- `CompanionConfigImportActivity.importUseCase` - 2, the import-and-dialog flow needs a dedicated Activity-scoped manager.
- `CompanionQrShareActivity.settingsRepository` - 1, its setting flow belongs in a ViewModel rather than the renderer Activity.
- `LauncherHomeActivity.queryAppShortcuts` - 1, `LauncherHomeViewModel` already owns launcher commands.
- `LauncherHomeActivity.startAppShortcut` - 1, `LauncherHomeViewModel` already owns launcher commands.
- `MainActivity.saveCapturedMedia` - 1, `MainViewModel` already owns the main capture flow.
- `MainActivity.observePinnedStreamSources` - 1, `MainViewModel` already owns main-screen streams state.
- `MainActivity.unpinStreamSource` - 1, `MainViewModel` already owns main-screen streams state.
- `PlayerActivity.saveTextNoteUseCase` - 1, `PlayerViewModel` already owns player state and actions.
- `PhotoCaptureLaunchActivity.settingsRepository` - 2, `PhotoCaptureLaunchManager` already owns the one-shot widget capture flow.
- `PhotoCaptureLaunchActivity.saveCapturedMedia` - 2, `PhotoCaptureLaunchManager` already owns the one-shot widget capture flow.
- `ScreenVideoRecordingConsentActivity.settingsRepository` - 2, the consent host needs an Activity-scoped recording-flow manager.
- `StreamsActivity.streamTrackPreferenceUseCase` - 1, `StreamsViewModel` already owns stream preference state and actions.
- `StreamsActivity.streamResumeStateRepository` - 1, `StreamsViewModel` already owns stream state and persistence.

No outcome-3 baseline keeps were selected. Each affected Activity has an existing ViewModel or an
Activity-scoped manager can own its narrow UI flow without retaining state after the host is destroyed.

**Status:** `[x]` done

---

### Step 05.2 - Fix the ViewModel-backed Activities

**Files:** per Step 05.1 classification - `MainActivity.kt`, `PlayerActivity.kt`, `StreamsActivity.kt`, `CameraCaptureActivity.kt`, `LauncherHomeActivity.kt` and their ViewModels
**Depends on:** Step 05.1

**Prompt for developer:**

> Move each outcome-1 dependency into the corresponding ViewModel: inject it there, expose the operation as a ViewModel function returning state or a one-shot event, and delete the Activity's `@Inject` field. Keep call sites behaviourally identical - this is a layering fix, not a refactor of what the feature does.
>
> Watch three things. Constructor changes to a ViewModel break its unit tests, so compile tests as part of verification, not after. `MainActivity` is at its LOC ceiling, so removing a field must not add more than it removes. `LauncherHomeActivity` lives in the `launcherEnabled` source set, so its ViewModel must stay in a source set that variant sees.

**Verification:**

- `.\a.ps1 fk` passes.
- `.\a.ps1 fu` passes - constructor changes compile against the tests.
- `Grep` per touched Activity - no `@Inject` field of a `Repository` / `UseCase` type remains except those classified outcome 3.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - All 9 outcome-1 findings moved behind their ViewModel. `CompanionQrShareViewModel` is
  new (the screen had none); `LauncherHomeViewModel`, `MainViewModel`, `PlayerViewModel` and
  `StreamsViewModel` gained the dependency plus a narrow operation each. Three helper managers now take
  the operation as a function type instead of the use case
  (`LauncherAppShortcutMenuManager`, `MainPanelItemActionsManager`, `MainStreamsPanelManager`,
  `MainCameraCaptureManager`, `TextEditorSaveFlow`), so the Activity never names a domain type.
  `LauncherStartMenuFragment` and `TextStandaloneActivity` were adapted as the other hosts of two of
  those helpers; the Fragment's two now-redundant `@Inject` use cases were dropped in favour of the
  shared `LauncherHomeViewModel` it already held.
- `StreamsActivity` also lost its `@ApplicationScope` field: the only consumer was the S1152 exit clear,
  which moved into `StreamsViewModel` (the scope must outlive the host, so the ViewModel injects it).
- Verification 3/3 PASS. `.\a.ps1 fk` -> `BUILD SUCCESSFUL`, exit 0, `temp/S1195/phase05-fk2.log`.
  `.\a.ps1 fu` -> `BUILD SUCCESSFUL`, exit 0, `temp/S1195/phase05-fu.log`, 421 reports / 419 test files,
  `assert-test-suite-complete: PASS` (no S1244 truncation). That run also executed
  `hiltJavaCompileStandardDebug`, so the added constructor bindings resolve.
  `Grep` per Activity: expected: only baselined (non-Phase-05) `@Inject` domain fields remain |
  actual: `MainActivity` 5, `PlayerActivity` 22, `LauncherHomeActivity` 1 - none of them among the 15;
  the other six Activities are clean.
- `StreamsViewModelAutoGridTest` updated for the three new constructor parameters.

---

### Step 05.3 - Fix the Manager-delegated Activities

**Files:** per Step 05.1 classification, plus new or existing `ui/<feature>/helpers/*Manager.kt`
**Depends on:** Step 05.1

**Prompt for developer:**

> Move each outcome-2 dependency into a helper following the `NounVerbManager` convention, constructed by the Activity and holding no state past it. Where a suitable helper already exists, extend it rather than adding a second one.
>
> Note the interaction with Phase 03: these helpers are Activity-scoped and hold Activity references by design. That is legitimate and is precisely why Phase 03 removes the `Manager`-name heuristic from `UiContextLeakDetector`. If Phase 03 has not landed yet, expect this step to add `UiContextLeak` findings that Phase 03 then removes - do not baseline them.

**Verification:**

- `.\a.ps1 fk` passes.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*Manager*"` lists every new helper after `catalog_sync.ps1`, with `role` and `status` set via `set.ps1`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - All 6 outcome-2 findings resolved. Two shapes were used, picked per Activity:
  - **Logic moved into a new Activity-scoped manager** where the Activity was doing the work itself:
    `CompanionConfigImportManager` (reads, caps and parses the `.fmscfg` attachment, then imports it)
    and `ScreenRecordingDisclosureManager` (reads and writes the disclosure flag). Both Activities now
    only resolve the intent and drive dialogs.
  - **Dependencies moved behind an `@Inject constructor` factory** where the Activity was already a
    pure wirer and the logic already lived in a helper: `PhotoCaptureLaunchManagerFactory` and
    `CameraCaptureHelperFactory`. The factory owns the domain types and builds the lifecycle-bound
    helper from the host's views/scope, so no repository or use case is named in the Activity.
    `CameraCaptureHelperFactory` also absorbed the two settings reads/writes the camera host still did
    inline (`currentSettings`, `rememberAspectRatio`).
- `CameraCaptureActivity` additionally lost its `SaveCapturedMediaUseCase` field, which was a
  *baselined* `ActivityLogicViolation` rather than one of the live 15 - noted for Phase 07, which
  regenerates the baseline anyway.
- Verification 2/2 PASS. `.\a.ps1 fk` -> `BUILD SUCCESSFUL`, exit 0, `temp/S1195/phase05-fk2.log`.
  Catalog: `catalog_sync.ps1 -Module app_v2` run once for the phase; `role`/`status` set on the five
  new classes via `set.ps1`.

---

### Step 05.4 - Verify across the affected variants and re-measure

**Files:** none - measurement only
**Depends on:** Step 05.2, Step 05.3

**Prompt for developer:**

> Two of the 15 live outside `src/main`. Compile the variants that include them, not only `standardDebug`: `launcherEnabled` for `LauncherHomeActivity`, `screenCapture` for `ScreenVideoRecordingConsentActivity`. Consult `dev/FLAVOR_DEVELOPMENT_RULES.md` before assuming which flavor carries which source set - do not infer it from the source-set name.
>
> Then run a full `:app_v2:lintStandardDebug` under `temp/BUILD.LOCK`, output to `temp/S1195/phase05-lint.log`, and record the `ActivityLogicViolation` count. Expected: only the outcome-3 findings remain, and their count matches Step 05.1 exactly. A mismatch means a classification was wrong, not that the baseline needs an entry.

**Verification:**

- `.\a.ps1 fc` passes, plus a compile of each affected non-standard variant.
- `expected: <count of outcome-3> live ActivityLogicViolation | actual: <N>` recorded here with the log path.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - **No separate variant compile was needed, and the plan's premise here is wrong.** Both
  "non-`main`" source sets are mounted by the `standard` flavor itself: `src/launcherEnabled/java` at
  `app_v2/build.gradle.kts:603` and `src/screenCapture/java` at `:586` (gated on `fms.screenCapture`,
  default `on`). `.\a.ps1 fk` therefore already compiles `LauncherHomeActivity` and
  `ScreenVideoRecordingConsentActivity`. Correction folded into the phase for the record.
- Full `:app_v2:lintStandardDebug`, 2026-07-31 00:48, 6m06s, `temp/S1195/phase05-lint.log`.
  Errors **40 -> 22**.

| Rule | After 01-04 | After 05 | Reading |
|------|------------:|---------:|---------|
| `ActivityLogicViolation` | 15 | 1 | The 15 are gone; the survivor is explained below |
| `MainThreadIo` | 7 | 7 | Untouched - Phase 07 (baseline staleness) |
| `PlayerNotReleased` | 6 | 6 | Untouched - Phase 07 |
| `UiContextLeak` | 4 | 4 | Untouched - Phase 07 |
| `UseAppTint` | 4 | 0 | Cleared by Phase 06 step 06.2 |
| `MissingPermission` | 2 | 2 | Phase 06 step 06.1 |
| `RepeatOnLifecycleWrongUsage` | 2 | 2 | Phase 06 step 06.3 |

- `expected: 0 live ActivityLogicViolation (no outcome-3 keeps were selected) | actual: 1`.
- **The mismatch was a defect in this plan's enumeration, not a wrong classification.** The survivor is
  `LauncherHomeActivity.pickContactShortcut: PickContactShortcutUseCase` - a sixteenth live finding the
  "The 15 findings" list above missed. It is not a resurfaced baseline entry: `Grep` of
  `app_v2/lint-baseline.xml` for `LauncherHomeActivity` returns zero matches, so it was live all along
  and the 2026-07-29 census recorded only two of that file's three.
- Fixed rather than deferred - it is the same rule, the same file and the same remedy already applied
  twice in step 05.2. `LauncherHomeViewModel` gained `contactPickIntent` / `resolveContactPick`, and
  `LauncherContactPickManager` now takes those two operations as functions instead of a
  `() -> PickContactShortcutUseCase` supplier. The supplier existed because the manager is built in an
  Activity field initialiser and nothing may be dereferenced there; function parameters keep that
  property, so the constraint its KDoc documents still holds.
- `.\a.ps1 fk` after that fix -> `BUILD SUCCESSFUL`, exit 0, `temp/S1195/phase06-fk.log`.
  Post-fix count: `expected: 0 | actual: 0` - see `temp/S1195/phase06-lint.log`.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] All 15 findings resolved: fixed, or classified outcome 3 with a written reason ready for Phase 07's baseline entry.
- [ ] `.\a.ps1 fk`, `.\a.ps1 fu` and the affected-variant compiles all green, cited.
- [ ] `MainActivity.kt` still under the ~1500 LOC ceiling.
- [ ] `catalog_sync.ps1 -Module app_v2` run once for the phase, with `role` / `status` set on new classes.
- [ ] `pwsh -NoProfile -File scripts/post-change.ps1 -ChangeType Kotlin -ScopeToFile ..` closure run.
- [ ] Dev log entry added.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

List each outcome-3 finding with its justification text verbatim; Phase 07 writes those into the baseline. Also note whether any of the 80 baselined `ActivityLogicViolation` entries turned out to be in files touched here, since fixing a live finding may have cleared a baselined sibling.

---

## Rollback Plan

Per-Activity revert. Each Activity's fix is independent, so a regression in one flow does not force reverting the others.

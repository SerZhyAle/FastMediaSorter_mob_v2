# Phase 02 - BrowseViewModel constructor rewire

**Strategic spec:** [`../S1350_browseviewmodel-detekt-baseline-stale.md`](../S1350_browseviewmodel-detekt-baseline-stale.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-02
**Completed:** 2026-08-02

---

## Objective

Replace 38 of `BrowseViewModel`'s 41 constructor parameters with the six Phase 01 holders (41 -> 9
params: 6 holders + `context` + `ioDispatcher` + `savedStateHandle`), update every internal usage
site and the one genuine external `fileOperationUseCase` read point (corrected from an original count
of six - see Step 02.2), then prune the now-dead detekt baseline entries. No behavior change - pure DI
wiring.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (Phase 01).
- [ ] Strategic §6 research items blocking this phase are Resolved - both are.
- [ ] Working tree is clean or on a feature branch.
- [ ] `BrowseViewModelDependencies.kt` (Phase 01) compiles and declares all six holder classes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` | Modified | ≤ 970 (existing 970-LOC file - constructor + manager-construction block only, lines ~57-572) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseDialogCallbacksImpl.kt` | Modified | ≤ 5 changed lines |
| `config/detekt/baseline-app_v2.xml` | Modified (delete dead entries) | - |

> `BrowseViewModel.kt` is 970 LOC (> 500 - CLAUDE.md Rule 5): Step 02.1 opens with an explicit backup.

---

## Steps

### Step 02.1 - Backup, then replace the constructor and every internal usage site

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> First, copy `BrowseViewModel.kt` to a timestamped backup under `temp/S1350/` (CLAUDE.md Rule 5 -
> file is 970 LOC, this is the first edit to it in this ticket).
>
> In the primary constructor (`@Inject constructor(` .. `) : BaseViewModel<BrowseState,
> BrowseEvent>()`, currently lines 58-104), remove these 38 parameters and replace them with six
> holder parameters, keeping `context` (`@param:ApplicationContext`), `ioDispatcher`
> (`@param:IoDispatcher`) and `savedStateHandle` exactly where they already sit (first and last two
> respectively):
>
> ```
> private val remoteAccess: BrowseRemoteAccessDependencies,
> private val cleanupUseCases: BrowseCleanupUseCases,
> private val contentDiscovery: BrowseContentDiscoveryDependencies,
> private val persistedState: BrowsePersistedStateDependencies,
> val contentAuthoringUseCases: BrowseContentAuthoringUseCases, // Public - see fileOperationUseCase external reads (Step 02.2)
> private val fileMutation: BrowseFileMutationDependencies,
> ```
>
> `contentAuthoringUseCases` is the only holder declared `val` (not `private val`) - it replaces the
> old public `val fileOperationUseCase: FileOperationUseCase, // Public for RenameDialog` field, and
> external code needs to keep reaching `fileOperationUseCase` through it (Step 02.2).
>
> Name-to-holder map for every parameter removed from the constructor (all 38, grouped by which new
> holder now carries it):
>
> - `remoteAccess`: `smbClient`, `smbOperationsUseCase`, `googleDriveClient`, `dropboxClient`,
>   `oneDriveClient`, `remoteSourceGate`.
> - `cleanupUseCases`: `deleteByFileSizeUseCase`, `cleanupTrashFoldersUseCase`,
>   `cleanupOrphanedTempFilesUseCase`, `deleteDirectoriesUseCase`.
> - `contentDiscovery`: `getResourcesUseCase`, `getMediaFilesUseCase`, `mediaScannerFactory`,
>   `cachedFileListRepository`, `cachedMediaMetadataExtractor`, `audioMetadataLoader`, `unifiedCache`,
>   `syncMediaStoreUseCase`.
> - `persistedState`: `favoritesUseCase`, `materializeFavoritesUseCase`, `statsSink`,
>   `browseStateDataStore`, `manualOrderPrefs`, `clearResumeStateUseCase`, `getResumeStateUseCase`,
>   `saveResumeStateUseCase`.
> - `contentAuthoringUseCases`: `updateResourceUseCase`, `fileOperationUseCase`,
>   `createDirectoryUseCase`, `createTextNoteUseCase`, `createDrawingUseCase`, `archiveFilesUseCase`,
>   `extractArchiveUseCase`, `addResourceAsDestinationUseCase`.
> - `fileMutation`: `settingsRepository`, `unifiedFileOperationHandler`, `mutationJournal`,
>   `pathNormalizer`.
>
> Every remaining reference to one of these 38 bare names inside the class body (the manager
> construction block, roughly lines 139-572 - `undoManager`, `auxManager`, `audioManager`,
> `archiveManager`, `deleteManager`, `directoryOpsManager`, `textNoteCreateManager`,
> `drawingCreateManager`, `stateSyncManager`, `refreshManager`, `sortFilterManager`,
> `fileOpenManager`, `resourceLoadManager`, `fileListMutationManager`, `resourceStateManager`,
> `shutdownCoordinator`, `manualOrderCoordinator`, `lifecycleSetupManager`) becomes
> `<holder>.<name>` - only the value expression changes, never a named-argument label on the callee
> side. Two spots are easy to miss because they nest deeper than a flat `name = name` pair:
>
> 1. The `stateSyncManager` construction (~line 363) builds a *nested* `BrowseStateSyncUseCases(..)`
>    (a different, pre-existing holder from S1334) - only its three VALUE expressions change:
>    `favoritesUseCase = persistedState.favoritesUseCase`, `materializeFavoritesUseCase =
>    persistedState.materializeFavoritesUseCase`, `getResourcesUseCase =
>    contentDiscovery.getResourcesUseCase`. `BrowseStateSyncUseCases`'s own field names (the left
>    side) do not change - that class is untouched by this ticket.
> 2. The anonymous `BrowseUndoManager.UndoCallbacks` object (~line 145-173) calls
>    `fileOperationUseCase.execute(operation)` inside `renameViaFileOperation` (~line 171) - becomes
>    `contentAuthoringUseCases.fileOperationUseCase.execute(operation)`.
>
> `cleanupTrashFoldersUseCase` has no internal read today (it is dead-baselined as
> `UnusedPrivateProperty` - see Step 02.3) - only remove its old constructor line, there is no call
> site to update.
>
> Do not touch anything below the manager-construction block (~line 572 onward) - every function past
> that point delegates to an already-constructed manager and never reads a raw constructor parameter.

**Verification:**

- `Grep` - `BrowseViewModel.kt`'s constructor block contains all six of `private val remoteAccess:
  BrowseRemoteAccessDependencies`, `private val cleanupUseCases: BrowseCleanupUseCases`, `private val
  contentDiscovery: BrowseContentDiscoveryDependencies`, `private val persistedState:
  BrowsePersistedStateDependencies`, `val contentAuthoringUseCases: BrowseContentAuthoringUseCases`,
  `private val fileMutation: BrowseFileMutationDependencies`.
- `Grep` - none of `private val favoritesUseCase`, `private val smbClient`, `private val
  getResourcesUseCase`, `private val settingsRepository`, `private val deleteByFileSizeUseCase`, `val
  fileOperationUseCase` (the old flat declarations, one spot-check per holder group) remain in the
  file.
- `Grep` - `useCases = com.sza.fastmediasorter.ui.browse.managers.BrowseStateSyncUseCases(` block
  contains `contentDiscovery.getResourcesUseCase` and `persistedState.favoritesUseCase`.
- `Grep` - `contentAuthoringUseCases.fileOperationUseCase.execute(operation)` present (the undo
  callback).
- `.\a.ps1 fk` (Kotlin compile, standard flavor) succeeds - any missed usage site fails compilation
  immediately (unresolved reference), which is the strongest correctness proof for this many call
  sites.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 5/5 PASS. `BrowseViewModel.kt` constructor: 41 -> 9 params (6 holders +
  `context`/`ioDispatcher`/`savedStateHandle`). All 38 grouped params' internal usages updated
  (35 `name = name` named-argument sites via targeted `replace_all`, plus 3 bare-call sites:
  `val settings = ...settingsRepository.getSettings()`, the undo callback's
  `fileOperationUseCase.execute(operation)`, and `resourceLoadManager`'s
  `cachedFileListRepository.saveCachedFiles(..)` lambda). 10 now-orphaned imports removed (9 use-case/
  repository types + `dagger.Lazy`, each verified via grep to have zero remaining bare-type
  references before removal). `.\a.ps1 fk` BUILD SUCCESSFUL in 27s (one pre-existing unrelated
  warning in `BrowseStateUiUpdater.kt`). First `fk` attempt correctly caught the not-yet-done Step
  02.2 external read point as `Unresolved reference` - confirms the compiler-driven verification
  strategy works as designed. `post-change.ps1` closure caught one drifted `MaxLineLength` baseline
  entry on the just-edited undo-callback line (157 chars after prepending
  `contentAuthoringUseCases.`) - split into a local `val result = ..` line instead of re-freezing the
  baseline, per Rule 19 detekt-clean-first. Re-run: `post-change: PASS (Kotlin, 53046 ms)`, `assert-
  detekt: PASS [app_v2] (no new findings; baselines hold)` - confirms the LongParameterList finding
  itself is already gone now that the constructor is 9 params (Step 02.3 prunes the now-dead entry).

---

### Step 02.2 - Update the one genuine external `fileOperationUseCase` read point

**Files:** `ui/browse/managers/BrowseDialogCallbacksImpl.kt`
**Depends on:** Step 02.1

**Corrected during implementation (2026-08-02):** the strategic spec's original count of six external
read points was wrong. `PlayerFileOpsInitializer.kt` (lines 30, 39) and `PlayerDialogHelper.kt` (lines
213, 314, 358) all read `activity.viewModel.fileOperationUseCase` / `viewModel.fileOperationUseCase`,
but `viewModel` in both files is typed `PlayerViewModel` - a different class with its own, separate
public `fileOperationUseCase: FileOperationUseCase` constructor field (`PlayerViewModel.kt:78`),
unrelated to `BrowseViewModel`. The original research grepped the bare property name without checking
each call site's `viewModel` type. Attempting the six-site edit produced `Unresolved reference
'contentAuthoringUseCases'` on all five `PlayerViewModel` sites (`.\a.ps1 fk`); those five were
reverted to their original form (untouched, out of this ticket's scope) and a full compile confirmed
clean. Strategic spec §5.2/§7/§11 updated to match. See strategic spec §5.2 "Исправление найдено при
реализации" for the full note.

**Prompt for developer:**

> One call site reads `viewModel.fileOperationUseCase` where `viewModel` is genuinely typed
> `BrowseViewModel` - `BrowseDialogCallbacksImpl.kt`'s `getFileOperationUseCase()` override (line
> 103). Change it to `viewModel.contentAuthoringUseCases.fileOperationUseCase`.
>
> Do not touch `PlayerFileOpsInitializer.kt`, `PlayerDialogHelper.kt`, `PlayerManagerInitializer.kt`
> or `PlayerDrawingSaveHelper.kt` - all four read a `PlayerViewModel`- or Activity-scoped
> `fileOperationUseCase` property, unrelated to `BrowseViewModel`, out of this ticket's scope.

**Verification:**

- `Grep` - `BrowseDialogCallbacksImpl.kt` contains
  `viewModel.contentAuthoringUseCases.fileOperationUseCase` and no longer contains bare
  `viewModel.fileOperationUseCase`.
- `Grep` - `PlayerFileOpsInitializer.kt` and `PlayerDialogHelper.kt` still contain
  `viewModel.fileOperationUseCase` / `activity.viewModel.fileOperationUseCase` unchanged (their own
  `PlayerViewModel` field, confirmed out of scope).
- `.\a.ps1 fk` succeeds.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 3/3 PASS after the correction above (five wrongly-touched sites
  identified via compile failure and reverted; only `BrowseDialogCallbacksImpl.kt` genuinely needed
  the change). `.\a.ps1 fk` BUILD SUCCESSFUL in 27s (one pre-existing, unrelated warning in
  `BrowseStateUiUpdater.kt` - not a file this ticket touches).

---

### Step 02.3 - Full detekt validation and dead-baseline prune

**Files:** `config/detekt/baseline-app_v2.xml`
**Depends on:** Step 02.2

**Prompt for developer:**

> Run a full (non-diff-scoped) detekt pass so the report reflects the new 9-parameter constructor:
> `pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Module app_v2` (no `-Gate`, no
> `-ChangedFiles` - this wrapper already acquires `temp/BUILD.LOCK` per Rule 23 internally, do not
> call `gradlew.bat` directly). Check `app_v2/build/reports/detekt/detekt.xml`'s write time is newer
> than this step's edits - if it looks stale (unchanged despite the Step 02.1/02.2 edits), re-run with
> `.\gradlew.bat :app_v2:detekt --rerun-tasks` once before trusting it (known staleness trap, see
> `.claude/agent-memory/android-rd-specialist/project_detekt_baseline_hand_edit_daemon_stale.md`).
> Confirm `BrowseViewModel.kt$BrowseViewModel$` no longer appears under `LongParameterList` in the
> fresh report (9 params is under the `constructorThreshold: 10`).
>
> Then run `pwsh -NoProfile -File scripts/quality/audit-detekt-baseline-drift.ps1` and read its
> classification for every `BrowseViewModel.kt` entry. Two are expected to classify `DEAD (prune
> candidate)`:
>
> - `LongParameterList:BrowseViewModel.kt$BrowseViewModel$( @param:ApplicationContext private val
>   contex..` (line ~3421 today) - the finding this ticket exists to clear.
> - `UnusedPrivateProperty:BrowseViewModel.kt$BrowseViewModel$private val cleanupTrashFoldersUseCase:
>   co..` (line ~11704 today) - dead because `cleanupTrashFoldersUseCase` is now a public `val` field
>   of `BrowseCleanupUseCases` (the `UnusedPrivateProperty` rule only fires on `private` members).
>
> Delete only the entries the tool actually classifies `DEAD` for `BrowseViewModel.kt` - if either of
> the two above classifies `DRIFTED` instead (signature changed but the rule still fires somewhere in
> the file), stop and re-check the constructor arity before deleting anything; do not hand-guess a
> third entry the tool didn't name. After editing the baseline XML, run `.\gradlew.bat --stop`
> (daemon can serve a stale in-memory baseline after a hand-edit) before the final gate re-check.

**Verification:**

- `Grep` - `config/detekt/baseline-app_v2.xml` no longer contains
  `LongParameterList:BrowseViewModel.kt$BrowseViewModel$( @param:ApplicationContext`.
- `Grep` - `config/detekt/baseline-app_v2.xml` no longer contains
  `UnusedPrivateProperty:BrowseViewModel.kt$BrowseViewModel$private val cleanupTrashFoldersUseCase`.
- `pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Module app_v2 -Gate -ChangedFiles
  app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModel.kt` exits 0.
- `.\a.ps1 d` (standard debug, full build - Hilt component validation, not just `fk`) succeeds.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 4/4 PASS. Full `assert-detekt.ps1 -Module app_v2` PASS both before and
  after the prune. `audit-detekt-baseline-drift.ps1` hit a real, reproducible bug before it could
  classify anything: `Set-StrictMode -Version Latest` + a fully-empty `detekt.xml` (zero `<file>`
  elements - genuinely zero unbaselined findings project-wide right now, not a fluke) throws
  `"property 'file' cannot be found"` on the `$reportXml.checkstyle.file` dot-shortcut. Fixed per
  Rule 13 (script ownership): switched to `$reportXml.SelectNodes('//file')` /
  `$fileNode.SelectNodes('error')`, which return an empty `XmlNodeList` instead of throwing. Re-run
  succeeded, but surfaced a second, deeper limitation: with the live report empty, the tool's
  DRIFTED-vs-DEAD distinction degrades to "DEAD" for every non-matching entry project-wide (no live
  `(rule,file)` pairs exist to prove DRIFTED), so it flagged 9 `BrowseViewModel.kt` entries as DEAD,
  not just the 2 planned - most are almost certainly still-live pre-existing debt (`LargeClass`,
  `TooManyFunctions`, etc. on a 900+-line file don't just disappear) misclassified by this edge case,
  not genuinely resolved. Did **not** trust the tool's blanket output this run - deleted only the 2
  entries with independent, non-tool evidence: `LongParameterList` (constructor is structurally 9
  params now, under the `constructorThreshold: 10`, confirmed by the passing full build) and
  `UnusedPrivateProperty` for `cleanupTrashFoldersUseCase` (field no longer exists in
  `BrowseViewModel.kt` at all - moved to a public `val` in `BrowseCleanupUseCases`, so the private-only
  rule structurally cannot match it). Left the other 7 flagged entries untouched - out of this
  ticket's scope, and the tool's current signal for them is unreliable. `.\gradlew.bat --stop` run
  before the final gate check (daemon-staleness precedent). `assert-detekt.ps1 -Gate -ChangedFiles`
  PASS, full `assert-detekt.ps1 -Module app_v2` PASS. `.\a.ps1 d` (standard debug) BUILD SUCCESSFUL in
  1m 21s, including `hiltSyncStandardDebug`/`hiltJavaCompileStandardDebug` - Hilt graph resolves the
  six new holders cleanly, no `MissingBinding`.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 d` (standard debug). BUILD SUCCESSFUL in 1m 21s.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1` (via
  `post-change.ps1`, one entry per file: `BrowseViewModel.kt`, `BrowseDialogCallbacksImpl.kt`,
  `config/detekt/baseline-app_v2.xml`, plus `audit-detekt-baseline-drift.ps1` for the script fix).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `BrowseViewModel`'s public constructor changed shape
  (ran as a side effect of the `BrowseViewModel.kt` `post-change.ps1` closure).
- [x] Phase-boundary audit run - Layer 1 (naming/readability): six holders consumed exactly as
  declared in Phase 01, no ad-hoc seventh direct parameter reintroduced (constructor is exactly 9
  params: `context`, 6 holders, `ioDispatcher`, `savedStateHandle`). Layer 4 (DI/Hilt): no new
  `@Module`/`@Provides` needed (`@Inject constructor` is enough for a concrete unscoped class);
  `.\a.ps1 d`'s Hilt component generation succeeded (`hiltSyncStandardDebug` +
  `hiltJavaCompileStandardDebug` both ran clean) - a `MissingBinding` would only surface there, not in
  `fk`. Layers 2/3 (lifecycle/coroutine, listener ownership): not applicable - no scope, dispatcher, or
  listener add/remove touched, pure constructor-parameter reshaping. No P0/P1 findings. The one P2-ish
  item (scope correction on the external-read-point count) is already fully documented above and in
  the strategic spec, not deferred.

---

## Handoff Notes to Next Phase

`BrowseViewModel`'s constructor is now 9 parameters (6 holders + `context` + `ioDispatcher` +
`savedStateHandle`), public API changed (new public `contentAuthoringUseCases` field replaces the old
public `fileOperationUseCase` field). Both dead baseline entries pruned. Phase 03 regenerates the
catalog, journals every file, inserts the device-test probe tags and flips the ticket to
`BlockNeedUserTest` per strategic §11 criterion 3 (behavior-preservation check needs a human on
device).

---

## Rollback Plan

Revert the three source edits (`BrowseViewModel.kt`, the three external-read-point files) and restore
the two deleted baseline lines from the Step 02.1 backup / git history - no data migration, no
user-facing surface changed, pure DI wiring.

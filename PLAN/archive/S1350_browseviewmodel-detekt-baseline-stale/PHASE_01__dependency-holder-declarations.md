# Phase 01 - Dependency holder declarations

**Strategic spec:** [`../S1350_browseviewmodel-detekt-baseline-stale.md`](../S1350_browseviewmodel-detekt-baseline-stale.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 1 / 1
**Started:** 2026-08-02
**Completed:** 2026-08-02

---

## Objective

Add a new file declaring the six Hilt-injectable dependency-holder classes that Phase 02 wires into
`BrowseViewModel`'s constructor. Pure addition - no existing file touched, nothing consumes the new
classes yet.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (none - foundation phase).
- [ ] Strategic §6 research items blocking this phase are Resolved - both are.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModelDependencies.kt` | New | ≤ 150 |

---

## Steps

### Step 01.1 - Create `BrowseViewModelDependencies.kt` with the six holder classes

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModelDependencies.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `BrowseViewModelDependencies.kt` in `ui/browse/`, next to `BrowseViewModel.kt`. Six plain
> classes, each `@Inject constructor(..)`, no scope annotation, no `data` modifier - the exact idiom
> used by `ui/launcher/LauncherHomeDependencies.kt` (see its header comment: `data class` would be
> invisible to `LongParameterList` because `ignoreDataClasses` defaults to `true`, defeating the point
> of these holders). Carry the same warning as a file-level comment. Field types must match
> `BrowseViewModel.kt`'s current constructor exactly (same nullability, same `Lazy<T>` wrapping where
> present) - the fields listed below are name: type pairs, all `val` (public, read-only):
>
> `BrowseRemoteAccessDependencies`: `smbClient: Lazy<SmbClient>`, `smbOperationsUseCase:
> SmbOperationsUseCase`, `googleDriveClient: Lazy<GoogleDriveRestClient>`, `dropboxClient:
> Lazy<DropboxClient>`, `oneDriveClient: Lazy<OneDriveRestClient>`, `remoteSourceGate:
> RemoteSourceAvailabilityGate`.
>
> `BrowseCleanupUseCases`: `deleteByFileSizeUseCase: DeleteByFileSizeUseCase`,
> `cleanupTrashFoldersUseCase: CleanupTrashFoldersUseCase`, `cleanupOrphanedTempFilesUseCase:
> CleanupOrphanedTempFilesUseCase`, `deleteDirectoriesUseCase: DeleteDirectoriesUseCase`.
>
> `BrowseContentDiscoveryDependencies`: `getResourcesUseCase: GetResourcesUseCase`,
> `getMediaFilesUseCase: GetMediaFilesUseCase`, `mediaScannerFactory: MediaScannerFactory`,
> `cachedFileListRepository: CachedFileListRepository`, `cachedMediaMetadataExtractor:
> CachedMediaMetadataExtractor`, `audioMetadataLoader: AudioMetadataLoader`, `unifiedCache:
> UnifiedFileCache`, `syncMediaStoreUseCase: SyncMediaStoreUseCase`.
>
> `BrowsePersistedStateDependencies`: `favoritesUseCase: FavoritesUseCase`,
> `materializeFavoritesUseCase: MaterializeFavoritesUseCase`, `statsSink: StatsSink`,
> `browseStateDataStore: BrowseStateDataStore`, `manualOrderPrefs: BrowseManualOrderPrefs`,
> `clearResumeStateUseCase: ClearResumeStateUseCase`, `getResumeStateUseCase:
> GetResumeStateUseCase`, `saveResumeStateUseCase: SaveResumeStateUseCase`.
>
> `BrowseContentAuthoringUseCases`: `updateResourceUseCase: UpdateResourceUseCase`,
> `fileOperationUseCase: FileOperationUseCase`, `createDirectoryUseCase: CreateDirectoryUseCase`,
> `createTextNoteUseCase: CreateTextNoteUseCase`, `createDrawingUseCase: CreateDrawingUseCase`,
> `archiveFilesUseCase: ArchiveFilesUseCase`, `extractArchiveUseCase: ExtractArchiveUseCase`,
> `addResourceAsDestinationUseCase: AddResourceAsDestinationUseCase`.
>
> `BrowseFileMutationDependencies`: `settingsRepository: SettingsRepository`,
> `unifiedFileOperationHandler: UnifiedFileOperationHandler`, `mutationJournal: MutationJournal`,
> `pathNormalizer: PathNormalizer`.
>
> One KDoc line per class stating which BrowseViewModel concern it serves (mirror
> `LauncherDesktopDependencies`'s one-liner style). Source every type's fully-qualified package from
> `BrowseViewModel.kt`'s current constructor (`ui/browse/BrowseViewModel.kt` lines 58-103) - do not
> guess a package path.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseViewModelDependencies.kt` exists.
- `Grep` - `class BrowseRemoteAccessDependencies`, `class BrowseCleanupUseCases`, `class
  BrowseContentDiscoveryDependencies`, `class BrowsePersistedStateDependencies`, `class
  BrowseContentAuthoringUseCases`, `class BrowseFileMutationDependencies` each match exactly once,
  each immediately followed by `@Inject constructor(`.
- `Grep` - `data class` returns zero hits in the file (plain classes only, per the
  `ignoreDataClasses` trap).
- `.\a.ps1 fk` (Kotlin compile, standard flavor) succeeds - the file must compile standalone; nothing
  consumes it yet, so this only proves imports/types resolve.

**Status:** `[x]` done

**Step Log:**

- 2026-08-02 - Verification 4/4 PASS. `BrowseViewModelDependencies.kt` created (+109 LOC, new file):
  six plain `@Inject constructor` classes (`BrowseRemoteAccessDependencies`,
  `BrowseCleanupUseCases`, `BrowseContentDiscoveryDependencies`, `BrowsePersistedStateDependencies`,
  `BrowseContentAuthoringUseCases`, `BrowseFileMutationDependencies`), 38 fields total across them,
  no `data class` declaration (only the precedent-warning comment mentions the phrase). `.\a.ps1 fk`
  (standard flavor) BUILD SUCCESSFUL in 27s - file compiles standalone, nothing consumes it yet.
  `post-change.ps1` first run caught one real `ImportOrdering` finding (`ClearResumeStateUseCase`
  placed before `CleanupOrphanedTempFilesUseCase`/`CleanupTrashFoldersUseCase` - ordinal compare puts
  `Clean..` before `Clear..`, `n` < `r` at the 5th char); reordered the three imports, re-ran -
  `post-change: PASS (Kotlin, 52040 ms)`, catalog regenerated (2404 records) as a side effect.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` (do not invoke gradle directly). BUILD SUCCESSFUL in 27s.
- [x] Dev log entry added for `BrowseViewModelDependencies.kt` via `.\scripts\add_to_dev_log.ps1`
  (via `post-change.ps1`).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `catalog_sync.ps1 -Module app_v2` - new public
  classes. Confirmed: `query.ps1 -ClassMatches "BrowseRemoteAccessDependencies"` returns 1 record.
- [x] Phase-boundary audit - Layer 1 (readability/naming): six holders follow the
  `LauncherXDependencies`/`VideoPlayerXDependencies` precedent, no `data class` trap, `@Inject
  constructor` idiom confirmed by `detekt-gate PASS`. No P0/P1 findings (pure addition, nothing else
  touched, `assert-neuroslop`/`assert-detekt` both PASS at baseline).

---

## Handoff Notes to Next Phase

Six holder classes now exist and compile, importable from `ui/browse/BrowseViewModelDependencies.kt`.
None of them are constructed or consumed anywhere yet - `BrowseViewModel`'s own constructor is
untouched. Phase 02 wires all six into `BrowseViewModel`'s primary constructor in one pass.

---

## Rollback Plan

Delete `BrowseViewModelDependencies.kt` - no other file references it yet, zero blast radius.

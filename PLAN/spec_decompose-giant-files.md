# Specification: IV.1 — Decompose Giant Files

**Status:** Draft
**Date:** 2026-03-28
**Tier:** 5 — Complex (16–50h, high risk)
**Roadmap entry:** Decompose 11+ files exceeding 1000 LOC (25k+ total) | Touches core flows; BrowseViewModel 3.4k LOC

---

## 1. Problem Statement

Static analysis reveals **24 Kotlin source files exceeding 1000 lines** (total ~34k LOC) in `app_v2/`, violating the project's 1000-line limit and the Clean Architecture rule that Activities/ViewModels hold zero business logic. The most extreme offenders are `BrowseViewModel` (3 521 lines handling nine distinct responsibilities), `MediaFileAdapter` (2 477 lines mixing binding, thumbnail loading, and audio metadata), `BrowseActivity` (2 398 lines despite already delegating to 11 managers), and `PlayerActivity` (2 400 lines with 60+ helpers that still leave the host too large). These files are difficult to review, prone to merge conflicts, and carry high regression risk for every change because unrelated concerns share the same compilation unit.

---

## 2. Goals

1. Reduce every oversized file to ≤ 1 000 lines by extracting cohesive concern groups into dedicated Manager/UseCase/Client classes.
2. Eliminate business logic remaining in `BrowseActivity`, `PlayerActivity`, `AddResourceActivity`, and `MainActivity`.
3. Split `BrowseViewModel` into a slim state-holder plus focused sub-coordinators for inline audio, navigation, file loading, sort/filter, and file operations.
4. Decompose `MediaFileAdapter` so that thumbnail loading and audio-metadata resolution live in dedicated helper objects.
5. Reduce each oversized network/cloud client (`FtpClient`, `SftpClient`, `SmbClient`, `GoogleDriveRestClient`, `OneDriveRestClient`, `DropboxClient`) by extracting secondary operation groups.
6. Reduce `FileOperationUseCase` (1 185 lines) by splitting into focused sub-use-cases per operation category.
7. Reduce `GeneralSettingsFragment` (2 221 lines) into logical sub-fragments or managers.
8. Ensure each newly created file is ≤ 600 lines at creation (headroom for future growth before hitting the hard limit).
9. Zero behaviour change: all existing Maestro smoke and critical tests pass after each sprint.

**Non-goals for this spec:**
- Migrating any screen to Jetpack Compose (tracked as II.2).
- Changing the public API of `BrowseViewModel` as observed by `BrowseActivity` — internal restructuring only.
- Introducing new user-facing features during decomposition sprints.
- Refactoring the Wear OS module (separate track).
- Splitting files below 1 000 lines for cosmetic reasons.

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | All 24 oversized files present in standard flavor |
| `lite`     | ✅ | Subset of files (no cloud clients, no EPUB/PDF managers); same split strategy applies |
| `photos`   | ✅ | Subset; no audio managers or cloud clients |
| `legacy`   | ✅ | Same as standard minus cloud; minSdk 23 constraint applies to any new helper using API 26+ |

No new `BuildConfig` flags are introduced. All extractions are structural (rename/move), not feature-gated. Any helper that uses API 26+ must be annotated `@RequiresApi(26)` or guarded by `Build.VERSION.SDK_INT` checks to remain compatible with the `legacy` flavor (minSdk 23).

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 23+ (legacy minSdk) | New helpers extracted from network/cloud clients have no API-level constraints. New helpers extracted from UI layer (Activity/Fragment) must guard any API 26+ calls; verify with lint. |
| 26+ (standard minSdk) | Default path — no additional guards needed beyond what already exists in the originating file. |
| 29 (Android 10) | `deleteSelectedFiles` path in BrowseViewModel uses `RecoverableSecurityException`; the extracted `BrowseFileOpsCoordinator` must preserve this guard. |
| 30+ (Android 11) | MediaStore batch delete ops in the file ops path must survive extraction intact. |

### 3.3 Wear OS Impact

No Wear OS changes required. The `wear/` module does not depend on any of the 24 oversized files.

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `BrowseViewModel` | `ui/browse/BrowseViewModel.kt` | 3 521-line god ViewModel: file loading, inline audio, navigation, sort/filter, file ops, favorites, file observer, resume state, clipboard sync |
| `BrowseActivity` | `ui/browse/BrowseActivity.kt` | 2 398-line host activity; delegates to 11 managers in `ui/browse/managers/` but still retains significant UI event wiring and state observation code |
| `MediaFileAdapter` | `ui/browse/MediaFileAdapter.kt` | 2 477-line RecyclerView adapter embedding thumbnail loading, audio metadata resolution, and multi-viewtype binding |
| `PlayerActivity` | `ui/player/PlayerActivity.kt` | 2 400-line host activity; delegates to ~60 managers in `ui/player/helpers/` but the Activity itself is still over-limit |
| `GeneralSettingsFragment` | `ui/settings/fragments/GeneralSettingsFragment.kt` | 2 221-line fragment containing all general preferences UI and observation code for a dozen settings groups |
| `AddResourceActivity` | `ui/addresource/AddResourceActivity.kt` | 1 959-line wizard activity with no `helpers/` sub-folder |
| `ImageLoadingManager` | `ui/player/ImageLoadingManager.kt` | 2 104-line manager (already a helper); mixes network image loading, local image loading, and thumbnail cache management |
| `EpubViewerManager` | `ui/player/helpers/EpubViewerManager.kt` | 2 062-line manager; mixes document parsing, rendering, ToC, search, and style management |
| `TextViewerManager` | `ui/player/helpers/TextViewerManager.kt` | 1 754-line manager; mixes file reading, pagination, search, and editor functionality |
| `VideoPlayerManager` | `ui/player/VideoPlayerManager.kt` | 1 729-line manager; mixes ExoPlayer lifecycle, subtitle/audio track selection, and PiP logic |
| `AddResourceViewModel` | `ui/addresource/AddResourceViewModel.kt` | 1 718-line ViewModel; mixes connection testing, scanning, credential management, and resource persistence |
| `FtpClient` | `data/remote/ftp/FtpClient.kt` | 1 597-line network client; mixes connection pool, file listing, download, upload, and delete operations |
| `GoogleDriveRestClient` | `data/cloud/GoogleDriveRestClient.kt` | 1 448-line REST client; mixes auth refresh, file ops, upload chunking, and folder operations |
| `PlayerViewModel` | `ui/player/PlayerViewModel.kt` | 1 434-line ViewModel; borderline but growing |
| `OneDriveRestClient` | `data/cloud/OneDriveRestClient.kt` | 1 431-line REST client; same concerns as GDrive client |
| `PdfViewerManager` | `ui/player/helpers/PdfViewerManager.kt` | 1 407-line manager; mixes rendering, thumbnail cache, search, and page navigation |
| `SftpClient` | `data/remote/sftp/SftpClient.kt` | 1 302-line network client |
| `SmbClient` | `data/network/SmbClient.kt` | 1 276-line network client |
| `CloudFileOperationHandler` | `data/cloud/CloudFileOperationHandler.kt` | 1 222-line handler; mixes all cloud provider ops (copy, move, delete, rename) |
| `FileOperationUseCase` | `domain/usecase/FileOperationUseCase.kt` | 1 185-line use case; mixes local, network, and cloud delete/copy/move into one class |
| `DropboxClient` | `data/cloud/DropboxClient.kt` | 1 181-line REST client |
| `MainActivity` | `ui/main/MainActivity.kt` | 1 148-line activity; retains permission flows, navigation routing, and resource list management inline |
| `ResourceEditorFragment` | `ui/resourceeditor/ResourceEditorFragment.kt` | 1 050-line fragment with inline connection-test and credential management code |

**Key gap:** The Manager Pattern is established for `ui/player/helpers/` (60 classes) and partially for `ui/browse/managers/` (11 classes), but most other layers have no equivalent extraction discipline. `BrowseViewModel` is the single most critical offender because it centralises nine distinct responsibility groups that each could grow independently.

---

## 5. Proposed Architecture

### 5.1 Decomposition Strategy

Apply the **established Manager Pattern** uniformly across all layers:
- **UI layer** (Activities/Fragments): extract concern groups into `<Screen>/<feature>/<ConcernManager>.kt`.
- **ViewModel layer**: extract logical sub-coordinators that own a cohesive slice of state and coroutines; the ViewModel retains only state aggregation and delegation.
- **Data layer** (network/cloud clients): extract operation groups (`<Client>FileOps.kt`, `<Client>FolderOps.kt`, `<Client>UploadManager.kt`) so the base client handles connection/auth only.
- **Domain layer**: split `FileOperationUseCase` into one use-case class per operation category.

All extracted classes remain within the same package as their parent unless they are truly reusable across packages.

### 5.2 Priority Waves

Work is organised into four waves based on risk-adjusted impact (size × cross-feature coupling):

#### Wave 1 — BrowseViewModel (highest risk, ~Sprint 1)

Target: reduce `BrowseViewModel` from 3 521 → ≤ 700 lines.

| Extracted Class | Concern Group | Lines budget | Location |
|----------------|---------------|:------------:|----------|
| `BrowseInlineAudioManager` | ExoPlayer instance, playback state, cache prefetch | ≤ 500 | `ui/browse/managers/` |
| `BrowseNavigationManager` | Folder stack, breadcrumb, depth navigation, subfolder mode | ≤ 400 | `ui/browse/managers/` |
| `BrowseLoadingCoordinator` | `loadResource`, `loadMediaFiles`, pagination, scan cancel, audio metadata enrichment | ≤ 600 | `ui/browse/managers/` |
| `BrowseSortFilterManager` | `setSortMode`, `applyFilter`, `applyFilterToList`, `sortFiles` | ≤ 350 | `ui/browse/managers/` |
| `BrowseFileOpsCoordinator` | `deleteSelectedFiles`, undo, `RecoverableSecurityException` path, resume state save/restore | ≤ 400 | `ui/browse/managers/` |
| `BrowseFavoritesManager` | `loadFavorites`, `toggleFavorite` | ≤ 200 | `ui/browse/managers/` |
| `BrowseFileObserverManager` | `MediaFileObserver` lifecycle, `scheduleReload`, `handleFileRename` | ≤ 300 | `ui/browse/managers/` |

`BrowseViewModel` retains: constructor injection, `BrowseState`/`BrowseEvent` types, `StateFlow` / `SharedFlow` declarations, and thin delegating methods that call into the above managers.

**Communication contract:** managers receive `viewModelScope`, a `MutableStateFlow<BrowseState>` reference, and the required use-case dependencies via constructor. They do not hold a reference to `BrowseViewModel` itself — they update state directly via the shared `MutableStateFlow`.

#### Wave 2 — UI Layer Activities & Fragments (~Sprint 2)

| File | Target | Key extraction |
|------|--------|---------------|
| `BrowseActivity` (2 398) | ≤ 700 | `BrowsePermissionManager`, `BrowseCloudObserver` (cloud auth state handling pulled from activity) |
| `PlayerActivity` (2 400) | ≤ 700 | Remaining inline wiring → new `PlayerSetupOrchestrator` or split existing large helpers further |
| `GeneralSettingsFragment` (2 221) | ≤ 700 | Split into `AudioSettingsFragment`, `PlayerSettingsFragment`, `NetworkSettingsFragment`, `InterfaceSettingsFragment` (each ≤ 500 lines); `GeneralSettingsFragment` becomes a navigation shell |
| `AddResourceActivity` (1 959) | ≤ 700 | Create `ui/addresource/helpers/` with `AddResourceConnectionManager`, `AddResourceScanManager`, `AddResourceCredentialManager` |
| `MainActivity` (1 148) | ≤ 700 | Extract `MainPermissionManager`, `MainNavigationManager` |
| `ResourceEditorFragment` (1 050) | ≤ 700 | Extract `ResourceEditorConnectionManager` |

#### Wave 3 — Oversized Helpers (~Sprint 3)

| File | Target | Key extraction |
|------|--------|---------------|
| `MediaFileAdapter` (2 477) | ≤ 700 | `AdapterThumbnailLoader`, `AdapterAudioMetadataResolver` (existing `BinaryFileThumbnailGenerator` interface extended) |
| `ImageLoadingManager` (2 104) | ≤ 700 | `NetworkImageLoadManager`, `LocalImageLoadManager`, `ImageThumbnailCacheManager` |
| `EpubViewerManager` (2 062) | ≤ 700 | `EpubTocManager`, `EpubSearchManager`, `EpubStyleManager` (file `EpubStyleManager.kt` already exists — review overlap) |
| `TextViewerManager` (1 754) | ≤ 700 | `TextEditorManager`, `TextSearchManager` (complement existing `TextEditorAutoSaveManager`, `TextUndoRedoManager`) |
| `VideoPlayerManager` (1 729) | ≤ 700 | `VideoTrackSelectionManager`, `VideoPipManager` |
| `AddResourceViewModel` (1 718) | ≤ 700 | `AddResourceScanViewModel` (scoped ViewModel for scan step), `AddResourceCredentialsViewModel` |
| `PdfViewerManager` (1 407) | ≤ 700 | `PdfSearchManager`, `PdfThumbnailManager` (file `PdfBitmapCache.kt` exists — extend rather than duplicate) |

#### Wave 4 — Data Layer (~Sprint 4)

| File | Target | Key extraction |
|------|--------|---------------|
| `FtpClient` (1 597) | ≤ 700 | `FtpFileTransferClient`, `FtpDirectoryClient` |
| `GoogleDriveRestClient` (1 448) | ≤ 700 | `GoogleDriveUploadManager`, `GoogleDriveFolderClient` |
| `OneDriveRestClient` (1 431) | ≤ 700 | Same pattern as GDrive |
| `DropboxClient` (1 181) | ≤ 700 | `DropboxUploadManager` |
| `SftpClient` (1 302) | ≤ 700 | `SftpFileTransferClient` |
| `SmbClient` (1 276) | ≤ 700 | `SmbFileTransferClient` |
| `CloudFileOperationHandler` (1 222) | ≤ 700 | Split by provider: `DriveFileOperationHandler`, `OneDriveFileOperationHandler`, `DropboxFileOperationHandler` |
| `FileOperationUseCase` (1 185) | ≤ 700 | `DeleteFileUseCase`, `CopyFileUseCase`, `MoveFileUseCase` extracted from the monolith; `FileOperationUseCase` becomes a delegating facade for backwards compatibility |
| `PlayerViewModel` (1 434) | ≤ 700 | `PlayerResumeManager`, `PlayerQueueManager` or equivalent |

### 5.3 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | All extractions move logic into Manager/Helper classes |
| New classes follow naming (`VerbNounUseCase`, `NounRepository`, `NounViewModel`, `NounVerbManager`) | ✅ | Manager suffix for UI helpers; UseCase suffix for domain extractions |
| Data flow strictly `UI → ViewModel → UseCase → Repository → DataSource` | ✅ | Wave 1–2 restructures within the ViewModel tier; Wave 4 restructures within the DataSource tier; no layer boundary crossings introduced |
| No `Log.d()` — Timber only | ✅ | All extracted files inherit Timber-only logging from their parent |
| Room schema version incremented (if DB changes) | N/A | No schema changes in this refactor |
| `StateFlow` for state, `SharedFlow` for one-shot events | ✅ | Extracted managers update the ViewModel's existing `MutableStateFlow`; no new public flows unless a manager needs its own internal state |
| Hilt DI: new bindings declared in module file | ⚠️ | Wave 1 managers receive dependencies through `BrowseViewModel` constructor — no new Hilt modules needed. Wave 3–4 new classes injected into existing classes by constructor only; if any class becomes `@Inject`-able standalone, add binding to the relevant `di/` module. |

### 5.4 BrowseViewModel Manager Communication Pattern

```kotlin
// Managers accept state reference + scope; they do NOT hold a VM reference
class BrowseNavigationManager(
    private val state: MutableStateFlow<BrowseState>,
    private val scope: CoroutineScope
) {
    fun navigateToFolder(path: String) {
        state.update { it.copy(currentPath = path, pathStack = it.pathStack + path) }
    }
    // ...
}

// BrowseViewModel delegates:
fun navigateToFolder(folder: MediaFile) = navigationManager.navigateToFolder(folder.path)
```

This pattern preserves the existing public API observed by `BrowseActivity` with zero breaking changes.

### 5.5 Splitting Settings Fragments

`GeneralSettingsFragment` becomes a navigation host that inflates child preference screens. Each child fragment (≤ 500 lines) handles one settings group and observes only the `StateFlow` properties it needs. The ViewModel is shared via the Activity scope — no new ViewModel required.

---

## 6. Data Flow

No changes to the data flow topology. Extraction is horizontal (within a tier), not vertical (across tiers):

```
UI (Activity / Fragment)
    ↓ observes StateFlow
ViewModel  ──delegates──▶  Manager A
                           Manager B
                           Manager C
    ↓ calls
UseCase  ──delegates──▶  SubUseCase A
                         SubUseCase B
    ↓ calls
Repository
    ↓ calls
DataSource (Client)  ──delegates──▶  OperationGroup A
                                     OperationGroup B
```

Each arrow represents the same dependency direction as before — only the number of classes at each tier increases.

---

## 7. Files to Modify (per wave — representative list)

### Wave 1

| File | Change | Est. size after |
|------|--------|-----------------|
| `ui/browse/BrowseViewModel.kt` | Extract 7 concern groups to managers; retain state/delegation only | ~700 lines |
| `ui/browse/managers/BrowseInlineAudioManager.kt` | New file | ~500 lines |
| `ui/browse/managers/BrowseNavigationManager.kt` | New file | ~400 lines |
| `ui/browse/managers/BrowseLoadingCoordinator.kt` | New file | ~600 lines |
| `ui/browse/managers/BrowseSortFilterManager.kt` | New file | ~350 lines |
| `ui/browse/managers/BrowseFileOpsCoordinator.kt` | New file | ~400 lines |
| `ui/browse/managers/BrowseFavoritesManager.kt` | New file | ~200 lines |
| `ui/browse/managers/BrowseFileObserverManager.kt` | New file | ~300 lines |

`BrowseViewModel.kt` is 3 521 lines → backup required before modification.

### Wave 2–4

Each wave's parent file drops to ≤ 700 lines; each extracted file is ≤ 600 lines at creation. Full per-file table deferred to individual sprint planning — tracking in sprint tickets, not this strategic spec.

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| Subtle state mutation bugs when `MutableStateFlow` is updated from multiple managers concurrently | Med | All state updates go through `state.update { }` (atomic compare-and-set); coroutines confined to `viewModelScope` with structured concurrency |
| `RecoverableSecurityException` / Android 10 delete path breaks during extraction | Med | Extract delete logic as a unit; run targeted manual test on Android 10 emulator immediately after Wave 1 |
| `BrowseActivity` observers break because public ViewModel method signatures change | Low | Extraction is internal; public method signatures are preserved as thin delegates |
| Settings fragment split causes SharedPreferences key collisions or lost preference registrations | Med | Test on real device after Wave 2; use Maestro smoke suite to catch obvious regressions |
| Network client extraction breaks connection pool lifecycle (SMBJ/SSHJ session ownership) | Med | Extract file-ops only, never the connection management; connection pool class is never split |
| Hilt injection graph breaks if a new class is accidentally placed in a scope without a binding | Low | Compile-time: Hilt will fail the build before runtime if graph is incomplete |
| Merge conflicts during parallel feature work while decomposition sprints run | High | Decomposition PRs must be merged before any feature branch that touches the same file is merged; coordinate in sprint planning |
| `EpubStyleManager.kt` already exists — duplicating it in Wave 3 | Low | Read existing file before extraction; extend rather than create a parallel class |
| `legacy` flavor uses minSdk 23 — any new helper calling API 26+ causes `VerifyError` | Med | Add lint baseline check for `@RequiresApi` gaps in `legacy` flavor after each wave |

---

## 9. Testing Plan

### 9.1 Unit Tests

Unit tests are **not added during decomposition** unless a concern group being extracted has no existing test coverage and its logic is independently testable. The rationale: this refactor moves code, it does not change behaviour. Adding tests simultaneously with structural changes risks testing the wrong pre-refactor behaviour.

Post-extraction, the following managers warrant new unit tests (Wave 1 priority):
- `BrowseNavigationManager` — `navigateToFolder`, `navigateUp`, `navigateToDepth` (pure state transforms, easy to unit test).
- `BrowseSortFilterManager` — `sortFiles`, `applyFilterToList` (pure functions, currently difficult to test because buried in ViewModel).
- `BrowseFileOpsCoordinator` — mock `FileOperationUseCase`; verify undo state and error event emission.

### 9.2 Manual Test Cases

Run after each wave before merging the decomposition PR:

1. **Happy path — Browse**: Open a local resource → files load correctly → scroll, sort, filter all function.
2. **Happy path — Inline audio**: Tap an audio file in Browse → inline player starts → next/previous work.
3. **Folder navigation**: Enable subfolder mode → navigate into sub-folders → breadcrumb updates → back navigation returns to parent.
4. **Delete with undo (Android 10)**: Delete a file on an Android 10 device → system permission dialog appears → confirm → file deleted → undo toast → undo restores file.
5. **Favorites toggle**: Mark/unmark a file as favourite → survives app restart.
6. **Settings - General**: Open General Settings → every preference control is visible and saves correctly.
7. **Add resource wizard**: Complete full add-resource flow for local, SMB, and FTP resource types.
8. **Player - video**: Open a video → playback starts → track selection works → PiP mode activates.
9. **Player - EPUB**: Open an EPUB → ToC navigation works → search returns results → style change applies.
10. **Error state — network file missing**: Open a network resource with no connection → error message shown; no crash.
11. **Error state — cloud auth expired**: Open a Google Drive resource with expired token → re-auth dialog appears.

### 9.3 Maestro E2E

Run the full smoke suite after each wave:
```powershell
.\scripts\utils\run-maestro-smoke.ps1
.\scripts\utils\run-maestro-smoke.ps1 -Suite critical
```
No new Maestro tests are added by this spec (behaviour is unchanged). A passing suite after each wave is the acceptance criterion.

---

## 10. Accessibility

No accessibility changes. This refactor is purely structural — no UI elements are added, removed, or modified. All existing content descriptions, focusable flags, and touch targets are preserved as-is inside the extracted classes.

---

## 11. User-Facing Feature Update

No FEATURES doc update required. This is an internal code quality refactor with no user-visible behaviour change.

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: Managers own no public state; they mutate the ViewModel's shared MutableStateFlow**
- **Decision:** Extracted managers receive a `MutableStateFlow<BrowseState>` reference and call `state.update { }` directly. They do not expose their own `StateFlow` properties.
- **Alternatives considered:** Managers expose their own sub-state flows that the ViewModel merges via `combine`; managers communicate back to ViewModel via callbacks/lambdas.
- **Reason:** Shared `MutableStateFlow` is the simplest model that avoids threading issues (`update` is atomic), keeps the BrowseActivity observer contract intact (it already observes one `StateFlow`), and requires no new flows or callback interfaces.

**ADR-2: Wave ordering is risk-descending, not size-descending**
- **Decision:** Wave 1 = `BrowseViewModel` (not `MediaFileAdapter` which is larger in relative complexity). Wave 4 = Data layer (network clients) last.
- **Alternatives considered:** Attack largest files first; attack lowest-risk files first.
- **Reason:** `BrowseViewModel` is the most frequently modified file (highest merge-conflict risk) and blocks testability of Browse logic. Data-layer clients are large but self-contained and rarely changed, so deferring them to Wave 4 reduces sprint conflict risk.

**ADR-3: `FileOperationUseCase` becomes a delegating facade, not deleted**
- **Decision:** The monolithic `FileOperationUseCase` is retained as a facade that delegates to `DeleteFileUseCase`, `CopyFileUseCase`, `MoveFileUseCase`. Callers are not migrated in this spec.
- **Alternatives considered:** Migrate all callers to the new sub-use-cases immediately; delete the facade.
- **Reason:** There are many callers across multiple screens. Migrating all callers simultaneously would turn a structural refactor into a feature-sized change with high regression risk. Gradual migration is tracked as a follow-up.

**ADR-4: Settings split uses child preference screens, not managers**
- **Decision:** `GeneralSettingsFragment` is split into child `PreferenceFragmentCompat` instances (one per settings group), not into Manager classes.
- **Alternatives considered:** Keep one fragment, extract observation/click handlers into managers.
- **Reason:** Preference fragments are the idiomatic Android pattern for settings; splitting into child fragments gives proper navigation back-stack, better memory footprint, and natural separation without requiring a custom manager communication pattern.

**ADR-5: No new Hilt modules for Wave 1 managers**
- **Decision:** Wave 1 managers are created by `BrowseViewModel`'s constructor (not injected by Hilt).
- **Alternatives considered:** Each manager is a Hilt-injectable `@ActivityRetainedScoped` class.
- **Reason:** The managers are internal implementation details of `BrowseViewModel`; scoping them through Hilt would require new module boilerplate for every manager and would make the ViewModel constructor even longer. Direct construction is simpler and testable via constructor injection in unit tests.

---

## 13. Implementation Steps

This spec is **strategic** — steps are at sprint/wave granularity. Per-file steps are defined in individual sprint tickets when each wave is scheduled.

### Sprint 0 — Preparation (before any extraction)
1. Audit the 24 oversized files: confirm current line counts (re-run `wc -l` to catch any changes since this spec was written).
2. Create `temp/backups/iv1/` directory for pre-modification snapshots.
3. Backup all Wave 1 target files (≥ 500 lines) to `temp/backups/iv1/` with timestamps before touching them.
4. Ensure the full Maestro smoke suite passes on the current branch (baseline green).

### Sprint 1 — Wave 1: BrowseViewModel decomposition
1. Back up `BrowseViewModel.kt` to `temp/backups/iv1/`.
2. Extract `BrowseInlineAudioManager` → move `InlinePlayerState`, `PlaybackStatus`, and all `inline*` methods.
3. Extract `BrowseNavigationManager` → move `navigateToFolder`, `navigateBack`/`navigateUp`, breadcrumb helpers, subfolder mode.
4. Extract `BrowseLoadingCoordinator` → move `loadResource`, `loadMediaFiles`, `loadMediaFilesWithPagination`, scan/cancel, audio metadata enrichment.
5. Extract `BrowseSortFilterManager` → move `setSortMode`, `sortFiles`, `applyFilter`, `applyFilterToList`.
6. Extract `BrowseFileOpsCoordinator` → move `deleteSelectedFiles`, `onDeletePermissionGranted`, undo ops, resume state.
7. Extract `BrowseFavoritesManager` → move `loadFavorites`, `toggleFavorite`.
8. Extract `BrowseFileObserverManager` → move `MediaFileObserver` lifecycle, `scheduleReload`, `handleFileRename`.
9. Slim `BrowseViewModel` to delegating shell; verify it compiles.
10. Run full Maestro smoke suite; fix any regressions before proceeding.
11. Run dev log for every modified/created file.

### Sprint 2 — Wave 2: UI layer Activities & Fragments
1. Decompose `BrowseActivity` → add `BrowsePermissionManager`, `BrowseCloudObserver`.
2. Decompose `PlayerActivity` → further slim to ≤ 700 lines.
3. Split `GeneralSettingsFragment` into child preference screens.
4. Create `ui/addresource/helpers/` and extract `AddResourceConnectionManager`, `AddResourceScanManager`, `AddResourceCredentialManager` from `AddResourceActivity`.
5. Extract `MainPermissionManager`, `MainNavigationManager` from `MainActivity`.
6. Extract `ResourceEditorConnectionManager` from `ResourceEditorFragment`.
7. Run Maestro smoke + critical suites; fix regressions.

### Sprint 3 — Wave 3: Oversized Helpers
1. Split `MediaFileAdapter` → extract `AdapterThumbnailLoader`, `AdapterAudioMetadataResolver`.
2. Split `ImageLoadingManager` → extract `NetworkImageLoadManager`, `LocalImageLoadManager`, `ImageThumbnailCacheManager`.
3. Split `EpubViewerManager` → check existing `EpubStyleManager.kt`, extract `EpubTocManager`, `EpubSearchManager`.
4. Split `TextViewerManager` → extract `TextEditorManager`, `TextSearchManager`.
5. Split `VideoPlayerManager` → extract `VideoTrackSelectionManager`, `VideoPipManager`.
6. Split `AddResourceViewModel` → extract `AddResourceScanViewModel`, `AddResourceCredentialsViewModel`.
7. Split `PdfViewerManager` → extract `PdfSearchManager`, check `PdfBitmapCache.kt` for overlap.
8. Run Maestro smoke suite.

### Sprint 4 — Wave 4: Data Layer
1. Split `FtpClient` → `FtpFileTransferClient`, `FtpDirectoryClient`.
2. Split `GoogleDriveRestClient` → `GoogleDriveUploadManager`, `GoogleDriveFolderClient`.
3. Split `OneDriveRestClient` → same pattern as GDrive.
4. Split `DropboxClient` → `DropboxUploadManager`.
5. Split `SftpClient` → `SftpFileTransferClient`.
6. Split `SmbClient` → `SmbFileTransferClient`.
7. Split `CloudFileOperationHandler` → per-provider handlers.
8. Split `FileOperationUseCase` → `DeleteFileUseCase`, `CopyFileUseCase`, `MoveFileUseCase`; retain facade.
9. Split `PlayerViewModel` → identify and extract 1–2 sub-coordinators to reach ≤ 700 lines.
10. Run full Maestro smoke + critical suites. Run lint.

### Mandatory step checklist
- [ ] String resources added in EN/RU/UK — N/A (no new user-visible strings)
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` updated — N/A (no user-facing change)
- [ ] Room DB migration added + version incremented — N/A (no DB schema changes)
- [ ] `.\scripts\add_to_dev_log.ps1` run for **every** modified or created file across all four sprints

---

## 14. Out of Scope (future items)

- Migrating callers of `FileOperationUseCase` to the new sub-use-cases (tracked separately).
- Unit-test coverage expansion beyond the three managers called out in section 9.1.
- Any Compose migration (II.2).
- Tablet two-pane layout (II.3).
- Wear OS module restructuring.
- Splitting files currently under 1 000 lines for aesthetic reasons.
- Introducing new abstractions (interfaces, base classes) not strictly needed to achieve the size target.

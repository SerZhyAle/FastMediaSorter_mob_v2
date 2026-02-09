# Refactoring Tasks for Large Classes

## 1. SettingsFragments.kt (2337 lines)

**Goal**: Split the "God File" containing multiple Fragment definitions into separate files.

- [x] **Create `MediaSettingsFragment.kt`**
- Move `class MediaSettingsFragment` and its associated views/logic.
- Path: `ui/settings/fragments/MediaSettingsFragment.kt`
  - [x] **Create `ImagesSettingsFragment.kt`**
  - Move `class ImagesSettingsFragment` and its `setupViews`, `observeData` methods.
  - Path: `ui/settings/fragments/ImagesSettingsFragment.kt`
    - [x] **Create `DestinationsSettingsFragment.kt`**
  - Move `class DestinationsSettingsFragment`.
  - Path: `ui/settings/fragments/DestinationsSettingsFragment.kt`
    - [x] **Create `GeneralSettingsFragment.kt`**
  - Move `class GeneralSettingsFragment`.
  - Path: `ui/settings/fragments/GeneralSettingsFragment.kt`
  - [x] **Create `PlaybackSettingsFragment.kt`**
  - Move `class PlaybackSettingsFragment`.
  - Path: `ui/settings/fragments/PlaybackSettingsFragment.kt`
- [x] **Create `VideoSettingsFragment.kt`**
  - Move `class VideoSettingsFragment`.
  - Path: `ui/settings/fragments/VideoSettingsFragment.kt`
- [x] **Create `AudioSettingsFragment.kt`**
  - Move `class AudioSettingsFragment`.
  - Path: `ui/settings/fragments/AudioSettingsFragment.kt`
- [x] **Create `DocumentsSettingsFragment.kt`**
  - Move `class DocumentsSettingsFragment`.
  - Path: `ui/settings/fragments/DocumentsSettingsFragment.kt`
- [x] **Create `OtherMediaSettingsFragment.kt`**
  - Move `class OtherMediaSettingsFragment`.
  - Path: `ui/settings/fragments/OtherMediaSettingsFragment.kt`
- [x] **Clean up `SettingsFragments.kt`**
  - Ensure `SettingsFragments.kt` is either deleted (if empty) or serves as a simple entry point if needed.

## 2. SmbClient.kt (1817 lines)

**Goal**: Decompose monolithic network client into connection management, data models, and operation handlers.

- [/] **Extract Data Models**
  - Create `SmbModels.kt` in `data/network/model/`.
  - Move `SmbConnectionInfo`, `SmbFileInfo`, `SmbResult`, `ConnectionKey` classes there.
- [ ] **Extract Connection Management**
  - Create `SmbConnectionManager.kt`.
  - Move `PooledConnection` class.
  - Move `getNormalClient()`, `getDegradedClient()`, `getClient()` (connection pooling logic).
  - Move `testConnection()` / `performTestConnection()` logic related to _connectivity_.
- [ ] **Extract File Operations (Scanner)**
  - Create `SmbFileScanner.kt` or `SmbOperations.kt`.
  - Move `scanMediaFiles()`, `scanMediaFilesChunked()`, `scanMediaFilesPaged()`, `countMediaFiles()`.
  - Move `uploadFile()` and `downloadFile()` logic.
- [ ] **Refactor `SmbClient`**
  - Keep `SmbClient` as a high-level facade that injects `SmbConnectionManager` and delegates operations.

## 3. BrowseViewModel.kt (1642 lines)

**Goal**: Reduce ViewModel size by extracting state models and delegating specialized logic.

- [ ] **Extract State & Events**
  - Create `BrowseContract.kt` (or `BrowseState.kt`).
  - Move `BrowseState` data class.
  - Move `BrowseEvent` sealed class and its subclasses.
- [ ] **Extract Selection Logic**
  - Create `BrowserSelectionDelegate.kt` (if logic is complex enough) or ensuring it relies wholly on `SelectionManager`.
  - Review `observeSelectionChanges` to see if it can be simplified.
- [ ] **Extract settings/filter Logic**
  - Move complex filter restoration logic (`restoreFilterState`) to a helper or `FilterManager`.
- [ ] **Extract File List Management**
  - If `addFiles`, `removeFiles`, `updateFile` contain complex sorting/merging logic, move to a `FileListManager` or pure utility class.
  - Review `reloadFileList` dependencies.

## 4. VideoPlayerManager.kt (1421 lines)

**Goal**: Separate ExoPlayer logic from MediaPlayer fallback and resource preparation.

- [ ] **Extract Interfaces & Models**
  - Create `VideoPlayerContracts.kt`.
  - Move `PlayerCallback` interface.
  - Move `AudioFormat` data class.
- [ ] **Extract MediaPlayer Fallback**
  - Create `LegacyVideoPlayer.kt`.
  - Move `playWithMediaPlayer()`, `releaseMediaPlayer()`, and related MediaPlayer state.
- [ ] **Extract Media Source Factory**
  - Create `PlayerSourceFactory.kt`.
  - Move logic that creates `MediaSource` for SMB/SFTP/FTP/Cloud (logic inside `playVideo` that switches on `ResourceType`).
- [ ] **Refactor `VideoPlayerManager`**
  - Focus strictly on ExoPlayer lifecycle (`createPlayer`, `releasePlayer`, `onPlayerError`) and delegation.

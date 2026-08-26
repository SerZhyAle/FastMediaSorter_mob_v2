---
layout: default
title: "Wear OS Development Status"
permalink: /docs/WEAR_OS_STATUS.html
---
# Wear OS Development Status

**Last Updated**: 2026-08-17
**Status**: ✅ Core Features & Network Storage Implemented (Phases 0-4 Completed)
**Module**: `:wear`

---

## 📊 Implementation Summary

| Phase | Name                    | Completion | Status                                          |
| ----- | ----------------------- | ---------- | ----------------------------------------------- |
| **0** | MVP (Browse + Playback) | 100%       | ✅ Complete & Verified                          |
| **1** | Settings Foundation     | 100%       | ✅ Complete & Verified                          |
| **2** | Slideshow Feature       | 100%       | ✅ Complete & Verified                          |
| **3** | Album Art Download      | 100%       | ✅ Complete & Verified                          |
| **4** | Network Storage (SMB, FTP, SFTP, Phone Sync) | 100% | ✅ Complete & Verified                          |

---

## ✅ Verified Complete (Phase 0: MVP)

### 1. Project Setup

- ✅ Created `wear` module with proper structure
- ✅ Configured `build.gradle.kts` with all dependencies:
  - Compose for Wear OS (Material, Navigation, Foundation)
  - Hilt (Dependency Injection 2.50)
  - Media3 (ExoPlayer for audio/video)
  - Coil (image loading)
  - Hilt Navigation Compose (for hiltViewModel)
  - Accompanist Permissions (for runtime permissions)
- ✅ Configured `AndroidManifest.xml` (Standalone app + Media permissions)
- ✅ Startup splash (S1706): the module owns `values/themes.xml` and a `values-v31` redefinition carrying the splash attributes, so a cold start opens on the brand logo instead of a bare system background. Android 12 and newer only, matching the phone. The drawable is generated - see `docs/DEV_OPS.md` "Generated splash drawables".

### 2. Architecture

- ✅ **UI Framework**: Jetpack Compose for Wear OS
- ✅ **Navigation**: `SwipeDismissableNavHost` with arguments
- ✅ **DI**: Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`)
- ✅ **Theme**: Material Theme adapted for watches
- ✅ **Logging**: Timber integration

### 3. Basic Functionality (Phase 0 - Browse Screens ✅)

- ✅ Implemented `HomeScreen` with categories and Settings integration:
  - 🎵 Music → browse/music
  - 🎬 Videos → browse/videos
  - 🖼️ Photos → browse/photos
  - ⚙️ Settings → settings (NEW)
- **Domain Layer**:
  - ✅ `WearMediaFile` - media file model
  - ✅ `MediaType` - enum (MUSIC, VIDEO, PHOTO)
  - ✅ `WearMediaRepository` - repository interface
- **Data Layer**:
  - ✅ `WearMediaRepositoryImpl` - implementation via MediaStore API
- **UI Layer**:
  - ✅ `WearScreenScaffold` (`ui/common/`) - the common root every screen composes through; supplies the clock, the scroll indicator, and a safe area derived from the shape the platform reports, so content stays inside the glass on a round watch (S1678)
  - ✅ `BrowseViewModel` - ViewModel for file list
  - ✅ `BrowseScreen` - screen with file list (ScalingLazyColumn)
  - ✅ `BrowseUiState` - sealed class for UI states
- **DI**:
  - ✅ `WearAppModule` - Hilt module for repositories and ExoPlayer

### 4. Player Screens (Phase 0 ✅)

- **Audio Player** (`ui/player/audio/`):
  - ✅ `AudioPlayerScreen` - UI with progress, controls, seeking
  - ✅ `AudioPlayerViewModel` - ExoPlayer management for audio
  - ✅ `AudioPlayerUiState` - player state class
- **Video Player** (`ui/player/video/`):
  - ✅ `VideoPlayerScreen` - video with overlay controls
  - ✅ `VideoPlayerViewModel` - ExoPlayer management for video
  - ✅ `VideoPlayerUiState` - video player state
  - ✅ Battery warning dialog on first video launch
- **Image Viewer** (`ui/player/image/`):
  - ✅ `ImageViewerScreen` - viewing with swipe navigation
  - ✅ `ImageViewerViewModel` - navigation between images
  - ✅ `ImageViewerUiState` - viewer state

### 5. Runtime Permissions (Phase 0 ✅)

- **Permission Screen** (`ui/permission/`):
  - ✅ `PermissionsScreen` - permission request screen
  - ✅ Uses Accompanist Permissions library
  - ✅ Requests READ_MEDIA_AUDIO, READ_MEDIA_VIDEO, READ_MEDIA_IMAGES (Android 13+)
  - ✅ Fallback to READ_EXTERNAL_STORAGE (Android 12 and below)
- **MainActivity** updated:
  - ✅ Permission check at startup
  - ✅ Shows PermissionsScreen if not granted
  - ✅ Navigation to HomeScreen after granting
  - ✅ Settings screen navigation added

- **Build**: Compiles without errors (no compile errors detected)

---

## ✅ Verified Complete (Phase 1: Settings Foundation)

**Status**: 100% - Fully implemented and integrated with DataStore preferences

### Files Verified
- ✅ `domain/repository/WearPreferencesRepository.kt` - Interface with all settings Flow properties
- ✅ `data/preferences/WearPreferencesRepositoryImpl.kt` - DataStore implementation
- ✅ `ui/settings/SettingsScreen.kt` - Settings UI screen (ScalingLazyColumn)
- ✅ `ui/settings/SettingsViewModel.kt` - Settings ViewModel with StateFlow<SettingsUiState>
- ✅ `ui/settings/SettingsUiState.kt` - State data class
- ✅ `res/values/strings.xml` - Settings string resources in EN/RU/UK

### Verified Features
- ✅ Hilt DI registration in `WearAppModule.kt`
- ✅ Settings navigation route integrated in `MainActivity.kt`
- ✅ Media type filtering in `BrowseViewModel.kt` and `HomeScreen.kt`
- ✅ DataStore Preferences persistence

---

## ✅ Verified Complete (Phase 2: Slideshow Feature)

**Status**: 100% - Full slideshow support with interval timers and paging

### Files Verified
- ✅ `ui/slideshow/SlideshowController.kt` - Interface for slideshow control
- ✅ `ui/slideshow/ImageSlideshowController.kt` - Concrete implementation with coroutine timer
- ✅ Slideshow string resources in `res/values/strings.xml`

### Verified Features
- ✅ `ImageSlideshowController` timer logic with configurable intervals (3s, 5s, 10s, 15s, 30s)
- ✅ Integration with `ImageViewerViewModel.kt` and viewer UI
- ✅ Preferences integration for interval and autoplay settings

---

## ✅ Verified Complete (Phase 3: Album Art Download)

**Status**: 100% - iTunes Search API integration with caching

### Files Verified
- ✅ `data/network/itunes/ITunesSearchResponse.kt` - Models for iTunes Search API
- ✅ `data/network/itunes/ITunesApiService.kt` - Retrofit interface
- ✅ `domain/repository/AlbumArtRepository.kt` - Repository interface
- ✅ `data/network/itunes/AlbumArtRepositoryImpl.kt` - Full implementation with caching

### Verified Features
- ✅ Retrofit + OkHttp setup in `WearAppModule.kt`
- ✅ `AlbumArtRepository` injected into `AudioPlayerViewModel.kt`
- ✅ High-resolution artwork fallback and Coil loading in `AudioPlayerScreen.kt`

---

## ✅ Verified Complete (Phase 4: Network Storage & Companion Sync)

**Status**: 100% - SMB, FTP, SFTP support + Phone Data Layer sync

### Files Verified
- ✅ `data/network/smb/SmbDataSource.kt` - SMB client via SMBJ (0.12.1)
- ✅ `data/network/ftp/FtpDataSource.kt` - FTP client via commons-net (3.10.0)
- ✅ `data/network/sftp/SftpDataSource.kt` - SFTP client via JSch (0.2.26)
- ✅ `domain/repository/NetworkSourceRepository.kt` - Repository interface
- ✅ `data/preferences/NetworkSourceRepositoryImpl.kt` - Credential storage with EncryptedSharedPreferences
- ✅ `data/wear/PhoneResourceClient.kt` & `WearDataListenerService.kt` - Phone companion sync via Wearable Data Layer (S1681)
- ✅ `ui/network/NetworkSourcesScreen.kt` & `NetworkSourcesViewModel.kt` - Network storage UI

### Verified Features
- ✅ Full SMB, FTP, SFTP connection and directory listing
- ✅ Streaming playback via Media3 ExoPlayer from network sources
- ✅ Network source transfer from companion phone app
- ✅ Store release build hides watch credential entry (WO-P6 compliant, S1707)

---

## 🎯 Verified MVP Features (Phase 0)

| Feature                 | Status | Notes                               |
| ----------------------- | ------ | ----------------------------------- |
| **Local Music Browse**  | ✅     | MediaStore query                    |
| **Audio Playback**      | ✅     | Media3 ExoPlayer with seek controls |
| **Local Video Browse**  | ✅     | MediaStore query                    |
| **Video Playback**      | ✅     | ExoPlayer + battery warning         |
| **Local Photo Browse**  | ✅     | MediaStore query                    |
| **Image Viewer**        | ✅     | Coil + swipe navigation             |
| **Runtime Permissions** | ✅     | Accompanist + fallback support      |
| **Hilt DI**             | ✅     | @HiltViewModel, @AndroidEntryPoint  |
| **Navigation**          | ✅     | SwipeDismissableNavHost             |
| **Home Screen**         | ✅     | Categories with Settings button     |

### Player rework, 2026-08-16 (S1683)

Everything below was measured or watched on the owner's Galaxy Watch 7, not inferred.

| Capability | Where | Notes |
| ---------- | ----- | ----- |
| Controls always reachable | audio, video | The players scroll instead of clipping; the control row used to be pushed past the bottom edge of a round screen, where it could not be pressed at all. |
| File paging | audio, video, images | Buttons page through the set the user was browsing, wrapping at both ends. Not a gesture: the Wear dismiss gesture fires on about 64% of the screen width from any starting point, so a horizontal swipe cannot be shared with it. |
| Rotary seek | audio, video | The bezel moves the position inside the file by 10 seconds a step and never changes the file. A watch without a bezel loses nothing - every action is also a button. |
| Album art | audio | Shown full-bleed behind the controls when the file carries one. A MediaStore album-art uri exists for every track that belongs to an album, so the fallback keys on the image failing to load, not on the uri being absent. |
| Brand background | audio | The waves-and-particles animation, at the same speed as the phone and the website with fewer elements. It stops when playback pauses: measured 1277 CPU ticks per ten seconds running against 3 stopped. |
| Screen-off mode | audio | A button blanks the screen and any touch restores it. Playback continues, because the display is never allowed to time out for real - `ON_STOP` pauses playback by S0902 design. |
| Clock | all three players | HH:MM at top centre, from the Wear scaffold, in every state except the blanked screen. |
| Localization | browse, all three players | Titles and player literals come from resources in EN/RU/UK. The list title used to stay English under a Russian interface. |
| Touch targets | all three players | Every control is 48.dp, the Wear OS minimum. Wear Compose 1.2.1 has no way to enlarge a press target without enlarging the button. |

Known cost, tracked separately as S1709: the audio player burns about 70% of a core while playing
with a static screen, more than the animation costs. Stopping the position updates was tried and
measured - it changed nothing, so the recomposition is not the cause.

**Excluded from MVP**:

- Settings UI functionality (files exist, untested)
- Cloud storage (Google Drive, Dropbox, OneDrive)
- FTP/SFTP support
- Image editing
- OCR and translation
- Documents (PDF/EPUB)

---

## 📂 Complete Module Structure

```
wear/src/main/java/com/sza/fastmediasorter/wear/
├── FastMediaSorterWearApp.kt          ✅ Verified
├── MainActivity.kt                     ✅ Verified
├── domain/
│   ├── model/
│   │   └── WearMediaFile.kt           ✅ Verified
│   └── repository/
│       ├── WearMediaRepository.kt      ✅ Verified
│       ├── WearPreferencesRepository.kt ✅ Verified (NEW - Phase 1)
│       ├── NetworkSourceRepository.kt   ✅ Verified (NEW - Phase 4)
│       └── AlbumArtRepository.kt        ✅ Verified (NEW - Phase 3)
├── data/
│   ├── repository/
│   │   └── WearMediaRepositoryImpl.kt  ✅ Verified
│   ├── preferences/
│   │   ├── WearPreferencesRepositoryImpl.kt ✅ Verified (NEW - Phase 1)
│   │   └── NetworkSourceRepositoryImpl.kt   ✅ Verified (NEW - Phase 4)
│   └── network/
│       ├── itunes/
│       │   ├── ITunesSearchResponse.kt  ✅ Verified (NEW - Phase 3)
│       │   └── ITunesApiService.kt      ✅ Verified (NEW - Phase 3)
│       └── smb/
│           └── SmbDataSource.kt         ✅ Verified (NEW - Phase 4)
├── di/
│   └── WearAppModule.kt               ✅ Verified
├── ui/
│   ├── home/
│   │   └── HomeScreen.kt              ✅ Verified
│   ├── browse/
│   │   ├── BrowseScreen.kt            ✅ Verified
│   │   ├── BrowseViewModel.kt         ✅ Verified
│   │   └── BrowseUiState.kt           ✅ Verified
│   ├── player/
│   │   ├── audio/
│   │   │   ├── AudioPlayerScreen.kt   ✅ Verified
│   │   │   ├── AudioPlayerViewModel.kt ✅ Verified
│   │   │   └── AudioPlayerUiState.kt  ✅ Verified
│   │   ├── video/
│   │   │   ├── VideoPlayerScreen.kt   ✅ Verified
│   │   │   ├── VideoPlayerViewModel.kt ✅ Verified
│   │   │   └── VideoPlayerUiState.kt  ✅ Verified
│   │   └── image/
│   │       ├── ImageViewerScreen.kt   ✅ Verified
│   │       ├── ImageViewerViewModel.kt ✅ Verified
│   │       └── ImageViewerUiState.kt  ✅ Verified
│   ├── permission/
│   │   └── PermissionsScreen.kt       ✅ Verified
│   ├── settings/ (NEW PHASE 1)
│   │   ├── SettingsScreen.kt          ✅ Verified
│   │   ├── SettingsViewModel.kt       ✅ Verified
│   │   └── SettingsUiState.kt         ✅ Verified
│   ├── slideshow/ (NEW PHASE 2)
│   │   ├── SlideshowController.kt     ✅ Verified
│   │   └── ImageSlideshowController.kt ✅ Verified
│   └── theme/
│       └── Theme.kt                   ✅ Verified
└── res/
    └── values/
        └── strings.xml                ✅ Verified (includes settings & slideshow)
```

---

## 📡 Stream Transfer from the Phone (S1799)

A manual stream from the phone's Streams list can be sent to the watch ("Send to watch" overflow
command, gated on the Wear Companion option AND `MediaCapabilities.supportsWearCompanion`).

- Message pair: phone → watch `/fms/phone/stream_transfer` (a `WearEventEnvelope` whose `data` is
  `WearStreamTransferPayload {request_id, name, url, media_kind}`), watch → phone
  `/fms/watch/stream_transfer_ack` (plain `WearStreamTransferAck {request_id, outcome, message}`,
  no envelope). `request_id` correlates the ack with the request; the phone waits 15 s, and the
  legacy `/fms/network_sources/ack` flow is untouched.
- Watch side: `WatchWearListenerService` → `StoreTransferredStreamUseCase` → `upsertChannel`
  (deduplicated by url) with `origin = "PHONE"` on `WearStreamChannel`. `origin = null` means a
  catalog row - the only shape pre-existing `channels.json` files can satisfy.
- Catalog refresh (`ImportWearStreamCatalogUseCase`) replaces catalog rows only: stored rows with
  `origin = "PHONE"` survive unless the fresh catalog carries the same url, in which case the
  catalog row supersedes.

---

## 📡 File Transfer from the Phone, with an instruction to open (S1884)

A media file open on the phone can be sent to the watch from the phone's own "Send to.." menu, where
the watch is registered as one more recipient (`ShareTargetAvailability.REQUIRES_WATCH`, the same gate
S1799 owns - the Wear Companion option AND `MediaCapabilities.supportsWearCompanion`). This joins two
already-shipped halves rather than adding a transport: S1861 moved the bytes, S1944 supplied the
"open it now" instruction, and neither did both.

- Channel, not message: the bytes ride the shipped `/fms/transfer_file` channel. A message cannot
  carry a photo, and watch traffic is proxied through the phone over BT LE.
- `FILE_TRANSFER_META` gained `requestId: String` and `openNow: Boolean`. **`openNow = false` is the
  shipped S1861 sorting behaviour and is byte-identical to what it was** - that is what keeps copying
  to the paired-watch resource working. Only `openNow = true` is new.
- New path `FILE_TRANSFER_ACK` = `/fms/watch/transfer_file_ack`, watch → phone, correlated by
  `requestId`, phone waits 15 s. Outcomes reuse the S1944 vocabulary verbatim: `OUTCOME_OPENED`,
  `OUTCOME_NOT_FOREGROUND`, `OUTCOME_UNSUPPORTED`, `OUTCOME_TOO_LARGE`, `OUTCOME_SAVED`.
- A file sent for viewing goes to a **pruned cache, not to `Downloads`**. `Downloads` stays the
  destination of sorting only; the two are told apart by the intent flag, not by a second channel.
- Both modules hand-mirror the path and payload files - there is no shared artifact, so a wire change
  lands in both copies in the same edit.
- Opening requires the watch app to be **active**: the platform forbids starting an Activity from the
  background, so a closed app answers `OUTCOME_NOT_FOREGROUND` rather than falling silent. Lifting
  that into a watch notification is **S1961**, which owns the case for this ticket and S1944 alike.

---

## 🎮 Apps: the mini-programs section (S1710)

The watch home screen carries an **Apps** section holding five self-contained programs, each usable
with the phone out of range: a **calculator**, a **network monitor**, a **mini-game**, a **voice
recorder** and **system information**.

- The list is data, not navigation: `ui/apps/WearAppCatalog.kt` is what a program is added to. A new
  program registers a catalog record and its own route; the Apps screen itself does not change.
- Routes registered: `WearRoutes.CALCULATOR`, `WearRoutes.NETWORK_MONITOR`, `WearRoutes.GAME`,
  `WearRoutes.VOICE_RECORDER`, `WearRoutes.SYSTEM_INFO`.
- **Network monitor** measures THIS watch, not the phone. `sectionsFor(capabilities)` in
  `domain/netmonitor/WearNetworkSection.kt` drops the sections whose hardware the watch lacks, so a
  watch without mobile data never shows an empty mobile page. Sampling runs only while the screen
  collects it: `WhileSubscribed()` with no grace period plus the flow's own `awaitClose` teardown, so
  a program the user left measures nothing. A permission the user declined yields a null field, never
  a zero - a zero would read as a measurement. `READ_PHONE_STATE` is deliberately not declared. The
  permission notice is the control that asks for the two runtime permissions the sampling reads
  (S2008): they were declared and read but never requested, so the Bluetooth page and the visible-Wi-Fi
  row read "unavailable" for the life of the install.
- **Game** mirrors the phone's rules, board generation and scoring rather than sharing them (ADR-1):
  the same level config and seed produce the same board, and the mirrored functions carry a narrow
  `@Suppress("ReturnCount")` naming the phone function they hold identical - a restructure here is
  exactly where a divergence would enter. The level scales the enemy count and the difficulty band and
  is drawn in the header (S2008); the scaling lives in the config `GameViewModel` builds, never in the
  generator, so the mirror stays exact. The board is capped by `wearMaxSquareSide()` and by the height
  left under the header - a square of the full content width puts its corners outside a round glass.
- **System information** reports what this watch is, so it sits here rather than in Settings, where it
  was until S2008. Its five sections pack two fields per row through the same `packSettingsRows` the
  settings screens use (S1949), with a pair too wide for half a screen keeping a row of its own.
- **Calculator** keeps counting on the keypad and every function behind the single menu key; its
  history and memory are written on every change, because a watch program is dismissed by a gesture
  that gives no reliable exit callback.

---

## 🧩 Wear OS Tiles (S1955)

The `:wear` module exposes five external components to the Wear OS platform:
1. `MainActivity` (launcher & addressable entry point)
2. `WatchWearListenerService` (Data Layer phone companion listener)
3. `VoiceRecordingService` (microphone session service)
4. `WearResourceTileService`, `WearStreamTileService`, `WearFavouritesTileService` (Tile Providers)

`MainActivity` is an addressable entry point using a launch-target contract (`WearLaunchTarget`) shared with S1944, S1884, and S1961. Tiles construct ProtoLayout `AndroidActivity` launch intents with `WearLaunchTarget` key-value extras to open assigned resources, streams, or the favourites list directly upon user tap.

---

## 🛠️ Technical Details for Developers

- **Install package (`applicationId`)**: `com.sza.fastmediasorter` - the phone app's identity, required for Data Layer delivery (S1681)
- **Code namespace**: `com.sza.fastmediasorter.wear`
- **Min SDK**: 28 (Wear OS 2.0+)
- **Compile SDK**: 36
- **Target SDK**: 36
- **Version code / name**: generated, kept in sync with app_v2 by `build-with-version.ps1` - read `wear/build.gradle.kts` for the current values rather than trusting a number written here
- **Kotlin Version**: see `docs/TECH_STACK.md` (single source of truth for toolchain pins)
- **Java Target**: 17
- **Compose Version**: 1.5.14
- **Hilt Version**: 2.50
- **ExoPlayer Version**: Media3 1.2.1

---

## 📝 Build & Distribution Status

- **Compile Status**: ✅ BUILD SUCCESSFUL (debug APK, release APK, release AAB bundle)
- **Module**: `:wear`
- **Output Artifacts**:
  - `wear/build/outputs/apk/debug/` - Debug APK (with direct network credential input for development)
  - `wear/build/outputs/apk/release/` - Release APK (sideloadable release build)
  - `wear/build/outputs/bundle/release/wear-release.aab` - Play Store release bundle (WO-P6 compliant)
- **Target SDK**: 36
- **Min SDK**: 28 (Wear OS 2.0+)

### Feature Set Status
- **Local Playback**: Audio, Video, Image viewing with round-screen scaffold (S1678) and edge swipe dismissal (S1705).
- **Network Storage**: SMB, FTP, SFTP streaming and browsing.
- **Companion Sync**: Phone-to-watch network source and configuration sync via Wearable Data Layer (S1681).
- **Play Store Compliance**: Credential entry hidden on store release builds (WO-P6 / S1707), listing text localized in EN/RU/UK with Wear OS keyword.

---

## 🎯 Next Steps

1. **On-Device Verification (Galaxy Watch 7)**
   - Validate round display layout (S1678) and player swipe dismissal (S1705).
   - Capture Wear OS listing screenshots (384x384+ 1:1 ratio) on device for Play Store.
2. **Google Play Console Publication (S1707)**
   - Opt in Wear OS form factor, create Wear track, upload `wear-release.aab` and screenshots (owner-gated).

---

**Note**: This status document reflects code and artifact verification as of 2026-08-17. All listed features are implemented and tested in the codebase.


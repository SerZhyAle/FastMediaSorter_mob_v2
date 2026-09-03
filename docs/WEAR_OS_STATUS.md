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

## 📐 Verified Watch Shapes (WO-V16 Compliance, S2273)

The watch app layout is declared and verified against three watch screen shape profiles in `scripts/devtest/wear-shape-profiles.json`:

1. **`small-round` (192 dp, 153.6 dp content box)** - `Wear OS small round 1.2"`, `reviewedByPlay = true`. The Play Store WO-V16 baseline floor. Every fixed-width row is sized against this content box.
2. **`large-round` (227 dp, 181.6 dp content box)** - `Wear OS large round 1.39"`, `reviewedByPlay = true`.
3. **`xl-round` (240 dp, 192.0 dp content box)** - `Wear OS XL round`, `reviewedByPlay = false`. Regression control shape.

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
- ✅ Startup splash (S1706, corrected by S2274): the module owns `values/themes.xml` and a `values-v31` redefinition carrying `windowSplashScreenBackground` only, so a cold start opens on the **launcher icon** over black instead of a bare system background. Android 12 and newer only, matching the phone. `windowSplashScreenAnimatedIcon` is deliberately unset - the platform then draws the launcher icon itself, which is what Wear App Quality rule WO-V15 requires ("the splash screen icon must match the app launcher icon"). S1706 had pointed it at the generated brand glyph, and Play rejected the watch on 2026-08-31 with `Missing app icon in splash screen`; setting it again re-opens that rejection. The brand mark now lives only in `BrandFrameScreen`, which runs after the splash.

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

#### Search, filter and sort inside a resource (S2136, reshaped by S2473)

Every screen that lists the contents of a resource carries two small outlined icons above the list:
search, and refine. All four content routes have it - local category, phone category, network source
(`BrowseScreen`) and the phone's own folder listing (`PhoneResourceScreen`). The icons are drawn without
a plate so the list stays readable under them, and they fade out while the list is being scrolled,
returning on their own when it stops.

- **Search** matches a substring of the file name, ignoring case. Tapping it goes straight to the watch's
  own input path, so the keyboard and the microphone both work; a watch that offers neither says so under
  the icons rather than silently returning everything. An active query is cleared from the refine menu,
  which is also where it is shown back.
- **Refine** opens one full-screen menu carrying both the sort orders and the content-type filter, each
  as a one-column list with the whole label visible. It never borrows the file list's own view mode, so a
  wearer browsing in tiles still gets a readable menu.
- **Filter** narrows by content type, and its group appears only when the loaded list actually holds more
  than one type - a category screen already lists one kind. Where there is nothing to filter by, the menu
  says so in a sentence instead of dropping the group without explanation.
- **Sort** offers only the orders whose key the item carries. `BrowseScreen` shows seven - the source's
  own order, plus name, date and size in both directions. The phone-folder route shows five: the wire
  protocol between the phone and the watch carries no date, so the two date orders would be choices with
  no effect and are not listed.
- The three choices last for the visit and are not remembered. Reopening the screen starts unnarrowed,
  the same way the watch's stream list behaves.
- Narrowing never re-reads the source. The loaded list is kept and re-projected in memory, so clearing a
  query costs nothing even when the files came over the network or from the phone.
- A list emptied by the narrowing says that nothing matched, which is a different message from a resource
  that holds nothing - and the icon row stays on screen in that state, because clearing the query is what
  the user needs next.

The shared pieces live in `ui/common/` (`WearRefineControlHeader`, `WearRefineMenuScreen`,
`WearChoiceDialog`, `WearOverlayVisibility`, `WearSearchInputLauncher`, `WearRefineLabels`); the
narrowing itself is a pure function in `domain/browse/BrowseListProjection`, covered by unit tests.

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
| **Audio Playback**      | ✅     | Media3 foreground playback service, seek controls, and optional background audio |
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
| Screen-off mode | audio | A button blanks the screen and any touch restores it. With Background playback enabled, audio files and streams also continue after the app is minimized or the display times out; notification controls remain available. Video and slideshows still pause when their host stops. |
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

## 📌 Pinned-stream ranking from the Phone (S2149)

Streams the owner pinned on the phone are raised into the watch's top group, beside the marks made on
the watch itself. Phone → watch only: a mark made on the watch does not travel back by this path.

- Path `STREAM_PINS` (`/fms/phone/stream_pins`), phone → watch, **Data Item** carrying a
  `WearEventEnvelope` whose `data` is `WearStreamPinsPayload {identities}`. A Data Item rather than a
  message because the set is state: a watch switched on a day later must see the current set rather
  than have missed the moment it changed. It rides the `/fms/phone` prefix the watch manifest already
  declares, so it needed no manifest edit.
- The set is always published **whole**, including when empty. A delta or a skipped empty publish
  would leave the last pin stuck on the watch with no way for the phone to withdraw it.
- `identities` are folded channel identities (`web://host/path`, `http` and `https` collapsed into one
  token), never raw addresses - so the watch never has to reconcile two catalogs. The phone folds with
  `StreamChannelIdentity`; the watch compares with `foldWearStreamIdentity`, which is a comparison rule
  only and never the key a favourite is stored under.
- Phone side: `PushWearStreamPinsUseCase.observeAndPush` collects the pinned sources and republishes on
  every change, started once per process from `AppStartupInitializer`.
- Watch side: `WatchWearListenerService` → `WearPhonePinsRepository.replaceAll`, persisted to
  `filesDir/streams/phone_pins.json` and kept **separate** from the watch's own favourites store, so
  the star still means "I marked this here" and the phone can withdraw only what the phone sent. A
  payload that fails to parse is dropped, leaving the previously stored set in place.

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

## 📡 Receive File Outcome Ack & Deferred Outcome (S2087)

- Path `FILE_RECEIVE_ACK` (`/fms/phone/receive_file_ack`), phone → watch, message carrying `WearFileReceiveAck { fileName, outcome, destination }`. Tells the watch immediately after transfer whether the file landed in a local folder or was queued for remote upload.
- Path `FILE_UPLOAD_OUTCOME` (`/fms/phone/receive_file_upload_outcome`), phone → watch, Data Item carrying `WearFileUploadOutcome { fileName, succeeded, destination, completedAtMillis }`. Published by the phone upload worker upon completion and consumed by `WatchWearListenerService`. If `succeeded == false`, the watch raises a failure notification (`WearUploadOutcomeNotifier`) and deletes the Data Item.

---

## 📡 Open on the Phone - the twelfth path (S2004)

The reverse of S1884: a file the watch fetched **from** the phone can be handed back to the phone to be
opened there. Nothing is transferred - the phone still holds the original - so this is a message pair
and no channel.

- Paths: watch → phone `OPEN_ON_PHONE_REQUEST` = `/fms/watch/open_on_phone` carrying
  `WearOpenOnPhoneRequest {token, displayName}`, phone → watch `OPEN_ON_PHONE_ACK` =
  `/fms/phone/open_on_phone_ack` carrying `WearOpenOnPhoneAck {token, outcome}`. Correlated by the
  token, because a token addresses one file and the watch has at most one open outstanding for it.
- The token is the address the phone's **own** browse protocol issued. The watch never invents one, so
  `OpenPhoneResourceChannelUseCase` - the same use case that listed the item - resolves it, and no
  second addressing scheme exists to drift.
- Four outcomes, kept apart on purpose: `SHOWN` (the phone app was in front and opened it),
  `NOTIFIED` (it was not, so a notification was posted), `REFUSED_NO_NOTIFICATION` (the phone answered
  and can show nothing - its notifications are off) and `NOT_FOUND`. A phone that never answers is not
  an outcome at all; the watch reads the silence itself and says the phone is unreachable.
- `REFUSED_NO_NOTIFICATION` and silence must not be merged: one is fixed on the phone's settings
  screen, the other by bringing the phone closer.
- Phone side: the dispatcher branch and `OpenOnPhoneNotifier` live in `src/wearGms`, beside the eleven
  existing handlers, because the flavors that mount `src/wearStub` must gain no GMS reference. The
  path constants and the payload models sit in `src/main` with the other eleven - a `const val` and a
  `data class` name no GMS type.
- Foreground is read from `ProcessLifecycleOwner`, never from a task query, and a refused direct launch
  falls through to the same notification the background case posts.
- Watch side: the phone-browse list offers the action for every file row it drew, fetched or not
  (S2092). The request carries the browse token and moves no bytes, so gating it on a local copy meant
  the one action that can succeed was reachable only after a transfer - and for a document, only after
  a transfer that was then refused. Every other surface still follows the `PHONE_COPY` storage class,
  and the favourites list subtracts the offer outright because a favourite carries no token.
- A row the watch cannot render opens that menu on a plain tap rather than on a long press (S2092):
  the phone nulls the type of everything outside image, video and audio, so the refusal arrives with
  the list and the transfer never starts.
- Both modules hand-mirror the path and payload files, and both copies pin their wire names with
  `@SerializedName`: the two share no code, so the wire name is the whole contract and a minified
  release must not rename it.

### The copies those opens leave behind (S2004)

A phone file opened on the watch lands in `cacheDir/phone-files/`, and that directory is what makes the
open-on-phone action possible at all - `WearFileCapabilityPolicy` recognises it as the `PHONE_COPY`
storage class, meaning "the phone still holds the original of this".

- Capped at 128 MB, trimmed by the shipped `MediaCacheEvictor` at the moment the next copy is written.
  No worker, no startup sweep, no expiry: the directory grows only on arrival, so that is the only
  moment a check changes anything (S2004 ADR-6).
- The file that has just arrived is passed as `keep`, so it is never the one evicted. That, and not the
  cap's size, is what keeps a large file from disappearing between landing and being read.
- The cap's floor is `WEAR_FILE_TRANSFER_MAX_BYTES` (32 MB), the largest single file the bridge carries.
- Not to be confused with `getExternalFilesDir(DOWNLOADS)`, where sorting transfers land. Those are
  files the user asked for and nothing evicts them.

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
- **Voice recorder** records a note through a foreground service, so the session outlives the screen
  going dark (S1862). Since S2161 the screen shows the running state in its own tone on the status dot
  and the elapsed counter - deliberately not `MaterialTheme.colors.error`, which already means "something
  is wrong" across the module, so a running recording drawn in it would say the opposite of the truth.
  The tone is a third signal beside the glyph and the words, never a replacement: the state still reads
  with colour off and still speaks to TalkBack as one stop. A finished note plays on the watch itself -
  from the recorder screen for the note just recorded, and from any row of the note list, where a plain
  tap plays and a long press opens the actions sheet with Play above Send and Delete. Playback reuses the
  existing audio player through `PrepareVoiceNotePlaybackUseCase` and the same `playerRouteFor` the folder
  walk uses; there is no second player (ADR-2). On API 29 and above a stopped recording is published into
  the watch's shared audio collection, so it appears among the other audio files instead of staying inside
  the app - the private file is deleted only after the publication is confirmed, because a voice recording
  cannot be made again (ADR-3). A note that could not be published stays private and is still listed,
  playable and sendable. On API 28 it stays private by decision, no write permission being declared for it
  (ADR-4). The note list is not replaced by the audio collection: it remains the only place a note's
  delivery state - waiting, sent, failed - is visible.
- **System information** reports what this watch is, so it sits here rather than in Settings, where it
  was until S2008. Its sections pack two fields per row through the same `packSettingsRows` the
  settings screens use (S1949), with a pair too wide for half a screen keeping a row of its own.
  Since S2165 the content is selected by one criterion - a fact the watch's own settings screens do not
  show - and the screen is assembled from contributors rather than from one interface with a property
  per fact: `domain/systeminfo/WearSystemInfoContributor.kt` declares the seam, `WearSystemInfoOrder`
  holds the section order, and `di/WearSystemInfoModule.kt` declares the set with `@Multibinds`. Three
  consequences worth knowing before editing it:
  - **A section that cannot be filled says why instead of disappearing**, on the S2130/S1584 pattern and
    matching the form S2156 settled for the network monitor. A single missing *field* still just
    vanishes.
  - **A set the user counts more often than reads is collapsed to its size** and expands on tap - the
    sensor inventory, the capability set of the pair. Poured in whole they would turn the two-column
    report into one long column.
  - **The report is re-read on demand, never on a timer.** Thermal state, battery voltage and uptime
    move while the screen is open, so there is a refresh chip under the title; a watch polling on a
    timer would spend battery on a screen opened because the battery is misbehaving.
  Every Data Layer lookup it makes is bounded by `withTimeoutOrNull` - an unresponsive Play Services
  used to hold the whole screen, which is the one case the report exists to survive. The storage
  section measures the app's own footprint through `StorageStatsManager`, not the volume: the `StatFs`
  reading it replaced reported the whole `/data` partition while its comment claimed otherwise, which is
  why it repeated what the watch's settings already show. The `noLegal` build adds one further section
  (the signing-certificate fingerprint) from `wear/src/noLegal/`; `standard` leaves that slot empty.
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

## ⌚ Wear OS Complications (S2047)

The `:wear` module exposes three complication data sources to watch face slots:
1. `WearLastResourceComplicationService` (Last Used Resource - `SHORT_TEXT`, `LONG_TEXT`)
2. `WearFavouritesComplicationService` (Favourites Count - `SHORT_TEXT`, `MONOCHROMATIC_IMAGE`)
3. `WearNowPlayingComplicationService` (Now Playing / Last Played track - `SHORT_TEXT`, `LONG_TEXT`)

All complication services inherit from `BaseWearComplicationService` and load data locally via `LoadWearComplicationContentUseCase`. Tapping a complication launches `MainActivity` with a `WearLaunchTarget` intent. User-driven complication sources (`LAST_RESOURCE`, `FAVOURITES_COUNT`) receive event-driven update requests via `RequestWearComplicationRefreshUseCase`, while `NOW_PLAYING` uses platform polling (300s period).

---

## ⚙️ Two-Way Settings Sync (S2093)

Watch settings used to travel in one direction only, and the watch could not report its own state, so the
phone showed its memory of its last send rather than what the watch held. An edit made on the watch did
not exist for the phone at all.

- The list of watch settings is declared once per module in `WearSettingsRegistry` - id, value type,
  default, owning side, and a written `exceptionReason` for anything not editable from both sides. The
  two copies are hand-mirrored, like the payload and path files above, because the modules share no code.
- `SETTINGS_REPORT` is the reverse leg: `GatherWearSettingsUseCase` reads the whole watch state out and
  `ReportWearSettingsUseCase` publishes it as a Data Item, so the phone reads the latest set after
  reconnecting instead of having to listen at the moment the watch sent it.
- Merging is per field, and the later edit wins. Each side stamps the time of every edit it makes; the
  receiver corrects the other side's stamps by the clock skew it measures from the envelope's own
  `sentAt` against its arrival time, so a constant offset between the two devices cannot invert a merge.
- A missing field still means "the other side did not send this" and never resets a value, which is what
  keeps an older build on either side from losing settings it does not know about.
- Both sides carry one button with the same label and a caption saying when they last agreed. Two
  settings stay watch-only by decision (auto rotation, voice note policy) and the background picture
  stays a phone choice; each is a registry entry with its reason written down.
- Three settings the watch has always had were missing from the published settings reference and are now
  in it. `scripts/quality/assert-wear-settings-parity.ps1` runs in `scripts/post-change.ps1`, so a
  setting added to one side and not the other fails the closure and names the missing side.
- That gate checks a setting **exists** on both sides; `scripts/quality/assert-wear-mirrored-strings.ps1`
  checks its label still **reads** the same. The two modules ship no shared resource artifact, so every
  label the owner sees on both sides exists twice, and before this gate editing one copy left no trace on
  the other. Which pairs are mirrored is declared in `scripts/quality/wear-mirrored-strings.psd1`, never
  inferred from a matching key name: 20 key names occur in both modules and 14 of them are worded
  differently on purpose, the watch taking the shorter form where the round screen demands it. A pair
  named differently on the two sides is an ordinary record - the watch background setting is a section
  heading on the phone (`wear_background_section_title`) and a row label on the watch
  (`wear_setting_background_mode`). The gate is summoned by a changed `strings.xml` under either module,
  and a key that appears in both modules without being classified fails it, so the list cannot age in
  silence.

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
  - `wear/build/outputs/apk/<flavor>/debug/` - Debug APK (with direct network credential input for development)
  - `wear/build/outputs/apk/<flavor>/release/` - Release APK (sideloadable release build)
  - `wear/build/outputs/bundle/<flavor>/release/wear-<flavor>-release.aab` - Play Store release bundle (WO-P6 compliant); the store track takes the `standard` one
  - `<flavor>` is `standard` or `noLegal` - the module gained its own flavor dimension in S2090, and `standard` is what Play accepts
- **Target SDK**: 36
- **Min SDK**: 28 (Wear OS 2.0+)

### Feature Set Status
- **Local Playback**: Audio, Video, Image viewing with round-screen scaffold (S1678) and edge swipe dismissal (S1705).
- **Network Storage**: SMB, FTP, SFTP streaming and browsing.
- **Companion Sync**: Network source and configuration sync over the Wearable Data Layer (S1681), settings in both directions since S2093.
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

# Specification: III.15 — Standalone Player ↔ In-App Player Feature Parity

**Status:** Draft
**Date:** 2026-04-23
**Tier:** 4 — Substantial (notable risk)
**Roadmap entry:** Follow-up to III.11 (StandalonePlayer file ops) and III.12 (StandalonePlayer playlist). Covers the remaining parity gap: per-file playback features (stereo/3D, delete-undo, speed range, volume presets, resume state, stereo auto-detect CTA) and the architectural unification of the duplicated dialog + activity stacks.

---

## 1. Problem Statement

The standalone entrypoint (`StandalonePlayerActivity`, launched via `ACTION_VIEW` / "Open with..") is a near-parallel reimplementation of the in-app player: it ships its own activity, view model, playback-control dialog, fullscreen manager, lifecycle manager, settings manager, touch delegate and file-operation handler. Navigation differences (no next/prev/random) are expected, but the fork also drops a number of per-file capabilities that should apply equally to a single file: stereo/3D mode selection and auto-detect, playback speed beyond 2.0x, delete-with-undo, resume state, VR-install CTA, volume presets, and several document/image auxiliary actions. At the same time the duplicated surfaces (`PlaybackControlDialogFragment` 684 LOC vs `StandalonePlaybackControlDialogFragment` 388 LOC; `PlayerViewModel` 691 LOC vs `StandalonePlayerViewModel` 120 LOC) carry ~40% overlap and drift independently. This spec enumerates every divergence found in code, classifies each as *expected* or *loss*, and proposes a unification path so future player features land in both places automatically.

## 2. Goals

1. Produce a complete, code-verified divergence matrix between in-app and standalone players (see §4).
2. Close all "loss"-class gaps: stereo/3D tab in standalone dialog, full speed range (2.5x, 3.0x), delete-with-undo, volume presets, stereo auto-detect CTA, resume state persistence for standalone-opened files.
3. Preserve the expected differences: no list-navigation controls (prev/next/random/slideshow/swipe), no command-panel folding, no list-aware prefetch.
4. Extract shared coordinators so that stereo detection, delete/undo and playback-control UI are implemented once and consumed by both activities.
5. Retire `StandalonePlaybackControlDialogFragment` in favour of the unified dialog driven by a host-capability contract.
6. Keep both entrypoints below the 1000-LOC rule after refactor; standalone activity must shrink, not grow.
7. Elevate the standalone experience to match the premium "Entertainment" feel of the in-app player, ensuring micro-animations, haptic feedback on destructive actions, and smooth tactile UI transitions are universally applied.

Non-goals for this spec:
- Building a playlist from `ACTION_SEND_MULTIPLE` (owned by III.12).
- Cast / Chromecast output from standalone (flavor- and session-scoped; deferred to X.2).
- Image OCR, Google Lens, image translation, document print, sleep timer, lyrics, now-playing bottom sheet — these are either list/session features or belong to the in-app immersive shell and stay in-app only.
- Rotating/flipping images from standalone (content:// write path is out of scope).
- Full decomposition of `PlayerActivity` / `StandalonePlayerActivity` beyond what parity requires.

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | Full parity work: all goals apply. |
| `lite`     | ✅ | Stereo/3D tab stays hidden (no `SUPPORT_VIDEO` stereo content expected); delete-undo + speed range + volume presets still apply. |
| `photos`   | ✅ | No video → stereo tab hidden; delete-undo for images, volume/speed tabs hidden for static content (already the case). |
| `legacy`   | ✅ | `minSdk 23` requires scoped-storage fallback for delete-with-undo on pre-Q content:// URIs (see §3.2). |
| `vr`       | ✅ | Stereo/3D auto-detect CTA (`ShowVrInstallCta`) must fire from standalone too when SBS/OU content is opened on non-VR flavor; on the VR flavor, the 3DVR toggle introduced by [spec_vr-3dvr-toggle-button.md](spec_vr-3dvr-toggle-button.md) must also be wired from standalone entry. |

Gating flags already declared in [app_v2/build.gradle.kts](../app_v2/build.gradle.kts):
- `BuildConfig.SUPPORT_VIDEO` — gates stereo/3D tab + speed tab for video.
- `BuildConfig.SUPPORT_AUDIO` — gates speed tab for audio.
- `BuildConfig.SUPPORT_VR_PLAYER` — gates VR-specific entries in stereo tab.
- `BuildConfig.ENABLE_PERSISTENT_AUDIO_PLAYBACK` — stays false for standalone even on `standard`; see ADR-2.

No new `BuildConfig` fields are introduced by this spec.

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 23+ (legacy minSdk) | Delete-with-undo for `file://` URIs uses direct `File.delete()` + local trash folder under `context.filesDir/trash_standalone/`. No `MediaStore.createTrashRequest`. |
| 26+ (standard minSdk) | Default path. Resume-state DB table reused across entrypoints (same Room DB, no schema change). |
| 29 (Android 10) | Delete on `content://` URIs must handle `RecoverableSecurityException` — existing logic in [StandalonePlayerActivity.performDelete](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt) lines 667–756 covers this; refactor must preserve the `IntentSender` user-consent step. |
| 30+ (Android 11) | `MediaStore.createTrashRequest(..)` becomes the preferred undo primitive for `MediaStore`-backed content URIs; the undo coordinator must prefer it over our own trash copy when available. |
| 31+ (Android 12) | Notification permission — not applicable (standalone raises no foreground notification; see ADR-2). |
| 34+ (Android 14) | Predictive back already covered via base `OnBackPressedDispatcher`; ensure the undo snackbar does not intercept predictive gesture. |

### 3.3 Wear OS Impact

No Wear OS changes required. The `wear/` module has no player and receives no intents from `StandalonePlayerActivity`.

---

## 4. Current Architecture (Relevant Parts)

### 4.1 Entry points and hosts

| Component | Location | Role |
|-----------|----------|------|
| `PlayerActivity` | [ui/player/PlayerActivity.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt) (711 LOC) | In-app player. Receives a `files: List<MediaFile>` + `currentIndex`; wires all managers. |
| `StandalonePlayerActivity` | [ui/player/StandalonePlayerActivity.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt) (1124 LOC — **over the 1000-LOC rule**) | External entry (`ACTION_VIEW`, `ACTION_SEND`). Resolves single URI via SAF + MediaStore + direct FS. Owns its own fullscreen, lifecycle, settings, delete and rename logic. |
| `PlayerViewModel` | [ui/player/PlayerViewModel.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt) (691 LOC) | Rich state: file list, navigation coordinator, stereo coordinator, prefetch/offload coordinator, delete-undo coordinator, slideshow, resume state, events (`ShowVrInstallCta`, `ShowUndoSnackbar`, `CastStateChanged`). |
| `StandalonePlayerViewModel` | [ui/player/StandalonePlayerViewModel.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerViewModel.kt) (120 LOC) | Minimal: single `MediaFile`, favourites flag, media-type detection. |
| `PlaybackControlDialogFragment` | [ui/player/PlaybackControlDialogFragment.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt) (684 LOC) | Tabs: Volume, Audio (tracks), Subtitles, Stereo/3D, Hue, Brightness, Speed. |
| `StandalonePlaybackControlDialogFragment` | [ui/player/StandalonePlaybackControlDialogFragment.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlaybackControlDialogFragment.kt) (388 LOC) | Tabs: Volume (no presets), Audio, Subtitles, Hue, Brightness, Speed (capped at 2.0x). **No Stereo/3D tab.** |

### 4.2 Managers — shared vs duplicated vs single-side

| Capability | In-app class | Standalone class | Status |
|------------|--------------|------------------|--------|
| Video/image/audio routing | `MediaDisplayCoordinator` + `PlayerMediaLoaderManager` | `StandaloneViewManager` | **Parallel** — different APIs, overlapping responsibilities. |
| Video ExoPlayer control | `VideoPlayerManager` | `StandaloneViewManager.getExoPlayer()` | **Parallel** — standalone reimplements a subset of ExoPlayer wiring. |
| Video effects (hue/brightness/stereo crop) | `PlayerSetupHelper.applyConfiguredVideoEffects` + `VideoColorProcessor` + `StereoVideoProcessor` | `StandaloneViewManager.applyVideoEffects` ([line ~334](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt)) | **Parallel** — same classes reused, but call-site is duplicated; Media3 1.2.1 deferral fix from [spec_vr-3dvr-toggle-button.md](spec_vr-3dvr-toggle-button.md) must land in both call sites. |
| Audio/subtitle tracks | `VideoTrackSelectionManager` | `VideoTrackSelectionManager` | **Shared**. |
| Document viewers | `PdfViewerManager`, `EpubViewerManager`, `TextViewerManager` | Same classes (lazy-loaded by `StandaloneViewManager`) | **Shared**. |
| Document search | `SearchControlsManager` | `SearchControlsManager` | **Shared**. |
| Picture-in-Picture | `PictureInPictureManager` | `PictureInPictureManager` | **Shared**. |
| Stereo mode coordinator (detect + select) | `PlayerStereoModeCoordinator` | — | **In-app only** (loss). |
| VR-install CTA | `PlayerViewModel` emits `ShowVrInstallCta` | — | **In-app only** (loss). |
| Delete | `FileOperationsHandler.performDelete` + `PlayerDeleteUndoCoordinator` | `StandalonePlayerActivity.performDelete` (lines 667–756, inline, no undo) | **Parallel** — and standalone drops undo. |
| Rename | `FileOperationsHandler.performRename` | `StandalonePlayerActivity` (lines 1058–1123) | **Parallel**. |
| Share | `PlayerShareManager` / `FileOperationsHandler.performShare` | `StandalonePlayerActivity` (lines 761–783) | **Parallel**. |
| Copy / Move / Rotate / Flip | `FileOperationsHandler` | — | **In-app only**; out of scope per §2 (rotate/flip). Copy/Move is a loss to address. |
| Favourites | `PlayerViewModel.toggleFavorite` | `StandalonePlayerViewModel.toggleFavorite` | **Parallel** — identical call shape, trivially unifiable. |
| Resume state (seek pos, play state) | `SaveResumeStateUseCase` via `PlayerViewModel` | — | **In-app only** (loss). |
| Playback-control dialog | `PlaybackControlDialogFragment` | `StandalonePlaybackControlDialogFragment` | **Parallel** — the worst duplication. |
| Fullscreen | `SystemBarsManager` | `StandaloneFullscreenManager` | **Parallel**. |
| Lifecycle (wakelock, resume) | `PlayerLifecycleManager` | `StandalonePlayerLifecycleManager` | **Parallel**. |
| Touch delegate (video gestures) | `VideoTouchDelegate` | `StandaloneVideoTouchDelegate` | **Parallel** — standalone covers volume/brightness only; seek not yet. |
| List navigation | `PlayerNavigationManager` / `PlayerNavigationCoordinator` | — | **In-app only, expected** (kept out). |
| Prefetch / stream offload | `PlayerPrefetchManager` + `PlayerPrefetchOffloadCoordinator` | — | **In-app only, expected** (single-file has no lookahead). |
| Slideshow | `SlideshowController` | — | **In-app only, expected**. |
| Touch-zone hint overlay (images) | `PlayerTouchZoneSetupManager` | — | **In-app only, expected** (not suited to single-file viewer). |
| Cast (Chromecast) | `CastMediaManager` | — | **In-app only**; deferred (§2 non-goal). |
| Persistent background audio / notification | `NowPlayingManager`, `AudioServiceController`, `MediaNotificationManager` | — | **In-app only**; stays that way — see ADR-2. |

### 4.3 Dialog fragment divergence detail

| Tab / control | In-app | Standalone | Verdict |
|---------------|:------:|:----------:|---------|
| Volume seekbar + mute | ✅ | ✅ | Parity. |
| Volume half/max preset buttons | ✅ ([PlaybackControlDialogFragment.kt:174-232](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt)) | ❌ | **Loss** — add. |
| Audio track list (video) | ✅ | ✅ | Parity. |
| Subtitle track list incl. "None" | ✅ | ✅ (already supports `-1,-1`) | Parity. |
| Stereo/3D flat modes (SBS, OU, MONO) | ✅ ([PlaybackControlDialogFragment.kt:322-361](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt)) | ❌ | **Loss** — add. |
| Stereo/3D spherical modes (360, VR180, cylinder) | ✅ ([PlaybackControlDialogFragment.kt:339-350](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt)) | ❌ | **Loss** — add. |
| Stereo/3D auto-detect toggle | ✅ | ❌ | **Loss** — add. |
| VR-only controls (IPD, render mode) | ✅ (vr flavor) | ❌ | Expected when on non-vr flavor; on vr flavor → loss (covered under §5.4). |
| Hue | ✅ | ✅ | Parity. |
| Brightness | ✅ | ✅ | Parity. |
| Speed steps | 10 (0.25x .. 3.0x) | 8 (0.25x .. 2.0x) | **Loss** — extend to 10. |
| Speed reset | ✅ | ✅ | Parity. |

### 4.4 ViewModel silent losses

- Standalone holds no stereo mode state → `PlayerStereoModeCoordinator` never consulted → auto-detect/override never surfaces.
- Standalone never emits `ShowVrInstallCta` → SBS/OU content opened via "Open with.." on `standard` flavor will not suggest the VR edition.
- Standalone has no `saveResumeState`/`clearResumeState` calls → closing and re-opening the same file from "Open with.." loses seek position even though the in-app resume-state table exists.
- Standalone delete is inline in the activity and has no undo coordinator → a destructive action with no safety net, contrasted with the in-app 10-second undo window.

---

## 5. Proposed Architecture

### 5.1 Host-capability contract

Introduce a single capability interface that both activities implement, consumed by shared dialogs and coordinators:

```kotlin
// ui/player/contracts/PlayerHostCapabilities.kt
interface PlayerHostCapabilities {
    val supportsListNavigation: Boolean        // false for standalone
    val supportsSlideshow: Boolean             // false for standalone
    val supportsPersistentAudio: Boolean       // false for standalone
    val supportsCast: Boolean                  // false for standalone (until X.2)
    val supportsDeleteUndo: Boolean            // true for both after this spec

    val currentMediaFile: StateFlow<MediaFile?>
    val currentMediaType: StateFlow<PlayerMediaType>
    val stereoModeCoordinator: PlayerStereoModeCoordinator
    val videoPlayerHandle: VideoPlayerHandle?  // wraps ExoPlayer + track selector

    fun requestFinishAfterDelete()             // standalone: finish(); in-app: advance to next
}
```

The dialog fragment uses only this contract; it never casts to a specific activity.

### 5.2 Dialog unification

Retire `StandalonePlaybackControlDialogFragment` and make `PlaybackControlDialogFragment` capability-aware:

- Tabs that depend on a capability observe `host.supports*` and hide themselves when false.
- Speed steps list becomes a single constant (10 steps) shared by both hosts.
- Stereo/3D tab observes `host.stereoModeCoordinator.isStereoContent` (already a StateFlow in-app) and hides the tab otherwise, regardless of entrypoint.

### 5.3 Delete + undo coordinator extraction

Promote `PlayerDeleteUndoCoordinator` from in-app-only to a shared coordinator in `ui/player/coordinators/`:

- Accepts a `PlayerHostCapabilities` for the post-delete callback (in-app advances, standalone finishes).
- Encapsulates the API-level fork (`RecoverableSecurityException` on Q, `createTrashRequest` on R+, local trash folder on legacy).
- Exposes `SharedFlow<UndoSnackbarEvent>` consumed by both activities.
- `StandalonePlayerActivity.performDelete` (lines 667–756) is deleted; the activity calls the coordinator.

### 5.4 Stereo/3D parity in standalone

Wire `PlayerStereoModeCoordinator` into `StandalonePlayerViewModel`:

- On `MediaFile` load, run the same detection path that `PlayerViewModel` uses.
- Expose `stereoMode: StateFlow<StereoMode>` and `detectedStereoMode: StateFlow<StereoMode?>`.
- Emit `ShowVrInstallCta` when SBS/OU detected on non-VR flavor with the VR edition not installed (same logic as in-app).
- On the `vr` flavor, the 3DVR toggle described in [spec_vr-3dvr-toggle-button.md](spec_vr-3dvr-toggle-button.md) is shown in the standalone bottom bar under the same `BuildConfig.SUPPORT_VR_PLAYER && mediaType == VIDEO` rule.

### 5.5 Resume state for standalone-opened files

Persist resume state by content identity (canonical URI string + MD5 of URI for stability across reboots). `SaveResumeStateUseCase` already takes a URI; standalone simply calls it on pause and reads it on load. No DB schema change — reuse the existing resume-state table.

### 5.6 New classes / files

| Class / File | Location | Lines budget |
|--------------|----------|--------------|
| `PlayerHostCapabilities.kt` | `ui/player/contracts/` | ≤ 60 |
| `VideoPlayerHandle.kt` (interface wrapping ExoPlayer + track selector) | `ui/player/contracts/` | ≤ 40 |
| `SharedDeleteUndoCoordinator.kt` (promotion + generalisation of `PlayerDeleteUndoCoordinator`) | `ui/player/coordinators/` | ≤ 320 |
| `StandaloneStereoBridge.kt` (glues `StandalonePlayerViewModel` ↔ `PlayerStereoModeCoordinator`) | `ui/player/helpers/` | ≤ 120 |
| `PlaybackControlHostAdapter.kt` (bridges `PlayerActivity` / `StandalonePlayerActivity` to `PlayerHostCapabilities`) | `ui/player/contracts/` | ≤ 180 |

### 5.7 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | All new logic lives in `SharedDeleteUndoCoordinator`, `StandaloneStereoBridge`, `PlayerHostCapabilities` — activities just plug them in. |
| New classes follow naming (`VerbNounUseCase`, `NounRepository`, `NounViewModel`, `NounVerbManager`) | ✅ | Coordinators and contracts follow existing patterns already accepted in `ui/player/coordinators/` and `ui/player/contracts/`. |
| Data flow strictly `UI → ViewModel → UseCase → Repository → DataSource` | ✅ | Resume-state, delete and favourites all go through existing UseCases; standalone VM now calls them. |
| No `Log.d()` — Timber only | ✅ | New code uses `Timber`; any `Log.d` touched in standalone activity during refactor is migrated. |
| Room schema version incremented (if DB changes) | N/A | No schema change (resume-state table already exists). |
| `StateFlow` for state, `SharedFlow` for one-shot events | ✅ | `currentMediaFile` / `stereoMode` are StateFlow; `UndoSnackbarEvent`, `ShowVrInstallCta` are SharedFlow. |
| Hilt DI: new bindings declared in module file | ✅ | `SharedDeleteUndoCoordinator`, `StandaloneStereoBridge` bound in [di/PlayerModule.kt](../app_v2/src/main/java/com/sza/fastmediasorter/di/PlayerModule.kt) (file may not exist with this exact name — confirm at implementation; bind in the existing module that already provides `PlayerDeleteUndoCoordinator`). |

### 5.8 What stays untouched

- List navigation (`PlayerNavigationManager`, `PlayerNavigationCoordinator`, swipe gesture) — in-app only, by design.
- Prefetch / stream-offload — in-app only; standalone has no "next file" to prefetch.
- Slideshow, touch-zone overlay, now-playing bottom sheet, sleep timer, lyrics, OCR, Lens, image translation, document print, copy-to/move-to destination pickers — in-app only (copy/move stays out per §2; can be revisited in a follow-up).
- The `StandalonePlayerActivity` stays a separate activity class (different intent filters, different entry contract) — it just becomes a thinner shell that implements `PlayerHostCapabilities` and delegates to shared coordinators.

---

## 6. Data Flow

```
External app (ACTION_VIEW / ACTION_SEND)
   │
   ▼
StandalonePlayerActivity  ─implements─▶  PlayerHostCapabilities
   │                                          ▲
   │ resolveUri()                             │
   ▼                                          │
StandalonePlayerViewModel ───▶ StandaloneStereoBridge ──▶ PlayerStereoModeCoordinator
   │                                                            │
   │ currentMediaFile                                           │ stereoMode / detectedStereoMode
   ▼                                                            │
StandaloneViewManager  ──▶  ExoPlayer / Glide / PdfViewerManager..
   ▲                                                            │
   │                                                            │
   │  observes via PlayerHostCapabilities                       │
   │                                                            ▼
PlaybackControlDialogFragment ◀────── host.stereoModeCoordinator
        │                                             │
        │ tap "Delete"                                │ tap "3D mode"
        ▼                                             ▼
SharedDeleteUndoCoordinator             PlayerStereoModeCoordinator.setStereoMode()
   │       ▲                                          │
   │       │ UndoSnackbarEvent (SharedFlow)           │
   ▼       │                                          ▼
SafeDeleteUseCase ──▶ Repository ──▶ DataSource     VideoPlayerHandle.applyStereo()
   │
   │ on success
   ▼
host.requestFinishAfterDelete()   (standalone: finish();  in-app: navigate next)
```

---

## 7. Files to Modify

| File | Change | Est. size after |
|------|--------|-----------------|
| [ui/player/StandalonePlayerActivity.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt) | Remove inline `performDelete` (667–756), remove parallel `performRename` wiring where it duplicates in-app, implement `PlayerHostCapabilities`, drop references to `StandalonePlaybackControlDialogFragment`. | ≤ 700 LOC (down from 1124 — must drop under 1000) |
| [ui/player/StandalonePlayerViewModel.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerViewModel.kt) | Add stereo bridge wiring, resume-state save/load calls, delete coordinator integration. | ≤ 260 LOC |
| [ui/player/PlayerActivity.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt) | Implement `PlayerHostCapabilities`; swap direct `PlayerDeleteUndoCoordinator` use for the shared one. | ≈ 720 LOC |
| [ui/player/PlayerViewModel.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt) | Expose the existing stereo coordinator on the capabilities interface; unchanged semantics. | ≈ 695 LOC |
| [ui/player/PlaybackControlDialogFragment.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlaybackControlDialogFragment.kt) | Consume `PlayerHostCapabilities` for tab visibility + speed steps; single source of truth. | ≤ 720 LOC |
| [ui/player/StandalonePlaybackControlDialogFragment.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlaybackControlDialogFragment.kt) | **DELETE** after dialog unification. | 0 |
| [ui/player/helpers/PlayerDeleteUndoCoordinator.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerDeleteUndoCoordinator.kt) | Promote / rename to `SharedDeleteUndoCoordinator` under `coordinators/`; accept capability interface. | ≤ 320 LOC |
| [ui/player/StandaloneFullscreenManager.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/) (look-up) | Keep; small tweaks to respect user fullscreen preference. | ≈ existing |
| [di/](../app_v2/src/main/java/com/sza/fastmediasorter/di/) (player module) | Bind new coordinator + bridge. | +20 LOC |
| [res/layout/dialog_playback_control.xml](../app_v2/src/main/res/layout/) | Confirm stereo tab exists; no structural change. | unchanged |
| [res/values/strings.xml](../app_v2/src/main/res/values/strings.xml) + [values-ru](../app_v2/src/main/res/values-ru/strings.xml) + [values-uk](../app_v2/src/main/res/values-uk/strings.xml) | No new user-facing strings expected (all reuses). Add only if a new capability surfaces a new label. | +0–5 lines per file |

`StandalonePlayerActivity` is currently **over the 1000-LOC rule**; this refactor is the correction opportunity. Before editing: create timestamped backup in `temp/` per CLAUDE.md rule for files > 500 LOC.

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|------------|
| Dialog unification regresses in-app speed/stereo behaviour | Med | Capability-flag unit tests + manual regression on standard flavor before touching lite/photos/vr. |
| Delete undo on `content://` URIs behaves differently under Android 10 `RecoverableSecurityException` path | Med | Keep the existing user-consent `IntentSender` flow from current standalone delete; port verbatim into `SharedDeleteUndoCoordinator`; test on API 29 emulator explicitly. |
| Resume state leaks rows (no pruning) when same URI opens many times from "Open with.." | Low | Reuse existing in-app pruning policy (same Use Case); add TTL row to cleanup (already covered by IX.3 when it lands). |
| Stereo auto-detect false-positive fires VR-install CTA on non-stereo content from external apps | Low | Detection identical to in-app; CTA suppressed by user's "do not ask again" preference (existing). |
| `StandaloneViewManager` and `VideoPlayerManager` drift further during refactor | Med | Centralise `applyConfiguredVideoEffects` into a single helper consumed by both view managers; covered in §5. |
| File size of `PlayerActivity.kt` grows past 1000 LOC when implementing capabilities | Low | Capabilities interface is a thin adapter; most methods return existing members. If size creeps: extract `PlayerHostCapabilitiesAdapter` to a separate file. |
| Media3 1.2.1 `errorCode=7001` deferral fix from [spec_vr-3dvr-toggle-button.md](spec_vr-3dvr-toggle-button.md) not yet merged when this lands | High | Gate stereo-effects enablement behind the same deferral; if upstream spec not merged, apply deferral locally in both call sites and mark as duplicated-temporarily in ADR-3. |
| Removing `StandalonePlaybackControlDialogFragment` breaks any external deep-link or restore path | Low | Grep for class name across the module before deletion; add a temporary empty stub that redirects to the unified dialog only if an external reference exists. |

---

## 9. Testing Plan

### 9.1 Unit Tests

- `SharedDeleteUndoCoordinatorTest` — covers: success on `file://`, `RecoverableSecurityException` path on Q, `createTrashRequest` path on R+, undo window expiry, double-undo protection, finish-vs-advance callback routing.
- `PlayerHostCapabilitiesContractTest` — stub host verifying each capability flag toggles the correct dialog-tab visibility.
- `StandaloneStereoBridgeTest` — given a `MediaFile` with SBS metadata: coordinator reports `SBS`, `detectedStereoMode` emits non-null, `ShowVrInstallCta` emits on non-VR flavor (verify via `BuildConfig` stub).
- `PlaybackControlDialogFragmentSpeedStepsTest` — both hosts expose the same 10-step list.

### 9.2 Manual Test Cases

Happy paths:
1. In-app: open a file, launch playback dialog, verify all tabs (Volume, Audio, Subtitles, Stereo/3D, Hue, Brightness, Speed) present and behave as before.
2. Standalone: "Open with.." a local MP4 from Files app, launch dialog, verify Stereo/3D, full speed range (up to 3.0x), volume preset buttons all present and functional.
3. Standalone: open SBS sample video on `standard` flavor — verify `ShowVrInstallCta` dialog fires once.
4. Standalone: seek into a file, press Back, reopen same URI — verify playback resumes at last position.
5. Standalone: delete a file — verify undo snackbar appears; undo restores file; after 10s, deletion becomes permanent.

Error states:
6. Standalone on Android 10: delete a `MediaStore` URI owned by another app — verify `RecoverableSecurityException` triggers system consent dialog; after user approval, undo still works (via local trash copy).
7. Standalone: delete fails (storage full / readonly) — verify coordinator surfaces error snackbar instead of fake-success undo.
8. Standalone on legacy flavor (API 23): delete a `file://` path under app-private storage — verify local-trash fallback path.
9. Standalone on lite flavor: open a file, verify Stereo/3D tab hidden (no `SUPPORT_VIDEO`-stereo content expected), Speed tab still capped to audio rules when applicable.
10. Standalone: open a file, rotate device, relaunch dialog — verify tab state and selected values preserved.

### 9.3 Maestro E2E (if applicable)

Add a single smoke flow `maestro/smoke/standalone_player_parity.yaml`:
- Launch app intent with `ACTION_VIEW` on a test file.
- Open playback dialog.
- Assert: Stereo/3D tab present, Speed seekbar max label = "3.0x", Volume "Max" button visible.
- Tap Delete.
- Assert: Undo snackbar appears.
- Tap Undo.
- Assert: Activity remains with file loaded.

---

## 10. Accessibility & Premium UX (Entertainment)

The refactor does not introduce new UI layout surfaces — it unifies existing ones. As such, the **UI AMBIGUITY GATE** is cleared: the standalone player will perfectly mirror the in-app player's layout, overflow rules, and icon placement, leaving no implicit design decisions.

**Premium UX & Entertainment enhancements:**
- **Haptic Feedback:** Wire system haptics (e.g., `HapticFeedbackConstants.CONFIRM` / `REJECT` or custom vibration patterns) to destructive actions like Delete, and subtle haptics to the Undo action. The standalone viewer must feel tactile, premium, and safe.
- **Micro-animations:** Ensure the shared `PlaybackControlDialogFragment` tab switches, speed sliders, and volume preset selections use standard modern view micro-animations (e.g., crossfade on tab switch, scale-bounce on preset tap). The interface should feel dynamic and alive, not static.
- **Undo Snackbar Animation:** The undo snackbar must slide in smoothly from the bottom with an easing curve, matching modern Material Design 3 guidelines, rather than abruptly popping up.

**Accessibility Action items:**
- Ensure the added volume-preset buttons in the standalone-rendered dialog reuse existing `contentDescription` strings (already translated EN/RU/UK).
- Verify Stereo/3D tab radio buttons carry the same `contentDescription`/label pattern as in-app — no colour-only affordances; selection uses radio dot + label.
- Delete-undo snackbar: ensure action button is TalkBack-reachable and announced on appearance; reuse the in-app snackbar helper.
- All touch targets already meet 48dp via the dialog layout.

---

## 11. User-Facing Feature Update

The refactor closes parity gaps that users will perceive directly. Add bullets to the FEATURES docs:

- `docs/FEATURES.md` (EN): "Standalone player (Open with..) now supports stereo/3D mode selection, delete with undo, full playback speed range (up to 3.0x), volume presets, and resume of last playback position."
- `docs/FEATURES_RU.md` (RU): "Автономный плеер (..открыть с помощью..) поддерживает выбор режима стерео/3D, удаление с возможностью отмены, полный диапазон скорости (до 3.0x), быстрые кнопки громкости и возобновление позиции воспроизведения."
- `docs/FEATURES_UK.md` (UK): "Автономний плеєр (..відкрити за допомогою..) підтримує вибір режиму стерео/3D, видалення з можливістю скасування, повний діапазон швидкості (до 3.0x), швидкі кнопки гучності та відновлення позиції відтворення."

Run `/doc-update` when editing these docs to keep the EN/RU/UK mirrors aligned.

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: Keep `StandalonePlayerActivity` as a separate activity class instead of merging into `PlayerActivity`.**
- **Decision:** Retain two activity classes; unify via a capability contract and shared coordinators.
- **Alternatives considered:** (a) Single `PlayerActivity` with branching intent parsing. (b) Abstract `BaseMediaPlayerActivity` generic over state.
- **Reason:** Two distinct entry contracts (intent filters, consent flows, resume semantics) lead to two distinct lifecycles. Collapsing them would push runtime branching into every method; the capability interface achieves code reuse with less conditional logic.

**ADR-2: Standalone entry does not opt into persistent background audio or `NowPlayingManager`.**
- **Decision:** `supportsPersistentAudio = false` for standalone; no foreground service, no media notification.
- **Alternatives considered:** Start `AudioPlaybackService` from standalone to match in-app behaviour.
- **Reason:** External intents are short-lived sessions; users expect playback to stop when the activity closes. Spinning up a foreground service on every "Open with.." would be surprising and breaks the principle of least astonishment. Also avoids the runtime notification-permission prompt (API 33+) for a session most users will not want backgrounded.

**ADR-3: Port the Media3 1.2.1 effects-deferral fix into both view managers even if [spec_vr-3dvr-toggle-button.md](spec_vr-3dvr-toggle-button.md) has not yet landed.**
- **Decision:** Apply `setVideoEffects(..)` only after `onVideoSizeChanged(width>0, height>0)` in both `PlayerSetupHelper` and `StandaloneViewManager`.
- **Alternatives considered:** Wait for the VR spec to merge and reuse its helper unchanged.
- **Reason:** Parity work must not re-introduce the `errorCode=7001` crash into the standalone surface; the deferral is cheap to duplicate and will be collapsed into a single helper when the VR spec lands.

**ADR-4: Delete-with-undo is in scope; copy-to/move-to destination pickers are out of scope.**
- **Decision:** Ship delete parity now; defer copy/move.
- **Alternatives considered:** Full file-operation parity in one spec.
- **Reason:** Delete is a destructive operation; the asymmetry (in-app safe, standalone unsafe) is a real user risk. Copy/move require a full destination-picker subsystem that is list-oriented and deserves its own scoping.

---

## 13. Implementation Steps

1. Create `temp/StandalonePlayerActivity_<timestamp>.kt.backup` (file > 500 LOC, required by CLAUDE.md).
2. Add `ui/player/contracts/VideoPlayerHandle.kt`.
3. Add `ui/player/contracts/PlayerHostCapabilities.kt`.
4. Add `ui/player/contracts/PlaybackControlHostAdapter.kt`.
5. Promote `PlayerDeleteUndoCoordinator` → `ui/player/coordinators/SharedDeleteUndoCoordinator.kt`; generalise over `PlayerHostCapabilities`; keep existing tests.
6. Bind `SharedDeleteUndoCoordinator` in the existing player Hilt module (same module that already provides `PlayerDeleteUndoCoordinator`).
7. Add `ui/player/helpers/StandaloneStereoBridge.kt` and bind in the Hilt module.
8. Update `StandalonePlayerViewModel`: inject `StandaloneStereoBridge`, call it on file load, expose stereo flows + resume-state calls.
9. Update `PlayerViewModel`: expose its existing `stereoModeCoordinator` via the capabilities path (no behavioural change).
10. Update `PlayerActivity`: implement `PlayerHostCapabilities`; switch delete to `SharedDeleteUndoCoordinator`.
11. Update `StandalonePlayerActivity`: implement `PlayerHostCapabilities`; remove inline `performDelete` (667–756); reuse `SharedDeleteUndoCoordinator`; remove the `StandalonePlaybackControlDialogFragment` reference and use the unified dialog.
12. Update `PlaybackControlDialogFragment`: observe `PlayerHostCapabilities` for tab visibility; extend standalone-side to 10 speed steps (now shared constant); unhide volume presets and stereo tab under capability.
13. Delete `StandalonePlaybackControlDialogFragment.kt` after verifying no remaining references (grep across module + manifest).
14. Port the Media3 effects-deferral logic into `StandaloneViewManager.applyVideoEffects` (per ADR-3) if not already covered by an upstream merge.
15. Add resume-state save/load in `StandalonePlayerActivity.onPause` / `onCreate`.
16. Wire `ShowVrInstallCta` observer in `StandalonePlayerActivity` (reuse the existing in-app CTA dialog).
17. Run `./gradlew.bat assembleStandardDebug lintStandardDebug testStandardDebugUnitTest`.
18. Run `./gradlew.bat assembleLiteDebug assemblePhotosDebug assembleLegacyDebug assembleVrDebug` to confirm flavors still compile.
19. Execute the manual test matrix in §9.2 on a `standard` flavor device first, then `vr`, then `legacy` (API 23 emulator), then `photos`.
20. Add Maestro flow `maestro/smoke/standalone_player_parity.yaml`.
21. Update `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` per §11 (via `/doc-update`).
22. For every modified file, run:
    ```powershell
    .\scripts\add_to_dev_log.ps1 "<path>" "<target>" "<description>"
    ```

Mandatory step checklist:
- [ ] String resources added in EN/RU/UK only if new labels introduced (`values/`, `values-ru/`, `values-uk/`).
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` updated (user-facing parity).
- [ ] Room DB migration — N/A (resume-state table already exists).
- [ ] `.\scripts\add_to_dev_log.ps1` run for every modified file.
- [ ] Added inline comments explaining WHY for the new capability interfaces, per STRICT CODING RULES.
- [ ] Confirmed haptic feedback and micro-animations are wired in the unified dialog.

---

## 14. Out of Scope (future items)

- Copy-to / move-to destination pickers from standalone (requires a destination-picker subsystem that is list-oriented).
- Cast / Chromecast output from standalone (tracked under X.2).
- Image rotation / flip from standalone (needs a content:// write path).
- OCR, Google Lens, image translation, document print, sleep timer, lyrics, now-playing bottom sheet — in-app-immersive features that do not apply to a single-file external session.
- Playlist construction from `ACTION_SEND_MULTIPLE` (owned by III.12).
- Full decomposition of `PlayerActivity` / `StandalonePlayerActivity` below 500 LOC (parity refactor only trims standalone under the 1000-LOC rule; deeper decomposition is a separate item).
- Consolidating `VideoPlayerManager` and `StandaloneViewManager` into a single video pipeline — structural follow-up, tracked as a new item after parity lands.

# Research 01 - Immersive browser architecture (S0963, Pillar 2)

**Date:** 2026-07-11
**Method:** read-only codebase investigation (query.ps1 + file reads), delegated to `android-solution-researcher`.
**Feeds:** strategic §4/§6, tactical INDEX + phases.

## Established seam from S0962 (Pillar 1) - verified present

- Transport `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/VrLaunchContract.kt` (144 LOC): `StartVrPlaybackRequest`, `VrLaunchPoint` (incl. `BROWSE_TILE` - the "from browser" entry point), `VrLaunchMode` (`FILE_URI` / `DIAGNOSTIC_PLAYLIST`), `VrMediaType`, `VrPanelReturnTarget`, `PlayerStateSnapshot`. Only `fileUriString: String?` carries a target - **no resource/directory target exists**.
- Use-case contract `core/xr/StartVrPlaybackUseCase.kt` (32 LOC) + impl `src/vr/.../StartVrPlaybackUseCaseImpl.kt` (150 LOC): `invoke(request, returnTarget)` preflights `XrDetectionFacade.state().first()`, builds `VrLaunchInput`, asks `XrEntryGateway.createImmersiveIntent(input)`, `startActivity(FLAG_ACTIVITY_NEW_TASK)`. Result sealed type: `Started` / `Unavailable` / `Failed` / `InvalidUri`.
- Gate `core/xr/XrDetectionFacade.kt` + `XrDetectionState` (`NONE` / `AVAILABLE_DISABLED_BY_USER` / `AVAILABLE_ENABLED`).
- DI: `src/vr/.../core/xr/di/XrModule.kt` (real `@Binds`) vs `src/vrStub/.../core/xr/di/NoOpXrModule.kt` (No-Op). AGP mounts exactly one per variant; `src/main` injection compiles everywhere, no flavor guards.
- Precedents: `ui/browse/helpers/BrowseVrCinemaLaunchManager.kt` (105 LOC, `@ActivityScoped`, self-observes `XrDetectionFacade.state()` via `repeatOnLifecycle(STARTED)`, `isAvailable`, `launch(file)`); `ui/player/helpers/PlayerVrLaunchManager.kt` (437 LOC, warm-launch, snapshot + return-target).

## 1. Immersive / native OpenXR surface (src/vr)

- `ui/xr/DiagnosticXrActivity.kt` - **1356 LOC (near 1500 cap)**. Diagnostic immersive host: OpenXR session, ad-hoc scan, HUD, single-item playback.
  - `scanMediaFiles()` (465-476): hardcoded scan of `/sdcard/Pictures|Movies/FastMediaSorterVrTest` against static `VR_TEST_MEDIA_ORDER` allowlist. **Disconnected from `MediaResource`/`MediaFile`/`GetMediaFilesUseCase`.**
  - `prepareLaunchMedia()` branches on `VrLaunchMode`: `DIAGNOSTIC_PLAYLIST` -> `scanMediaFiles()`; `FILE_URI` -> `resolveSingleLaunchFile(input)`. Builds `mediaPlaylist: List<File>` + `currentPlaylistIndex`.
  - `loadCurrentMediaItem()`: image -> decode -> `runtime.queueFrame(rgba,w,h)`; video -> ExoPlayer on native `Surface` (`startVideoPlayback`).
  - `navigateToNextMedia`/`navigateToPrevMedia` (951-965): walk the pre-resolved `mediaPlaylist` only; **no live re-query, no set-swap after session start**.
  - `onNativeRayInteraction(uvX,uvY,isHover,isClick)` (`@Keep`, 1026) -> `HudInteractionDispatcher.dispatch` hit-tests HUD `RectF`s (prev/play/next/volume/depth). **No third "browse" surface.**
  - Heap guard already here: `pickSampleSizeForBudget()` + `MAX_EXTERNAL_DECODE_BYTES = 96 MB` (841-849, 1341), `returnToPool()` recycles into Glide pool, reusable direct `ByteBuffer`s (`getReusableDirectBuffer`/`getReusableHudBuffer`).
  - `checkHandTrackingPermission()` (227-238): requests `com.oculus.permission.HAND_TRACKING` before init - inherited by any Activity reusing the runtime.
- `ui/xr/DiagnosticXrRenderThread.kt` (123): native frame loop. KDoc documents a ~5 s `XrInstance` cold-init window (`windowFocused` deferred, 5000 ms timeout, 80-94).
- `ui/xr/helpers/HudCanvasRenderer.kt` (129): draws ONE 1024x512 Canvas panel (buttons/sliders as `RectF`).
- `ui/xr/helpers/HudInteractionDispatcher.kt` (86): UV->pixel hit-test against `RectF`s.
- `ui/xr/helpers/HudHapticBridge.kt` (22) / `HudPlaybackController.kt` (52).
- `core/xr/runtime/NativeDiagnosticXrRuntime.kt` (233): JNI surface. **Only two texture channels: `queueFrame` (media quad) + `queueHud` (HUD quad)**, plus `applyHaptic`. `nativeQueueFrame`/`nativeQueueHud` (210-227). Short-circuits on `UnsatisfiedLinkError` when arm64 `libfms_diagnostic_xr.so` absent (x86_64 emulator).
- `core/xr/XrEntryGatewayImpl.kt` (74): builds immersive `Intent`, **hardcoded `Intent(appContext, DiagnosticXrActivity::class.java)`** regardless of `VrLaunchMode` (42).

## 2. Browser domain layer + repository (read path to reuse)

- `domain/usecase/GetMediaFilesUseCase.kt` (474): **the** browser UseCase. `invoke(resource, sortMode, sizeFilter, ..., currentPath, isSubfolderMode, forceFullScan, progressiveLoading): Flow<List<MediaFile>>`. Backed by `MediaScanner` (`scanFolder`, `listDirectoryContents`, `scanFolderPaged`, `getFileCount`, `isWritable`) via `MediaScannerFactory`. Subfolder drill-down via `currentPath` + `isSubfolderMode=true` -> `listDirectoryContents`.
- `ui/browse/loading/BrowseLoadingManager.kt` (254): orchestrates `GetMediaFilesUseCase` + sort/favorites/cache.
- Domain item: `MediaFile` (fields incl. `type: MediaType`, `toLaunchUriString()`). Resource entity: `MediaResource` (has id; `GetResourcesUseCase.getById` available for resource lookup).

## 3. Stereo / 3D-image taxonomy

- `ui/player/StereoDetector.kt` (530): SBS/OU/equirect classification via filename+metadata+dimension cascade. Entry `detectForImage` (+ user-initiated variants). Well unit-covered (`StereoDetectorTest`, `...PhotoSphereTest`, `...UserInitiatedTest`).
- `domain/model/StereoMode.kt` (159): taxonomy enum.

## 4. Thumbnail / preview model

- `ui/browse/AdapterThumbnailLoader.kt` (771): 2D Browse Glide pipeline - `.override(300,300)` (`CACHED_THUMBNAIL_SIZE`) + `centerCrop()` + memory-pressure-aware `decodeFormatResolver.decodeFormat()`.
- Not `.into(ImageView)`-usable inside the native quad, but `Glide.with(ctx).asBitmap().load(...).override(w,h).submit()` composes into a Canvas grid (mirrors `DiagnosticXrActivity.decodeFilePooled()`).
- Heap-tier precedent: S0772 `PrefetchLoadControlFactory.videoBufferCapBytesForHeap(maxHeapMb)` derives cap from `Runtime.maxMemory()` (Quest 3 LOW-tier = 512 MB heap). Same idiom reusable for grid decode budget.

## 5. Immersive player handoff

- `XrEntryGatewayImpl.createImmersiveIntent` hardcoded to one Activity target. No set-swap after session start.
- **Re-launching a full immersive session per pick pays ~5 s OpenXR cold-init each time (render-thread KDoc).** -> Design MUST do in-process BROWSE -> PLAYBACK inside one immersive Activity/session, reusing `loadCurrentMediaItem`-equivalent playback, NOT `StartVrPlaybackUseCase` re-launch per selection.

## 6. Resource context menu (resource, not file)

- `ui/main/ResourceAdapter.kt` (905): resource-card (⋮) overflow built in **two near-duplicate `PopupMenu` blocks** - `GridViewHolder` (~447) and `ResourceViewHolder` (~805), both using `R.menu.resource_item_actions`.
- Precedent for a new optional action: `onOpenInNewWindowClick: ((MediaResource) -> Unit)? = null` + `isOpenInNewWindowVisible: Boolean = false` (56-59), wired in `MainActivity.kt:938-940` through `MainPanelItemActionsManager` (111 LOC) - the off-Activity home for resource-scoped side effects.
- `MainActivity.kt` = **1390 LOC (near cap)** - wire the new callback through `MainPanelItemActionsManager`, not inline.

## 7. Flavor / source-set map

- `noLegal` mounts `src/vr/java|res` + `src/vr/AndroidManifest.xml` (build.gradle.kts:602-609); `SUPPORT_VR_PLAYER=true` (397).
- `vr` flavor auto-mounts `src/vr` by name + `src/vrOnly/java` (639-650); **`SUPPORT_VR_PLAYER=false` (543)** - S0241-legacy gate mismatch, separately tracked (S0962 INDEX:21). S0963 is `noLegal`-only until that follow-up lands.
- `standard`/`photos`/`legacy`/`lite` mount `src/vrStub/java` -> No-Op bindings.
- Contract-in-main + impl-in-vr + noop-in-vrStub + per-variant Hilt module = `dev/FLAVOR_DEVELOPMENT_RULES.md` §2. No `SUPPORT_VR_PLAYER` in `src/main`.

## 8. Resolutions for strategic §6 open items

- **§6.1 render form of BROWSE window** -> RESOLVED: **thumbnail grid on a single Canvas quad** (NxM `RectF` cells, hit-tested UV->pixel exactly like HUD buttons). Spatial panels (multiple independent 3D quads) are **not feasible** - native runtime exposes only `queueFrame`+`queueHud` (two channels); multi-panel needs C++/JNI changes, out of scope.
- **§6.2 preview model within heap budget (S0772 7K OOM)** -> RESOLVED: Glide `asBitmap().override(cellW,cellH)` sized to grid-cell pixels, composited onto the grid Canvas, pushed through existing `queueHud`/`queueFrame`; per-bitmap footprint capped via the existing `pickSampleSizeForBudget`/`MAX_EXTERNAL_DECODE_BYTES` idiom; decode **on-demand for visible cells only**.

## 9. Risks (for tactical plan)

- **High (feasibility):** no native support for >1 media quad + 1 HUD quad -> grid must live on a Canvas quad, not spatial panels.
- **High:** `DiagnosticXrActivity` 1356 LOC - do NOT bolt BROWSE onto it (ADR-1). New Activity, shared render thread/runtime/haptics.
- **High:** `XrEntryGatewayImpl` hardcoded to one Activity - add routing branch by `launchMode`.
- **High (UX):** ~5 s session re-init per pick if re-launch-per-selection chosen -> in-process transition required.
- **Medium:** `VrLaunchContract` has no resource/directory target - extend `VrLaunchMode` (+ `resourceId`), don't hack `FILE_URI`.
- **Medium:** `ResourceAdapter` two duplicate popup blocks - add item in both + `R.menu.resource_item_actions.xml`.
- **Medium:** `MainActivity` 1390 LOC - route resource callback through `MainPanelItemActionsManager`.
- **Low:** `toLaunchUriString()` duplicated in `BrowseVrCinemaLaunchManager` + `PlayerVrLaunchManager` - consolidate before a 3rd call site.
- **Medium (known/tracked):** `vr` flavor `SUPPORT_VR_PLAYER=false` -> `noLegal`-only until S0241-successor gate unification.

## 10. Recommended tactical decomposition (phases)

1. Contract extension: `VrLaunchMode.RESOURCE_BROWSE` + `resourceId: Long?` on `StartVrPlaybackRequest`/`VrLaunchInput`, `StartVrPlaybackUseCaseImpl.validateRequest` branch; keep `FILE_URI`/`DIAGNOSTIC_PLAYLIST` intact.
2. New immersive browser Activity in `src/vr/.../ui/xr/` (own state machine BROWSE->SELECT->PLAYBACK), shares `DiagnosticXrRenderThread`/`NativeDiagnosticXrRuntime`/haptics, in-process play (no re-launch per pick).
3. `XrEntryGatewayImpl` routing branch by `launchMode` -> new Activity for `RESOURCE_BROWSE`; `DiagnosticXrActivity` untouched.
4. Domain read integration: inject `GetMediaFilesUseCase` (+ `GetResourcesUseCase.getById`); drive grid off `Flow<List<MediaFile>>` with `isSubfolderMode`/`currentPath` drill-down; classify 3D images via `StereoDetector`/`StereoMode`.
5. Grid-on-quad renderer: extend `HudCanvasRenderer`/`HudInteractionDispatcher` pattern (sibling class) - NxM `RectF` thumbnail grid on a Canvas, UV->pixel hit-test. No multi-quad.
6. Heap-bounded on-demand thumbnail decode (Glide `asBitmap().override`, `pickSampleSizeForBudget` cap, visible cells only).
7. Resource context-menu entry: optional `onOpenInVrCinemaClick` callback on `ResourceAdapter` (mirror `onOpenInNewWindowClick`), wired via `MainPanelItemActionsManager`, both popup blocks + menu XML, gated by resource-scoped `XrDetectionFacade` mirror.
8. Consolidate `toLaunchUriString()` into a shared location before the 3rd call site.

## /spec-draft candidates (out-of-scope)

- `vr` flavor VR-gate mismatch (`SUPPORT_VR_PLAYER=false`) - already tracked (S0962 INDEX:21, successor of archived S0241); not re-parking.
- No other qualifying independent defects surfaced.

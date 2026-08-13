# Phase 03 - Immersive browse Activity

**Strategic spec:** [`../S0963_vr-cinema-immersive-browser.md`](../S0963_vr-cinema-immersive-browser.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 6
**Started:** -
**Completed:** -

---

## Objective

Add a new immersive Activity (`src/vr`) that reads the target resource through the existing browser domain layer, classifies 3D images via the existing stereo taxonomy, renders the BROWSE grid on a Canvas quad, hit-tests controller-ray input, and on selection transitions in-process to playback of the picked item - all without touching `DiagnosticXrActivity` (ADR-1) and without re-launching the OpenXR session per pick.

---

## Prerequisites

- [ ] Phase 01 + Phase 02 ✅ Done.
- [ ] `domain/usecase/GetMediaFilesUseCase.kt`, a resource-by-id lookup use-case, `ui/player/StereoDetector.kt`, `core/xr/runtime/NativeDiagnosticXrRuntime.kt`, `ui/xr/DiagnosticXrRenderThread.kt` confirmed present.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/browse/ImmersiveBrowseContentLoader.kt` | New | ≤ 220 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/browse/ImmersiveBrowsePlaybackController.kt` | New | ≤ 320 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/ImmersiveBrowseActivity.kt` | New | ≤ 700 |

> All three are `src/vr`-only. `ImmersiveBrowseActivity` must stay < 1500 LOC; if it approaches the cap, push more logic into the two helpers before adding lines.

---

## Steps

### Step 03.1 - Content loader (domain read + stereo classification)

**Files:** `ui/xr/browse/ImmersiveBrowseContentLoader.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class ImmersiveBrowseContentLoader @Inject constructor(getMediaFilesUseCase, getResourcesUseCase/resource-by-id lookup, stereoDetector)`. Expose `suspend fun load(resourceId: Long, currentPath: String?): List<ImmersiveBrowseCell>`: resolve the `MediaResource` by id, call `getMediaFilesUseCase.invoke(resource, .., currentPath = currentPath, isSubfolderMode = currentPath != null)` and take the first emission (`.first()`), map each `MediaFile` to an `ImmersiveBrowseCell` (label = display name, `isFolder` for directory entries, `mediaType` mapped to `VrMediaType`, `stereoBadge` from `stereoDetector.detectForImage(..)`/equivalent for images else null). Filter to video + image types only. Return empty list on missing resource. Business logic only - no rendering.

**Verification:**

- `Glob` - `ui/xr/browse/ImmersiveBrowseContentLoader.kt` exists.
- `Grep` - `class ImmersiveBrowseContentLoader` matches exactly once.
- `Grep` - `getMediaFilesUseCase` and `stereoDetector` referenced.
- `Grep` - `suspend fun load(` present.

**Status:** `[x]` done

---

### Step 03.2 - In-process playback controller

**Files:** `ui/xr/browse/ImmersiveBrowsePlaybackController.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class ImmersiveBrowsePlaybackController` owning playback of one selected item into the main media quad, mirroring `DiagnosticXrActivity`'s proven approach but self-contained: `playImage(runtime, file)` decodes (respecting a heap-budget sample-size cap like `pickSampleSizeForBudget`/`MAX_EXTERNAL_DECODE_BYTES`) and calls `runtime.queueFrame(rgba,w,h)`; `playVideo(runtime, surface, file, exoPlayer)` starts ExoPlayer on the native `Surface`. Expose `stop()` releasing ExoPlayer (`setVideoSurface(null)`, remove listeners, `release()`, abandon audio focus) and recycling the decoded bitmap into the Glide pool. One owner per ExoPlayer (Rule 18 / audit "Player/Glide ownership"). No grid logic here.

**Verification:**

- `Glob` - `ui/xr/browse/ImmersiveBrowsePlaybackController.kt` exists.
- `Grep` - `class ImmersiveBrowsePlaybackController` matches exactly once.
- `Grep` - `fun stop()` present and `release()` referenced.
- `Grep` - `queueFrame` referenced.

**Status:** `[x]` done

---

### Step 03.3 - Activity skeleton, OpenXR session, BROWSE-state grid push

**Files:** `ui/xr/ImmersiveBrowseActivity.kt` (New)
**Depends on:** Step 03.1, Step 03.2

**Prompt for developer:**

> Create `@AndroidEntryPoint class ImmersiveBrowseActivity : ComponentActivity()`. In `onCreate`: parse the launch token (reuse the `DiagnosticXrLaunchArgs`/`VrLaunchPayloadHolder` decode path) to obtain `resourceId` from `VrLaunchInput.requireResourceId()`; check hand-tracking permission and native availability exactly as the diagnostic path does (short-circuit finish on `!isNativeAvailable`); construct a `DiagnosticXrRenderThread` bound to the injected `DiagnosticXrRuntime`; hold state `enum { BROWSE, PLAYBACK }` starting `BROWSE`. Launch a coroutine to `contentLoader.load(resourceId, currentPath=null)`, build the `ImmersiveBrowseGridRenderer` Canvas, and push it via `runtime.queueHud(..)` (the BROWSE grid uses the HUD-quad channel; the main media quad stays blank until selection). Insert a single `Timber.d("S0963: immersive browse opened resource=..")` probe here (spec will be BlockNeedUserTest).

**Verification:**

- `Glob` - `ui/xr/ImmersiveBrowseActivity.kt` exists.
- `Grep` - `class ImmersiveBrowseActivity` matches exactly once.
- `Grep` - `@AndroidEntryPoint` present.
- `Grep` - `requireResourceId()` referenced.
- `Grep` - `Timber.d("S0963:` present exactly once.

**Status:** `[x]` done

---

### Step 03.4 - Controller-ray interaction + haptics

**Files:** `ui/xr/ImmersiveBrowseActivity.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add `@Keep fun onNativeRayInteraction(uvX, uvY, isHover, isClick)` (JNI callback, same signature as diagnostic) that, while state == BROWSE, forwards to `ImmersiveBrowseInteractionDispatcher.dispatch(..)`; on a resolved hover re-draw the grid with the new `hoveredIndex` and re-push via `queueHud`; on a click fire a haptic pulse through the existing `HudHapticBridge` and invoke the dispatcher's `onCellSelected`. Folder cells re-run `contentLoader.load(resourceId, currentPath = folderPath)` and re-render (in-headset drill-down). Reserved edge bands scroll the grid page.

**Verification:**

- `Grep` - `fun onNativeRayInteraction(` present in `ImmersiveBrowseActivity.kt`.
- `Grep` - `ImmersiveBrowseInteractionDispatcher` referenced.
- `Grep` - `HudHapticBridge` referenced.

**Status:** `[x]` done

---

### Step 03.5 - Selection -> in-process playback transition + back navigation

**Files:** `ui/xr/ImmersiveBrowseActivity.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> On `onCellSelected` for a media (non-folder) cell: set state = PLAYBACK, hide/blank the grid quad, and hand the cell's file to `ImmersiveBrowsePlaybackController.playImage/playVideo` on the main media quad (no session re-launch - same OpenXR session, per §8 handoff risk). Provide a HUD/back affordance that returns state -> BROWSE: call `playbackController.stop()`, re-push the grid. Controller `onBackPressed` (or the native back gesture) returns to BROWSE when in PLAYBACK, otherwise finishes the Activity (returns to the flat app). No state left half-torn between BROWSE and PLAYBACK.

**Verification:**

- `Grep` - `PLAYBACK` and `BROWSE` state references present.
- `Grep` - `playbackController.stop()` referenced.

**Status:** `[x]` done

---

### Step 03.6 - Injections, lifecycle release, heap discipline

**Files:** `ui/xr/ImmersiveBrowseActivity.kt`
**Depends on:** Step 03.5

**Prompt for developer:**

> Field-inject (`@Inject`) `ImmersiveBrowseContentLoader`, `ImmersiveThumbnailDecoder`, `ImmersiveBrowsePlaybackController`, `DiagnosticXrRuntime`, `VrLaunchPayloadHolder`. In `onPause`/`onStop`: pause the render thread and ExoPlayer (release heavy resources immediately, Rule 18). In `onDestroy`: `playbackController.stop()`, `thumbnailDecoder.release()`, stop the render thread, remove every native/listener callback (listener symmetry). No `Log.d` (Timber only). No broad empty catch.

**Verification:**

- `Grep` - `@Inject` field injections for the four helpers present.
- `Grep` - `override fun onDestroy` present and calls `stop()` + `release()`.
- `Grep -n "Log\.d\("` returns zero hits in `ImmersiveBrowseActivity.kt`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fkn` (noLegal) and `.\a.ps1 vr debug` (VR source set), plus `.\a.ps1 fc` (standard No-Op unaffected).
- [ ] `ImmersiveBrowseActivity.kt` < 1500 LOC (target ≤ 700).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new `src/vr` classes) - deferred to Phase 06 batch.

---

## Handoff Notes to Next Phase

`ImmersiveBrowseActivity` exists and compiles but is not yet reachable - it has no manifest entry and no gateway route. Phase 04 declares it in `src/vr/AndroidManifest.xml` and routes `RESOURCE_BROWSE` to it in `XrEntryGatewayImpl`.

---

## Rollback Plan

Revert the phase commit - three new `src/vr` files with no manifest/gateway wiring yet; `DiagnosticXrActivity` untouched, so the diagnostic path is unaffected.

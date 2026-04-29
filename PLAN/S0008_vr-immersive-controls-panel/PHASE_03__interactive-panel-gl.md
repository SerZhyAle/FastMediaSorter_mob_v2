# Phase 03 — Interactive Panel GL

**Strategic spec:** [`../spec_vr-immersive-controls-panel.md`](../spec_vr-immersive-controls-panel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 0 / 8
**Started:** —
**Completed:** —

---

## Objective

Create a second OpenXR Quad swapchain (`VrInteractivePanelRenderer`) backed by a Canvas-based composer (`VrInteractivePanelComposer`) that draws interactive controls with hover highlighting. Extend `VrHudState` with panel-specific fields. Wire the new renderer into `OpenXrSessionManager` via new JNI calls. Replace the existing `VrControlOverlayManager` show/hide path with the new GL panel, keeping the public `show()` / `hide()` API intact.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`VrControlOverlayManager` extended with all Row 2 buttons).
- [ ] Phase 02 is ✅ Done (`VrControllerRayManager` wired; native emits `onControllerPointerMove`).
- [ ] Research Q3 resolved: quad layer order in `xrEndFrame` confirmed (panel quad must appear after video layer but before or alongside HUD).
- [ ] Working tree is clean or on a feature branch.
- [ ] `OpenXrNative.cpp` backed up (see Step 2.1 from Phase 02 — reuse that backup or create a fresh one in `temp/`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudState.kt` | Modified | ≤ 120 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelRenderer.kt` | **New** | ≤ 140 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelComposer.kt` | **New** | ≤ 400 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelDriver.kt` | **New** | ≤ 250 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrNative.kt` | Modified | ≤ 170 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt` | Modified | ≤ 570 |
| `app_v2/src/vr/cpp/OpenXrNative.cpp` | Modified | ≤ 3300 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControlOverlayManager.kt` | Modified | ≤ 450 |

---

## Steps

### Step 3.1 — Extend VrHudState with interactive panel fields

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudState.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the following optional fields to `VrHudState` data class (all nullable, default null):
>
> - `brightnessPercent: Int?` — current brightness 0–100. Null = not shown.
> - `playbackSpeed: Float?` — current speed (0.5, 0.75, 1.0, 1.25, 1.5, 2.0). Null = not shown.
> - `audioTrackLabel: String?` — current audio track label (e.g. `"Track 1 | RUS"`). Null = not shown.
> - `panelVisible: Boolean = false` — whether the interactive GL panel is shown. Drives `VrInteractivePanelDriver`.
> - `hoveredZoneId: Int = -1` — ID of the hit zone currently under the ray cursor. -1 = none. Drives composer highlight.
> - `seekDragFraction: Float = -1f` — fractional seek position (0f–1f) while trigger is held on the seek slider. -1f = not dragging.
>
> Do not remove or rename existing fields. Add fields at the end of the constructor parameter list.

**Verification:**

- `Grep` — `brightnessPercent: Int?` found in `VrHudState.kt`.
- `Grep` — `panelVisible: Boolean` found in `VrHudState.kt`.
- `Grep` — `hoveredZoneId: Int` found in `VrHudState.kt`.
- `Grep` — `seekDragFraction: Float` found in `VrHudState.kt`.
- File size — `VrHudState.kt` ≤ 120 lines.

**Status:** `[ ]` not done

---

### Step 3.2 — Add JNI bindings for panel swapchain in OpenXrNative.kt

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrNative.kt`
**Depends on:** — start of phase (parallel with Step 3.1)

**Prompt for developer:**

> In `OpenXrNative.kt` add the following four `@JvmStatic external fun` declarations inside the existing `internal object OpenXrNative`:
>
> ```kotlin
> @JvmStatic external fun nativeCreatePanelSwapchain(width: Int, height: Int): Boolean
> @JvmStatic external fun nativeDestroyPanelSwapchain()
> @JvmStatic external fun nativeSetPanelLayerVisible(visible: Boolean)
> @JvmStatic external fun nativeUploadPanelBitmap(bitmap: Bitmap): Boolean
> ```
>
> Also add companion wrappers to `OpenXrSessionManager` (mirror the pattern used by `createHudSwapchain` / `uploadHudBitmap`):
>
> ```kotlin
> fun createPanelSwapchain(width: Int, height: Int) = OpenXrNative.nativeCreatePanelSwapchain(width, height)
> fun destroyPanelSwapchain() = OpenXrNative.nativeDestroyPanelSwapchain()
> fun setPanelLayerVisible(visible: Boolean) = OpenXrNative.nativeSetPanelLayerVisible(visible)
> fun uploadPanelBitmap(bitmap: Bitmap) = OpenXrNative.nativeUploadPanelBitmap(bitmap)
> ```
>
> Do not touch any existing functions.

**Verification:**

- `Grep` — `nativeCreatePanelSwapchain` found in `OpenXrNative.kt`.
- `Grep` — `nativeDestroyPanelSwapchain` found in `OpenXrNative.kt`.
- `Grep` — `nativeSetPanelLayerVisible` found in `OpenXrNative.kt`.
- `Grep` — `nativeUploadPanelBitmap` found in `OpenXrNative.kt`.
- `Grep` — `createPanelSwapchain` found in `OpenXrSessionManager.kt`.

**Status:** `[ ]` not done

---

### Step 3.3 — Implement panel swapchain in OpenXrNative.cpp

**Files:** `app_v2/src/vr/cpp/OpenXrNative.cpp`
**Depends on:** Step 3.2

**Prompt for developer:**

> Add four JNI functions for panel swapchain lifecycle, mirroring the existing HUD swapchain functions (`nativeCreateHudSwapchain`, `nativeDestroyHudSwapchain`, `nativeSetHudLayerVisible`, `nativeUploadHudBitmap`):
>
> - `Java_..._nativeCreatePanelSwapchain(JNIEnv*, jclass, jint width, jint height)` — allocate a second `XrSwapchain` (`g_panelSwapchain`) with RGBA8 format, `usage = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT | XR_SWAPCHAIN_USAGE_SAMPLED_BIT`.
> - `Java_..._nativeDestroyPanelSwapchain` — destroy `g_panelSwapchain`, null it.
> - `Java_..._nativeSetPanelLayerVisible(jboolean visible)` — set `g_panelLayerVisible` flag. When false, exclude the panel quad from `xrEndFrame` composition layers.
> - `Java_..._nativeUploadPanelBitmap(JNIEnv*, jclass, jobject bitmap)` — same as HUD upload: `AndroidBitmap_lockPixels` → `glTexSubImage2D` → `AndroidBitmap_unlockPixels`. Upload to `g_panelSwapchain` texture.
>
> Add the panel quad to `xrEndFrame` composition layers array immediately after the HUD quad (when `g_panelLayerVisible && g_panelSwapchain != XR_NULL_HANDLE`). Panel quad dimensions: default 1024×512; position: head-locked, centred horizontally, anchored below the video layer (use `g_uiPlaneDistance` with a vertical offset of –0.35 m).
>
> Guard all new globals with appropriate null/init checks. Use `LOG_D` / `LOG_W` macros.

**Verification:**

- `Grep` — `g_panelSwapchain` found in `OpenXrNative.cpp`.
- `Grep` — `g_panelLayerVisible` found in `OpenXrNative.cpp`.
- `Grep` — `nativeCreatePanelSwapchain` JNI function found in `OpenXrNative.cpp`.
- `Grep` — `nativeUploadPanelBitmap` JNI function found in `OpenXrNative.cpp`.
- File size — `OpenXrNative.cpp` ≤ 3300 lines.

**Status:** `[ ]` not done

---

### Step 3.4 — Create VrInteractivePanelRenderer

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelRenderer.kt`
**Depends on:** Step 3.2

**Prompt for developer:**

> Create `VrInteractivePanelRenderer(private val sessionManager: OpenXrSessionManager)`. Model it exactly after `VrHudRenderer`, replacing HUD JNI calls with panel equivalents:
>
> - `fun ensureSwapchainCreated(): Boolean` → calls `sessionManager.createPanelSwapchain(width, height)`.
> - `fun submit(producer: (Canvas) -> Unit): Boolean` → same bitmap + canvas pattern as `VrHudRenderer.submit`.
> - `fun setVisible(visible: Boolean)` → calls `sessionManager.setPanelLayerVisible(visible)`.
> - `fun release()` → calls `sessionManager.destroyPanelSwapchain()`, recycles bitmap.
>
> Default dimensions: `DEFAULT_WIDTH = 1024`, `DEFAULT_HEIGHT = 512`.
>
> Use Timber for logging. No `Log.d`.

**Verification:**

- `Glob` — `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelRenderer.kt` exists.
- `Grep` — `class VrInteractivePanelRenderer` in that file.
- `Grep` — `fun ensureSwapchainCreated` in that file.
- `Grep` — `fun setVisible` in that file.
- `Grep` — `DEFAULT_WIDTH = 1024` and `DEFAULT_HEIGHT = 512` in that file.
- `Grep` — `Log\.d(` returns zero hits in that file.
- File size — `VrInteractivePanelRenderer.kt` ≤ 140 lines.

**Status:** `[ ]` not done

---

### Step 3.5 — Create VrInteractivePanelComposer

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelComposer.kt`
**Depends on:** Step 3.1

**Prompt for developer:**

> Create `VrInteractivePanelComposer(private val context: Context, private val width: Int = 1024, private val height: Int = 512)`. Pure Canvas painter — no allocations inside `draw()`.
>
> Layout (at 1024×512):
>
> ```text
> ┌──────────────────────────────────────────────────────────┐
> │  [◀◀ -10s]  [◀ Prev]  [⏯]  [Next ▶]  [+30s ▶▶]        │  Row 1: nav buttons
> │  ─────────────────────────────────── (seek slider)       │  Row 2: seek bar
> │  [00:03 / 01:22:05]           (time labels)              │  Row 3: time
> │  [Vol -] [▓░░] [Vol +]  [Bright -] [▓░] [Bright +]     │  Row 4: vol + bright
> │  [Speed: 1.0x]  [Track: RUS]  [Format: 360° SBS] [Exit] │  Row 5: meta + exit
> └──────────────────────────────────────────────────────────┘
> ```
>
> Each interactive zone is defined by a `PanelZone(id: Int, bounds: RectF, label: String)`. Expose `fun zoneAt(u: Float, v: Float): PanelZone?` that returns the zone containing UV point (u, v ∈ [0,1]). Expose `fun getAllZones(): List<PanelZone>` for the hit-tester.
>
> `fun draw(state: VrHudState, canvas: Canvas)`:
> - Clear to transparent.
> - Apply the same Y-flip (`canvas.scale(1f, -1f, ...)`) as `VrHudSceneComposer`.
> - Draw a dark semi-transparent pill background for the whole panel.
> - For each zone: draw button outline; if `state.hoveredZoneId == zone.id` apply a lighter highlight fill.
> - Draw seek slider bar using `state.positionMs` / `state.totalMs`; if `state.seekDragFraction >= 0f`, use that fraction instead.
> - Draw current values for vol/brightness/speed/track/format from `VrHudState` fields.
>
> Define zone IDs as `companion object` constants (e.g. `ZONE_PREV = 1`, `ZONE_NEXT = 2`, `ZONE_PLAY_PAUSE = 3`, `ZONE_SEEK_BACK = 4`, `ZONE_SEEK_FWD = 5`, `ZONE_SEEK_SLIDER = 6`, `ZONE_VOL_DOWN = 7`, `ZONE_VOL_UP = 8`, `ZONE_BRIGHT_DOWN = 9`, `ZONE_BRIGHT_UP = 10`, `ZONE_SPEED = 11`, `ZONE_TRACK = 12`, `ZONE_FORMAT = 13`, `ZONE_EXIT = 14`).
>
> Use Timber for logging. No `Log.d`.

**Verification:**

- `Glob` — `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelComposer.kt` exists.
- `Grep` — `class VrInteractivePanelComposer` in that file.
- `Grep` — `fun zoneAt` in that file.
- `Grep` — `fun getAllZones` in that file.
- `Grep` — `ZONE_SEEK_SLIDER` constant in that file.
- `Grep` — `ZONE_EXIT` constant in that file.
- `Grep` — `Log\.d(` returns zero hits in that file.
- File size — `VrInteractivePanelComposer.kt` ≤ 400 lines.

**Status:** `[ ]` not done

---

### Step 3.6 — Create VrInteractivePanelDriver

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelDriver.kt`
**Depends on:** Step 3.4, Step 3.5

**Prompt for developer:**

> Create `VrInteractivePanelDriver(private val renderer: VrInteractivePanelRenderer, private val composer: VrInteractivePanelComposer)`. Models `VrHudSceneDriver` but for the interactive panel.
>
> State: maintains a mutable `VrHudState`; all mutations run on the main looper.
>
> Public API:
> - `fun show()` — set `state.panelVisible = true`, call `renderer.setVisible(true)`, trigger redraw.
> - `fun hide()` — set `state.panelVisible = false`, call `renderer.setVisible(false)`.
> - `fun toggle()` — flip `panelVisible`.
> - `fun updateHoverZone(zoneId: Int)` — update `state.hoveredZoneId`, trigger redraw.
> - `fun updateSeekDrag(fraction: Float)` — update `state.seekDragFraction`, trigger redraw.
> - `fun updateVolume(percent: Int)` — update `state.volumePercent`, trigger redraw.
> - `fun updateBrightness(percent: Int)` — update `state.brightnessPercent`, trigger redraw.
> - `fun updateSpeed(speed: Float)` — update `state.playbackSpeed`, trigger redraw.
> - `fun updateTrackLabel(label: String)` — update `state.audioTrackLabel`, trigger redraw.
> - `fun updateFormatLabel(label: String)` — update `state.stereoModeLabel`, trigger redraw.
> - `fun updateProgress(positionMs: Long, bufferedMs: Long, totalMs: Long)` — update progress fields.
> - `fun release()` — call `renderer.release()`.
>
> Auto-hide: `scheduleAutoHide()` — post a `Handler` runnable to call `hide()` after `AUTO_HIDE_DELAY_MS = 10_000L`. Cancel and reschedule on any user interaction (any `update*` call while panel is visible). Reset on `show()`.
>
> Use Timber. No `Log.d`.

**Verification:**

- `Glob` — `app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelDriver.kt` exists.
- `Grep` — `class VrInteractivePanelDriver` in that file.
- `Grep` — `fun show()` in that file.
- `Grep` — `fun toggle()` in that file.
- `Grep` — `AUTO_HIDE_DELAY_MS` constant in that file, value `10_000L`.
- `Grep` — `fun updateHoverZone` in that file.
- `Grep` — `fun release()` in that file.
- `Grep` — `Log\.d(` returns zero hits in that file.
- File size — `VrInteractivePanelDriver.kt` ≤ 250 lines.

**Status:** `[ ]` not done

---

### Step 3.7 — Replace VrControlOverlayManager show/hide with GL panel toggle

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControlOverlayManager.kt`
**Depends on:** Step 3.6

**Prompt for developer:**

> In `VrControlOverlayManager`, add an optional constructor parameter `private val panelDriver: VrInteractivePanelDriver? = null`.
>
> Override `show()`, `hide()`, and `toggle()` so that when `panelDriver != null`:
> - `show()` → calls `panelDriver.show()` instead of the View-based overlay path; do NOT show the 2D View overlay.
> - `hide()` → calls `panelDriver.hide()`.
> - `toggle()` → calls `panelDriver.toggle()`.
>
> When `panelDriver == null` (fallback / unit-test mode), preserve the existing 2D View logic unchanged.
>
> The `dispatchCommand()` method is NOT changed here — it remains wired to `onCommand` callback.

**Verification:**

- `Grep` — `panelDriver` field exists in `VrControlOverlayManager.kt`.
- `Grep` — `panelDriver?.show()` in `VrControlOverlayManager.kt`.
- `Grep` — `panelDriver?.hide()` in `VrControlOverlayManager.kt`.
- `Grep` — `Log\.d(` returns zero hits in `VrControlOverlayManager.kt`.

**Status:** `[ ]` not done

---

### Step 3.8 — Wire VrInteractivePanelDriver into VrPlayerActivity

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`
**Depends on:** Step 3.6, Step 3.7

**Prompt for developer:**

> In `VrPlayerActivity` (inside `initializeVrRenderPipeline()` or its equivalent):
> - Construct `VrInteractivePanelRenderer(sessionManager)`.
> - Construct `VrInteractivePanelComposer(this)`.
> - Construct `VrInteractivePanelDriver(renderer, composer)`.
> - Call `panelDriver.renderer.ensureSwapchainCreated()` on the GL thread (after session is ready, alongside `hudRenderer.ensureSwapchainCreated()`).
> - Pass `panelDriver` to `VrControlOverlayManager(…, panelDriver = panelDriver)`.
> - In the XR session teardown path: call `panelDriver.release()` alongside `hudRenderer.release()`.
>
> Do not add logic directly to `VrPlayerActivity` — delegate to the manager / driver. If `VrPlayerActivity` already delegates construction to `initializeVrRenderPipeline()`, keep it that way.

**Verification:**

- `Grep` — `VrInteractivePanelRenderer` instantiation found in `VrPlayerActivity.kt` or the file that calls `initializeVrRenderPipeline`.
- `Grep` — `VrInteractivePanelDriver` instantiation found.
- `Grep` — `panelDriver.release()` called at teardown.
- `Grep` — `Log\.d(` returns zero hits in touched files.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 3.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `OpenXrNative.cpp` ≤ 3300 lines.
- [ ] `VrInteractivePanelComposer.kt` ≤ 400 lines.
- [ ] On device (Quest 3): pressing the controls button shows a GL panel quad with buttons visible. (Manual test — document in Blockers Log if unavailable.)
- [ ] `Grep` for `Log\.d(` in every Kotlin file touched returns zero hits.
- [ ] Dev log entries:

  ```powershell
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrHudState.kt" "feature" "Phase 03: add panel-specific fields (brightness, speed, track, panelVisible, hoveredZoneId, seekDragFraction)"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelRenderer.kt" "feature" "Phase 03: new VrInteractivePanelRenderer (panel swapchain owner)"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelComposer.kt" "feature" "Phase 03: new VrInteractivePanelComposer (Canvas painter for interactive panel)"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/render/VrInteractivePanelDriver.kt" "feature" "Phase 03: new VrInteractivePanelDriver (state machine + auto-hide for GL panel)"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrNative.kt" "feature" "Phase 03: add panel swapchain JNI bindings"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/cpp/OpenXrNative.cpp" "feature" "Phase 03: implement panel swapchain, upload, and quad layer in xrEndFrame"
  .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/ui/VrControlOverlayManager.kt" "feature" "Phase 03: delegate show/hide/toggle to VrInteractivePanelDriver when available"
  ```

---

## Handoff Notes to Next Phase

- `VrInteractivePanelComposer` exposes `zoneAt(u, v)` and `getAllZones()` — Phase 04 uses these to resolve ray hits into zone IDs.
- `VrInteractivePanelDriver.updateHoverZone(id)` and `updateSeekDrag(fraction)` are the entry points Phase 04 calls when the hit-test resolves.
- `VrInteractivePanelDriver.AUTO_HIDE_DELAY_MS = 10_000L` is the canonical auto-hide constant for Phase 05 verification.
- The panel quad transform (position, orientation) is set in native — Phase 04 must use the same transform when computing ray-plane intersection in `VrRayPanelHitTester`.

---

## Rollback Plan

Revert phase commits. New files (`VrInteractivePanelRenderer`, `VrInteractivePanelComposer`, `VrInteractivePanelDriver`) are deleted. `VrControlOverlayManager` reverts to the Phase 01 state (2D overlay). `VrHudState` loses the new fields — callers that reference them must also be reverted. Native swapchain changes revert; `OpenXrNative.cpp` backup in `temp/` restores pre-phase state if needed.

---

## Revision History

- **2026-04-26** — by `/spec-update` (`claude-sonnet-4-6`, focus: all, --tactical --apply-all)
  - ACCEPT applied: 2 (A1: code fence language tag `text` added to layout diagram in Step 3.5; MD031 blank line before dev-log powershell block)
  - REVIEW applied: 1 (R2: Files Touched — added `OpenXrSessionManager.kt | Modified | ≤ 570` row; Step 3.2 adds 4 wrapper methods to it)
  - DISCUSS proposed: 0

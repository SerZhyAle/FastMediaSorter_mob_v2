# Specification: VR — 3DVR Panel ↔ Immersive Toggle Button + Media3 1.2.1 Effects Deferral Fix

**Status:** Draft
**Date:** 2026-04-23
**Tier:** 3 — Moderate (medium risk)
**Roadmap entry:** Follow-up to [PLAN/spec_vr.md](spec_vr.md) Phase D (on-device Quest validation). Adds a user-requested explicit toggle between panel and immersive routes on the VR flavor and repairs the Media3 1.2.1 effects-pipeline crash (`errorCode=7001`, `Presentation.createForWidthAndHeight(-1,-1)`) that blocks VR release playback before the first decoded frame.

---

## 1. Problem Statement

On the `vr` flavor release build, opening a 3D/stereoscopic video currently ends in a dead-end state: the Media3 1.2.1 effects pipeline installs `CompositingVideoSinkProvider` eagerly when `setVideoEffects(..)` is called by [PlayerSetupHelper.applyConfiguredVideoEffects](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt) and crashes with `errorCode=7001` the moment the decoder emits its first `video/raw` format with dimensions `[-1, -1]` (see [logs/fastmediasorter_20260423_002432.log:77-117](../logs/fastmediasorter_20260423_002432.log#L77-L117)). The decoder then never delivers frames to the OpenXR bridge surface, so the user sees neither a panel preview nor an immersive view. Separately, even when playback works, there is no explicit control to jump from the flat panel player into the immersive player (or back) on a per-file basis — the route is chosen once by [VrRouteDecisionHelper](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelper.kt) at file load and the user cannot change their mind without navigating away from the file.

## 2. Goals

1. Apply `setVideoEffects(..)` only after ExoPlayer reports a valid video size (`onVideoSizeChanged(width>0, height>0)`), eliminating the `errorCode=7001` crash while keeping stereo-Crop, hue and brightness effects fully functional.
2. Add a `3DVR` bottom-bar button in [CommandPanelController](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt) visible only when `BuildConfig.SUPPORT_VR_PLAYER = true` and the current file is `MediaType.VIDEO`; clicking it re-opens the current file in `VrPlayerActivity` with a force-immersive intent extra.
3. In [VrPlayerActivity](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt) the same bottom-bar slot carries an `Exit 3D` button that re-opens the current file in `PlayerActivity` (standard panel) preserving playback position.
4. Duplicate the `Exit 3D` action on a Quest controller input: left thumbstick click (`KEYCODE_BUTTON_THUMBL`) returns to panel; existing X/B/Back still perform full exit to Browse.
5. Honour `disable3dVr` kill-switch: when enabled, the `3DVR` button stays hidden in panel mode (the VR host is already bypassed globally by [VrRouteDecisionHelper](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelper.kt) today).

Non-goals for this spec:
- Changing the automatic route decision for the initial launch (stays with `VrRouteDecisionHelper`).
- Adding the same toggle for images, GIFs, PDFs or audio — video only.
- Implementing the toggle button inside `VrControlOverlayManager` (QuadLayer overlay); the request is for the 2D command bar, not the XR head-locked overlay.
- Upgrading Media3 to 1.3+ (separate initiative).

---

## 3. Flavor & API Level Scope

### 3.1 Product Flavor Impact

| Flavor | Affected? | Notes |
|--------|:---------:|-------|
| `standard` | ✅ | Media3 effects deferral fix applies — benefit even without VR host. |
| `lite`     | ✅ | Media3 effects deferral fix applies. |
| `photos`   | ✅ | Media3 effects deferral fix applies (hue/brightness for images via ExoPlayer? — N/A, but the deferral in `PlayerSetupHelper` is shared code). |
| `legacy`   | ✅ | Media3 effects deferral fix applies. |
| `vr`       | ✅ | Primary target: 3DVR toggle button + controller remap. |

Gating flag: `BuildConfig.SUPPORT_VR_PLAYER` (already declared in [app_v2/build.gradle.kts](../app_v2/build.gradle.kts) lines 126, 152, 176, 203, 253, 300). No new flag needed.

### 3.2 Android API Level Forks

| API level | Behavior / Constraint |
|-----------|-----------------------|
| 26+ (standard minSdk) | Default path. `KEYCODE_BUTTON_THUMBL` available since API 9, no guard needed. |
| 32+ (Quest 3 HorizonOS) | Primary target for the VR flavor. |
| 34/35 (compileSdk) | Re-verify `Intent.setClass(..)` activity routing under predictive-back enabled targets; already exercised by [launchStandardPlayerFallback](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt#L722). |

No legacy-flavor-specific branches needed: the `vr` flavor uses `minSdk 26` which matches the main track, and `legacy` does not ship the VR host.

### 3.3 Wear OS Impact

No Wear OS changes required. The `wear/` module has no player or effects pipeline.

---

## 4. Current Architecture (Relevant Parts)

| Component | Location | Role |
|-----------|----------|------|
| `PlayerActivity` | [ui/player/PlayerActivity.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt) (708 lines) | Panel player host; reads `BuildConfig.PLAYER_ACTIVITY_CLASS` for entry routing (but is itself the standard entry). |
| `VrPlayerActivity` | [vr/VrPlayerActivity.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt) (975 lines) | Extends `PlayerActivity`; adds XR session, `VrRouteDecisionHelper`, per-file route resolution, controller `dispatchKeyEvent` bindings (X/B/Menu/Back). |
| `VrLaunchRoute` | [vr/VrLaunchRoute.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrLaunchRoute.kt) | Enum: `STANDARD_PANEL_FALLBACK`, `IMMERSIVE_VIDEO`, `IMMERSIVE_STATIC_IMAGE`, `UNSUPPORTED_IMMERSIVE_WITH_MESSAGE`. |
| `VrRouteDecisionHelper` | [vr/helpers/VrRouteDecisionHelper.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelper.kt) | Maps `(file, stereoMode, settings) → VrLaunchRoute`. No notion of "user explicitly requested immersive for this launch". |
| `CommandPanelController` | [ui/player/CommandPanelController.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt) (986 lines — at limit) | Wires click listeners on the bottom command bar; owns visibility rules. |
| `CommandPanelLayoutPlanner` | [ui/player/helpers/CommandPanelLayoutPlanner.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/CommandPanelLayoutPlanner.kt) | Portrait overflow priorities for center-group commands. |
| `PlayerCommandPanelCallbackImpl` | [ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt) | Dispatches callbacks from bar taps to activity/managers. |
| `VideoPlayerManager` | [ui/player/VideoPlayerManager.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt) (770 lines) | Owns `ExoPlayer`, `StereoVideoProcessor`, `VideoColorProcessor`; has `applyStereoEffect` that already gates the stereo Crop behind `SUPPORT_VR_PLAYER` but not the hue/brightness effects. |
| `PlayerSetupHelper` | [ui/player/helpers/PlayerSetupHelper.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt) | Hosts `applyConfiguredVideoEffects` — the unconditional `setVideoEffects(..)` call site causing the crash. |
| `StandaloneViewManager` | [ui/player/helpers/StandaloneViewManager.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt) | Parallel standalone player also calls `setVideoEffects` ([line 334](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt#L334)). |
| `activity_player_unified.xml` | [res/layout/activity_player_unified.xml](../app_v2/src/main/res/layout/activity_player_unified.xml), [layout-land/](../app_v2/src/main/res/layout-land/activity_player_unified.xml) | Bottom command bar — new `ImageButton` slot required. |

Key gaps today:
- `setVideoEffects(..)` fires before the decoder produces a first frame → Media3 1.2.x auto-Presentation blows up on `-1 × -1`.
- `VrRouteDecisionHelper` has no way to accept a per-launch override ("this specific launch must go immersive regardless of auto-detected stereo mode"), which is exactly what the 3DVR toggle needs.
- There is no existing UI surface from which the user can re-route an already-opened file between panel and immersive.

---

## 5. Proposed Architecture

### 5.1 Media3 effects deferral (root-cause fix for `errorCode=7001`)

In [VideoPlayerManager](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt) add a volatile size-ready gate and a pending-effects flag:

```kotlin
@Volatile internal var videoSizeKnown: Boolean = false
@Volatile internal var pendingEffectsApply: Boolean = false
```

In the existing `playerListener: Player.Listener` extend `onVideoSizeChanged` (the listener object already exists at [VideoPlayerManager.kt:235](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt#L235)):

```kotlin
override fun onVideoSizeChanged(videoSize: VideoSize) {
    if (videoSize.width > 0 && videoSize.height > 0 && !videoSizeKnown) {
        videoSizeKnown = true
        if (pendingEffectsApply) {
            pendingEffectsApply = false
            applyConfiguredVideoEffects()
        }
    }
}
```

In `onMediaItemTransition` / `createPlayer` reset the gate: `videoSizeKnown = false` at every fresh media item so a new file re-arms the deferral.

Modify [applyConfiguredVideoEffects](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt#L91) to defer when the gate is closed:

```kotlin
internal fun VideoPlayerManager.applyConfiguredVideoEffects() {
    val effects = mutableListOf<Effect>()
    stereoVideoProcessor.buildGlEffect(stereoVideoProcessor.getCurrentMode())?.let(effects::add)
    videoColorProcessor.buildHueEffect()?.let(effects::add)
    videoColorProcessor.buildBrightnessEffect()?.let(effects::add)

    if (effects.isEmpty() && !effectsPipelineActive) return

    // Media3 1.2.x bug: setVideoEffects(..) constructs CompositingVideoSinkProvider
    // which auto-creates a Presentation effect from the current input size.
    // Before the decoder emits its first frame that size is (-1, -1) and the shader
    // program creation crashes with errorCode=7001. Defer until the first
    // onVideoSizeChanged with valid dimensions arrives.
    if (!videoSizeKnown && effects.isNotEmpty()) {
        pendingEffectsApply = true
        Timber.d("VideoPlayerManager: applyConfiguredVideoEffects deferred — video size not yet known")
        return
    }

    // rest unchanged: debounced handler + setVideoEffects
}
```

Apply the same guard in [StandaloneViewManager.applyVideoColorEffects](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt#L331-L340): introduce local `videoSizeKnown` + `pendingEffectsApply` flags and a `Player.Listener.onVideoSizeChanged` observer; skip the `setVideoEffects` call while size is unknown.

### 5.2 New classes / files

| Class / File | Location | Lines budget |
|-------------|----------|-------------|
| `VrToggleButtonManager.kt` | `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/` | ≤ 180 |
| `ic_vr_3d.xml` (vector, 24dp) | `app_v2/src/main/res/drawable/` | ≤ 40 |
| `ic_vr_exit.xml` (vector, 24dp) | `app_v2/src/main/res/drawable/` | ≤ 40 |

`VrToggleButtonManager` holds all bar-button wiring for the 3DVR toggle so [CommandPanelController](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt) stays under the 1000-line limit (currently 986). Responsibilities:

- Bind click listener on `binding.btn3dVrCmd` (new view, see §5.4).
- Compute visibility from `(BuildConfig.SUPPORT_VR_PLAYER, state.currentFile?.type == MediaType.VIDEO, settings.disable3dVr == false)`.
- Decide icon + contentDescription: `ic_vr_3d`/`vr_toggle_enter_description` in panel, `ic_vr_exit`/`vr_toggle_exit_description` in immersive.
- Produce the routing `Intent` via a single method `fun launchOppositeRoute(currentFile: MediaFile, currentPosition: Long)` that:
  - in panel mode → starts `Class.forName(BuildConfig.PLAYER_ACTIVITY_CLASS)` (i.e. `VrPlayerActivity`) with `EXTRA_FORCE_IMMERSIVE=true` and `EXTRA_RESUME_POSITION_MS`.
  - in immersive mode → starts `PlayerActivity::class.java` with `EXTRA_RESUME_POSITION_MS`; this follows the exact pattern used today by [launchStandardPlayerFallback](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt#L722-L742).

### 5.3 Architecture Compliance

| Rule | Compliant? | Notes |
|------|:----------:|-------|
| No business logic in Activities/Fragments | ✅ | Routing + visibility decisions live in `VrToggleButtonManager`; Activity only delegates. |
| Naming conventions | ✅ | `VrToggleButtonManager` = `NounVerbManager`. |
| Data flow UI → ViewModel → UseCase → Repository → DataSource | ✅ | Toggle reads `viewModel.state.value.currentFile` and `settings.disable3dVr` only — no new data path. |
| Timber only | ✅ | New logs use Timber. |
| Room schema version incremented | N/A | No DB changes. |
| `StateFlow` for state | ✅ | Uses existing `PlayerViewModel.state` flow via `viewModel.state.value` reads inside callbacks. |
| Hilt DI | ✅ | `VrToggleButtonManager` is instantiated in `CommandPanelController.setupCommandPanelControls`; no new `@Module` binding needed (constructor injection from existing graph: `activity`, `settingsRepository`, `coroutineScope`). |
| File size ≤ 1000 lines | ✅ | `CommandPanelController` gains ~15 lines; `VrPlayerActivity` gains ~25 lines; `PlayerCommandPanelCallbackImpl` gains ~6 lines. All stay below 1000. `VideoPlayerManager` gains ~20 lines (new listener override + flags). |

### 5.4 UI layout changes (portrait + landscape)

Add one new `ImageButton` to both [layout/activity_player_unified.xml](../app_v2/src/main/res/layout/activity_player_unified.xml) and [layout-land/activity_player_unified.xml](../app_v2/src/main/res/layout-land/activity_player_unified.xml):

```xml
<ImageButton
    android:id="@+id/btn3dVrCmd"
    android:layout_width="@dimen/player_cmd_button_size"
    android:layout_height="@dimen/player_cmd_button_size"
    android:background="?attr/selectableItemBackgroundBorderless"
    android:contentDescription="@string/vr_toggle_enter_description"
    android:src="@drawable/ic_vr_3d"
    android:visibility="gone"
    app:tint="@color/selector_player_button_tint"
    android:scaleType="centerInside"
    android:padding="@dimen/player_button_padding" />
```

Placement: inside the `topCommandPanel` center group (inside the `<LinearLayout>` at [line 14](../app_v2/src/main/res/layout/activity_player_unified.xml#L14)), immediately after `btnSaveFrameCmd` ([line 67](../app_v2/src/main/res/layout/activity_player_unified.xml#L67)) so a video-dedicated cluster stays adjacent. The button is always `visibility="gone"` in XML; `VrToggleButtonManager` toggles visibility based on flavor + file type at runtime.

No new `CommandPanelLayoutPlanner.PlayerCommand` entry is needed — the button is a fixed right-adjacent video action (same tier as `SAVE_FRAME`), but only visible in `vr` flavor. In portrait, since the VR flavor ships with Quest-only form factor, overflow-spill is not a scenario (Quest panel is effectively landscape-equivalent).

### 5.5 Intent extras + route override

Extend [VrPlayerActivity](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt):

```kotlin
companion object {
    const val EXTRA_FORCE_IMMERSIVE =
        "com.sza.fastmediasorter.vr.force_immersive"
    const val EXTRA_RESUME_POSITION_MS =
        "com.sza.fastmediasorter.vr.resume_position_ms"
    // existing EXTRA_VR_SHELL_LAUNCH_ID preserved
}

private val forceImmersiveThisLaunch: Boolean by lazy {
    intent?.getBooleanExtra(EXTRA_FORCE_IMMERSIVE, false) == true
}
```

Extend [VrRouteDecisionHelper.decide](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelper.kt#L27) to accept a `userForcedImmersive: Boolean` parameter. When `true`:
- `disable3dVr` still wins (kill-switch) — return `STANDARD_PANEL_FALLBACK`.
- Otherwise, VIDEO → `IMMERSIVE_VIDEO`, IMAGE → `IMMERSIVE_STATIC_IMAGE`, else → `UNSUPPORTED_IMMERSIVE_WITH_MESSAGE`. Skip the `isSpherical()/isStereoscopic()` gate.

Forward the flag from [VrPlayerActivity.buildRouteDecision](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt#L669).

### 5.6 Controller key mapping for exit-to-panel

Extend [VrPlayerActivity.dispatchKeyEvent](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt#L544-L576):

```kotlin
KeyEvent.KEYCODE_BUTTON_THUMBL -> {
    // Left thumbstick click — return to panel, preserve file + position.
    Timber.i("VrPlayerActivity: THUMBL click → switch to panel player")
    switchToPanelPreservingPosition("controller-thumbl")
    return true
}
```

New private method `switchToPanelPreservingPosition(reason: String)` extracts the shared code path for the 3DVR exit button and the controller input. It reads `videoPlayerManager.exoPlayer?.currentPosition` before calling `launchStandardPlayerFallback` with an augmented intent that carries `EXTRA_RESUME_POSITION_MS`.

Existing X / B / Back / Menu bindings are unchanged (X/B/Back = full exit to Browse; Menu = open PlaybackControlDialog).

### 5.7 Resume-position preservation

Both directions must resume where the user left off. Reuse the existing position-save machinery: [PlayerLifecycleManager.saveCurrentPlaybackPosition](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/) (called already from [forceStopVrPlayback](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt#L828-L854)). The toggle flow:

1. Read current position from `videoPlayerManager.exoPlayer?.currentPosition ?: 0L`.
2. Call `lifecycleManager.saveCurrentPlaybackPosition()` to persist to the existing store.
3. Build the new `Intent` with `EXTRA_RESUME_POSITION_MS` as a belt-and-braces hint — the receiving Activity reads saved position first, uses intent extra as fallback only if no saved position exists.

---

## 6. Data Flow

```text
PANEL → IMMERSIVE (user taps 3DVR button in PlayerActivity on VR flavor)

User taps btn3dVrCmd
  ↓
CommandPanelController.setup... → VrToggleButtonManager.onClick
  ↓
viewModel.state.value.currentFile  →  currentPositionMs
  ↓
lifecycleManager.saveCurrentPlaybackPosition()
  ↓
Intent(VrPlayerActivity) { putExtra(EXTRA_FORCE_IMMERSIVE, true), EXTRA_RESUME_POSITION_MS }
  ↓
startActivity(intent) + finish()
  ↓
VrPlayerActivity.onCreate → super.onCreate → resolvePlaybackRoute(onResume)
  ↓
VrRouteDecisionHelper.decide(currentFile, stereoMode, settings, userForcedImmersive=true)
  ↓
VrLaunchRoute.IMMERSIVE_VIDEO → startXrInitialization


IMMERSIVE → PANEL (user taps Exit 3D button OR left thumbstick click)

User taps btn3dVrCmd (icon=ic_vr_exit)  OR  dispatchKeyEvent(KEYCODE_BUTTON_THUMBL)
  ↓
VrToggleButtonManager.onClick  OR  VrPlayerActivity.switchToPanelPreservingPosition
  ↓
videoPlayerManager.exoPlayer?.currentPosition  →  currentPositionMs
  ↓
lifecycleManager.saveCurrentPlaybackPosition()
  ↓
xrSessionManager.release()          // ←— graceful XR teardown
  ↓
Intent(PlayerActivity) { EXTRA_RESUME_POSITION_MS }
  ↓
startActivity(intent) + finish()
  ↓
PlayerActivity.onCreate (no XR path; normal panel playback)


MEDIA3 EFFECTS DEFERRAL (applies to every playback start)

createPlayer → exoPlayer.setMediaItem + prepare
  ↓   videoSizeKnown = false, effectsPipelineActive = false
  ↓
applyConfiguredVideoEffects() called (from createPlayer, stereo change, color change)
  ↓   effects.isNotEmpty() AND !videoSizeKnown
  ↓
pendingEffectsApply = true  →  return (NO setVideoEffects call yet)
  ↓
MediaCodec decodes first frame
  ↓
playerListener.onVideoSizeChanged(width>0, height>0)
  ↓   videoSizeKnown = true
  ↓   pendingEffectsApply = true → applyConfiguredVideoEffects()
  ↓
exoPlayer.setVideoEffects(effects)  ←— safe, size is known, Presentation OK
```

---

## 7. Files to Modify

| File | Change | Est. size after |
|------|--------|-----------------|
| [VideoPlayerManager.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt) | Add `videoSizeKnown`/`pendingEffectsApply` flags; extend `playerListener.onVideoSizeChanged`; reset flags on new media | ~790 |
| [PlayerSetupHelper.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt) | Add defer-until-size-known guard in `applyConfiguredVideoEffects` | ~175 |
| [StandaloneViewManager.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt) | Mirror size gate in `applyVideoColorEffects` + listener wiring | +15 lines |
| [activity_player_unified.xml](../app_v2/src/main/res/layout/activity_player_unified.xml) | Add `btn3dVrCmd` `ImageButton` after `btnSaveFrameCmd` | 277 |
| [layout-land/activity_player_unified.xml](../app_v2/src/main/res/layout-land/activity_player_unified.xml) | Mirror the new button | +4 lines |
| [CommandPanelController.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt) | Add one `VrToggleButtonManager` field, forward click in setup, forward state update in updateCommandAvailability | ~1000 |
| [CommandPanelController.CommandPanelCallback](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt#L45) interface | Add `fun on3dVrToggleClicked()` | +1 line |
| [PlayerCommandPanelCallbackImpl.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt) | Implement `on3dVrToggleClicked` → delegate to activity | ~270 |
| [PlayerActivity.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt) | Thin `on3dVrToggleClicked` delegator → `vrToggleButtonManager.launchOppositeRoute(..)` | ~725 |
| `VrToggleButtonManager.kt` (new) | Create — visibility + routing logic | ≤ 180 |
| `ic_vr_3d.xml` (new) | Create — drawable | ≤ 40 |
| `ic_vr_exit.xml` (new) | Create — drawable | ≤ 40 |
| [VrPlayerActivity.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt) | Add `EXTRA_FORCE_IMMERSIVE`, `EXTRA_RESUME_POSITION_MS`, `forceImmersiveThisLaunch`, `switchToPanelPreservingPosition`; wire `THUMBL` in `dispatchKeyEvent`; pass force flag to `buildRouteDecision` | ~1000 |
| [VrRouteDecisionHelper.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelper.kt) | Add `userForcedImmersive: Boolean = false` parameter; short-circuit logic | ~95 |
| [values/strings.xml](../app_v2/src/main/res/values/strings.xml) | `vr_toggle_enter_description`, `vr_toggle_exit_description` | +2 entries |
| [values-ru/strings.xml](../app_v2/src/main/res/values-ru/strings.xml) | Russian mirror | +2 entries |
| [values-uk/strings.xml](../app_v2/src/main/res/values-uk/strings.xml) | Ukrainian mirror | +2 entries |

All four files over 500 lines — `VideoPlayerManager.kt` (770), `CommandPanelController.kt` (986), `PlayerActivity.kt` (708), `VrPlayerActivity.kt` (975) — get a timestamped backup in `temp/` before modification per CLAUDE.md rule 5.

Approved tests to extend:
- [VrRouteDecisionHelperTest](../app_v2/src/test/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelperTest.kt) — new cases for `userForcedImmersive=true`.

---

## 8. Risk Analysis

| Risk | Likelihood | Mitigation |
|------|:----------:|-----------|
| Media3 size-gate deferral swallows a real `setVideoEffects` call if `onVideoSizeChanged` never fires (audio-only stream or broken codec) | Med | Only defer when the effects list is non-empty; audio-only files have no video size but also produce no effects to defer. Log at `Timber.w` if `pendingEffectsApply` stays true for > 10 s. |
| Flag reset race: user changes hue during decoding transition | Low | The flag is volatile; both `applyConfiguredVideoEffects` and `onVideoSizeChanged` run on the main thread (noted in [PlayerSetupHelper.kt:466 comment](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerManagerInitializer.kt#L466)). Debounce handler already in place coalesces within 80 ms. |
| `EXTRA_FORCE_IMMERSIVE` bypasses user's `disable3dVr` preference | Low | `VrRouteDecisionHelper` checks `disable3dVr` *before* the forced flag. |
| `KEYCODE_BUTTON_THUMBL` collides with system gesture | Low | Quest 3 HorizonOS does not intercept thumbstick clicks in foreground immersive apps (confirmed in [spec_vr.md §5](spec_vr.md#section-5)). If collision appears in validation, fall back to `KEYCODE_BUTTON_THUMBR`. |
| Recreation flicker when switching panel ↔ immersive on a 21 GB file | Med | Resume position is persisted; ExoPlayer rebuilds from that position. Visual flicker is acceptable — `recreate()` pattern is already used by [onNewIntent](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt#L528) without user complaint. |
| Landscape layout misses the new button | Med | Implementation step explicitly updates both `layout/` and `layout-land/` files; regression caught by manual test case. |
| `CommandPanelController` exceeds 1000 lines | Med | New wiring lives in `VrToggleButtonManager`; target delta ≤ 15 lines in the controller. Backup of the controller is mandated before the edit. |
| ABI mismatch between standard/lite/photos/legacy (effects fix) and `vr` | Low | The fix is in `main/` so all flavors benefit uniformly; `BuildConfig.SUPPORT_VR_PLAYER` is only used by the new toggle button, not by the effects deferral. |

---

## 9. Testing Plan

### 9.1 Unit Tests

Extend [VrRouteDecisionHelperTest](../app_v2/src/test/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelperTest.kt) with four new scenarios:

1. `userForcedImmersive=true, video MONO, disable3dVr=false` → `IMMERSIVE_VIDEO`.
2. `userForcedImmersive=true, video MONO, disable3dVr=true` → `STANDARD_PANEL_FALLBACK` (kill-switch wins).
3. `userForcedImmersive=true, image MONO` → `IMMERSIVE_STATIC_IMAGE`.
4. `userForcedImmersive=true, PDF` → `UNSUPPORTED_IMMERSIVE_WITH_MESSAGE`.

No unit tests for `VrToggleButtonManager` (trivial orchestration, no branching logic beyond the route helper which is already covered).

No unit tests for the Media3 deferral gate — covered by manual playback tests below; internal Media3 behaviour is the real assertion.

### 9.2 Manual Test Cases

Build: `.\gradlew.bat assembleVrDebug` and `.\gradlew.bat assembleVrRelease`. Install both to Quest 3; run also `assembleStandardDebug` on a phone for the shared Media3 fix.

Happy path:
1. **VR release, play 180° SBS video** from `/storage/emulated/0/Movies/18VR_The_Best_is_Yet_to_Come_7K_180_180x180_3dh.mp4` — video must start within 5 s, auto-enter immersive, no `errorCode=7001` in logcat.
2. **Panel → immersive toggle**: open a MONO 2D video on VR release, tap `3DVR` button → file re-opens immersively with the same timestamp (within ± 1 s of last playback position).
3. **Immersive → panel toggle (button)**: from inside immersive, tap `Exit 3D` button → file re-opens in panel player with same timestamp.
4. **Immersive → panel toggle (controller)**: press left thumbstick click → equivalent to step 3.
5. **Standard flavor regression**: play a stereo SBS video in `standardDebug` with non-default hue/brightness — no `errorCode=7001`; effects apply visibly after first frame.

Error-state cases:
6. **`disable3dVr=true`**: `3DVR` button must not appear even in `vr` flavor; enabling the setting while in immersive must not affect the currently running file (only subsequent launches).
7. **Audio-only file on VR**: `3DVR` button must be hidden (video-only). Command bar still functional.
8. **Decoder-unsupported file**: a corrupt MP4 that never fires `onVideoSizeChanged` — after 10 s the `Timber.w` diagnostic fires; no hard crash; ExoPlayer's own error path surfaces normally.
9. **X button full-exit** still goes to Browse (not to panel player).
10. **`3DVR` button invisible on non-VR flavors** — regression check on `standardDebug`.

API-level variant:
11. On Quest 3 (API 34) + on a `legacyDebug` install on API 23 device: verify no crashes due to the new `ic_vr_*.xml` vector drawables at `minSdk 23`.

### 9.3 Maestro E2E

Not applicable. Maestro does not drive Quest controllers and cannot validate immersive transitions. Existing Maestro smoke/critical flows on non-VR flavors remain green as long as the `3DVR` button is hidden outside `vr` flavor.

---

## 10. Accessibility

The new button is focusable by TalkBack. Content descriptions live in `strings.xml` with EN/RU/UK mirrors:

- EN: "Switch to 3D VR view" / "Exit 3D VR view"
- RU: "Переключиться в 3D VR-просмотр" / "Выйти из 3D VR-просмотра"
- UK: "Перемкнутися у 3D VR-перегляд" / "Вийти з 3D VR-перегляду"

Button size: `@dimen/player_cmd_button_size` (48 dp minimum — matches the existing command-bar row, satisfies the 48 dp touch target guideline).

Icon drawables (`ic_vr_3d`, `ic_vr_exit`) must have distinct shapes, not rely on colour alone — "3D" glyph vs "exit/arrow-out" glyph — so colour-blind users perceive the mode transition.

Controller `THUMBL` mapping is a duplication for convenience, not a replacement; on-screen button remains primary.

---

## 11. User-Facing Feature Update

- `docs/FEATURES.md` (EN, VR section): `- Explicit 3DVR toggle button on the video player command bar (VR flavor) — re-open the current file in immersive or panel mode without leaving the player; controller thumbstick click also returns to panel.`
- `docs/FEATURES_RU.md` (RU): `- Отдельная кнопка «3DVR» на командной панели видеоплеера (VR-версия) — открыть текущий файл в иммерсивном или панельном режиме без выхода из плеера; клик левого стика контроллера также возвращает в панельный режим.`
- `docs/FEATURES_UK.md` (UK): `- Окрема кнопка «3DVR» на командній панелі відеоплеєра (VR-версія) — відкрити поточний файл в імерсивному або панельному режимі без виходу з плеєра; клік лівого стіка контролера також повертає в панельний режим.`

Run `/doc-update` skill after docs are edited.

---

## 12. Architecture Decision Records (ADRs)

**ADR-1: Defer `setVideoEffects` via a size gate in `VideoPlayerManager`, not via Media3 upgrade.**
- **Decision:** Add `videoSizeKnown`/`pendingEffectsApply` flags and hook `onVideoSizeChanged` to flush pending effects.
- **Alternatives considered:** (a) Upgrade Media3 to 1.3+ where auto-Presentation handles unknown size. (b) Disable the main-flavour effects pipeline entirely on VR.
- **Reason:** (a) is multi-flavor, multi-week risk and out of scope. (b) loses hue/brightness functionality. The deferral is a surgical 1.2.x workaround with zero feature loss and <30 lines of code.

**ADR-2: `3DVR` toggle reroutes via `recreate` + `Intent`, not by dynamically switching Activity classes in place.**
- **Decision:** Tap on `3DVR` (or `Exit 3D`) saves playback position, starts the opposite Activity with the current file intent + `EXTRA_FORCE_IMMERSIVE` / `EXTRA_RESUME_POSITION_MS`, and `finish()`es the current one.
- **Alternatives considered:** Inline switching (spawn XR session from within `PlayerActivity`; conversely dismantle XR inside `VrPlayerActivity` and show panel on the same Activity instance).
- **Reason:** The Activities have different Hilt graphs (`VrPlayerActivity` has `VrModule`), manifest categories, and lifecycle ordering around `super.onCreate`. Inline switching doubles the failure surface. The Intent-hop pattern is already proven in production via [launchStandardPlayerFallback](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt#L722) and [onNewIntent](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt#L528).

**ADR-3: Button visible only for `MediaType.VIDEO`, not for images/GIFs.**
- **Decision:** Scope the first iteration to video. Images, GIFs, PDFs hide the button.
- **Alternatives considered:** Show for images too (immersive static image route already exists).
- **Reason:** The user's request explicitly referenced "стереоскопический видеоплеер"; static-image immersive is a separate phase in [spec_vr.md Phase A](spec_vr.md#section-6). Keeps scope bounded and the behaviour predictable.

**ADR-4: Controller duplication on `KEYCODE_BUTTON_THUMBL`, not a repurposed X/B/Menu.**
- **Decision:** Left thumbstick click returns to panel; X/B/Back keep their current "exit to Browse" semantics; Menu keeps opening PlaybackControlDialog.
- **Alternatives considered:** Repurpose X (exit) to mean "back to panel"; repurpose Menu.
- **Reason:** Breaking existing X/B/Back semantics would surprise users already trained on them. Menu opens a settings dialog that depends on the dialog being reachable from immersive. Thumbstick click is unassigned today and is a natural "mode switch" affordance on Quest.

**ADR-5: `userForcedImmersive` flag does not override `disable3dVr` kill-switch.**
- **Decision:** Kill-switch wins.
- **Alternatives considered:** Let the user's explicit tap on `3DVR` override `disable3dVr`.
- **Reason:** `disable3dVr` is intentionally a global safety valve (reserved for platform/kiosk scenarios). The toggle button hiding when the switch is on already gives the user correct feedback.

---

## 13. Implementation Steps

Prep:

1. Create timestamped backups in `temp/` for every file >500 lines being edited:
   - `temp/VideoPlayerManager.<ts>.backup`
   - `temp/CommandPanelController.<ts>.backup`
   - `temp/PlayerActivity.<ts>.backup`
   - `temp/VrPlayerActivity.<ts>.backup`

Media3 fix (shared across all flavors — land first so VR testing is unblocked):

2. In [VideoPlayerManager.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt): add `videoSizeKnown` + `pendingEffectsApply` volatile flags next to `effectsPipelineActive`. Override `onVideoSizeChanged` in the existing `playerListener` object. Reset both flags at `createPlayer` and on `onMediaItemTransition` if present (add the callback if not).
3. In [PlayerSetupHelper.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt) `applyConfiguredVideoEffects`: insert the deferral guard per §5.1.
4. In [StandaloneViewManager.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt) `applyVideoColorEffects`: mirror the guard; register an `onVideoSizeChanged` listener inside the method.
5. `./scripts/add_to_dev_log.ps1` for each modified file.

UI layout + drawables:

6. Create [ic_vr_3d.xml](../app_v2/src/main/res/drawable/ic_vr_3d.xml) (24 dp vector — choose glyph combining "3D" text + headset silhouette).
7. Create [ic_vr_exit.xml](../app_v2/src/main/res/drawable/ic_vr_exit.xml) (24 dp vector — headset silhouette with arrow-out).
8. Add `btn3dVrCmd` to [activity_player_unified.xml](../app_v2/src/main/res/layout/activity_player_unified.xml) after `btnSaveFrameCmd`.
9. Mirror the button in [layout-land/activity_player_unified.xml](../app_v2/src/main/res/layout-land/activity_player_unified.xml).
10. Add EN/RU/UK strings `vr_toggle_enter_description`, `vr_toggle_exit_description` per §10.

New helper + wiring:

11. Create [VrToggleButtonManager.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/VrToggleButtonManager.kt) per §5.2. Constructor parameters: `activity: PlayerActivity`, `binding: ActivityPlayerUnifiedBinding`, `viewModel: PlayerViewModel`, `settingsRepository: SettingsRepository`, `coroutineScope: CoroutineScope`. Expose `setup()`, `updateVisibility(state)`, `launchOppositeRoute()`.
12. Extend the `CommandPanelCallback` interface in [CommandPanelController.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt) with `fun on3dVrToggleClicked()`.
13. In `CommandPanelController.setupCommandPanelControls`, instantiate `VrToggleButtonManager` and call `.setup()`. In `updateCommandAvailability`, call `.updateVisibility(state)`.
14. Implement `on3dVrToggleClicked` in [PlayerCommandPanelCallbackImpl.kt](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerCommandPanelCallbackImpl.kt) by delegating to `activity.vrToggleButtonManager.launchOppositeRoute(..)`.
15. Add a `vrToggleButtonManager: VrToggleButtonManager` lazily-initialised field on [PlayerActivity](../app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt) (visible to `VrPlayerActivity` via inheritance).

VR route override:

16. Extend [VrRouteDecisionHelper.decide](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrRouteDecisionHelper.kt) with `userForcedImmersive` parameter per §5.5.
17. Update existing unit tests for the new signature (default `= false` to avoid regression).
18. Add four new unit tests per §9.1.
19. In [VrPlayerActivity.kt](../app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt): add `EXTRA_FORCE_IMMERSIVE`, `EXTRA_RESUME_POSITION_MS` constants, `forceImmersiveThisLaunch` lazy, `switchToPanelPreservingPosition` method, THUMBL mapping in `dispatchKeyEvent`, and pass `forceImmersiveThisLaunch` into `buildRouteDecision`.

Controller wiring verification:

20. Manual pass on Quest 3: verify `KEYCODE_BUTTON_THUMBL` (not THUMBR) triggers return-to-panel; if not, switch to THUMBR per §8 risk mitigation.

Validation + docs:

21. Rebuild: `.\scripts\builders\install-vr-release-to-device.ps1` → run manual test cases per §9.2 on Quest 3. Capture a clean logcat via `.\scripts\utils\extract-device-logs.ps1` for the happy path.
22. `/doc-update` to mirror the `FEATURES` entries across EN/RU/UK.
23. Final `./scripts/add_to_dev_log.ps1` pass for every modified file.

Mandatory step checklist at the end:
- [ ] String resources added in EN/RU/UK (`values/`, `values-ru/`, `values-uk/`).
- [ ] `docs/FEATURES.md` + `docs/FEATURES_RU.md` + `docs/FEATURES_UK.md` updated.
- [ ] Room DB migration added + version incremented — N/A, no DB changes.
- [ ] `.\scripts\add_to_dev_log.ps1` run for every modified file.
- [ ] Backups in `temp/` created for every >500-line file before editing.
- [ ] Quest 3 manual validation pass completed (cases 1–11 in §9.2).

---

## 14. Out of Scope (future items)

- Adding the same toggle to the VR QuadLayer overlay (`VrControlOverlayManager`) so the command bar is reachable without leaving immersive XR. Needs separate UI design for head-locked elements.
- Photo / GIF / PDF immersive toggle — pending [spec_vr.md Phase A](spec_vr.md#section-6) photo-sphere renderer.
- Per-file "preferred route" persistence (remember that the user chose immersive for a specific file and skip the panel step next time). Interacts with `StereoFormatOverride` Room entity; to be designed jointly.
- Media3 1.3+ upgrade — removes the `setVideoEffects` deferral workaround, unlocks photo-sphere video filtering primitives.
- Exposing the 3DVR toggle to Wear OS companion remote control (out of current Wear OS scope).

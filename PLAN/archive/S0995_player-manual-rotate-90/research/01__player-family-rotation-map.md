# S0995 Research 01 - Player-family rotation map + mechanism verdict

**Date:** 2026-07-15
**Mode:** read-only codebase research (android-solution-researcher), no code changed.
**Purpose:** F2 tactical-plan input; resolve §6 research items from code; verdict on video-mechanism determinability.

## Headline verdict

All four crux dimensions are **CODE-DETERMINABLE** - none require an on-device mechanism experiment. The strategic spec's §6.1 premise ("PlayerView defaults to SurfaceView, can't transform-rotate, choose mechanism on device") is **factually false for this codebase**. Device is needed only for final *verification*, which is the standard `BlockNeedUserTest` gate.

## A. Player family = TWO independent families (not one shared engine)

- **Family 1 - internal:** `PlayerActivity` (single host), layout `activity_player_unified.xml`. ViewModel `PlayerViewModel` (state `PlayerState` at `PlayerViewModel.kt:132-141`). Video engine `VideoPlayerManager`, reached via `PlayerActivityVideoHandle` implementing shared `VideoPlayerHandle`.
- **Family 2 - standalone:** 5 activities share ONE `StandalonePlayerViewModel` (state `StandalonePlayerState` at `StandalonePlayerViewModel.kt:50-59`) + ONE engine `StandaloneViewManager`, reached via `PhotoVideoStandaloneVideoHandle` implementing `VideoPlayerHandle`. **Only `PhotoVideoStandaloneActivity`** renders both video+images -> the only in-scope standalone host.
- `StandalonePlayerActivity` is `@Deprecated` (S0393, pending removal) - overflow only wires "Open in FMS" - **out of scope, do not touch.**
- `StandalonePlayerDispatcherActivity` is a no-UI trampoline - out of scope.
- **Cross-family apply-layer interface:** `VideoPlayerHandle` (`ui/player/contracts/VideoPlayerHandle.kt`) - has hue/brightness/speed getters/setters today, NO rotation. This is the proven pattern to extend with rotation get/set (mirror `getHueAdjustmentDegrees`/`setHueAdjustmentDegrees` at `:25-26`).

## B. Command/menu infra - wired TWICE, two different mechanisms

- `CommandId.ROTATE = "view.rotate"` declared at `CommandId.kt:38`, **zero other usages** - fully unwired (confirmed).
- **Internal:** dynamic, priority-driven enum `CommandPanelLayoutPlanner.PlayerCommand` (`CommandPanelLayoutPlanner.kt:34-193`); `buildActiveCommands()` (`:212-330`) gates per media type; `CommandPanelController.showOverflowMenu()` (`:566-681`) builds PopupMenu, `setForceShowIcon(true)` (`:584`), icon via `cmd.iconResId`; dispatch `handleOverflowCommand()` (`:684-727`). Low-priority overflow-only precedent: `SLEEP_TIMER(500)`, `OPEN_IN_VR(605)`, `DRAW_OVERLAY(650)` (`:159-193`, `barCapable=false`).
- **Standalone:** static XML `res/menu/overflow_menu_standalone_player.xml` (no icons on any item), inflated per host, items shown/hidden via `popup.menu.findItem(id).isVisible = <cond>` (`PhotoVideoStandaloneActivity.kt:652-675`), dispatch local `when(item.itemId)` (`:677-712`).
- Icon on standalone overflow is a **convention gap** (text-only today) - design decision, not device-gated.

## C. Image path - CODE-DETERMINABLE

- Two views, both in `binding.mediaContentArea` (FrameLayout): `imageView` (plain ImageView, `fitCenter`, `activity_player_unified.xml:167`) and `photoView` (`com.github.chrisbanes.photoview.PhotoView` 2.3.0, `:176`).
- `ImageLoadingManager.setImage()` (`:465,473-475,530`): animated content or full-size-non-slideshow -> `photoView`; slideshow / non-full-size -> plain `imageView`.
- `photoView`: use PhotoView `setRotationTo(float)` (Matrix-based, recomputes fit). No app code calls it today (unused API).
- `imageView`: only `View.rotation` (bounding-box); at 90/270 `fitCenter` does NOT auto-swap w<->h -> needs explicit scale-fit math (no existing pattern - implementation nuance).

## D. Video path - CODE-DETERMINABLE (the crux, de-risked)

- **Already `app:surface_type="texture_view"` in EVERY player layout**: `activity_player_unified.xml:153`, `activity_standalone_photo_video.xml:72`, `activity_standalone_audio.xml:64`, `activity_standalone_document.xml:94`. **No SurfaceView anywhere.** Spec §6.1 option (1) is already done project-wide.
- **No DRM/secure path** (grep `MediaDrm|DrmSessionManager|WIDEVINE|setVideoSurfaceView` -> only an unused pass-through in `NetworkAwareMediaSourceFactory.kt:55-75`, never given a provider). Streams (`StreamPlaybackHelper.kt`) are plain progressive/RTP. -> No secure-decoder constraint blocks TextureView/effects.
- **Existing production `setVideoEffects()` GL pipeline in BOTH families:** `VideoColorProcessor` (`VideoColorProcessor.kt`, builds `HslAdjustment`/`Brightness` from `androidx.media3.effect`); internal `applyConfiguredVideoEffects()` (`PlayerSetupHelper.kt:98-136`); standalone `StandaloneViewManager.kt:518-528`. `androidx.media3:media3-effect:1.2.1` (has `ScaleAndRotateTransformation`) already a dep (`build.gradle.kts:1292`).
- **Mechanism = option (3): add a rotation `Effect` to the existing chain.** Determined from code.

### THREE Media3 1.2.1 effect-pipeline bugs the impl MUST inherit (in-code workarounds already present):
1. `TexturePool.freeTexture -> IllegalStateException` when `setVideoEffects()` called with in-flight frames -> **80ms debounce** (`PlayerSetupHelper.kt:94-96,111-118`).
2. `Presentation.createForWidthAndHeight` crashes at -1,-1 before first frame -> **defer until `videoSizeKnown`/`onVideoSizeChanged`** (`PlayerSetupHelper.kt:111-113`).
3. `release()` hangs main thread with active GL pipeline (androidx/media #1139,#2098) -> **drain `setVideoEffects(emptyList())` before `release()`** (`VideoPlayerLifecycleHelper.kt:51-55` S0893; standalone `releaseVideoPlayer()` S0859).

### Aspect-fit gap (code-known, plannable now):
`PlayerView` auto-sizes its internal `AspectRatioFrameLayout` from `onVideoSizeChanged(VideoSize)` (decoded-track dims) - it is NOT aware of post-decode effect rotation. At 90/270 the displayed aspect swaps but PlayerView won't auto-swap w<->h. No `AspectRatioFrameLayout` reference exists in Kotlin (relies on `resize_mode="fit"` XML). -> impl must manually compensate at 90/270 (container re-measure or compensating scale in the effect chain). Knowable now; a *correctness* item for device verification, not a mechanism choice.

Controls are a sibling overlay outside the effect output -> "controls must not rotate" satisfied for free.

## E. Existing rotations - keep separate (no collision)
1. `RotateImageUseCase` - destructive file rewrite (`domain/usecase/RotateImageUseCase.kt`), wired from EDIT/crop.
2. `toggleRotationSensor()`/`btnEditRotate`/`ROTATION_TOGGLE` - screen-orientation sensor (`PlayerViewModel.kt:711-717`; `CommandPanelLayoutPlanner.kt:97-99` uses `ic_rotation_locked/unlocked` + string `big_btn_short_rotation`="Rotate"). **New command must NOT reuse `big_btn_short_rotation` nor the padlock-rotate icons.**
3. New S0995 - pure visual transform, no file, no sensor. Nothing provides it today.

## F. Session-state home - TWO owners (one per family)
- Internal: `PlayerViewModel.PlayerState` (`:132-141`) - add `sessionRotationAngle: Int = 0`; survives file nav (currentFile derived), cleared on VM destroy = "reset on exit".
- Standalone: `StandalonePlayerViewModel.StandalonePlayerState` (`:50-59`) - same shape/lifecycle; only `PhotoVideoStandaloneActivity` consumes.
- Apply-layer: extend `VideoPlayerHandle` with rotation get/set (mirror hue).

## G. Resources
- Internal overflow items carry `iconResId` (`ic_*`), tinted at `CommandPanelController.kt:610-613`. Standalone overflow = text-only (no icon convention).
- No dedicated "rotate content CW" drawable exists (`ic_rotation_*` are sensor padlocks - wrong semantics). **Need new `ic_rotate_90`.**
- String key pattern `<command>_title`/`_desc`/`big_btn_short_<command>`; locales EN `values/`, RU `values-ru/`, UK `values-uk/`. **`big_btn_short_rotation` already taken** -> new distinct key.

## Determinability verdict (per dimension)
| Dimension | Verdict |
|-----------|---------|
| Image path (C) | CODE-DETERMINABLE (photoView.setRotationTo; imageView needs scale-fit math) |
| Command/menu (B) | CODE-DETERMINABLE (two wiring paths mapped, precedents exist) |
| Session-state home (F) | CODE-DETERMINABLE (two per-family PlayerState owners) |
| **Video mechanism (D)** | **CODE-DETERMINABLE** (reuse effect pipeline + ScaleAndRotateTransformation; NOT a device A/B) |

Remaining device-VERIFICATION items (for `/spec-test-device`, not `/spec-tech`): effect-pipeline crash-resurface with a 4th effect; aspect-fit renders clean at 90/270 frame-to-frame; PiP/fullscreen/cast interaction with active rotate effect.

## /spec-draft candidates (caller to dedup+file)
1. Stale/unverifiable comment: `ImageLoadingManager.kt:482,491` claims PhotoView two-finger rotation gesture is live, but no gesture wiring found and the AAR isn't vendored. Doc-accuracy question, out of S0995 scope.
2. `StandalonePlayerActivity` deprecated dead-weight - already ticketed under S0393, no new draft.

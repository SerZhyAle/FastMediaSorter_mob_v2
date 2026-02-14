# Static Image Playback Implementation Checklist

This checklist operationalizes [STATIC_IMAGE_PLAYBACK_IMPROVEMENT_SPEC.md](STATIC_IMAGE_PLAYBACK_IMPROVEMENT_SPEC.md) into concrete file-by-file implementation steps.

## 0) Execution Rules

- Keep behavior backward-compatible by default.
- Implement behind feature flags where noted.
- Avoid Main-thread I/O and blocking decode operations.
- Complete each phase with build + regression checks before moving forward.

## 1) Phase Plan (High Level)

- Phase 1: Renderer extraction and contract definition.
- Phase 2: Dual-surface transition container.
- Phase 3: Prioritized prefetch queue.
- Phase 4: Gesture-path unification.
- Phase 5: Stabilization, profiling, regression hardening.

## 2) File-by-File Worklist

## A. Core Player and Image Pipeline

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt

- Tasks:
  1. Introduce adapter boundary to new renderer (`StaticImageRenderer` facade).
  2. Move transition orchestration out of direct `ImageView/PhotoView` toggling.
  3. Replace direct preloading calls with priority-driven queue API.
  4. Keep temporary compatibility shim during migration.
- Done when:
  - Image transitions no longer depend on single-view swap logic.
  - Existing slideshow/manual flows still function with migration flag ON.

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt

- Tasks:
  1. Wire renderer lifecycle (`init`, `pause`, `resume`, `release`).
  2. Replace direct static-image view operations with renderer callbacks.
  3. Ensure no duplicate ownership of image state between activity and renderer.
- Done when:
  - Activity acts as coordinator only; renderer owns static image drawing path.

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt

- Tasks:
  1. Add explicit renderer-facing lookahead model (next/prev/optional +2).
  2. Keep slideshow state transitions deterministic and idempotent.
  3. Prevent redundant emits that trigger duplicate image loads.
- Done when:
  - ViewModel exposes stable render-intent updates with no duplicate churn.

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/SlideshowController.kt

- Tasks:
  1. Ensure tick scheduling is cancellation-safe.
  2. Add hooks to query renderer readiness before committing next frame.
  3. Keep countdown and slideshow state in sync with actual transition completion.
- Done when:
  - No slide advance occurs on stale or canceled state.

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/SlideshowManager.kt

- Tasks:
  1. Reconcile with `SlideshowController` responsibilities (remove overlap).
  2. Keep one authoritative slideshow engine path.
- Done when:
  - Only one active slideshow scheduler remains in production path.

## B. New Renderer Components (to create)

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/StaticImageRenderer.kt

- Tasks:
  1. Implement render state machine (`Idle`, `Loading`, `Ready`, `Transitioning`, `Error`).
  2. Own dual-surface transitions and release lifecycle.
  3. Expose minimal API: `render(target)`, `prefetch(list)`, `setMode(mode)`, `onPause/onResume/release`.

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/RenderTarget.kt

- Tasks:
  1. Define immutable render request object (file/path/type/priority flags).
  2. Include mode hints (slideshow/manual, crop/fit).

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/PrefetchQueue.kt

- Tasks:
  1. Implement priority queue (Next > Previous > Lookahead).
  2. Add throttling controls and max depth.
  3. Add congestion-aware degradation hook.

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/TransitionPolicy.kt

- Tasks:
  1. Centralize transition duration and fallback policy.
  2. Disable cross-fade when target is not ready (controlled fallback path).

## C. Layout and View Wiring

### [ ] app_v2/src/main/res/layout/activity_player_unified.xml

- Tasks:
  1. Introduce dual-layer image container for static image rendering.
  2. Keep existing IDs or provide compatibility mapping to avoid breaking binding code.
- Done when:
  - Container hosts two render surfaces with deterministic z-order.

### [ ] app_v2/src/main/res/layout/custom_player_controls.xml

- Tasks:
  1. Verify slideshow controls and transition state indicators still align with renderer state.
  2. Remove any hard dependency on legacy single-image widget assumptions.

## D. Gesture and Interaction Managers

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerGestureSetupManager.kt

- Tasks:
  1. Route gesture listeners to renderer-owned active surface.
  2. Remove mode-specific duplication where possible.

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/TouchZoneGestureManager.kt

- Tasks:
  1. Ensure touch zones and PhotoView gestures do not conflict.
  2. Keep slideshow navigation gestures responsive under transition state.

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/ui/player/TouchZoneConfig.kt

- Tasks:
  1. Validate action mapping for unified PhotoView strategy.
  2. Update comments/constants if legacy assumptions are removed.

## E. Data/Network Awareness Hooks

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/data/network/ConnectionThrottleManager.kt

- Tasks:
  1. Expose lightweight signal for “congested” state consumption by prefetch queue.
  2. Keep read-only signal access from renderer path.

## F. Feature Flags and Settings

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt

- Tasks:
  1. Add flags for renderer migration and optional warm-up behavior.
  2. Set safe defaults (legacy-compatible).

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt

- Tasks:
  1. Optional: expose debug-only toggles for migration flags.
  2. Keep hidden in release if needed.

## G. Diagnostics and Safety

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt

- Tasks:
  1. Keep StrictMode checks active in debug.
  2. Ensure no new main-thread violations introduced by renderer path.

### [ ] app_v2/src/main/java/com/sza/fastmediasorter/core/logging/LoggingHelper.kt

- Tasks:
  1. Add concise renderer diagnostics tags (state transitions, prefetch drops, fallback reason).
  2. Avoid verbose log spam in release.

## 3) Recommended Commit Sequence

1. `phase1-render-contracts`: add renderer interfaces/models, no behavior switch.
2. `phase2-layout-dual-surface`: add container and inactive wiring.
3. `phase3-image-loading-integration`: bridge `ImageLoadingManager` to renderer.
4. `phase4-prefetch-priority`: add queue and replace direct preload path.
5. `phase5-gesture-unification`: align gesture managers and touch zones.
6. `phase6-slideshow-sync`: slideshow engine and renderer readiness sync.
7. `phase7-cleanup-legacy`: remove dead branches and obsolete toggles.

## 4) Validation Checklist per Phase

### Build/Static

- [ ] `Build standard debug APK` succeeds.
- [ ] No new critical lint/compile issues in touched files.

### Runtime Functional

- [ ] Manual next/prev works for local images.
- [ ] Slideshow advances without blank frame for cached/preloaded content.
- [ ] Zoom/pan works consistently after multiple transitions.

### Stability

- [ ] No crashes during rapid navigation bursts.
- [ ] No OOM during prolonged slideshow of high-resolution images.

### Performance

- [ ] Transition path visually smooth in normal and slideshow mode.
- [ ] Prefetch degrades gracefully on constrained network.

## 5) Exit Criteria

- [ ] Renderer path is default for static images.
- [ ] Legacy single-surface transition branch is removed or fully disabled.
- [ ] All validation checks pass for local + network-backed image sets.

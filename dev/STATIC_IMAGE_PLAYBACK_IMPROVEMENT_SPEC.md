# Static Image Playback Improvement Specification

## 1. Purpose

Define a concrete technical plan to improve static image playback (manual navigation + slideshow) for responsiveness, visual smoothness, and memory stability.

Primary target: eliminate perceived jank during image transitions while preserving current user-facing functionality.

## 2. Scope

### In Scope

1. Static image render pipeline and preloading strategy.
2. Slideshow transition engine and frame pacing behavior.
3. UI rendering architecture for image transitions.
4. Memory and cache policy for long-running sessions.
5. Gesture consistency across fit/crop/zoom display modes.

### Out of Scope

1. Video playback engine (ExoPlayer).
2. PDF/EPUB rendering internals.
3. SMB/SFTP/FTP protocol stack implementation.
4. Full player redesign beyond static image flow.

## 3. Current Issues

1. Rendering path is tightly coupled to `PlayerActivity` and mode branching.
2. Single-view drawable replacement can produce visible transition artifacts.
3. Part of navigation/render coordination occurs too close to Main-thread critical path.
4. Prefetch logic is not explicitly prioritized by user intent/state.
5. Divergent `ImageView` vs `PhotoView` behavior increases complexity and UX inconsistency.

## 4. Goals

1. **Smooth transitions:** no blank frames for cached/preloaded images.
2. **Fast navigation:** next/previous response should be immediate for local/cached paths.
3. **Memory stability:** avoid OOM and runaway memory growth during long sessions.
4. **Unified gestures:** consistent pinch/pan/zoom behavior across display modes.
5. **Predictable fallback:** controlled degradation when network/cache is slow.

## 5. Target Architecture

### 5.1 StaticImageRenderer

Introduce an isolated renderer component responsible for:

1. Render state machine (`Idle`, `Loading`, `Ready`, `Transitioning`, `Error`).
2. Two-surface rendering strategy (current/next) for transition safety.
3. Prefetch queue management independent from UI widgets.
4. Gesture mode application and matrix setup.

### 5.2 Rendering Surfaces

Default strategy: layered dual `PhotoView` surfaces in a dedicated container.

1. Surface A = current image (visible).
2. Surface B = next image (prepared off-screen/behind).
3. Transition = alpha cross-fade with bounded duration.
4. Post-transition = swap roles; recycle/release no-longer-needed resources.

### 5.3 View Strategy Decision

Primary path: use `PhotoView` as the single interaction surface type.

1. Fit/crop behavior represented through controlled matrix/scale setup.
2. Remove dual logic branches (`ImageView` vs `PhotoView`) after parity validation.
3. If parity is not achievable, keep hybrid strategy behind internal abstraction only.

## 6. Prefetch and Scheduling Policy

### 6.1 Priority Rules

1. Priority 1: immediate `Next` (full render target size).
2. Priority 2: immediate `Previous` (for fast reverse action).
3. Priority 3: lookahead (`Next +2/+3`) with reduced decode priority.

### 6.2 Slideshow Bias

When slideshow is active:

1. Increase forward prefetch priority.
2. Decrease backward cache pressure.
3. Keep one guaranteed ready candidate for the next tick when possible.

### 6.3 Network-Aware Degradation

1. Detect constrained network state via existing throttling/health signals.
2. Reduce lookahead depth under congestion.
3. Prefer stability over aggressiveness (no burst prefetch on weak links).

## 7. Threading and Main-Thread Safety

1. Decode, file checks, and prefetch orchestration must not block Main thread.
2. Main thread only performs view binding, transition commit, and lightweight state updates.
3. Transition callbacks must be bounded and cancellation-safe.

## 8. Memory and Cache Policy

1. Enforce explicit upper bounds for in-memory active bitmaps in renderer.
2. Release old surface content immediately after transition completion.
3. Use format optimizations (`RGB_565` or equivalent) only where quality constraints allow.
4. Keep cache behavior deterministic across rapid next/prev operations.

## 9. Optional Phase-2 Enhancements

1. Subsampling path for very large images (deep zoom use-case).
2. Adaptive transition duration based on decode readiness.
3. Dynamic prefetch depth tuned by device class.

## 10. Implementation Phases

### Phase 1: Decoupling

1. Extract static-image rendering logic into `StaticImageRenderer`.
2. Define renderer input/output contracts and state model.

### Phase 2: Dual-Surface Transition Engine

1. Replace single-surface swap path with two-layer transition container.
2. Implement deterministic cross-fade and resource release lifecycle.

### Phase 3: Prefetch Optimization

1. Implement prioritized prefetch queue.
2. Integrate slideshow/network-aware scheduling rules.

### Phase 4: Gesture Unification

1. Validate full `PhotoView` strategy for fit/crop/zoom parity.
2. Remove obsolete branch logic after parity sign-off.

### Phase 5: Stabilization and Regression

1. Profile memory and frame pacing under long sessions.
2. Fix regressions and finalize fallback behavior.

## 11. Risks and Mitigations

1. **Risk:** dual-surface strategy increases transient memory usage.  
   **Mitigation:** strict lifecycle release and bounded active bitmap policy.
2. **Risk:** `PhotoView`-only path may not fully replicate crop behavior.  
   **Mitigation:** matrix parity tests; fallback under abstraction if needed.
3. **Risk:** aggressive prefetch harms low-bandwidth sessions.  
   **Mitigation:** network-aware throttling and capped lookahead.

## 12. Acceptance Criteria

### Functional

1. Slideshow transitions for preloaded content show no blank frame.
2. Manual next/previous on local/cached images is immediate from user perspective.
3. Gesture behavior is consistent across display modes.

### Stability

1. No OOM/crash regressions in long static-image sessions.
2. Renderer handles rapid navigation bursts without stuck state.

### Performance

1. Smooth transition path with no visible spinner for preloaded/cached frames.
2. Memory profile remains stable during prolonged high-resolution slideshow.

## 13. Validation Scenarios

1. 1-hour slideshow with 12MP+ images (local storage).
2. Rapid next/previous spam on mixed portrait/landscape set.
3. Network-backed slideshow under constrained bandwidth.
4. Repeated mode changes between fit/crop/zoom.

## 14. Definition of Done

1. New renderer architecture is the default path for static images.
2. Acceptance criteria are satisfied for local and network-backed scenarios.
3. Regression checklist passes for navigation, slideshow, and gestures.
4. Legacy branch paths are removed or fully isolated behind non-default flags.

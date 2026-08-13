# Phase 1 — Create PauseAwareLoadControl

## Goal

Create a new `LoadControl` wrapper that stops ExoPlayer buffering when the player is paused.

## Steps

1. Create file `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PauseAwareLoadControl.kt`
2. Implement `LoadControl` via Kotlin `by` delegation to a `DefaultLoadControl` instance
3. Override `shouldContinueLoading(parameters: LoadControl.Parameters): Boolean`:
   - Return `false` when `!parameters.playWhenReady`
   - Otherwise delegate to the wrapped `DefaultLoadControl`
4. Constructor accepts `DefaultLoadControl` (not built internally — caller builds it with correct buffer params)

## Verification

- [x] Class compiles without errors
- [x] `shouldContinueLoading` returns `false` when `parameters.playWhenReady == false`
- [x] `shouldContinueLoading` delegates to `DefaultLoadControl` when `parameters.playWhenReady == true`
- [x] All other `LoadControl` methods are transparently delegated (no manual override needed)

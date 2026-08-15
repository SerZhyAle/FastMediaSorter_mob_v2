# Phase 01 - Session state + VideoPlayerHandle rotation contract

**Status:** Pending

Foundation: one session-scoped angle per family + a cross-family apply-layer contract. No visible behaviour yet.

## Files touched

- `ui/player/PlayerViewModel.kt` (state `PlayerState` at `:132-141`)
- `ui/player/StandalonePlayerViewModel.kt` (state `StandalonePlayerState` at `:50-59`)
- `ui/player/contracts/VideoPlayerHandle.kt` (interface, hue precedent at `:25-26`)
- `ui/player/PlayerActivityVideoHandle.kt` (impl for internal)
- `ui/player/helpers/PhotoVideoStandaloneVideoHandle.kt` (impl for standalone)

## Steps

1. Add `sessionRotationAngle: Int = 0` to `PlayerViewModel.PlayerState` and a VM method `rotateSession90()` that sets `angle = (angle + 90) % 360` and re-emits state. Session-scoped: NOT persisted, cleared when VM is destroyed (Activity finish) - matches "reset on exit".
   - Verify: `Grep sessionRotationAngle PlayerViewModel.kt` -> field + increment; no `SharedPreferences`/DataStore write of it.
2. Add the same `sessionRotationAngle: Int = 0` to `StandalonePlayerViewModel.StandalonePlayerState` and a `rotateSession90()` method (parity with internal).
   - Verify: field present; only `PhotoVideoStandaloneActivity` reads it (other 4 hosts untouched).
3. Extend `VideoPlayerHandle` with rotation apply-contract methods, mirroring hue: `fun setContentRotationDegrees(degrees: Int)` and `fun getContentRotationDegrees(): Int` (degrees ∈ {0,90,180,270}). KDoc: "visual frame-only rotation (S0995); not the screen sensor, not a destructive edit."
   - Verify: interface compiles; both impls must implement (compiler enforces).
4. Implement the two methods in `PlayerActivityVideoHandle` and `PhotoVideoStandaloneVideoHandle` - delegate to the engine's video-rotation apply (wired in Phase 03) and to the image-view rotation (Phase 02). For Phase 01 stub them to store the value + call a `applyContentRotation(degrees)` on the engine that Phase 02/03 will flesh out (do NOT leave `TODO()` - store field + no-op apply is acceptable interim, but this phase's build only needs the field+contract; the no-op is replaced in 02/03 within the same F3 run so nothing ships stubbed).
   - Verify: `standard debug` compiles.

## Done criteria

- Both `PlayerState`/`StandalonePlayerState` carry a non-persisted `sessionRotationAngle`.
- `VideoPlayerHandle` declares the rotation get/set contract; both impls satisfy it.
- Project compiles (`a.ps1 fk`).

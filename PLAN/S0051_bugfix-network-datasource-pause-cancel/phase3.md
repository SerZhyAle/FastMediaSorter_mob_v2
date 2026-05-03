# Phase 3 — Register PauseAwareLoadControl as Player.Listener in network helpers

## Goal

Wire PauseAwareLoadControl into ExoPlayer's listener chain so it receives playWhenReady events.

## Steps

1. In `SftpPlaybackHelper.kt`: add `exoPlayer?.addListener(loadControl)` before `addListener(playerListener)`
2. In `SmbPlaybackHelper.kt`: same
3. In `FtpPlaybackHelper.kt`: same
4. In `CloudPlaybackHelper.kt`: same
5. `PlayerSetupHelper.kt` (local): NOT changed — spec non-goal

## Verification

- [x] All 4 network helpers register loadControl as ExoPlayer listener
- [x] PlayerSetupHelper is untouched (local behaviour unchanged per spec non-goal)
- [x] No other callers need updating (PauseAwareLoadControl defaults isPlayWhenReady=true when unregistered)

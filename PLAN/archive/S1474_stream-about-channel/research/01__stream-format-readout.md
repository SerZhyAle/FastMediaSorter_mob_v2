# 01 - Reading the stream's own description from Media3

Resolves strategic §6 item 1 ("Полнота описания у живого потока").

## What the engine exposes (Media3 1.2.1, pinned in `docs/TECH_STACK.md`)

- `ExoPlayer.getVideoFormat()` / `getAudioFormat()` return the `Format` of the currently rendered track, or `null` before the renderer has one.
- `Format` carries `sampleMimeType`, `width`, `height`, `frameRate`, `bitrate`, `channelCount`, `sampleRate`, `codecs`. Every one of them may be `Format.NO_VALUE` / `null` on a live stream.
- `Player.getCurrentTracks()` exposes the selected `Format` per group. In-repo precedent: `VideoPlayerTracksObserver.kt:31-39` reads the selected video `Format` out of `tracks.groups`.
- `Player.getVideoSize()` gives the decoded picture size independently of `Format`. In-repo precedent: `PlayerDialogAndUiStateManager.showFileInfo()` already enriches file info from `player.videoSize`.

## Decision

Read the description at two moments and keep the latest non-empty value:

1. On `onPlaybackStateChanged(STATE_READY)`.
2. On `onTracksChanged` - a live manifest can select its real track after readiness.

At the deadline, report whatever has accumulated. Take picture size from `videoSize` when `Format.width/height` are `NO_VALUE`, because `videoSize` is populated by the decoder rather than the container.

Never substitute a catalog value for a missing measured one - strategic §3.2 and ADR-5 require the honest "unavailable".

## What remains empirical

Whether a given live HLS/RTSP channel actually reports `frameRate` or `bitrate` cannot be settled by reading the API - it depends on the manifest. This does not block implementation: the window is specified to show per-value unavailability, so a partly-empty measurement is a supported outcome, not a defect. It is a device-test observation, recorded in strategic §11 criterion 5.

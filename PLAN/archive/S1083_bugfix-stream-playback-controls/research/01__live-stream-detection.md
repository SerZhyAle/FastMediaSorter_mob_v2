# Research 01 - Live-stream vs VOD vs file detection

**Strategic item:** §6.1
**Status:** Resolved
**Method:** codebase investigation (no device experiment needed).

---

## Question

How to reliably tell, at playback time, that the active source is a stream (vs local file) and, for a stream, live (vs on-demand/VOD) - so the speed section can be gated and the colour strategy chosen.

## Findings

- **Stream vs file** is already decided upstream by `ResourceType`. `VideoPlayerManager` dispatches on it: `ResourceType.HTTP_STREAM, ResourceType.RTSP_STREAM -> playStreamVideo(..)` (routing block ~line 716). Every other `ResourceType` (LOCAL/CLOUD/SMB/SFTP/FTP) is a seekable file. So "active source is a stream" is knowable the moment playback is dispatched, before the first frame.

- **Live vs VOD** is exposed by Media3 on the active player: `exoPlayer.isCurrentMediaItemLive`. It is already used inside the stream recovery path (`StreamPlaybackHelper` re-anchor branch: `exoPlayer?.isCurrentMediaItemLive == true`). A related flag `exoPlayer.isCurrentMediaItemDynamic` is already consumed in the same routing block to suppress position save/restore for dynamic streams (`isDynamicStream`). `isCurrentMediaItemLive` becomes valid once the timeline is known (after prepare/first buffering), which is before/at the point the control dialog can be opened.

- The `HTTP_STREAM` path auto-detects progressive / HLS / DASH; a VOD HLS/DASH manifest reports `isCurrentMediaItemLive == false`, a live one reports `true`. So the same `playStreamVideo` path serves both, and only the runtime live flag separates them - not the `ResourceType`.

## Decision

- Expose two signals from the player layer to the control dialog:
  - `activeSourceIsStream` - from the dispatched `ResourceType` (stream types), recorded on the manager.
  - `activeSourceIsLive` - read live from `exoPlayer.isCurrentMediaItemLive`.
- Speed gate = hide/disable when `activeSourceIsLive`. VOD stream and local file keep speed.
- Colour strategy keys off `activeSourceIsStream` (the frame-effects lifecycle gap is stream-path-wide, not live-only) - see §6.2 (still Open, device-gated).

## Residual risk

- Timeline/live flag can be momentarily unknown during the very first buffering. The dialog snapshots section availability at open time (existing pattern for audio/subtitle/3D gates); by the time the user opens the dialog the stream is already buffering/ready, so the live flag is settled. Snapshot-at-open, do not live-observe.

# Phase 01 - Renderers-profile wiring on the stream paths

**Ticket:** S1125
**Status:** pending

## Step 1 - Real stream player: attach the shared renderers factory

- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`
- In `VideoPlayerManager.playStreamVideo`, add `.setRenderersFactory(createPlaybackRenderersFactory(context))`
  to the `ExoPlayer.Builder` chain (same package, no import needed).
- Rationale: decoder fallback ON + extension PREFER, identical to `PlayerSetupHelper`/cloud/SMB/FTP/
  SFTP/`AudioPlaybackService`. Hardware-first is preserved; a hardware-decoder init failure now
  retries another decoder instead of surfacing "channel unavailable".
- **Verification:** the builder passes `createPlaybackRenderersFactory(context)`; `.\a.ps1 fk`
  compiles; a live HLS stream still plays on the emulator with no regression.

## Step 2 - Headless grabber: software-preferred decoder selector

- File: `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamFrameSnapshotManager.kt`
- In `capture`, build a `DefaultRenderersFactory(context)` with `setEnableDecoderFallback(true)` and a
  `MediaCodecSelector` that sorts `MediaCodecInfo.softwareOnly` first
  (`research/01__force-software-decode-mechanism.md`); pass it via `.setRenderersFactory(..)` on the
  grabber's `ExoPlayer.Builder`.
- Rationale: forces software `MediaCodec` decode for the one-frame grab, so the grabber never occupies
  the hardware decode-surface pool the real player needs (strategic §2), and stays off the fragile
  hardware decoders (S0700/S0900). Sort, not filter: hardware stays as a tail so a codec without a
  software decoder still captures.
- **Verification:** the grabber builder passes the software-preferred factory; `.\a.ps1 fk` compiles;
  on the emulator a grid capture still produces a thumbnail (favicon fallback on timeout unchanged).

## Step 3 - Device-verification probes (final edits before build)

- One `Timber.d("S1125: ...")` probe at each changed-flow entry (playStreamVideo, capture), inserted
  as the last code edits before the phase build, per CLAUDE.md Debug Verification Tags.
- Set status `BlockNeedUserTest`; device-test gate runs on the attached device.
- **Verification:** exactly two `Timber.d("S1125:` lines exist across the two files; build succeeds.

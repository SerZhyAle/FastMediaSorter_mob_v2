# Phase 01 - Remote decoder parity

**Ticket:** S1056
**State:** implementable now (Play-safe, no native build)
**Flavor:** all (change lives in `src/main`)

## Goal

Route the four network playback helpers through the tuned renderers factory so the already-bundled FFmpeg audio decoders + `setEnableDecoderFallback(true)` + `EXTENSION_RENDERER_MODE_PREFER` apply to remote video exactly as they do to local video. Closes the local/remote decoder gap for MKV (and every container) - research/02 §H #1.

## Precondition (verified)

All four helpers build `ExoPlayer.Builder(context).setMediaSourceFactory(..).setLoadControl(..).setAudioAttributes(..).build()` with **no** `setRenderersFactory`. `createPlaybackRenderersFactory(context)` is a top-level fun in the same package (`com.sza.fastmediasorter.ui.player.helpers`) - no import needed. Evidence: `SmbPlaybackHelper.kt:120`, `FtpPlaybackHelper.kt:106`, `SftpPlaybackHelper.kt:99`, `CloudPlaybackHelper.kt:71`.

## Steps

1. In `ui/player/helpers/SmbPlaybackHelper.kt`, insert `.setRenderersFactory(createPlaybackRenderersFactory(context))` as the first chained call on `ExoPlayer.Builder(context)` (before `.setMediaSourceFactory(`).
   - Verification: `grep -n "setRenderersFactory(createPlaybackRenderersFactory" SmbPlaybackHelper.kt` returns one line.
2. Same insertion in `ui/player/helpers/FtpPlaybackHelper.kt`.
   - Verification: grep returns one line.
3. Same insertion in `ui/player/helpers/SftpPlaybackHelper.kt`.
   - Verification: grep returns one line.
4. Same insertion in `ui/player/helpers/CloudPlaybackHelper.kt`.
   - Verification: grep returns one line.
5. Insert one `Timber.d("S1056: <protocol> renderers factory applied")` probe per helper at the changed builder, ONLY while status is `BlockNeedUserTest` (CLAUDE.md Debug Verification Tags). Remove all on transition out.
6. Build `standard debug` (`a.ps1 dq`).
   - Verification: `BUILD SUCCESSFUL`.

## Done criteria

- All four helpers construct the player with the tuned renderers factory.
- `standard debug` compiles.
- Device test (deferred to `BlockNeedUserTest`): a network (SMB/Cloud) MKV whose audio is DTS/APE/WMA plays **with audio**, matching the same file played locally; a plain H.264/AAC network video still plays unchanged (no regression).

## Risk

Low. The change replicates the exact renderer configuration already used on the local path; no data/source change. Regression surface = network video that already played - covered by the "plain H.264/AAC unchanged" device check.

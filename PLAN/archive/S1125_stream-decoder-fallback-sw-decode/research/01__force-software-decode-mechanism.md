# Research 01 - Forcing software video decode in media3 (headless grabber)

**Ticket:** S1125
**Question (§6):** which media3 mechanism forces software video decode in the headless preview
grabber so it does not compete for hardware decode surfaces with the real player.

## Options considered

1. `DefaultRenderersFactory.setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)` (the shared
   `createPlaybackRenderersFactory` profile).
   - Prefers extension renderers over `MediaCodec`. For **video** this only forces software when a
     software video **extension** renderer is on the classpath. The project ships no software video
     extension (that is S1126's backstop). So on the base build this profile still resolves video to
     the platform `MediaCodec` (hardware). Does not satisfy "decode in software" for the grabber.

2. `DefaultRenderersFactory.setMediaCodecSelector(MediaCodecSelector)` with a selector that ranks
   `MediaCodecInfo.softwareOnly` decoders first.
   - `MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)` returns the candidate
     `MediaCodecInfo` list; each exposes the public `softwareOnly` flag. Sorting `softwareOnly`
     descending puts software-only `MediaCodec` decoders (e.g. `c2.android.*`, `OMX.google.*`) ahead
     of hardware ones, so ExoPlayer instantiates a software decoder for the grabber's first frame.
   - Pairs with `setEnableDecoderFallback(true)`: if the preferred software decoder fails to init,
     media3 retries the next candidate instead of failing the capture.

## Decision

Use option **2** for the grabber: a `MediaCodecSelector` that sorts `softwareOnly` first, on a
`DefaultRenderersFactory` with decoder fallback on. Rationale:

- It forces software `MediaCodec` decode for video without needing a software **extension** renderer
  (option 1 cannot, until S1126 ships one).
- **Sort**, do not **filter**: keep hardware decoders as a tail so a codec with no software decoder
  still plays (favicon fallback remains the only miss path), rather than hard-failing capture.
- It moves the grabber off the hardware decode-surface pool that the real player needs (§2 surface
  starvation), and off the specific hardware decoders that native-crashed the process historically
  (S0700/S0900 - though the current TextureView path (S0933) already avoided that on the S21, this
  makes the grabber decoder-path independent of the fragile hardware decoders).

The real stream player keeps the shared `createPlaybackRenderersFactory` (hardware-first + decoder
fallback + extension prefer) - the recommendations doc is explicit that mobile must **not** force
software globally, only for the short-lived grabber.

## API references

- `androidx.media3.exoplayer.DefaultRenderersFactory#setMediaCodecSelector`
- `androidx.media3.exoplayer.DefaultRenderersFactory#setEnableDecoderFallback`
- `androidx.media3.exoplayer.mediacodec.MediaCodecSelector` (functional; `DEFAULT` + `getDecoderInfos`)
- `androidx.media3.exoplayer.mediacodec.MediaCodecInfo#softwareOnly`

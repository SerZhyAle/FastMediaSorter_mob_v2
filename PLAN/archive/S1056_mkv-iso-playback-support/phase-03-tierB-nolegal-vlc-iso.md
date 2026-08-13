# Phase 03 - Tier B: noLegal alternate engine (libVLC) - any-codec MKV + ISO

**Ticket:** S1056
**State:** BLOCKED - owner legal/size/maintenance decision + external build
**Flavor:** noLegal only

## Goal

One dependency solves both remaining hard cases:
1. Any-codec MKV video (SW HEVC/H.264/VC-1/MPEG-2 + all audio) - beyond what platform + Tier A cover.
2. DVD/BD ISO playback (libdvdnav/libdvdread for DVD, libbluray for BD, optional libdvdcss for CSS).

## Why noLegal-only

- Patent-encumbered SW video decoders (HEVC/AVC/VC-1/MPEG-2) - patent-royalty exposure unwanted in the Play `standard` channel.
- Optional libdvdcss decrypts DVD CSS - DMCA anti-circumvention exposure (research/01 §2.4).
- libVLC engine is LGPL v2.1; obligations (relink/attribution) are manageable but belong in the isolated sideload channel.

## Blocking gates (do not fabricate)

1. **Owner decision** on: accepting libVLC (LGPL) in noLegal; APK size (~25-40 MB/ABI); maintaining a second player surface; whether to bundle libdvdcss (DMCA).
2. **External build** of libVLC-android `.so` (or vendored `org.videolan:libvlc-all`), 16KB-aligned.

## Recommendation

libVLC integration is a large effort (alternate `IVLCVout`/`TextureView` surface, own controls mirrored against the ExoPlayer host, remote-input bridging via libVLC media callbacks instead of ExoPlayer `DataSource`). On owner GO, **split into a dedicated Full child ticket** rather than executing as a phase here.

## Sketch (for the child ticket)

1. `noLegalImplementation(..)` libVLC dependency (isolation like `NewPipeExtractor`, research/02 §D).
2. New engine behind a `main`-side interface + `src/noLegal/` impl (Contributor or NoOp-in-main template).
3. Format routing: direct `.iso` (reclassify from `BINARY_DISK`) and, optionally, any-codec fallback video to the libVLC engine when ExoPlayer reports `DECODING_FORMAT_UNSUPPORTED`.
4. Bridge SMB/FTP/SFTP/Cloud byte sources to libVLC media input callbacks (the seekable DataSources already exist - research/02 §B).
5. Own playback UI surface + controls; lifecycle release contract per Rule 18 / Player-ownership audit.

## ISO in standard - explicitly out

No permissively-licensed Android DVD/BD demuxer exists; a custom ISO9660/UDF DataSource only helps the degenerate "single progressive file inside an unencrypted data-ISO" case (low ROI). Read-only ISO9660 *file browsing* (ZIP-manager analog) is a separate feature - park as its own idea if the owner wants it.

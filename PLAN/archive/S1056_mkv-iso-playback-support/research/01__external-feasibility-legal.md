# S1056 Research 01 - External feasibility & legal (MKV / ISO / animated WEBP)

**Date:** 2026-07-15
**Method:** Web research against authoritative sources (developer.android.com, videolan.org, androidx/media issues).

---

## 0. Scope split (dedup)

- **Animated WEBP** - already delivered by **S1026** (`animated-webp-support`, status `BlockNeedUserTest`). NOT part of S1056. Glide-based; local and remote share the same decode path, so remote coverage follows local. Only residual action: confirm remote animated WEBP during S1026 device test.
- **MKV** - container already registered in `MediaExtensions.VIDEO`. Gap is codec-level, not recognition.
- **ISO** - not registered anywhere. Genuine new capability, hardest of the three.

---

## 1. MKV (Matroska) - platform reality

### 1.1 Container
Media3/ExoPlayer supports the Matroska container **natively** (`MatroskaExtractor` inside `DefaultExtractorsFactory`). Official supported-formats page: Matroska "Supported: YES". The contained audio/video sample formats "must also be supported" and, by default, **depend on the device platform decoders, not on ExoPlayer**.

> "By default ExoPlayer uses Android's platform decoders. Hence the supported sample formats depend on the underlying platform rather than on ExoPlayer."

### 1.2 What plays today (no new code)
Any codec the device's `MediaCodec` exposes, muxed in MKV:
- Video: H.264/AVC, HEVC/H.265 (most 2016+ devices), VP8/VP9 (most), AV1 (2021+ flagships only).
- Audio: AAC, AC-3/E-AC-3 (Media3 built-in decoders), FLAC, Opus, Vorbis, plus **DTS via the project's existing `fms-ffmpeg-dts` on-demand `.so`** (gated by the AAR dependency plus the runtime `DeliverableSet.FFMPEG_DTS` install check - not by a BuildConfig flag, see S1057).

### 1.3 What fails today (the real MKV gap)
MKV files whose codec has **no hardware decoder on that specific device**:
- HEVC/10-bit HDR on older/budget hardware.
- VP9 / AV1 where the SoC lacks the block.
- Legacy/exotic video: VC-1, MPEG-2, MPEG-4 ASP (DivX/Xvid).
- Lossless/object audio: TrueHD, DTS-HD MA (beyond core DTS), etc.

### 1.4 Software-decoder options (fill the gap)
Official list of Media3 software decoder extensions (all **manually built from source, NOT on Google Maven**):

> "We currently provide software decoder libraries for AV1, VP9, FLAC, Opus, FFmpeg, MIDI, IAMF and MPEG-H."

Split by licensing/patent posture:

| Extension | Fills | Licence | Play-safe (standard)? |
|-----------|-------|---------|-----------------------|
| `decoder_av1` (libgav1) | AV1 SW | BSD-2 (libgav1) | Yes - royalty-free codec |
| `decoder_vp9` (libvpx) | VP9 SW | BSD (libvpx) | Yes - royalty-free codec |
| `decoder_flac` / `decoder_opus` | FLAC/Opus | BSD/royalty-free | Yes (already covered) |
| `decoder_ffmpeg` (**audio only** - `FfmpegAudioRenderer`) | AC-3/E-AC-3/DTS/TrueHD/MP2/… audio | LGPL build; codecs patent-encumbered | Project already ships a **curated DTS-only** FFmpeg `.so` in standard |
| FFmpeg **video** | HEVC/H.264/VC-1/MPEG-2 SW video | Only `ExperimentalFfmpegVideoRenderer`; not a shipping renderer | No - patent-encumbered SW video |

**Critical fact:** Media3's FFmpeg extension is **audio-only** (`FfmpegAudioRenderer`). There is no production FFmpeg **video** renderer in Media3 (only an experimental one). So Media3 alone cannot software-decode HEVC/H.264/VC-1/MPEG-2 **video**. Robust any-codec MKV video therefore requires a **different engine** (libVLC / mpv) - see §3.

### 1.5 Verdict - MKV
- **standard (Play):** already works for HW-decodable codecs. Play-safe widening = build & ship `libgav1` (AV1) + `libvpx` (VP9) `.so` via the existing `fms-ffmpeg-dts.aar` build pipeline. Does NOT cover HEVC/VC-1/MPEG-2 SW.
- **noLegal:** full any-codec MKV video only via an alternate engine (libVLC) carrying patent-encumbered SW video decoders.

---

## 2. ISO (disc image) - platform reality

### 2.1 Nature of the problem
`.iso` is a disc-image filesystem (ISO 9660 / UDF), not a media stream. To play it you must (a) parse the filesystem, then (b) locate and demux the payload:
- **DVD-Video ISO:** `VIDEO_TS/*.VOB` = MPEG-2 Program Stream, multi-file, needs concatenation + DVD navigation (titles/chapters/menus).
- **Blu-ray ISO:** `BDMV/STREAM/*.m2ts` = MPEG-TS (`.m2ts` already in `MediaExtensions.VIDEO`), needs playlist (`.mpls`) navigation.
- **Data ISO wrapping a single media file:** the easy case.

### 2.2 ExoPlayer options
- ExoPlayer has **no** VOB/DVD-navigation demuxer and **no** ISO filesystem DataSource.
- A **custom `DataSource`** parsing ISO 9660/UDF is feasible ONLY for the trivial "ISO contains one progressive file" case - route that inner file to `ProgressiveMediaSource`. Not realistic for real DVD-Video structure.
- No maintained Android library plays DVD/BD ISO on top of ExoPlayer.

### 2.3 libVLC option (the only realistic "plays ISO")
libVLC (`org.videolan:libvlc-all`) opens DVD ISO via **libdvdnav/libdvdread** and BD ISO via **libbluray**:
- **Unencrypted** DVD/BD ISO: playable, though VLC's DVD-ISO path has historically had crash/regression issues.
- **Encrypted commercial** DVD (CSS) needs **libdvdcss**; Blu-ray (AACS/BD+) needs additional keys. `libbluray` alone plays only unprotected BD.

### 2.4 Legal
- **libdvdcss** decrypts CSS - use/distribution is controversial under the **US DMCA** (anti-circumvention) and similar laws. This is the decisive reason to keep CSS-DVD ISO **out of the Play `standard` flavor**.
- libVLC **engine** is **LGPL v2.1** (relicensed from GPL); VLC-for-Android app is GPLv2. LGPL linking is technically Play-compatible (VLC ships on Play since 2011), but bundling patent-encumbered SW video/audio decoders + libdvdcss is exactly the risk surface the project already isolates in **noLegal**.

### 2.5 Verdict - ISO
- **standard (Play):** not realistically feasible. At most, a custom DataSource for the degenerate "single media file inside an unencrypted data-ISO" case - low ROI, easy to mis-sell. Recommend **no ISO in standard**.
- **noLegal:** feasible via libVLC (libdvdnav + optional libdvdcss + libbluray). This is an **alternate player engine**, not an extractor add-on.

---

## 3. Convergence - one engine solves both hard cases

libVLC as a **noLegal-only alternate playback engine** answers BOTH:
1. any-codec MKV video (SW HEVC/H.264/VC-1/MPEG-2 + all audio), and
2. DVD/BD ISO (libdvdnav/libbluray, +libdvdcss for encrypted).

Cost / owner-gated tradeoffs (NOT resolvable from code - human decision):
- **APK size:** libVLC native `.so` ≈ 25-40 MB per ABI (arm64 alone still large). Multi-ABI multiplies.
- **Licensing obligations:** LGPL (relink/attribution) + libdvdcss DMCA exposure -> noLegal channel only.
- **Maintenance:** a second player surface (own `IVLCVout`/`TextureView`, own controls, own remote I/O bridging) mirrored against the ExoPlayer host.
- **Remote resources:** libVLC has its own I/O (network `Media` URIs + `callbacks`/`imem`); the project's SMB/FTP/SFTP/cloud byte pipes must be bridged to libVLC's media-input callbacks rather than ExoPlayer's `DataSource`.

---

## 4. Recommended tiering (for strategic spec)

- **Tier A - standard, low-risk, Play-safe:** build & ship `libgav1`(AV1)+`libvpx`(VP9) via the existing on-demand `DeliverableSet` `.so` pipeline. Widens MKV coverage without patent/DRM exposure. No ISO.
- **Tier B - noLegal, high-value, owner-gated:** integrate libVLC as an alternate engine -> any-codec MKV + DVD/BD ISO. Gated on owner decision (size/licence/maintenance) and an external native-build pipeline.
- **ISO in standard:** drop (legal + ROI). Optionally, degenerate single-file data-ISO via custom DataSource only if a concrete user need appears.

Both tiers require building native `.so` from source = **external dependency not in repo** = cannot be auto-implemented inside `/spec-all`; these are owner/external-build gated.

---

## Sources
- [ExoPlayer supported formats - Android Developers](https://developer.android.com/media/media3/exoplayer/supported-formats)
- [Android supported media formats (core)](https://developer.android.com/guide/topics/media/media-formats)
- [media3 decoder_ffmpeg README (audio renderer)](https://github.com/androidx/media/blob/release/libraries/decoder_ffmpeg/README.md)
- [Playing AV1 videos with ExoPlayer (libgav1)](https://medium.com/google-exoplayer/playing-av1-videos-with-exoplayer-a7cb19bedef9)
- [libdvdnav & libdvdread - VideoLAN](https://www.videolan.org/developers/libdvdnav.html)
- [libbluray / AACS limitations](https://echoshare.co/vlc-libbluray-and-alternative/)
- [VLC legal concerns - libdvdcss / DMCA](https://vlc-user-documentation.readthedocs.io/en/latest/support/faq/legalconcerns.html)
- [Press release: libVLC relicensing to LGPL - VideoLAN](https://www.videolan.org/press/lgpl-libvlc.html)
- [videolan/vlc-android (GPLv2 app, LGPL engine)](https://github.com/videolan/vlc-android)

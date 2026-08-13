# S0297 — Research artefact: noLegal-VR capability landscape

**Parent spec:** `PLAN/S0297_nolegal-vr-capability-research.md`
**Created:** 2026-05-25
**Status:** Completed - owner decisions recorded in parent S0297

This is the detailed research backing of S0297 §3 capability matrix. The parent spec contains the matrix and recommendations. This file contains the underlying facts, sources, variants pros/cons, and risks for the five A/B-candidates (A1, A2, B1, B2, B3). C-candidates are listed at the bottom with one-line defer rationales only.

---

## Methodology

Per S0244 research format: each candidate is structured as Question → Sources → Findings → Implementation variants (2..3 alternatives with pros/cons) → Best practice → Open risks → Maintenance estimate. Where applicable a "Differentiation note" calls out whether competing VR-players already ship the capability.

Research input mix:
- Web sources for upstream library/format/protocol state (yt-dlp, PaddleOCR, libVLC, FFmpeg, Meta Quest manifest schema, spatial media RFCs).
- Repo state for what already exists in noLegal (S0174, S0177, S0183, S0190, S0288).
- Light competitive-landscape scan for VR-video players on Quest (DeoVR, Skybox, Pigasus, HereSphere) to identify differentiation gaps.

No legal advice. No specific adult-content URLs or directories. Patent-pool references are public summaries only.

---

## Executive synthesis

Across the five A/B-candidates the cross-cutting picture is:

- **Lowest effort, durable, ships value immediately:** **B3** (VR companion APK classifier). One-day Kotlin work on top of S0183 with no new dependencies; signal sources (Meta `com.oculus.intent.category.VR` + `com.oculus.supportedDevices`) have been stable since 2018.
- **Clearest competitive differentiation:** **A2** (real-time PaddleOCR subtitle overlay). No major Quest VR-video player ships this — DeoVR/Skybox/Pigasus/HereSphere all rely on user-supplied `.srt`/`.ass` files. Reuses S0288 engine verbatim.
- **Largest pre-shipping risk:** **A1** (yt-dlp VR extraction). YouTube VR180 anonymous extraction is currently broken upstream (yt-dlp issue #14413, open as of 2025-09); without a spatial-metadata injector the immerse player won't auto-detect projection. A1.2 (extraction + metadata injection) is the only variant that survives upstream regressions.
- **Highest binary cost but production-validated:** **B2** (libVLC AAR). ~30 MB per arm64-v8a slice; every adjacent sideload VR app (Skybox, Pigasus, DeoVR) uses libVLC. Single dependency, full HEVC 10-bit / AV1 / DTS / AC4 coverage. Media3 FFmpeg extension is audio-only (covers DTS/AC4 audio cleanly but does nothing for HEVC/AV1 video). FFmpegKit was archived June 2025 — not a viable alternative.
- **Highest structural maintenance risk:** **B1.1** (adult-VR catalog with direct site APIs). No TMDB-equivalent for adult-VR exists; community integrations (stash-vr, xbvr) implement the consumer side of DeoVR/HereSphere protocols, not the aggregator side. **B1.3** (local sidecar mode with `.hsp` files) is the only variant with bounded maintenance cost.

**Recommended first-wave order** if owner picks two: **B3 first** (cheap, durable, advertises VR-readiness), **A2 second** (largest user-visible differentiation, reuses S0288 stack). A1 stays in first wave but gated behind S0296 closure + A1.2's metadata injector. B1.3 and B2.1 are credible second-wave choices.

---

## Active first-wave candidates

### A1: VR media extraction through yt-dlp

**Research question:** What concrete extension does the existing noLegal yt-dlp/Chaquopy/NewPipe pipeline need to deliver immersive-ready VR180/VR360 files (with spatial metadata intact) for playback by S0296's immerse mode?

**Sources consulted:**
- yt-dlp issue #14413 — "[Youtube] VR180 videos no more able to download" (2025-09-23) — https://github.com/yt-dlp/yt-dlp/issues/14413
- yt-dlp issue #12699 — "Latest yt-dlp (2025.3.21) Does Not extract VR180 Formats" — https://github.com/yt-dlp/yt-dlp/issues/12699
- yt-dlp issue #9903 — "8k VR180 SBS videos no longer available" — https://github.com/yt-dlp/yt-dlp/issues/9903
- Google `spatial-media` v2 RFC — `st3d`/`sv3d`/`equi` boxes inside `VisualSampleEntry` — https://github.com/google/spatial-media/blob/master/docs/spherical-video-v2-rfc.md
- Vimeo 360 help — equirectangular requirement, MV-HEVC spatial container — https://help.vimeo.com/hc/en-us/articles/12426105907729
- yt-dlp supported-sites master list — https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md

**Findings:**
- YouTube VR180 anonymous extraction is **currently broken** — requires `extractor_args: youtube:player_client=android_vr`, format `571+251` (SBS video + audio), returns HTTP 403 anonymously since ~2025-09; cookies allow login but prevent format detection (catch-22 per issue #14413).
- Spatial metadata lives in `moov > trak > mdia > minf > stbl > stsd > [VisualSampleEntry] > st3d/sv3d` boxes — yt-dlp's downloader **preserves byte-stream when no merge is required**, but our ADR-2 (yt-dlp as library, no built-in downloader) means our CDN-download path copies the raw `.mp4` blob unchanged. The danger is any future ffmpeg-mux step (e.g. S0190's `googlevideo.com` chunked path) dropping `st3d`/`sv3d` side data unless `-movflags use_metadata_tags` or post-injection is applied.
- `_type=spherical` / `projection_type` is **not a top-level yt-dlp metadata field** — projection has to be inferred from `format_id`, `format_note` strings (yt-dlp tags VR180 SBS as `571`-series), or by reading MP4 boxes post-download.
- Vimeo native extractor (already in noLegal §2) handles 360 equirectangular cleanly; Vimeo's MV-HEVC spatial container is a 2024+ format that requires Media3 1.5+ for decode.
- Reddit extractor exists in yt-dlp and handles `v.redd.it` hosted clips, but Reddit-VR posts are usually link-outs to external hosts (typically the same VR tubes already covered by yt-dlp's general extractor list).
- yt-dlp `supportedsites.md` confirms broad VR-tube coverage exists (specific enumeration omitted per scope).

**Implementation variants:**
- **A1.1:** Single VR-aware preset on `BaseUrlMediaExtractor` — pass `extractor_args: youtube:player_client=android_vr` plus format-selector `bv*[format_id~=^(571|572|573|574|575)]+ba/best` when the user invokes "extract as VR". Minimal code (one preset map). Pros: zero new modules, reuses existing pipeline. Cons: YouTube VR180 path is currently broken upstream; downstream player still has no signal that the file is VR (no spatial-metadata read).
- **A1.2:** A1.1 plus a small `SpatialMetadataInjector` Kotlin helper that, post-download, opens the MP4, locates `VisualSampleEntry`, and writes `st3d` (`stereo_mode=left_right`) + `sv3d/equi` boxes when format_id indicates a VR profile. This is the path Google's open-source `spatial-media` Python injector takes. Pros: produces files the S0296 immerse player can auto-detect via projection metadata; survives upstream extractor regressions. Cons: requires an MP4 box-walker (~300 LOC); adds a post-download step in the noLegal extraction chain.
- **A1.3:** Dedicated `VrUrlExtractor` strategy with its own URL pattern allowlist, its own "extract as VR" UI affordance, and explicit per-site profiles. Pros: clearest UX boundary. Cons: duplicates 60% of `YtDlpExtractionStrategy`; adds a second strategy to keep in sync with yt-dlp releases.

**Best practice (recommended variant):** **A1.2.** A1.1 alone produces files the immerse player cannot reliably classify (no `st3d`/`sv3d` boxes from yt-dlp's path); A1.3 is over-engineered. The metadata injector is the load-bearing piece — it decouples us from yt-dlp's chronic VR-extractor instability by recovering projection from format_id.

**Open risks:**
- YouTube VR180 anonymous extraction is broken as of 2026-05 (yt-dlp issue #14413, open). A1 launching today would ship a YouTube path that fails 100% on VR180 — only Vimeo/Reddit/other-tube URLs would work.
- ffmpeg muxing in S0190's `googlevideo.com` chunked path strips `st3d`/`sv3d` side data — needs explicit verification before merging A1.
- `format_id=571..575` mapping is YouTube-specific; cross-site VR projection detection must read MP4 boxes post-download, not trust extractor metadata.
- yt-dlp VR-tube extractors break ~quarterly (same cadence as Instagram per S0174); maintenance load is real.

**Maintenance estimate:** **Medium.** A1.2 inherits yt-dlp's quarterly breakage cadence on VR sources; the metadata injector itself (once written) is stable (Google's `spatial-media` Python source has had no breaking changes since 2018).

**Cross-cutting dependency:** A1 should not ship before S0296 (VIDEO immerse playback) — extracted VR files have no immerse user-flow without it.

---

### A2: Real-time subtitle OCR through PaddleOCR

**Research question:** Can the existing noLegal PaddleOCR stack (PP-OCRv5 mobile det+cls+rec on Paddle-Lite arm64-v8a) be repurposed during immerse VR playback on Quest 3 to read hard-coded subtitle regions from video frames and render the recognized text on the interactive HUD layer (S0283), within thermal and per-frame CPU budgets?

**Sources consulted:**
- PaddleOCR PP-OCRv5 docs and model card (HuggingFace `PaddlePaddle/PP-OCRv5_mobile_rec`, paddleocr.ai PP-OCRv5 page, arXiv 2507.05595 PaddleOCR 3.0 Technical Report)
- Paddle-Lite OpenCL/ARM GPU docs (`github.com/PaddlePaddle/Paddle-Lite/blob/develop/docs/demo_guides/opencl.md`) and ARM community blog on Paddle-Lite mobile perf
- Qualcomm Snapdragon XR2 Gen 2 product page; Notebookcheck Cortex-X3/A715/A510 cluster details; UploadVR/Mixed-News XR2 Gen 2 reviews; arXiv 2509.18929 "Native Mixed Reality Compositing on Meta Quest 3" thermal study
- VR-player landscape: DeoVR subtitle docs (`deovr.com/blog/69-stream-subtitles-at-deovr`), Skybox subtitle forum, Pigasus Meta Store page, HereSphere Steam/itch.io subtitle threads, OVR Overlay Translator on Steam (closest analogue)
- Local: `PLAN/S0288_nolegal-paddleocr-paddlelite-bundle.md` (engine arch), `PLAN/S0240_vr-stack-rewrite-epic.md` §6.5 (MSDF-vs-Canvas roadmap)

**Findings:**
- XR2 Gen 2 in Quest 3 is a tri-cluster 8-core (1× Cortex-X3, 4× A715 @ 1.6..2.05 GHz, 3× A510) on 4 nm. The OpenXR render loop typically pins the X3 + 1..2 A715 cores; 2..3 A715 plus all A510 cores are realistically available for background OCR without GPU contention (GPU is held by Adreno 740 for 72/90 Hz eye-buffer composition).
- PP-OCRv5 mobile is a 5 M-param system; PP-OCRv5 explicitly uses a larger dictionary than v4, which raises per-frame inference cost vs v4. PaddleOCR 3.0 notes hi-perf inference cuts mobile-rec latency by 73 % and mobile-det by 40 % on T4 GPU — on a mobile CPU the savings are far smaller. No first-party arm64 ms/frame figure published for PP-OCRv5 yet; community guidance and the PP-OCR Snapdragon-855 baseline imply a full det+cls+rec pass on a ~1080p frame lands in the **300..800 ms** range on a single A715 core, dropping to ~150..300 ms with a tight ROI crop and 4-thread ARM-CPU backend.
- Paddle-Lite supports OpenCL on Adreno; ARM blog reports ~22 % perf gain in mixed visual-inspection models; SSD speedups on Adreno 855/865 are quoted as "~1× speedup" (modest). First-run OpenCL kernel compile is slow without `set_opencl_binary_path_name` caching. Adding the OpenCL backend costs ~3..5 MB extra `.so` per ABI and competes with the same Adreno 740 that is rendering the immerse scene — a hard no for VR.
- Subtitle persistence: typical movie subtitle dwell is 1.5..3 s, so an OCR pass every 500..1000 ms is sufficient; running on every frame is wasteful.
- HUD context: S0240 §6.5 marks MSDF-font + atlas as the target text path; current HUD is bitmap-Canvas-to-texture upload — sufficient for an OCR text strip refreshed once per second, but a per-frame MSDF path would be needed only if subtitle text is animated or scrolled.
- Quest 3 thermal: arXiv 2509.18929 measures only ~5..10 min headroom for heavy native compositing tasks; community reports show first throttle warnings at ~60..75 min under typical VR-video load. A continuous OCR pass adds steady ~15..25 % single-core load — survivable for a 90-min movie if duty-cycled, dangerous if run free-running every frame.
- Competitive landscape: DeoVR, Skybox, Pigasus, HereSphere all support external `.srt/.ass/.ssa` subtitle files; **none** offer real-time OCR of burned-in subtitles. The only known VR OCR tool is `OVR Overlay Translator` (SteamVR-only, screen-overlay translator for games, not a video player). On Quest standalone this niche is empty — clear differentiation.

**Implementation variants:**
- **A2.1: Heuristic ROI + periodic pass (1 Hz).** Crop bottom ~22 % of video frame (configurable), run det+rec only (skip cls — orientation is constant for subtitles), 4-thread ARM CPU backend, no GPU, cache last text and only redraw on change. Pros: minimal thermal impact, reuses S0288 engine verbatim, no extra `.so`. Cons: misses non-bottom subtitle styles (e.g., top-positioned karaoke, side-positioned anime credits).
- **A2.2: Full-frame det → ROI rec, continuous (≈3 Hz).** Run det on full downscaled frame to locate any text region, then rec only on detected boxes; cross-frame box tracking by IoU. Pros: handles any subtitle position and style. Cons: 2..3× CPU cost vs A2.1, real risk of thermal throttle on long movies, harder to keep below 90 Hz frame deadline jitter.
- **A2.3: On-demand controller-button OCR ("read subtitle now").** Single OCR pass triggered by a HUD button or controller chord; text stays until dismissed or the button is pressed again. Pros: near-zero idle cost, perfect for sparse foreign signage. Cons: not a real "subtitle track" replacement, requires user action per line.

**Best practice (recommended variant):** **A2.1 as default**, with A2.3 exposed as a "battery-saver / manual" toggle in noLegal settings. A2.2 deferred behind a hidden debug flag until A2.1 has on-device thermal data from a real 90-min playback session. Keep current Canvas-to-texture HUD path for v1 — refresh rate is 1 Hz, well within bitmap-upload budget; only migrate to MSDF if a later spec proves frame-deadline misses.

**Open risks:**
- No published PP-OCRv5 arm64 latency number — first impl must produce its own bench on Quest 3 hardware before locking the OCR period. Without that, the 300..800 ms estimate is informed guesswork, not budget.
- CINEMA-quad geometric distortion of subtitle ROI: the source pixels at the bottom edge of a flat CINEMA layer are stretched; reading them off the source bitmap (pre-projection) is mandatory, not off the post-warp eye buffer.
- VR360 / VR180 immerse formats have no flat "bottom 22 %" — A2.1 heuristic ROI degenerates. Either auto-disable subtitle OCR for non-CINEMA formats in v1, or fall back to A2.2 only for spherical formats.
- Per-frame ExoPlayer surface-to-Bitmap copy is not free; needs a `PixelCopy` or `ImageReader`-tap path that doesn't block the render thread.
- Thermal sustainability over 90+ min is unproven; needs explicit thermal logging in the v1 impl ticket.

**Maintenance estimate:** **Low.** Reuses S0288 engine; only adds a periodic frame-tap, ROI cropper, and one HUD text element. No external API to break.

**Differentiation note:** No major Quest VR-video player (DeoVR, Skybox, Pigasus, HereSphere) ships real-time OCR-based subtitle overlay — all rely on user-supplied `.srt/.ass/.ssa` files. The only adjacent product is the SteamVR-only OVR Overlay Translator, which is not a video player. A2 therefore fills a real gap on standalone Quest and is genuine differentiation for noLegal users with foreign-language burned-in subtitle content.

---

## Second-wave candidates

### B1: Adult-VR catalog / library surface (DeoVR-style discovery)

**Research question:** Is there a publicly stable metadata source for adult-VR content (catalog-level, not file-level), and what is the realistic maintenance cost of building a discovery surface in the app without depending on volatile third-party APIs?

**Sources consulted:**
- HereSphere `.hsp` metadata format & DeoVR/HereSphere API protocol — itch.io devlogs, Steam community discussions on streaming integration.
- xbapps/xbvr (open-source scene-metadata manager) Scene/File APIs documentation on DeepWiki — community reference for stash-style adult-VR library management.
- `o-fl0w/stash-vr` (GitHub) — bridge that exposes a Stash library to HereSphere/DeoVR via their published Web Stream API contract.
- Existing project capability: `BaseUrlMediaExtractor`, `KnownAuthResources`, yt-dlp pipeline (S0174, S0190 Phase D) — what is already shipped and reusable.

**Findings:**
- **No TMDB-equivalent exists.** There is no public, community-maintained, vendor-neutral metadata index for adult-VR. xbapps/xbvr is the closest community artifact and is itself a scraper/aggregator over volatile sites — it does not expose a stable REST surface a third app can consume.
- **No publicly documented OpenAPI/GraphQL surface** for the major adult-VR aggregators. Existing community integrations (stash-vr) work by **implementing the consumer side of the DeoVR/HereSphere "Web Stream" JSON protocol**, not by calling an aggregator's API.
- **DeoVR / HereSphere expose the consumer-side library protocol publicly.** Both players accept a JSON feed at a configurable URL and render it as their library. HereSphere additionally persists per-scene metadata in `.hsp` sidecar files alongside the video; community feature requests confirm `.hsp` is the canonical metadata-edit format.
- **`.hsp` is the de-facto local sidecar standard.** Tags, ratings, favorites, projection type (180/360, SBS/TB), and scene chapters live there. XMP is not used by these players.
- **Maintenance reality of any direct-site approach is high.** Adult-VR-tube sites rotate token formats, add Cloudflare challenges, and shuffle URL patterns on roughly a 2..3 month cadence — same class of breakage as yt-dlp YouTube extractor churn but with a much smaller community of fixers.
- **Project already has the discovery primitive.** A "URL → extract → cache → open in immerse" pipeline exists (S0174 + S0292 immerse handoff). What's missing is the *library presentation* layer, not the extraction layer.

**Implementation variants:**
- **B1.1: Direct integration with adult-VR-aggregator APIs.** Wrap 1..3 site APIs behind a unified `VrCatalogProvider` interface, expose filterable list in a noLegal-only Browse mode. Pros: closest to "DeoVR-style discovery" user expectation; full catalog scale. Cons: structurally unbounded maintenance (per-site breakage every 2..3 months); zero ability to outsource fixes to a yt-dlp-scale community; brittle even with a robust HTTP layer.
- **B1.2: Curated URL-list mode.** Owner maintains a local/private JSON list of scene URLs; app renders it as a library and routes each entry through the existing yt-dlp/extraction pipeline. Pros: zero third-party API surface; failures isolated to one URL at a time; reuses S0174. Cons: catalog scale is bounded by owner's manual curation; no genre/actor filtering unless owner tags by hand.
- **B1.3: Local sidecar mode (`.hsp` + JSON sidecar generation for already-downloaded files).** App scans noLegal media folders, writes/edits `.hsp` sidecars compatible with HereSphere import, and exposes a noLegal-only `VrLibraryActivity` with tag/genre filtering driven by the sidecars. Pros: zero network surface; format directly compatible with the two dominant adult-VR players for users who want to round-trip; no API maintenance. Cons: does not solve discovery — user already has the files; competes more with file managers than with DeoVR.

**Best practice (recommended variant):** **B1.3 as baseline.** It is the only variant whose maintenance cost does not grow over time. The `.hsp` write path is a finite, well-documented format. If the owner later decides catalog-scale discovery is worth the maintenance cost, B1.2 layers cleanly on top (curated URL list feeding the same library UI as B1.3's local files). B1.1 is rejected as first-wave: the maintenance pattern is the same one that already costs us non-trivial effort in S0174's yt-dlp pin cadence, multiplied across smaller sites with weaker community coverage.

**Open risks:**
- `.hsp` format is documented by behaviour, not by spec. Need on-device round-trip test against current HereSphere build before declaring compatibility.
- "Adult-VR library mode" UX framing risks blurring the noLegal/standard boundary in user mental model — must be gated unambiguously to noLegal-flavor Browse mode, not surfaced anywhere in shared UI.
- Projection metadata (180/360, SBS/TB, FOV) is the most user-valuable tag and the hardest to infer reliably from filename alone; without it the library is just a folder view.

**Maintenance estimate:** **Low** for B1.3 alone; **High** for B1.1; **Medium** for B1.2.

---

### B2: DRM-free patent-encumbered codec pack (HEVC/AV1/DTS/AC4)

**Research question:** Which Android-native software-decoder stack covers the gap between MediaCodec's device-dependent codec set and the codecs commonly found in sideloaded VR files (HEVC 10-bit, AV1, DTS, AC4), and what is the minimum-invasive way to plug it into the existing Media3 ExoPlayer pipeline?

**Sources consulted:**
- `developer.android.com/media/media3/exoplayer/supported-formats` — baseline MediaCodec/ExoPlayer codec matrix.
- `github.com/androidx/media/blob/release/libraries/decoder_ffmpeg/README.md` — official Media3 FFmpeg extension scope.
- `github.com/arthenica/ffmpeg-kit` releases + License wiki — FFmpegKit codec packages and licensing.
- `tanersener.medium.com/saying-goodbye-to-ffmpegkit-33ae939767e1` — official retirement notice (Jan 2025).
- `github.com/videolan/vlc-android` + `get.videolan.org/vlc-android/` — official libVLC Android distribution.
- Streaming Media / Access Advance / Sisvel public summaries — HEVC and AV1 patent-pool structure.

**Findings:**
- **Media3 FFmpeg extension is audio-only.** `androidx.media3.exoplayer.ffmpeg` ships an `FfmpegAudioRenderer` for AC3, E-AC3, DTS, TrueHD, MLP, ALAC, MP1/2/3, Opus, Vorbis, etc. It does **not** include a video renderer. This solves DTS/AC3/AC4 audio cleanly but does nothing for HEVC/AV1 video.
- **FFmpegKit is dead.** Archived June 2025; binaries removed from Maven Central April 2025. Community forks exist (e.g. `moizhassankh/ffmpeg-kit-android-16KB`) but none is a sanctioned replacement; pinning to a private fork carries the same maintenance risk as a custom FFmpeg build.
- **libVLC Android is alive and shipped under LGPL-2.1+.** Current line is `3.6.x` (VLC for Android 3.6.3 released as official APK). The AAR provides a self-contained native player with full codec coverage: HEVC (8/10-bit), AV1 (dav1d), VP9, DTS, AC3/E-AC3, AC4, FLAC, etc. AAR per ABI slice is ~30 MB for arm64-v8a (consistent with the published 3.6.3 APK split sizes).
- **There is no `LibVLCMediaSource` for Media3.** libVLC binds to its own `MediaPlayer` over a `SurfaceView` / `TextureView`. Integration is "use libVLC as a separate Player implementation" — not "plug a MediaSource into ExoPlayer". The Media3 `Player` interface boundary lets this be done relatively cleanly, but the call sites in our `PlayerManagerInitializer` need a branch.
- **HEVC patent pools are real and active.** Three pools (MPEG-LA — still active for some licensees; Access Advance, formerly HEVC Advance; Velos closed late 2022 and now licenses direct). Disclosed per-device rates run $0.20..$1.50+; HEVC license costs scale with installed base, not just distribution. This is why Google Play store apps default to relying on device-side MediaCodec rather than bundling their own HEVC decoder.
- **AV1 has patent overhead despite "open" framing.** AOMedia patents are royalty-free among members; Sisvel runs a separate pool (Philips, GE, NTT, Ericsson, Dolby, Toshiba contributors) claiming third-party essential patents on AV1. Royalty-free is therefore a property of the AOMedia license, not the codec.
- **MediaCodec already covers HEVC on most modern Android.** API 21+ devices have HEVC Main; AV1 hardware support is ~Android 14+ flagships. The codec pack is genuinely needed for: older devices, 10-bit/HDR HEVC profiles, AV1 on pre-2023 hardware, and DTS/AC4 audio tracks in MKV containers — not the median case.

**Implementation variants:**
- **B2.1: libVLC AAR as a parallel `Player` implementation, gated to noLegal.** Add libVLC AAR to noLegal source set; introduce a `VlcPlayerImpl : Player` adapter; route playback through it when MediaCodec rejects the source. Pros: production-tested on every other major sideload VR app (Skybox, Pigasus, DeoVR all use libVLC); single dependency; full codec coverage including 10-bit HEVC HDR and AV1; clear ABI story (arm64-v8a). Cons: ~30 MB binary cost per slice; UX seam between two Players (state machines differ); no shared seek/loading abstractions.
- **B2.2: Audio-only Media3 FFmpeg extension + libVLC for video.** Use the official `decoder_ffmpeg` for DTS/AC4/E-AC3 audio (keeps ExoPlayer path intact for audio), fall back to libVLC only for true video codec gaps. Pros: keeps the common case on ExoPlayer; smaller surface for the libVLC branch; uses an official Google-maintained extension. Cons: still need libVLC for HEVC 10-bit / AV1 SW path; two extensions to ship and reason about.
- **B2.3: Custom-built minimal FFmpeg AAR with explicit `--enable-libdav1d` + `--enable-decoder=hevc`, wrapped as a custom Media3 video renderer.** Pros: smaller (~13 MB minimal build) and surgical. Cons: we own the FFmpeg build pipeline including 16 KB-alignment for Android 15; FFmpegKit's retirement is precisely the warning that this is a long-tail maintenance commitment; no existing Media3 video FFmpeg renderer to copy — we'd be writing decoder bindings.

**Best practice (recommended variant):** **B2.1 (libVLC AAR as a parallel Player gated to noLegal).** Production-validated by every adjacent sideload VR app, single dependency, clearest license story (LGPL-2.1+ compatible with sideload distribution; binary linking allowed), and the AAR size cost is non-blocking for noLegal (which already absorbs Paddle-Lite + Chaquopy). The Media3 FFmpeg audio extension (B2.2 idea) can be added later as a refinement if profiling shows the libVLC path costs measurably more battery for audio-only fallbacks; not first wave.

**Open risks:**
- libVLC `MediaPlayer` and Media3 `ExoPlayer` have different state machines. The "fall back to libVLC on MediaCodec failure" routing must happen before track preparation, not after — otherwise the user sees an error toast first. This means a probe pass on the file's codec list before player selection.
- libVLC's 360°/VR-tile / projection support is more limited than Media3's. For VR-immerse files the chosen Player must hand back a `Surface` that the project's `XrCompositionLayer` path can consume. Needs on-device validation against S0296's VIDEO immerse path before declaring done.
- 16 KB page-size compatibility (Android 15 / API 35). libVLC's official `3.6.x` AAR must be checked with `readelf -lW` for `LOAD ≥ 0x4000` alignment same as Paddle-Lite was checked under S0288. If 3.6.x is not aligned, we either ship arm64-v8a-only or wait for 3.7.x.
- Patent posture: bundling HEVC/AC4 decoders in a publicly distributed APK normally triggers licensing obligations. The noLegal flavor is sideload-only, owner-personal, not redistributed via stores — this is the precise reason this capability is noLegal-gated. Worth a short ADR note in the impl ticket making the distribution scope explicit.

**Maintenance estimate:** **Medium** — libVLC ships major releases ~quarterly; AAR bumps are usually drop-in. The Player-adapter glue is the only project-owned code and is small. Risk concentrates in 16 KB-alignment review at each libVLC bump, same as Paddle-Lite under S0288.

---

### B3: VR companion APK install

**Research question:** Can we cheaply detect VR-capable APKs from on-device manifest parsing alone (no install, no PackageManager round-trip) and surface them with a distinct icon in Browse, building on the already-shipped S0183 install path?

**Sources consulted:**
- Meta "Android Manifest Settings" — VR intent category + `com.oculus.supportedDevices` — https://developers.meta.com/horizon/documentation/native/android/mobile-native-manifest/
- Meta "Application Manifests for Release Builds" — VRC.Quest.Packaging.1 check — https://developers.meta.com/horizon/resources/publish-mobile-manifest/
- Google issue tracker #36908355 — `PackageManager.GET_INTENT_FILTERS` non-functional flag — https://issuetracker.google.com/issues/36908355
- `jaredrummler/APKParser` — on-device binary AXML manifest parser, Kotlin/Java compatible — https://github.com/jaredrummler/APKParser
- Stephen Lee, "How to get manifest from an APK file" — manual binary AXML walk approach — https://medium.com/@liwp.stephen/how-to-get-manifest-from-an-apk-file-4c7f90dadc80

**Findings:**
- VR-capable APKs declare one or more of: `<category android:name="com.oculus.intent.category.VR"/>` inside the launcher activity's `<intent-filter>`; `<meta-data android:name="com.oculus.supportedDevices" android:value="quest|quest2|questpro|quest3"/>` in `<application>`; `<uses-feature android:name="android.software.vr.mode" required="true"/>`; `<uses-feature android:name="android.hardware.vr.headtracking" required="true"/>`. Any one of these is a reliable VR-app signal; Meta enforces `com.oculus.intent.category.VR` via VRC.Quest.Packaging.1 for store builds, so it is near-universal on Quest-targeted APKs.
- The Android XR equivalent is `<category android:name="com.google.intent.category.VR_ONLY"/>` plus `com.android.extensions.xr` uses-feature — newer (2024+), less prevalent today, but worth detecting for forward compatibility.
- **Critical limitation:** `PackageManager.getPackageArchiveInfo(apkPath, GET_INTENT_FILTERS)` does **not actually return intent-filter data** (Google issue #36908355, open since 2012). Categories must be read by parsing `AndroidManifest.xml` (binary AXML) directly out of the APK zip.
- `jaredrummler/APKParser` (Apache-2.0, pure Java, ~80 KB) is the de-facto on-device option for reading binary AXML on Android without `aapt2` / `apkanalyzer` (both desktop-only). It exposes manifest elements as parsed strings.
- `getPackageArchiveInfo` with `GET_META_DATA | GET_CONFIGURATIONS` reliably returns `<uses-feature>` and application `<meta-data>`, so `com.oculus.supportedDevices` and the `vr.mode` / `vr.headtracking` features can be detected without an external library. **Only the `com.oculus.intent.category.VR` check needs the AXML walk.**
- Typical Quest sideload ecosystem (SideQuest, ApkPure-VR, Quest Games Optimizer companions, AppLab snapshots) — virtually all of these APKs declare both the VR category and `com.oculus.supportedDevices`, so the detection signal is high-fidelity in practice.

**Implementation variants:**
- **B3.1:** Inherit S0183 as-is, no VR-specific detection. Pros: zero work. Cons: doesn't satisfy the spec's intent — no user-visible affordance.
- **B3.2:** Native detection via `PackageManager.getPackageArchiveInfo(GET_META_DATA | GET_CONFIGURATIONS)` only — read `com.oculus.supportedDevices` meta-data and `android.software.vr.mode` / `android.hardware.vr.headtracking` uses-features. Show a small VR badge on the `BrowseAdapter` tile when any signal hits. Pros: zero new dependencies; ~50 LOC; all signals come from the same Platform API the install path already uses. Cons: misses VR APKs that declare **only** the intent-category and no uses-feature (rare in practice).
- **B3.3:** B3.2 plus on-device binary AXML walk (via `jaredrummler/APKParser` or a vendored 100-LOC `BinaryXmlParser`) to also read `<intent-filter><category com.oculus.intent.category.VR>`. Adds a separate `VrApkClassifier` helper. Pros: catches 100% of Quest-targeted APKs; future-proofs for `com.google.intent.category.VR_ONLY` (Android XR). Cons: ~80 KB dependency or ~250 LOC vendored; AXML parsing adds 20..50 ms per APK in Browse (cacheable by file hash).

**Best practice (recommended variant):** **B3.2.** uses-feature + supportedDevices meta-data covers 90%+ of real-world Quest APKs with zero new dependencies and no AXML complexity. Promote to B3.3 only if a concrete miss is reported during device testing. The Android XR `com.google.intent.category.VR_ONLY` signal can be added incrementally once XR APKs become common.

**Open risks:**
- A small minority of older Quest-only APKs declare *only* `com.oculus.intent.category.VR` without supportedDevices meta-data — B3.2 will miss these; mitigation is to also accept the presence of `arm64-v8a`-only native libs as a soft signal.
- `PackageManager.getPackageArchiveInfo` on Android 14+ requires the APK to be readable through scoped storage / SAF; the existing S0183 install path already solves this, so no new permission cost.
- The Android XR ecosystem is still nascent (May 2026) — over-investing in `VR_ONLY` detection today is premature; treat as a v2 follow-up.
- Browse-list performance: classifying every `.apk` on a large NAS share could stall the list. Mitigate by classifying lazily on first viewport hit and caching by SHA-1 prefix or `(path, size, mtime)`.

**Maintenance estimate:** **Low.** Meta's `com.oculus.intent.category.VR` and `com.oculus.supportedDevices` schema has been stable since 2018 and is enforced by store policy (VRC.Quest.Packaging.1) — no upstream breakage cadence to track. The whole feature is ~50 LOC if B3.2 is sufficient.

---

## C-candidates (deferred — one-line rationale each)

- **Passthrough camera capture / mid-VR session recording** — Meta's `Passthrough Camera API` is gated by `META_REVIEW` entitlement for store builds; sideload bypass is technically possible but adds Meta-policy risk + significant native pipeline. Defer until concrete user-pull from owner.
- **Widevine L1 / Custom CDM secure-decryption** — implementing a custom CDM means licensing dance with Google Widevine + secure-Surface plumbing; high effort, high risk, low practical win for noLegal sideload library.
- **Voice transcription / Whisper-based for VR videos** — Whisper Tiny is ~75 MB, Base ~150 MB; on-device CTranslate2 / whisper.cpp arm64 inference takes RTF 0.3..1.0 on Cortex-X3 which is 2..3× too slow for real-time. Defer until competitive landscape shows clear user-pull.
- **Experimental OpenXR loader / pre-release Meta SDK** — low pure-user-value; high operational risk (broken loader can brick immerse stack). Defer.
- **Custom controller mappings / non-standard input profiles** — belongs in the general VR roadmap (S0240), not noLegal-specific. Out of scope for this research.
- **Locally hosted yt-dlp-mirror catalog with web UI** — duplicates desktop tools (PinchFlat, Tube-Archivist); only marginal value above the existing `S0174` URL-driven extraction. Defer.

---

## Cross-cutting concerns

- **noLegal binary-size budget.** Current noLegal `arm64-v8a` slice already carries Paddle-Lite (~24 MB) and Chaquopy (~12..16 MB). Adding libVLC (B2.1) brings ~30 MB more; total would land in the 80..100 MB range. For owner-personal sideload this is acceptable; for any future Play-track variant it would be blocking. None of the A-candidates carry meaningful binary cost (A1 reuses Chaquopy; A2 reuses Paddle-Lite; B3 is ~50 LOC of Kotlin).
- **16 KB page-size alignment (Android 15 / API 35).** Already validated for Paddle-Lite under S0288. Any future libVLC bump for B2 must pass the same `readelf -lW` check. yt-dlp via Chaquopy already complies.
- **Maintenance cadence.** A1 inherits yt-dlp's quarterly VR-source breakage. B1.1 (rejected) would multiply it by per-site count. A2, B1.3, B3 are essentially zero-maintenance. B2 has predictable libVLC bump cadence (quarterly drop-in).
- **Differentiation.** A2 is the only candidate with genuine product differentiation versus competing Quest VR-video players. A1, B1, B2, B3 are all "table stakes" or "QoL polish" that match competitors' capabilities rather than exceeding them.
- **Spatial metadata as a cross-cutting primitive.** A1.2's `SpatialMetadataInjector` (writing `st3d`/`sv3d` MP4 boxes) is a small primitive that B1.3 (HereSphere `.hsp` sidecar generation) and future auto-detect-stereo-format work in S0240 §10.3 also need. Worth extracting as a shared `noLegal/util` module rather than as a one-off inside the A1 ticket.

---

## Recommended implementation order

If owner picks two first-wave directions from A+B:

1. **B3** — lowest effort, durable maintenance posture, immediate UX win in Browse. Independent of any in-flight VR ticket. Can ship before S0296 closure.
2. **A2** — largest competitive differentiation, reuses S0288 stack verbatim. Should ship after S0296 closure (immerse VIDEO playback) so the OCR has a real video pipeline to tap.

If owner picks three or four:

3. **A1** — gated behind S0296 closure. Must implement variant A1.2 (extraction + spatial metadata injector); A1.1 alone is not viable.
4. **B1.3** — local `.hsp` sidecar mode only. B1.1 stays in C unless owner explicitly accepts the maintenance trap.

**B2** belongs in second-wave regardless of slot count: 30 MB binary cost only pays off when owner has concrete files that fail to play under MediaCodec.

---

## Owner decisions recorded

Parent spec Last Audit records owner composite default approval: `B3 → A2 → A1 → B1.3 → B2.1`; A1 uses A1.2, B1 is limited to B1.3, B2 uses B2.1, and C-candidates stay as six-month revisit reminders. The list below is retained as the pre-decision question set that produced that audit entry:

- **Priority between A1 and A2** — which is more valuable for owner personally.
- **B1 scope** — accept B1.3 (local sidecar only) or push for B1.2 (curated URL list) or hold entirely.
- **B2 stack choice** — libVLC AAR (B2.1) vs hybrid Media3 FFmpeg-audio + libVLC-video (B2.2) — recommendation is B2.1 but owner has final call.
- **Whether C-candidates can be deleted entirely** — or should they remain as "revisit in 6 months" reminders.

---

## Source index (consolidated)

Most-cited sources across this document, for quick reference:

- yt-dlp GitHub repo + issue tracker (issues #14413, #12699, #9903)
- Google spatial-media v2 RFC
- PaddleOCR docs + paddleocr.ai PP-OCRv5 page + arXiv 2507.05595
- Paddle-Lite docs + ARM developer community
- Qualcomm Snapdragon XR2 Gen 2 specs + arXiv 2509.18929 Quest 3 thermal study
- Meta Quest manifest documentation (developers.meta.com/horizon/documentation/native/android/mobile-native-manifest/)
- Google issue tracker #36908355
- developer.android.com Media3 supported-formats + decoder_ffmpeg README
- ffmpeg-kit archival announcement (2025-01)
- videolan.org libVLC Android docs
- DeoVR / HereSphere protocol docs (community-documented)
- xbapps/xbvr DeepWiki

---

## Revision History

- **2026-05-27** - refined by GitHub Copilot via `/spec-update`
	- Reconciled owner-decision wording with the parent Last Audit, removed the forbidden three-dot placeholder in the B3 manifest example, and recorded the remaining status-field inconsistency as a proposed structural change.

## Proposed Structural Changes

- **DISCUSS:** `RESEARCH.md` header still says `Status: Awaiting owner review`, while the parent spec and spec catalog are `Approved`. `/spec-update` does not edit `Status:` fields; close this through `/spec-check` or a dedicated status-field correction.

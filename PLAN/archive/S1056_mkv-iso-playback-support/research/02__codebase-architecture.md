# S1056 Research 02 - Codebase architecture (player / decoder / datasource / flavor)

**Date:** 2026-07-15
**Method:** Read-only codebase investigation (android-solution-researcher), catalog-first, evidence = `path:line`.

---

## A. Player engine + extractors

- `ui/player/helpers/PlayerSetupHelper.kt:30-86` `createPlayer()` = the tuned factory (LoadControl, listeners, effects reset). Calls `createPlaybackRenderersFactory(context)` (line 52).
- `ui/player/helpers/PlaybackRenderersFactory.kt:21-28` = `DefaultRenderersFactory(context).setEnableDecoderFallback(true).setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)` + attaches delivered FFmpeg-DTS `.so` (lines 36-49). This is a RenderersFactory, not an ExtractorsFactory.
- Container demux: `ui/player/helpers/BdTsPlaybackHelper.kt:25-37 buildBdTsMediaSourceFactory()` is the shared `MediaSource.Factory` for every helper. Ordinary branch = plain `DefaultMediaSourceFactory(wrapped)` -> media3's internal `DefaultExtractorsFactory` (bundles `MatroskaExtractor`). **MKV demux present and unmodified, local + remote.**
- MIME: extension-inferred first (`LocalPlaybackHelper.kt:218` `"mkv" -> MimeTypes.VIDEO_MATROSKA`), sniff as fallback. Stream path sets no MIME hint (non-RTSP).
- **`.iso` today**: `MediaExtensions.getMediaType()` checks binary first (`:31-34`); `util/BinaryFileTypeDetector.kt:18-19` `DISK_IMAGES = {iso,dmg,img,vhd,vdi,qcow2,vmdk,toast}` -> `MediaType.BINARY_DISK`. ISO never reaches ExoPlayer.

## B. Remote datasource seekability - ALL random-access, none download-first

| Protocol | DataSource | Seek |
|---|---|---|
| SMB | `data/network/datasource/SmbDataSource.kt:245-279` | SMB2 open + `dataSpec.position` |
| FTP | `data/network/datasource/FtpDataSource.kt:111-118` | `setRestartOffset` + `REST` (new data conn per seek) |
| SFTP | `data/network/datasource/SftpDataSource.kt:295-298` | `ChannelSftp.get(path, monitor, position)` |
| Cloud | `data/cloud/datasource/CloudDataSource.kt:53` | HTTP Range `getFileInputStream(id, pos, len)` |
| HTTP/RTSP | `ui/player/helpers/StreamDataSourceFactoryProvider.kt:24-34` | `DefaultHttpDataSource` Range |

Seek primitive exists end-to-end -> favorable for large MKV and future ISO9660/UDF volume-descriptor access.

## C. Native-delivery mechanism + the Play bundling constraint

- Declare: `domain/delivery/DeliverableSet.kt:11-16` (4 sets: TRANSLATION, OCR_ENGINES, AUDIO_VISUALIZATIONS, FFMPEG_DTS). New set = enum constant + `NativeLib` entry in `data/delivery/DeliverableDescriptorCatalog.kt` (per-ABI SHA-256/size pinned).
- Per-flavor: `@Multibinds` contributors; each flavor `di/*BundledDeliverableSetsModule.kt` `@IntoSet`-provides.
- **CRITICAL (S0971):** Play forbids downloading executable `.so`, so OCR_ENGINES + FFMPEG_DTS are **re-bundled into the APK**; only AUDIO_VISUALIZATIONS (pure `.mp4` data) is truly on-demand. **Any new native codec `.so` for `standard` must ship bundled in the APK. Only `noLegal` (sideload) can keep true on-demand native delivery.**
- Loader: `data/delivery/DeliveredNativeLibraryLoader.kt:47-123` (SHA-256 verify, `DexPathList` reflection splice, `System.load`, re-verify).
- **FFmpeg = audio-only** (`fms-ffmpeg-dts.aar` / `libffmpegJNI.so`, ~7.5 MB/ABI; DTS+APE/WMA/WavPack/TTA/DSD). No FFmpeg video renderer. `build.gradle.kts:1439-1449` has AV1 (`media3-decoder-av1`) + VP9 (`media3-decoder-vpx`) **commented out** ("build from source, same pipeline as fms-ffmpeg-dts.aar").
- Extensions UI: `data/delivery/DeliverableInventoryImpl.kt:130-142` lists FFMPEG_DTS under `ExtensionSection.MEDIA_PLAYBACK`.

## D. noLegal-only capability wiring templates (Rule 14, no BuildConfig.IS_*)

1. **Multibinding contributor** (best for "swap engine only in noLegal"): interface in `main` + `@Multibinds` empty in `main` + real impl + `@IntoSet` in `src/noLegal/`. Precedent: `domain/ocr/OcrEngineContributor.kt` + `src/noLegal/.../PaddleOcrEngineContributor.kt`.
2. **Interface + NoOp-in-main + per-flavor factory**: `ui/player/helpers/OfficeDocumentViewerHost.kt` (`NoOpOfficeDocumentViewerHost` in main); each flavor ships own `OfficeDocumentViewerProviderFactory.kt`; only `src/noLegal/` wires the real engine.
3. Plain per-flavor capability flag: `core/capability/MediaCapabilities.kt` bound by a per-flavor module.
- "GPL extractor" in noLegal today = **NewPipeExtractor** (link extraction), **unrelated to codecs**. A GPL codec lib (libVLC) would follow template 1/2 via `noLegalImplementation(...)`; no precedent yet.

## E. Format -> player routing + unsupported fallback

- `ui/player/helpers/MediaDisplayCoordinator.kt:19-58 display()`: VIDEO/AUDIO -> `callback.playVideo`; binary types filtered one layer up at `ui/player/helpers/PlayerMediaFilesLoader.kt:625,628` (`isPlayerSupportedType = !type.isBinaryFile()`) -> ISO never reaches coordinator.
- Resource dispatch: `ui/player/VideoPlayerManager.kt:667-716 playVideo()` -> `when(resourceType){CLOUD/SMB/SFTP/FTP/LOCAL/HTTP_STREAM/RTSP_STREAM}`.
- ISO today lands in `ui/browse/managers/BrowseBinaryFileHandler.kt` bottom sheet; `openWithDefaultApp()` uses invented MIME `application/iso` -> most choosers find no handler.
- Unsupported-format landing: `ui/player/helpers/PlayerEventHandler.kt:194-234 showUnsupportedFormatError()` (local -> dialog + "open in external player"; network -> dialog + "copy local first").
- New disc-image pseudo-type would need: MediaType member (or reuse BINARY_DISK), exception from `isBinaryFile()` gate, coordinator branch + callback, `MediaFamilyResolver.kt:39-48` mapping.

## F. MKV current-state verdict

- Registered VIDEO (`MediaExtensions.kt:8`); MIME `video/x-matroska` consistent across 7+ sites, no drift.
- Demux via stock `DefaultExtractorsFactory` (MatroskaExtractor), identical local/remote.
- Video codec decodes via **platform MediaCodec**; tuned factory (`EXTENSION_RENDERER_MODE_PREFER` + fallback) applies **only** on local/background-audio path. **No SW/FFmpeg video decoder anywhere** -> a codec absent from device HW fails local + remote.
- Audio codec: platform first; FFmpeg DTS/APE/WMA/WavPack/TTA/DSD fallback applies **only** to local + background audio, **NOT** SMB/FTP/SFTP/Cloud. Network MKV with DTS -> `VideoPlayerErrorHandler.kt:110-148` "Variant B" (audio disabled, video continues, warning toast) instead of decoding.
- Error codes: `ERROR_CODE_DECODING_FORMAT_UNSUPPORTED` / `PARSING_CONTAINER_UNSUPPORTED` / `DECODING_FAILED` (`VideoPlayerErrorHandler.kt:151-154`). Local gets ExoPlayer->MediaPlayer fallback; network skips it. `.mkv` matches neither the `.m2ts`/BD-TS nor `.vob`/DVD special routes.

**Verdict:** MKV plays today for any codec the device HW supports (local + network). Loses audio for DTS/exotic tracks **over network** (works locally). Fully fails with no SW fallback when the video codec lacks a platform decoder (local + remote).

## G. Integration points

**(i) MKV codec-gap fill:**
- No VM/UseCase changes (routing already codec-agnostic).
- Delivery: new DeliverableSet + `NativeLib`/`.so` in `DeliverableDescriptorCatalog.kt` + flavor bundling module (subject to Play-bundling constraint C).
- **Must fix** `SmbPlaybackHelper.kt:120`, `FtpPlaybackHelper.kt:106`, `SftpPlaybackHelper.kt:99`, `CloudPlaybackHelper.kt:71`: they build `ExoPlayer.Builder(context)` raw (no renderers factory) -> network never gets FFmpeg/fallback.
- Build: new `<flavor>Implementation(files(..))` mirroring `build.gradle.kts:1458-1461`, or build the commented av1/vpx stubs.

**(ii) ISO disc-image handling (zero precedent):**
- `domain/model/Models.kt:25-57` MediaType; `util/BinaryFileTypeDetector.kt:18-19,40-48` + `MediaExtensions.kt:28-46` single choke point; `PlayerMediaFilesLoader.kt:625,628` gate; `MediaDisplayCoordinator` + `MediaFamilyResolver.kt:39-48` dispatch.
- **ISO9660/UDF parsing has zero precedent.** Closest analog = ZIP (`BrowseArchiveManager`, `net.lingala.zip4j`) but zip4j cannot read ISO9660/UDF. Greenfield Repo/DataSource work.
- "Play video track inside DVD/BD ISO" -> closer analog `BdTsPlaybackHelper`/`VideoPlayerErrorHandler.kt:180-187` VOB/BD-TS hints, but no demuxer locating VOB/M2TS inside ISO filesystem exists.

## H. Out-of-scope defects (spec-draft candidates)

1. **Network video bypasses tuned renderers factory** - `Smb/Ftp/Sftp/CloudPlaybackHelper` build `ExoPlayer.Builder(context)` raw; bundled FFmpeg + fallback + EXTENSION_RENDERER_MODE_PREFER never apply to network video (broader than MKV). **-> folded INTO S1056 as the primary in-scope increment (remote parity is explicitly requested).**
2. **Dead BuildConfig flag `ENABLE_DTS_DECODER`** - declared 6x in build.gradle + `docs/DEV_OPS.md:295`, zero Kotlin read-sites. Rule 20 dead-weight. **-> park via /spec-draft.**
3. **Synthetic non-registered MIME `application/iso`** in `BrowseBinaryFileHandler.kt:157` for binary open-with. **-> park via /spec-draft.**

## Native build convention (owner/external gate)

Any new `.so` must be built NDK r25c + 16KB page-aligned (`-Wl,-z,max-page-size=16384`, `build.gradle.kts:1457`) for targetSdk 35 / Android 15+ / Play. Pipeline: `scripts/builders/build-ffmpeg-dts.sh` (WSL). Prebuilt AAR checked in at `app_v2/libs/fms-ffmpeg-dts.aar`.

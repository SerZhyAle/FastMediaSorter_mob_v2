# FastMediaSorter VR Edition

## What is the VR Edition?

FastMediaSorter VR is a dedicated edition of FastMediaSorter designed for VR headsets - Meta Quest 3, Quest Pro, Quest 2, and Android XR devices (Samsung Project Moohan and future). It is a **complete media player** identical to the standard edition, extended with OpenXR stereoscopic rendering.

The VR edition is not a separate app - it is the same codebase built as a `vr` product flavor with an additional OpenXR rendering layer.

## Key Differences from Standard

| Feature | Standard | VR |
|---------|----------|----|
| Stereoscopic rendering (SBS/OU) | Crop-preview for one eye | Full per-eye OpenXR rendering |
| 3D photo viewing | Flat display | Per-eye stereo with UV-crop |
| Cinema mode | N/A | Flat virtual screen in VR space |
| Save Frame (3D content) | 2D PNG | SBS PNG with both eye views |
| 3DVR tab in Control dialog | Hidden | Active (format override, IPD) |
| VR Settings block | Hidden | Active (auto-detect, render mode) |
| Wear OS companion | Included | Not included |
| Target devices | Phones, tablets | VR headsets |

## What's Identical

Everything else works the same - file operations (copy, move, delete, rename), sorting, favorites, network drives (SMB, SFTP, FTP), cloud storage (Google Drive, OneDrive, Dropbox), subtitles, audio track selection, sleep timer, slideshow, and all navigation controls.

## How It Works

1. **On a VR headset:** `VrPlayerActivity` inherits from `PlayerActivity` and adds `VrOpenXrRenderManager` as the rendering layer. ExoPlayer output is routed to an OpenXR Surface instead of a phone screen. Per-eye rendering is handled by `VrStereoRenderer`.

2. **On a regular phone:** If the VR APK is launched on a phone without an XR runtime, it shows a fallback screen suggesting to install the standard edition.

3. **In the standard edition:** When 3D stereoscopic content (SBS/OU) is detected, a CTA dialog suggests installing the VR edition for proper headset playback.

## Supported Content

| Content Type | Rendering |
|-------------|-----------|
| SBS video (Side-by-Side) | Per-eye stereo |
| OU video (Over-Under) | Per-eye stereo |
| SBS/OU photos | Per-eye stereo via Bitmap → GL texture |
| 2D video | Cinema mode (flat virtual screen) |
| 2D photos | Cinema mode |
| Audio | Standard playback (inherited) |

## Distribution

| Store | Platform | Build |
|-------|----------|-------|
| Meta Horizon Store | Quest 3 / Quest Pro / Quest 2 | `assembleVrRelease` |
| Google Play | Android XR devices | `bundleVrRelease` (AAB) |

Package name: `com.sza.fastmediasorter.vr`

## Build Commands

```powershell
# Debug
.\scripts\builders\build-vr-debug.ps1

# Debug + install on Quest via ADB
.\scripts\builders\build-vr-device.ps1

# Release APK (Meta Horizon Store)
.\scripts\builders\build-vr-release.ps1

# Release AAB + APK (Google Play / Android XR)
.\scripts\builders\build-vr-aab.ps1

# Gradle direct
.\gradlew.bat assembleVrDebug
.\gradlew.bat assembleVrRelease
.\gradlew.bat bundleVrRelease
```

## Technical Constraints

- **Supported ABI:** `arm64-v8a` only. Meta Quest 2/3/Pro and Android XR headsets are exclusively 64-bit ARM - no `armeabi-v7a` or `x86_64` slices are produced for VR flavors.
- **Minimum Android:** API 26 (Android 8.0). Quest 2 ≈ Android 10, Quest 3 ≥ Android 12.
- **XR runtime:** OpenXR 1.1.48+ required at launch. Without an XR runtime the app shows a fallback screen and does not start playback.
- **Native code:** ships the OpenXR loader AAR plus `openxr_native.so` (C++ bridge built by CMake). Adds ~8 MB of native payload over the standard build.
- **No Wear OS companion:** headsets have no paired watch, so `SUPPORT_WEAR_COMPANION = false`.
- **Package ID:** `com.sza.fastmediasorter.vr` belongs to the `vr` (Store) flavor. The sideload-VR build ships as the `noLegal` flavor and uses `com.sza.fastmediasorter` - it can coexist with `vr` on the same device.
- **DTS / extended codecs:** always bundled via `fms-ffmpeg-dts.aar`. Quest hardware is uniformly arm64, so the single-ABI AAR is sufficient for every VR user.

### Distribution Channels

Two flavors carry the VR feature surface, distributed through different channels:

- **`vr`** - Meta Horizon Store / Google Play AAB. Store-reviewed. Stays Store-clean: no GPL extractors, no Python runtime, no yt-dlp. If a store rejects the DTS decoder, the `vr` build can ship without it and sideload users are routed to `noLegal`.
- **`noLegal`** - ADB sideload via Developer Mode. The all-inclusive sideload build covers phones, tablets, Quest, and Android XR in a single APK. Always ships DTS, Python+yt-dlp+NewPipeExtractor, and the full VR runtime regardless of store policy. The VR feature surface is runtime-gated: on devices without an OpenXR runtime the VR controls show as disabled with an advisory notice.

> **Historical note.** Prior to 2026-05-19 a separate `vrUnlicensed` flavor existed for VR-only sideload. It was merged into `noLegal` under S0250 because `noLegal` already carried every dependency needed for VR plus the broader sideload surface. See [PLAN/S0250_nolegal-vr-unification.md](../PLAN/S0250_nolegal-vr-unification.md).

### Phone Fallback

If the VR APK is launched on a regular phone (no XR runtime detected), `VrPlayerActivity` displays a static fallback screen prompting the user to install the Standard edition. Media playback does not start - the VR build is not designed for flat screens.

## Related Documentation

- [VR Sideloading Guide](VR_SIDELOAD.md) - how to install the VR APK on Quest without a store
- [Features](FEATURES.md) - full feature inventory
- [Architecture](ARCHITECTURE.md) - project architecture overview

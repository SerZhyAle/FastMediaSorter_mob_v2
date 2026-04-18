# FastMediaSorter VR Edition

## What is the VR Edition?

FastMediaSorter VR is a dedicated edition of FastMediaSorter designed for VR headsets — Meta Quest 3, Quest Pro, Quest 2, and Android XR devices (Samsung Project Moohan and future). It is a **complete media player** identical to the standard edition, extended with OpenXR stereoscopic rendering.

The VR edition is not a separate app — it is the same codebase built as a `vr` product flavor with an additional OpenXR rendering layer.

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

Everything else works the same — file operations (copy, move, delete, rename), sorting, favorites, network drives (SMB, SFTP, FTP), cloud storage (Google Drive, OneDrive, Dropbox), subtitles, audio track selection, sleep timer, slideshow, and all navigation controls.

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

## Related Documentation

- [VR Sideloading Guide](VR_SIDELOAD.md) — how to install the VR APK on Quest without a store
- [Features](FEATURES.md) — full feature inventory
- [Architecture](ARCHITECTURE.md) — project architecture overview

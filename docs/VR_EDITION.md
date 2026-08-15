---
layout: default
title: "FastMediaSorter VR Edition"
permalink: /docs/VR_EDITION.html
---

# FastMediaSorter VR Edition

## What is the VR Edition?

FastMediaSorter VR is a dedicated build of FastMediaSorter for VR headsets - Meta Quest 3, Quest Pro, Quest 2, and Android XR devices (Samsung Project Moohan and future). It shares the exact same codebase and features as Standard, plus the OpenXR scaffolding for stereoscopic headset rendering.

The VR edition is not a separate app - it is the same codebase built as a `vr` product flavor with an additional OpenXR rendering layer.

**Where things stand today:** the immersive, per-eye headset experience is live in the `noLegal` sideload build (see the [VR Sideloading Guide](VR_SIDELOAD.md)). The `vr` flavor - the one meant for Meta Horizon Store / Google Play - builds the same OpenXR scaffolding, but its headset rendering isn't wired up yet; that work is tracked in epic S0773. Until it ships, installing `vr` gets you the full app on a headset with no working immersive mode - use `noLegal` if you want 3D immersion today.

## Key Differences from Standard

| Feature | Standard | VR (`vr` / `noLegal`) |
|---------|----------|----|
| Single-eye 3D crop for SBS/OU/180/360 content | Included, works in the flat player | Included, same code |
| Full per-eye immersive rendering on a headset | Not available | `noLegal`: yes, today. `vr`: planned (epic S0773) |
| Manual 3D-format override + detection-source settings | Hidden | Active (3D tab in the Control dialog, VR/3D block in Media settings) |
| Wear OS companion | Included | Not included |
| Target devices | Phones, tablets | Phones/tablets today; Quest and Android XR headsets once the immersive rewrite ships |

## What's Identical

Everything else works the same - file operations (copy, move, delete, rename), sorting, favorites, network drives (SMB, SFTP, FTP), cloud storage (Google Drive, OneDrive, Dropbox), subtitles, audio track selection, sleep timer, slideshow, and all navigation controls. Internet Streams is fully included - radio and video stream playback (http/HLS/DASH/RTSP), the curated stream catalog, inline audio mini-player with ICY now-playing metadata, and category/language filters all work the same as on the phone.

## How It Works

1. **Single-eye 3D, every flavor:** SBS/OU/180/360 content is auto-detected (filename markers, MP4/Matroska metadata, or an optional aspect-ratio heuristic) and cropped to one eye so it plays back normally on any flat screen - phone, tablet, or a headset's regular 2D window. This works out of the box, no VR build required.

2. **Full immersion, `noLegal` today:** on a Quest or other OpenXR headset running the `noLegal` sideload build, opening 3D content through the player's VR badge, Browse's "Open in VR Cinema" menu item, or the "Test Immersive" button in Settings starts a dedicated OpenXR session with real per-eye rendering for SBS/OU/180/360 content. Moving between files inside that session is next/previous, or through the in-headset browser described below; any button, key, or click exits back to the flat screen.

**Browsing inside the headset:** the immersive session has its own browse window, so you are not limited to the one file that opened it. Choosing **"Open in VR Cinema"** on a *resource* (rather than on a single file) opens that resource as a tile grid in front of you. Aim the controller ray at a tile and pull the trigger to select it: folders open in place, and a video or 3D image starts in the immersive player without ever dropping back to the flat screen. Tiles carry previews, and 3D images are classified by the same SBS/OU/equirectangular detection the flat player uses.

Two limits worth knowing: the in-headset browser reads **local resources only** - network and cloud sources are not offered there yet - and the grid is driven by the controller ray, not by the thumbstick.

3. **`vr` flavor:** builds and installs, but its headset rendering isn't connected yet - it plays like Standard until the immersive rewrite (epic S0773) lands.

## Supported Content

| Content Type | Rendering |
|-------------|-----------|
| SBS video (Side-by-Side) | Single-eye crop everywhere; full per-eye stereo in immersive mode (`noLegal`) |
| OU video (Over-Under) | Same as above |
| SBS/OU photos | Single-eye crop everywhere; per-eye stereo in immersive mode (`noLegal`) |
| 2D video / 2D photos | Normal playback and display, same as Standard |
| Audio | Standard playback (inherited) |
| Internet radio (http/HLS, ICY) | Inline audio playback via Streams mini-player |
| Video stream / RTSP | Fullscreen player, same as Standard; not available inside the immersive OpenXR session yet |

## Distribution

Today, only the `noLegal` sideload build has a working immersive headset experience. The `vr` flavor below is the intended Store channel, but its headset rendering isn't wired up yet (epic S0773) - installing it gets you the full app with no working immersive mode.

| Store | Platform | Build | Status |
|-------|----------|-------|--------|
| Meta Horizon Store | Quest 3 / Quest Pro / Quest 2 | `assembleVrRelease` | Planned - immersive mode not wired yet |
| Google Play | Android XR devices | `bundleVrRelease` (AAB) | Planned - immersive mode not wired yet |

Package name: `com.sza.fastmediasorter.vr`

For a working 3D immersive experience today, sideload the `noLegal` build instead - see the [VR Sideloading Guide](VR_SIDELOAD.md).

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
- **XR runtime:** OpenXR 1.1.48+ is required to enter immersive mode. Without an XR runtime detected, the app plays normally in the flat player - there is no separate fallback screen.
- **Native code:** ships the OpenXR loader AAR plus `openxr_native.so` (C++ bridge built by CMake). Adds ~8 MB of native payload over the standard build.
- **No Wear OS companion:** headsets have no paired watch, so `SUPPORT_WEAR_COMPANION = false`.
- **Package ID:** `com.sza.fastmediasorter.vr` belongs to the `vr` (Store) flavor. The sideload-VR build ships as the `noLegal` flavor and uses `com.sza.fastmediasorter` - it can coexist with `vr` on the same device.
- **DTS / extended codecs:** always bundled via `fms-ffmpeg-dts.aar`. Quest hardware is uniformly arm64, so the single-ABI AAR is sufficient for every VR user.

### Distribution Channels

Two flavors build the VR/XR code paths, but only one has a working immersive experience today:

- **`noLegal`** - ADB sideload via Developer Mode. The all-inclusive sideload build covers phones, tablets, Quest, and Android XR in a single APK. Always ships DTS, Python+yt-dlp+NewPipeExtractor, and the full VR runtime regardless of store policy - it's the only channel where immersive headset playback actually works today. The VR feature surface is runtime-gated: on devices without an OpenXR runtime the VR controls show as disabled with an advisory notice.
- **`vr`** - Meta Horizon Store / Google Play AAB. Stays Store-clean: no GPL extractors, no Python runtime, no yt-dlp. Builds the same OpenXR scaffolding as `noLegal`, but its immersive rendering isn't connected yet (epic S0773) - today it plays like Standard on a headset.

> **Historical note.** The current VR / noLegal surface is documented in [FEATURES.md](FEATURES.md) and [VR_SIDELOAD.md](VR_SIDELOAD.md). The legacy `vrUnlicensed` split is no longer the public reference path in this repository.

### Phone Fallback

If the `vr` APK is launched on a regular phone, it works exactly like Standard - there is no dedicated fallback screen today, since the flavor's immersive rendering isn't wired up yet (epic S0773). Once that lands, a phone without an XR runtime is expected to keep using the normal flat player.

## Related Documentation

- [VR Sideloading Guide](VR_SIDELOAD.md) - how to install the VR APK on Quest without a store
- [Features](FEATURES.md) - full feature inventory
- [Architecture](ARCHITECTURE.md) - project architecture overview

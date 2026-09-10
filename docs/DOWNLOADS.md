---
layout: default
title: "FastMediaSorter APK Downloads"
permalink: /docs/DOWNLOADS_EN.html
---

# FastMediaSorter APK Downloads

Compiled APKs are published as **GitHub Release assets** - they are not committed to the repository.

## Download (recommended)

🔗 **[Latest release](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/latest)**

Each release carries one asset per public build, named `FastMediaSorter-<edition>-<version>.apk`:

- **standard** - full-featured application (mobile / TV / car)
- **vr** - Meta Quest / XR edition
- **lite** - lightweight build (images + video, no cloud)
- **photos** - image-focused build
- **legacy** - older Android devices (API 23+)
- **wear** - Wear OS companion APK (manual install via adb)

The download buttons on the website link straight to these assets and always reflect the latest version.

## Mirror (Google Drive)

🔗 **[Google Drive folder](https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp?usp=sharing)** - password-protected ZIP archives, **ZIP password: `1`**. Use this when a network policy blocks direct `.apk` downloads.

## How it works

1. `scripts/release/build-release-spectrum.ps1` builds every release edition at one shared version.
2. `scripts/release/publish-github-release.ps1` uploads them all to a single GitHub Release tag.
3. The Google Drive ZIP mirror is refreshed by the build scripts in parallel.

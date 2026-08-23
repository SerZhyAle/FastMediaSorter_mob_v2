---
layout: default
title: "Module Selection Guide"
permalink: /docs/MODULE_SELECTION.html
---
# Module Selection Guide

## Overview

FastMediaSorter is a multi-module project with different build targets. The public app surface currently spans the main phone app (`app_v2`) with Standard / Lite / Photos / Legacy builds, plus the XR / noLegal sideload surface that owns the VR-capable path.

| Module | Type | Purpose |
|--------|------|---------|
| **wear** | Wear OS App | Smartwatch companion app |
| **app_v2** | Android App | Main phone app (Standard, Lite, Photos, Legacy, XR / noLegal) |

This guide explains how to pick which module you build and run.

### Feature availability by flavor

Some features are flavor-scoped. Key examples:

- **Internet Streams** (internet radio, HLS/DASH/RTSP playback, curated catalog): full scope on `standard`, `legacy`, `noLegal`, and `vr`; absent on `lite` and `photos` - the screen has no entry point in either.
- **VR / OpenXR rendering**: only the `vr` and `noLegal` flavors compile the VR source set, and of those only `noLegal` declares `SUPPORT_VR_PLAYER`, so full headset immersion ships in `noLegal` alone.
- **Wear OS companion**: `standard` and `noLegal`; excluded from `lite`, `photos`, `legacy`, and `vr`. `legacy` left the list in S1951 - it carries `applicationIdSuffix = ".legacy"`, and Play Services routes the Wear Data Layer by the phone's applicationId, so a legacy phone could never reach the watch app it claimed to pair with.

See [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md) for the generated per-flavor grid and [ALL_FEATURES.jsonl](ALL_FEATURES.jsonl) for the full feature inventory.

## The repository ships no default module

`.idea/` is listed in `.gitignore`, so nothing under `.idea/runConfigurations/` travels with a clone. There is no repository-provided run configuration, no default startup module, and no file to edit to change one. Which module Android Studio runs is a per-developer local choice that lives only on your machine, and Android Studio rewrites those files itself.

Practically this means two things:

- A fresh clone has an empty Run Configuration dropdown until Gradle sync populates it from `settings.gradle.kts`.
- Nothing you do in that dropdown affects anyone else, and nothing anyone else did affects you.

If you want a reproducible build that does not depend on IDE state at all, use the command line instead - see [Building from the command line](#building-from-the-command-line) below. That is the path the project's own tooling and CI use.

## Selecting a module in Android Studio

1. **Sync Gradle first** - `File → Sync Now` (Ctrl+Shift+Y). Until this completes the dropdown may be empty or stale.

2. **Click** the Run Configuration dropdown in the toolbar.

3. **Select** the configuration you want. After a sync Android Studio offers one per module and variant, for example:
   - `wear` - Wear OS companion
   - `app_v2` with the `standardDebug`, `liteDebug`, `photosDebug`, `legacyDebug`, `noLegalDebug`, or `vrDebug` variant

4. **Pick the variant** in the Build Variants panel (`View → Tool Windows → Build Variants`) if the flavor you want is not the active one. The dropdown runs whatever variant is active there.

5. **Click Run** (green button, or Shift+F10).

## Building from the command line

This is the reproducible path - it needs no IDE state and changes nothing about your local configuration.

### Fast checks

Use the `a.ps1` launcher from the repository root. Note that the phone and watch checks are separate: `fk`/`fkn`/`fr`/`fc`/`fu` all check `app_v2` only and will pass without compiling a single watch file.

```powershell
# app_v2
.\a.ps1 fk    # Kotlin compile check, standard flavor
.\a.ps1 fkn   # Kotlin compile check, noLegal flavor
.\a.ps1 fr    # Resources / manifest check
.\a.ps1 fc    # Code + resources
.\a.ps1 fu    # Full unit suite

# wear
.\a.ps1 fw    # Kotlin compile check, wear module
.\a.ps1 fwr   # Resources / manifest check, wear module
.\a.ps1 fwu   # Unit suite, wear module
```

### Build Wear OS

```powershell
# Build scripts
.\scripts\builders\build-wear-debug.PS1
.\scripts\builders\build-wear-release.PS1

# Or Gradle directly
.\gradlew.bat :wear:assembleDebug
.\gradlew.bat :wear:assembleRelease
```

### Build the main app

```powershell
# Build scripts
.\scripts\builders\build-standard-debug.ps1
.\scripts\builders\build-lite-debug.ps1
.\scripts\builders\build-photos-debug.ps1
.\scripts\builders\build-legacy-debug.ps1
.\scripts\builders\build-nolegal-debug.ps1

# Or Gradle directly
.\gradlew.bat :app_v2:assembleStandardDebug
.\gradlew.bat :app_v2:assembleLiteDebug
.\gradlew.bat :app_v2:assemblePhotosDebug
.\gradlew.bat :app_v2:assembleLegacyDebug
.\gradlew.bat :app_v2:assembleNoLegalDebug
```

### Everything else

```powershell
.\gradlew.bat tasks   # List all available tasks
```

## Troubleshooting

### The Run Configuration dropdown is empty

Gradle has not synced yet, or the sync failed.

1. `File → Sync Now` (Ctrl+Shift+Y)
2. Check the Build output for a sync error
3. Close and reopen Android Studio

### The wrong module runs when I click Run

The dropdown and the Build Variants panel disagree, or you selected a different configuration earlier in the session. Check the dropdown, then check `View → Tool Windows → Build Variants` for the active variant.

### "Module not found" after switching branches

`File → Sync Now`. The module set comes from `settings.gradle.kts` and needs a resync when it changes.

### I want to build without touching my IDE configuration

Use the command line - see [Building from the command line](#building-from-the-command-line). None of those commands read or write `.idea/`.

## FAQ

**Q: Can I have both Wear and the main app running?**
A: One run configuration is active at a time, but you can switch between them from the dropdown, and you can build both from the command line in any order.

**Q: Does changing what I run affect version numbers?**
A: No. Version numbering is independent of which module you run. A release stamps `app_v2` and `wear` together from one timestamp - see [WEAR_OS_BUILD_CONFIG.md](WEAR_OS_BUILD_CONFIG.md).

**Q: How do I know which module is currently selected?**
A: The Run Configuration dropdown in the toolbar shows it, and the Build Variants panel shows the active variant.

**Q: Why is my dropdown different from a teammate's?**
A: Because `.idea/` is gitignored, so run configurations are local to each machine by design.

**Q: Can I create a custom run configuration?**
A: Yes - `Run → Edit Configurations → +` → select the module → configure → OK. It stays on your machine.

---

For more details on building and testing, see:

- [WEAR_OS_QUICK_START.md](WEAR_OS_QUICK_START.md)
- [WEAR_OS_SETUP.md](WEAR_OS_SETUP.md)
- [AGENTS.md](../AGENTS.md) - Development workflow

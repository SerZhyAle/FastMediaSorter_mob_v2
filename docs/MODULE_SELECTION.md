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

This guide explains how to select and configure which module runs by default.

### Feature availability by flavor

Some features are flavor-scoped. Key examples:

- **Internet Streams** (internet radio, HLS/DASH/RTSP playback, curated catalog): full scope on `standard`, `legacy`, `noLegal`, and `vr`; absent on `lite` and `photos` - the screen has no entry point in either.
- **VR / OpenXR rendering**: only the `vr` and `noLegal` flavors compile the VR source set, and of those only `noLegal` declares `SUPPORT_VR_PLAYER`, so full headset immersion ships in `noLegal` alone.
- **Wear OS companion**: `standard`, `noLegal`, and `legacy`; excluded from `lite`, `photos`, and `vr`.

See [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md) for the generated per-flavor grid and [ALL_FEATURES.jsonl](ALL_FEATURES.jsonl) for the full feature inventory.

## Quick Selection (Interactive)

Run this script to interactively select your module:

```powershell
.\scripts\select-wear-module.ps1
```

This will:

1. Show available modules
2. Guide you through selection
3. Provide next steps with the selected module

## Default Configuration: Wear OS

By default, **Wear OS (Debug)** is set as the startup module. This means:

- When you click the **Run** button in Android Studio, it builds and deploys to a Wear OS device
- The run configuration dropdown shows `wear [Debug]` pre-selected

### Why Wear OS is Default?

- ✅ Faster build times (smaller APK)
- ✅ Simpler configuration (single build variant)
- ✅ Ideal for Wear OS MVP testing

## Changing Default Module

### Option 1: Interactive Script (Recommended)

```powershell
.\scripts\select-wear-module.ps1
```

### Option 2: Manual Selection in Android Studio

1. **Click** the Run Configuration dropdown in the toolbar
   - It shows the module name (currently `wear [Debug]`)

2. **Select** your desired configuration:
   - `wear [Debug]` - Wear OS debug build
   - `wear [Release]` - Wear OS release build
   - `app [standardDebug]` - Main app (Standard flavor)
   - Other app variants

3. **Click Run** (green button or Shift+F10)

### Option 3: Edit XML Configuration

Edit `.idea/runConfigurations.xml`:

```xml
<!-- To set Wear Debug as default: -->
<component name="RunManager" selected="Android App.wear [Debug]">

<!-- To set Main App Standard as default: -->
<component name="RunManager" selected="Android App.app [standardDebug]">
```

Then sync: `File → Sync Now` (Ctrl+Shift+Y)

## Available Run Configurations

### Wear OS Configurations

| Config | File | Type |
|--------|------|------|
| `wear [Debug]` | `.idea/runConfigurations/wear__Debug_.xml` | Android App |
| `wear [Release]` | `.idea/runConfigurations/wear__Release_.xml` | Android App |

### Main App Configurations

All in `.idea/runConfigurations/`:

| Flavor | Debug | Release |
|--------|-------|---------|
| standard | `app__standardDebug_` | `app__standardRelease_` |
| lite | `app__liteDebug_` | `app__liteRelease_` |
| photos | `app__photosDebug_` | `app__photosRelease_` |
| legacy | `app__legacyDebug_` | `app__legacyRelease_` |
| noLegal | `app__noLegalDebug_` | `app__noLegalRelease_` |

## Building Without Changing Default

### Build Wear OS

```powershell
# Use build scripts (doesn't change default config)
.\scripts\builders\build-wear-debug.PS1
.\scripts\builders\build-wear-release.PS1

# Or use Gradle directly
.\gradlew.bat :wear:assembleDebug
.\gradlew.bat :wear:assembleRelease
```

### Build Main App

```powershell
# Use build scripts
.\scripts\builders\build-standard-debug.ps1
.\scripts\builders\build-lite-debug.ps1
.\scripts\builders\build-photos-debug.ps1
.\scripts\builders\build-nolegal-debug.ps1

# Or use Gradle
.\gradlew.bat :app_v2:assembleStandardDebug
.\gradlew.bat :app_v2:assembleLiteDebug
.\gradlew.bat :app_v2:assemblePhotosDebug
.\gradlew.bat :app_v2:assembleNoLegalDebug
```

## Syncing After Configuration Changes

After changing the default module configuration:

```
File → Sync Now
```

Or press: **Ctrl+Shift+Y**

This reloads the project and applies the configuration changes.

## Troubleshooting

### "Run configuration not available"

1. Sync Gradle: `File → Sync Now`
2. Close and reopen Android Studio
3. Check if all files exist in `.idea/runConfigurations/`

### "Wrong module selected when I click Run"

1. Check `.idea/runConfigurations.xml` - verify the `selected` attribute
2. Look at the run configuration dropdown - it should show the active config
3. Manually select the correct config before clicking Run

### "I built the main app but need to switch to Wear"

```powershell
# Use the selection script
.\scripts\select-wear-module.ps1

# Or manually select from dropdown in Android Studio
```

## Command Reference

```powershell
# Interactive module selection
.\scripts\select-wear-module.ps1

# Build Wear OS
.\scripts\builders\build-wear-debug.PS1      # Debug
.\scripts\builders\build-wear-release.PS1    # Release

# Build Main App (all flavors)
.\scripts\builders\build-standard-debug.ps1  # Standard Debug
.\scripts\builders\build-lite-debug.ps1      # Lite Debug
.\scripts\builders\build-photos-debug.ps1    # Photos Debug
.\scripts\builders\build-legacy-debug.ps1    # Legacy Debug
.\scripts\builders\build-nolegal-debug.ps1   # noLegal / XR Debug

# Gradle direct builds (without changing default)
.\gradlew.bat :wear:assembleDebug
.\gradlew.bat :app_v2:assembleStandardDebug

# View all available tasks
.\gradlew.bat tasks
```

## Configuration Files

| File | Purpose |
|------|---------|
| `.idea/runConfigurations.xml` | Specifies default run config (wear [Debug]) |
| `.idea/runConfigurations/wear__Debug_.xml` | Wear OS Debug config |
| `.idea/runConfigurations/wear__Release_.xml` | Wear OS Release config |
| `.idea/runConfigurations/app__standardDebug_.xml` | Main App Standard Debug |
| (more app configs..) | Other app flavor configs |

## Best Practices

1. ✅ Use `select-wear-module.ps1` for interactive selection
2. ✅ Use build scripts (`.\scripts\build-*.ps1`) to avoid changing default
3. ✅ Sync Gradle (`Ctrl+Shift+Y`) after manual configuration changes
4. ✅ Check the run config dropdown before clicking Run
5. ✅ Keep Wear OS as default if you're primarily testing the companion app

## FAQ

**Q: Can I have both Wear and Main app running?**
A: No, only one configuration is active at a time. But you can switch quickly between them using the dropdown.

**Q: Does changing the default affect version numbers?**
A: No. Version numbering is independent of which module you run.

**Q: How do I know which module is currently default?**
A: Look at the run configuration dropdown in the toolbar - it shows the active module.

**Q: Can I create a custom run configuration?**
A: Yes - Run → Edit Configurations → + → Select module → Configure → OK

**Q: Why is Wear OS the default?**
A: Faster iteration cycle for the companion app MVP. You can change this anytime.

---

For more details on building and testing, see:

- [WEAR_OS_QUICK_START.md](WEAR_OS_QUICK_START.md)
- [WEAR_OS_SETUP.md](WEAR_OS_SETUP.md)
- [AGENTS.md](../AGENTS.md) - Development workflow

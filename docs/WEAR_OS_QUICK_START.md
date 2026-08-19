---
layout: default
title: "Wear OS Quick Start - Running in Android Studio"
permalink: /docs/WEAR_OS_QUICK_START.html
---
# Wear OS Quick Start - Running in Android Studio

## TL;DR - Get Running in 5 Minutes

### Step 0: Configure Wear Module (First Time Only - Optional)

```powershell
.\scripts\select-wear-module.ps1
```

This interactive script helps you select Wear OS as your primary module. If you skip this, Wear OS is already the default.

### Step 1: Sync Gradle (First Time Only)

```
File → Sync Now
```

### Step 2: Select Run Configuration

- In Android Studio toolbar, find the **Run Configuration** dropdown
- It should show **wear [Debug]** (already selected by default)
- Or click the dropdown and select **wear [Debug]** or **wear [Release]**

### Step 3: Launch

- Click the green **Run** button (or press **Shift+F10**)
- Select target device/emulator from dialog

**Done!** The app should build and deploy to your Wear OS device/emulator.

---

## Module Selection

### Wear OS is Set as Default ✅

The project is configured so **Wear OS [Debug]** is the default run configuration. This means:

- ✅ Just click Run and it builds/deploys Wear OS
- ✅ Faster iteration for Wear OS development
- ✅ Can easily switch to other modules anytime

### Switch Between Modules

To change which module runs:

```powershell
# Interactive selection
.\scripts\select-wear-module.ps1
```

Or manually:

1. Click run config dropdown in Android Studio
2. Select your desired module
3. Click Run

For details: See [MODULE_SELECTION.md](MODULE_SELECTION.md)

---

## Settings Organization

Wear settings are grouped into **Media Types**, **Slideshow**, **Other**, and **About**. The owner
ruled on 2026-08-16: two settings about the same thing earn a new group; a single setting with no
relatives lives in **Other** until relatives appear, then it moves into that new group. This keeps
the root screen short while allowing the watch app's settings to grow without returning to one
flat list.

---

## Running Without Android Studio

### Quick Build + Deploy

From PowerShell in project root:

```powershell
# Build APK
.\scripts\builders\build-wear-debug.PS1

# Deploy (if device connected via ADB)
.\gradlew.bat :wear:installDebug
```

### Manual APK Installation

```powershell
# Install debug APK to device
adb install .\wear\build\outputs\apk\debug\wear-debug.apk

# Launch app
adb shell am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.wear.MainActivity
```

---

## Setting Up Wear OS Emulator

### Auto-Setup (Recommended)

Android Studio will automatically detect and create emulators. Just:

1. Click **Run**
2. Choose **Create New Virtual Device**
3. Select "Wear OS" category
4. Follow wizard

### Manual AVD Manager

```
Tools → Device Manager → Create Device
→ Select "Wear OS Large Round" (or your preferred model)
→ API 30+ (Wear OS 3.0 or later)
→ Finish
```

---

## Configuration Files

The following have been created/updated:

| File | Purpose |
|------|---------|
| `.idea/runConfigurations/wear__Debug_.xml` | Android Studio debug configuration |
| `.idea/runConfigurations/wear__Release_.xml` | Android Studio release configuration |
| `wear/build.gradle.kts` | Module build config (uses KSP, not KAPT) |
| `docs/WEAR_OS_SETUP.md` | Complete setup guide |

---

## Troubleshooting

### "Module not found" error

```powershell
# Reload project
File → Sync Now (Ctrl+Shift+Y)
```

### Compilation fails with annotation processor errors

Already fixed! This project uses **KSP** instead of deprecated KAPT for annotation processing.

### APK deployment fails

1. Check device connection: `adb devices`
2. Enable debugging on the watch (Settings → Developer Options). A dockless watch such as Samsung
   Galaxy Watch has no USB data path and needs **Wireless debugging** plus an `adb pair` /
   `adb connect` round trip - see [WEAR_OS_SETUP.md](WEAR_OS_SETUP.md) "Physical Wear Device"
3. Try: `.\gradlew.bat :wear:installDebug --info`

### Emulator is slow

- Disable animations in emulator
- Allocate more RAM: Emulator Settings → RAM

---

## What's Different About Wear OS

| Feature | Wear OS | Phone |
|---------|---------|-------|
| **Screen Size** | 380x380 or 454x454 | 1080x2400+ |
| **Input** | Rotary, touch, buttons | Touch, gestures |
| **Battery** | Critical | Important |
| **CPU** | Low-power | Full power |
| **Memory** | Limited (~500MB) | Ample |
| **UI Framework** | Compose for Wear OS | Material 3 Compose |

**Result**: Simplified UI, no complex transitions, minimal animations, optimized layouts in `values-sw480dp/`.

---

## Next Steps

1. ✅ Build and run the app
2. 📖 Read [WEAR_OS_SETUP.md](WEAR_OS_SETUP.md) for complete guide
3. 🔧 Check [AGENTS.md](../AGENTS.md) for development workflow
4. 🧪 Write tests: `wear/src/test/` and `wear/src/androidTest/`
5. 📤 Build release: `.\scripts\builders\build-wear-release.PS1`

---

## Key Commands

```powershell
# Build
.\scripts\builders\build-wear-debug.PS1       # Fast debug build
.\scripts\builders\build-wear-release.PS1     # Optimized release

# Clean
.\gradlew.bat :wear:clean

# Test
.\gradlew.bat :wear:testDebugUnitTest

# Lint
.\gradlew.bat :wear:lintDebug

# Analyze dependencies
.\gradlew.bat :wear:dependencies
```

---

## Emulator Shortcuts

| Shortcut | Action |
|----------|--------|
| **Ctrl+M** | Stop and restart app |
| **Ctrl+R** | Reload app |
| **F6** | Take screenshot |
| **Ctrl+Shift+Z** | Open AVD Manager |

---

**Happy developing!** 🚀

For issues, see WEAR_OS_SETUP.md or check [AGENTS.md](../AGENTS.md) development guidelines.

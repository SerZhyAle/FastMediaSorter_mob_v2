---
layout: default
title: "Wear OS Build Configuration Summary"
permalink: /docs/WEAR_OS_BUILD_CONFIG.html
---
# Wear OS Build Configuration Summary

**Date**: January 27, 2026  
**Status**: ✅ Ready for Development

## What's Been Set Up

### 1. **Annotation Processing Migration** ✅

- **From**: KAPT (deprecated, had NullPointerException bug with SavedStateHandle)
- **To**: KSP (modern, recommended by Google)
- **Files Changed**:
  - `build.gradle.kts` - Added KSP plugin v1.9.24-1.0.20
  - `wear/build.gradle.kts` - Replaced KAPT with KSP for Hilt compilation

### 2. **Compilation Errors Fixed** ✅

All compilation errors resolved:

- ✅ SavedStateHandle removed from ViewModels (KAPT bug workaround)
- ✅ WearMediaFile constructor calls fixed
- ✅ NetworkSourcesUiState duplicate removed
- ✅ Missing composable branches added to NetworkSourcesScreen

### 3. **Android Studio Run Configurations** ✅

Created two production-ready configurations with Wear OS as default:

| Configuration | Location | Build Type | Purpose | Default |
|---|---|---|---|---|
| `wear [Debug]` | `.idea/runConfigurations/wear__Debug_.xml` | Debug | Fast iteration, debuggable | ✅ Yes |
| `wear [Release]` | `.idea/runConfigurations/wear__Release_.xml` | Release | Production, optimized | - |

**Default Behavior**:

- Wear OS [Debug] is set as the startup module in `.idea/runConfigurations.xml`
- When you click Run in Android Studio, it automatically runs Wear OS
- Can be changed anytime using the run configuration dropdown or `select-wear-module.ps1` script

### 4. **Module Selection Setup** ✅

Added interactive module selection:

- Script: `scripts/select-wear-module.ps1` - Interactive module selector
- Documentation: `docs/MODULE_SELECTION.md` - Complete guide for switching modules
- Default: Wear OS is pre-configured as the primary module
- Configuration: `.idea/runConfigurations.xml` - Specifies which module runs by default

### 5. **Build Scripts** ✅

Already available in `scripts/`:

- `build-wear-debug.PS1` - Quick debug build
- `build-wear-release.PS1` - Release build
- `select-wear-module.ps1` - Interactive module selection

Neither wear builder stamps a version: both carry the constants checked into
`wear/build.gradle.kts`. A release version is stamped into `app_v2` and `wear` together by
`scripts/release/build-release-spectrum.ps1`, from one timestamp, so the two modules ship one
`versionName` and two distinct `versionCode`s - the watch code is the app code without its trailing
minute digit, because Play refuses a release that repeats a `versionCode` under the shared
`applicationId`. Gate: `scripts/quality/assert-module-version-parity.ps1`.

### 6. **Documentation** ✅

Comprehensive guides created:

| Document | Path | Purpose |
|---|---|---|
| **WEAR_OS_QUICK_START.md** | `docs/WEAR_OS_QUICK_START.md` | Get started in 5 min |
| **WEAR_OS_SETUP.md** | `docs/WEAR_OS_SETUP.md` | Complete setup guide |
| **MODULE_SELECTION.md** | `docs/MODULE_SELECTION.md` | Switch between modules |

## Build Status

```
BUILD SUCCESSFUL in 11s
40 actionable tasks: 14 executed, 26 up-to-date
```

**APK Generated**: `wear/build/outputs/apk/debug/wear-debug.apk`

Size, measured 2026-08-15 (S1679). Compare a new build against the **release** number - the debug one is not minified and is misleading by a factor of seven:

- release: 10,808,958 bytes (~10.3 MB), of which ~83 % is dex
- debug: 75,774,884 bytes (~72 MB), almost entirely un-minified bytecode

## How to Use

### In Android Studio

1. Sync Gradle: `File → Sync Now`
2. Select: **wear [Debug]** from Run Configuration dropdown
3. Click: Green Run button
4. Select: Your device/emulator

### From Command Line

```powershell
# Build and deploy
.\scripts\builders\build-wear-debug.PS1

# Or use Gradle directly
.\gradlew.bat :wear:assembleDebug
```

## Technical Details

### Architecture

- **Clean Architecture** + **MVVM**
- **KSP** for annotation processing (Hilt DI)
- **Jetpack Compose for Wear OS** 1.3.0
- **ExoPlayer media3** 1.2.1 for playback

### Module Structure

```
wear/
├── src/main/
│   ├── java/com/sza/fastmediasorter/wear/
│   │   ├── ui/           # Compose screens
│   │   ├── domain/       # Business logic
│   │   ├── data/         # Network & storage
│   │   └── di/           # Hilt modules
│   ├── res/
│   └── AndroidManifest.xml
└── build.gradle.kts
```

### Key Configurations

- **Compile SDK**: 36
- **Target SDK**: 36
- **Min SDK**: 28 (Wear OS 2.0+)
- **Install package (`applicationId`)**: `com.sza.fastmediasorter` - identical to the phone app on purpose; Play Services drops Data Layer traffic unless the package name and the signing certificate both match across the two devices (S1681)
- **Code namespace**: `com.sza.fastmediasorter.wear` - keeps the `.wear` segment, so class names are unaffected by the line above
- **Main Activity**: `com.sza.fastmediasorter.wear.MainActivity`

## Verification Checklist

- ✅ KAPT → KSP migration complete
- ✅ No compilation errors
- ✅ APK builds successfully
- ✅ Wear OS set as default module
- ✅ Module selection script available (`select-wear-module.ps1`)
- ✅ Run configurations available in dropdown
- ✅ Android Studio configs created
- ✅ Run configurations available in dropdown
- ✅ Build scripts functional
- ✅ Documentation complete

## Performance Metrics

| Metric | Value |
|---|---|
| **Build Time** | ~11s (first build), ~2s (incremental) |
| **APK Size (release)** | 10,808,958 bytes, measured 2026-08-15 |
| **APK Size (debug)** | 75,774,884 bytes - not minified, do not compare against it |
| **Min API** | 28 (Wear OS 2.0) |
| **Target API** | 36 |
| **Compile SDK** | 36 |

## Next Steps

1. **Module Selection (Optional)**
   - Run: `.\scripts\select-wear-module.ps1` to interactively choose module
   - Or manually select from dropdown in Android Studio
   - Wear OS is already the default

2. **Test Device Setup**
   - Set up Wear OS emulator (Tools → Device Manager)
   - Or connect physical Wear OS device

3. **First Run**
   - Wear OS [Debug] is pre-selected in dropdown
   - Just click Run button (or Shift+F10)
   - App will build and deploy

4. **Debug/Develop**
   - Use Logcat for debugging
   - Hot reload: Ctrl+M
   - Layout Inspector: Tools → Layout Inspector

5. **Switch Modules** (Optional)
   - Click run config dropdown
   - Select different module
   - Click Run

6. **Release**
   - Use `wear [Release]` config or
   - Run `.\scripts\builders\build-wear-release.PS1`

## Module Selection

See [MODULE_SELECTION.md](MODULE_SELECTION.md) for:

- Interactive module selection script
- Manual selection via dropdown
- Default configuration explained
- Troubleshooting module issues

## Troubleshooting

### "Module FastMediaSorter_mob_v2.wear.main not found"

```powershell
File → Sync Now  # or Ctrl+Shift+Y
```

### "KSP compilation failed"

```powershell
.\gradlew.bat :wear:clean :wear:assembleDebug
```

### "Wrong module selected"

```powershell
# Use interactive selection
.\scripts\select-wear-module.ps1

# Or manually select from dropdown in Android Studio toolbar

### Emulator not appearing

```powershell
.\gradlew.bat --stop  # Stop Gradle daemon
```

## Support Resources

- [Wear OS Developer Docs](https://developer.android.com/wear)
- [Jetpack Compose for Wear](https://developer.android.com/wear/compose)
- Project Guides: `docs/WEAR_OS_*.md`

---

**Configuration Complete!** ✅

You're ready to build and run the Wear OS app in Android Studio.

For quick start: See `docs/WEAR_OS_QUICK_START.md`  
For detailed guide: See `docs/WEAR_OS_SETUP.md`

---
layout: default
title: "Wear OS Build Configuration Summary"
permalink: /docs/WEAR_OS_BUILD_CONFIG.html
---
# Wear OS Build Configuration Summary

**Originally written**: January 27, 2026
**Last reconciled against the tree**: 2026-08-23 (S1978)
**Status**: Ready for development

> This page describes the `wear` module as it exists in the repository. It deliberately says nothing
> about Android Studio run configurations: `.idea/` is gitignored, so no run configuration, default
> module, or startup setting travels with a clone. Choosing what to run is a local, per-developer
> action - see [MODULE_SELECTION.md](MODULE_SELECTION.md).

## Build Setup

### 1. Annotation Processing: KSP

- **From**: KAPT (deprecated, had NullPointerException bug with SavedStateHandle)
- **To**: KSP (modern, recommended by Google)
- **Files**:
  - `build.gradle.kts` - KSP plugin
  - `wear/build.gradle.kts` - KSP for Hilt compilation

### 2. Compilation Fixes Applied

- SavedStateHandle removed from ViewModels (KAPT bug workaround)
- WearMediaFile constructor calls fixed
- NetworkSourcesUiState duplicate removed
- Missing composable branches added to NetworkSourcesScreen

### 3. Build Scripts

Available in `scripts/builders/`:

- `scripts/builders/build-wear-debug.PS1` - Quick debug build
- `scripts/builders/build-wear-release.PS1` - Release build of this module alone. `-Artifact Apk|Aab|Both` selects `:wear:assembleRelease`, `:wear:bundleRelease` or both in one gradle call. It carries the version already checked into `wear/build.gradle.kts` unless `-VersionName`/`-VersionCode` pin one.

For a release that ships the watch, the artifacts come from `scripts/release/build-release-spectrum.ps1` instead, with `wear` in `-Flavors`. That script stamps one version into both `app_v2` and `wear` first, then builds `:wear:assembleRelease` and `:wear:bundleRelease` in the same invocation, so the sideload APK and the Play bundle cannot disagree about what they are (S2040). It refuses to finish if either artifact is missing.

Fast checks live on the `a.ps1` launcher. The phone targets do not cover this module: `fk`, `fkn`,
`fr`, `fc` and `fu` all check `app_v2` and exit 0 without compiling a single watch file.

- `.\a.ps1 fw` - Kotlin compile check, wear module
- `.\a.ps1 fwr` - Resources / manifest check, wear module
- `.\a.ps1 fwu` - Unit suite, wear module

Neither wear builder stamps a version: both carry the constants checked into
`wear/build.gradle.kts`. A release version is stamped into `app_v2` and `wear` together by
`scripts/release/build-release-spectrum.ps1`, from one timestamp, so the two modules ship one
`versionName` and two distinct `versionCode`s - the watch code is the app code without its trailing
minute digit, because Play refuses a release that repeats a `versionCode` under the shared
`applicationId`. Gate: `scripts/quality/assert-module-version-parity.ps1`.

### 4. Documentation

| Document | Path | Purpose |
|---|---|---|
| **WEAR_OS_QUICK_START.md** | `docs/WEAR_OS_QUICK_START.md` | Get started in 5 min |
| **WEAR_OS_SETUP.md** | `docs/WEAR_OS_SETUP.md` | Complete setup guide |
| **MODULE_SELECTION.md** | `docs/MODULE_SELECTION.md` | Pick which module you build and run |

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
2. Select **wear** from the Run Configuration dropdown
3. Click the green Run button
4. Select your device/emulator

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

1. **Test Device Setup**
   - Set up Wear OS emulator (Tools → Device Manager)
   - Or connect physical Wear OS device

2. **First Run**
   - Select **wear** in the Run Configuration dropdown
   - Click Run (or Shift+F10)
   - App will build and deploy

3. **Debug/Develop**
   - Use Logcat for debugging
   - Hot reload: Ctrl+M
   - Layout Inspector: Tools → Layout Inspector

4. **Release**
   - Run `.\scripts\builders\build-wear-release.PS1`

## Troubleshooting

### "Module FastMediaSorter_mob_v2.wear.main not found"

```powershell
File → Sync Now  # or Ctrl+Shift+Y
```

### "KSP compilation failed"

```powershell
.\gradlew.bat :wear:clean :wear:assembleDebug
```

### Wrong module selected

Check the Run Configuration dropdown in the toolbar, then check the active variant in
`View → Tool Windows → Build Variants`. Nothing in the repository sets either one.

### Emulator not appearing

```powershell
.\gradlew.bat --stop  # Stop Gradle daemon
```

## Support Resources

- [Wear OS Developer Docs](https://developer.android.com/wear)
- [Jetpack Compose for Wear](https://developer.android.com/wear/compose)
- Project Guides: `docs/WEAR_OS_*.md`

---

For quick start: See `docs/WEAR_OS_QUICK_START.md`
For detailed guide: See `docs/WEAR_OS_SETUP.md`

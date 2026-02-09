---
layout: default
title: "Wear OS Configuration Guide"
permalink: /docs/WEAR_OS_SETUP.html
---
# Wear OS Configuration Guide

## Overview

FastMediaSorter now includes a Wear OS companion app for browsing and playing media on smartwatches. This guide covers how to build and run the Wear OS app in Android Studio.

## Prerequisites

1. **Android Studio** (latest version recommended)
2. **Android SDK Tools** (API 30+ for Wear OS 3.0)
3. **Wear OS Emulator** or **Physical Wear Device**
4. **Gradle** (included in project)

## Building the Wear OS App

### Via PowerShell (Recommended)

Run the build scripts from the project root:

```powershell
# Debug build (fastest, no version bump)
.\scripts\builders\build-wear-debug.PS1

# Release build (optimized, requires keystore)
.\scripts\builders\build-wear-release.PS1
```

### Via Gradle CLI

```powershell
# Debug build
.\gradlew.bat :wear:assembleDebug

# Release build
.\gradlew.bat :wear:assembleRelease

# Both main app and wear
.\gradlew.bat assemble
```

### Via Android Studio

1. Open the project in Android Studio
2. Select **wear [Debug]** or **wear [Release]** from the Run Configuration dropdown
3. Click the **Run** button (green play icon)
4. Select your target device/emulator

## Run Configurations

Pre-configured run configurations are available:

- **wear [Debug]**: Debug build for testing
- **wear [Release]**: Release build for production

These are defined in `.idea/runConfigurations/` and should appear in Android Studio's configuration dropdown.

## Setting Up a Wear OS Emulator

### Using Android Studio

1. **Open Device Manager**
   - Tools → Device Manager
   - Click **Create Device**

2. **Select Wear OS Category**
   - Choose a Wear OS device template (e.g., "Wear OS Large Round")
   - Click **Next**

3. **Select System Image**
   - Choose API 30+ (Wear OS 3.0 or later recommended)
   - Click **Download** if needed
   - Click **Next**

4. **Configure Virtual Device**
   - Set Device Name (e.g., "Wear OS 3.0 Emulator")
   - Enable **Cold Boot**
   - Click **Finish**

### Starting the Emulator

```powershell
# List available emulators
& "C:\Users\[YourUsername]\AppData\Local\Android\Sdk\emulator\emulator" -list-avds

# Start emulator
& "C:\Users\[YourUsername]\AppData\Local\Android\Sdk\emulator\emulator" -avd "Wear OS 3.0 Emulator"
```

## Deploying to Device

### Physical Wear Device

1. **Enable Developer Mode**
   - Settings → System → About → Build Number (tap 7 times)
   - Settings → Developer Options → USB Debugging (enable)

2. **Connect via USB**
   - Connect smartwatch to computer with USB cable
   - Allow USB Debugging permission on device

3. **Deploy APK**
   - In Android Studio, select your device from the configuration dropdown
   - Click **Run**

### Wear OS Emulator

- Follow the same process but select the emulator from the dropdown

## Project Structure

```
wear/
├── src/
│   ├── main/
│   │   ├── java/com/sza/fastmediasorter/wear/
│   │   │   ├── MainActivity.kt          # Entry point
│   │   │   ├── FastMediaSorterWearApp.kt    # Hilt Application
│   │   │   ├── ui/                      # UI screens (Compose)
│   │   │   ├── domain/                  # Business logic
│   │   │   ├── data/                    # Data sources & repositories
│   │   │   └── di/                      # Dependency Injection
│   │   ├── res/
│   │   │   ├── values/                  # Strings, colors, dimensions
│   │   │   └── values-sw480dp/          # Round watch optimization
│   │   └── AndroidManifest.xml          # Manifest
│   ├── debug/                           # Debug-only resources
│   └── test/                            # Unit tests
└── build.gradle.kts                     # Build configuration
```

## Architecture

### Clean Architecture + MVVM

- **UI Layer** (`ui/`): Composable screens, ViewModels
- **Domain Layer** (`domain/`): UseCases, interfaces, models
- **Data Layer** (`data/`): Repositories, network clients, local storage

### Key Dependencies

| Component | Library | Version |
|-----------|---------|---------|
| **UI Framework** | Jetpack Compose (Wear) | 1.3.0 |
| **Media Playback** | ExoPlayer (media3) | 1.2.1 |
| **DI Container** | Hilt | 2.50 |
| **Networking** | Retrofit + OkHttp | 2.9.0 + 4.12.0 |
| **Network Protocols** | SMBJ, SSHJ, Apache Commons FTP | Latest |
| **Image Loading** | Coil | 2.5.0 |

## Debugging Tips

### Logs in Logcat

```kotlin
Timber.d("Debug message")
Timber.e(exception, "Error message")
```

### Hot Reload

- **Ctrl+M** (Windows) - Stop and restart app
- **Ctrl+Shift+R** (Windows) - Rebuild and redeploy

### Layout Inspector

- Tools → Layout Inspector → Select device
- Inspect Compose hierarchy in real-time

## Common Issues

### Issue: "Wear module not found"

**Solution**: Sync project with Gradle

- File → Sync Now (Ctrl+Shift+Y)

### Issue: APK too large

**Solution**: Use minification in Release build

- Already enabled in `wear/build.gradle.kts`

### Issue: Network connectivity on emulator

**Solution**: Check network configuration

- Emulator Settings → Advanced → DNS Servers
- Use `8.8.8.8` if needed

### Issue: "Execution failed for task ':wear:kspDebugKotlin'"

**Solution**: This project uses KSP (Kotlin Symbol Processing) instead of KAPT

- Make sure Gradle is synced properly
- Run `.\gradlew.bat --stop` then rebuild

## Performance Optimization

### For Wear OS 3.0 (Round Watches)

- Dimension overrides in `values-sw480dp/dimens.xml`
- Layout optimization for 480x480 screens
- Battery-conscious animations (disabled during playback)

### Memory Constraints

- ExoPlayer buffer size reduced (smaller playlists)
- Image caching limited (only album art needed)
- No persistent cache for network shares

## Testing

### Run Unit Tests

```powershell
.\gradlew.bat :wear:testDebugUnitTest
```

### Run Instrumented Tests

```powershell
.\gradlew.bat :wear:connectedAndroidTest
```

## Building Release APK

### Prerequisites

1. **Keystore file** (see `keystore.properties`)
2. **Keystore password** set in `~/.gradle/gradle.properties`

### Build Release APK

```powershell
.\gradlew.bat :wear:assembleRelease
```

APK will be available at: `wear/build/outputs/apk/release/wear-release.apk`

## Deployment to Play Store

**Note**: Wear OS app requires separate listing on Google Play Store

1. **Generate Release APK** (see above)
2. **Sign APK** (automatic if keystore configured)
3. **Upload to Google Play Console**
   - Create new app
   - Select category: "Wearables"
   - Upload APK
   - Fill metadata (screenshots, description)
   - Submit for review

## Quick Reference

| Command | Action |
|---------|--------|
| `.\scripts\builders\build-wear-debug.PS1` | Build debug APK |
| `.\scripts\builders\build-wear-release.PS1` | Build release APK |
| `.\gradlew.bat :wear:clean` | Clean build outputs |
| `.\gradlew.bat :wear:dependencies` | Show dependency tree |
| `.\gradlew.bat :wear:lint` | Run lint checks |

## Support & Documentation

- [Wear OS Docs](https://developer.android.com/wear)
- [Jetpack Compose for Wear OS](https://developer.android.com/wear/compose)
- [ExoPlayer Documentation](https://exoplayer.dev)

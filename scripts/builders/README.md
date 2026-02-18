# Build Scripts

Build automation scripts for FastMediaSorter v2.

## Debug Builds

Quick builds for development and testing (no version bump):

```powershell
.\scripts\builders\build-debug.PS1              # Standard flavor (all features)
.\scripts\builders\build-debug.PS1 -SkipZip     # Standard flavor without zip/GDrive archive
.\scripts\builders\build-debug-clean.PS1        # Clean + standard debug + zip
.\scripts\builders\build-debug-clean.PS1 -SkipZip # Clean + standard debug without zip
.\scripts\builders\build-standard-debug.ps1     # Standard flavor explicit
.\scripts\builders\build-lite-debug.ps1         # Lite flavor (no cloud/audio)
.\scripts\builders\build-photos-debug.ps1       # Photos only flavor
.\scripts\builders\build-legacy-debug.ps1       # Legacy flavor (no cloud)
```

## Cleanup

```powershell
.\scripts\builders\clean-gradle-caches.ps1      # Stop daemons + remove project caches + gradlew clean
```

## Release Builds

Optimized builds with ProGuard (requires keystore):

```powershell
.\scripts\builders\build-release.ps1            # Standard release
.\scripts\builders\build-standard-release.ps1   # Standard explicit
.\scripts\builders\build-lite-release.ps1       # Lite release
.\scripts\builders\build-photos-release.ps1     # Photos release
.\scripts\builders\build-legacy-release.ps1     # Legacy release
.\scripts\builders\build-aab-release.ps1        # AAB bundle for Play Store
```

## Wear OS

```powershell
.\scripts\builders\build-wear-debug.PS1         # Wear debug
.\scripts\builders\build-wear-release.PS1       # Wear release
```

## Device Deployment

Build + install to connected device:

```powershell
.\scripts\builders\build-debug-device.ps1
.\scripts\builders\build-standard-device.ps1
.\scripts\builders\build-lite-device.ps1
.\scripts\builders\build-photos-device.ps1
.\scripts\builders\build-legacy-device.ps1
```

## Universal

```powershell
.\scripts\builders\build-universal.ps1          # All flavors
.\scripts\builders\build-and-push-all.ps1       # Build all + push to GDrive
```

## Output Locations

- **Debug APKs**: `app_v2/build/outputs/apk/[flavor]/debug/`
- **Release APKs**: `app_v2/build/outputs/apk/[flavor]/release/`
- **AAB Bundle**: `app_v2/build/outputs/bundle/standardRelease/`
- **Wear APKs**: `wear/build/outputs/apk/debug/` or `release/`
- **Auto-copy**: `DOWNLOADS/` (release builds only)

## Note

All build scripts should be run from project root:

```powershell
cd c:\GIT\FastMediaSorter_mob_v2
.\scripts\builders\build-debug.PS1
```

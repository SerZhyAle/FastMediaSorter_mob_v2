# Quick Start Guide - Maestro E2E Testing

This guide will help you get started with Maestro testing for FastMediaSorter v2 in 5 minutes.

## Prerequisites

- Android device or emulator (API 28+)
- USB debugging enabled (for physical device)
- Maestro CLI installed

## Step 1: Install Maestro

### macOS/Linux (Homebrew)

```bash
brew tap mobile-dev-inc/tap
brew install maestro
```

### Windows (PowerShell)

```powershell
# Run as Administrator
Invoke-WebRequest -Uri "https://get.maestro.mobile.dev/install.ps1" -OutFile install.ps1
.\install.ps1
Remove-Item install.ps1
```

### Linux/macOS (curl)

```bash
curl -Ls "https://get.maestro.mobile.dev" | bash
```

### Verify Installation

```bash
maestro --version
```

**Note**: DO NOT use `npm install -g maestro-cli` - that's a different, unrelated package!

## Step 2: Connect Device

### Physical Device

```bash
# Enable USB debugging on your device
# Connect via USB
adb devices
# Should show your device
```

### Emulator

```bash
# Start any Android emulator
emulator -avd <avd_name>

# Or use Android Studio's AVD Manager
```

## Step 3: Build and Install App

### Option A: Use build script (Recommended)

```powershell
# PowerShell (Windows)
.\dev\build-with-version.ps1
```

### Option B: Manual build

```bash
# Build debug APK
./gradlew assembleStandardDebug

# Install on device
adb install -r app_v2/build/outputs/apk/standard/debug/FastMediaSorter_standard_debug_*.apk
```

## Step 4: Run Your First Test

### Run Single Test

```bash
# From project root
maestro test maestro/smoke/app_launch.yaml
```

### Run All Smoke Tests

```bash
maestro test maestro/smoke/
```

### Use Test Runner Script

```powershell
# PowerShell
.\maestro\run-tests.ps1 smoke

# Bash
./maestro/run-tests.sh smoke
```

## What to Expect

When you run `maestro test maestro/smoke/app_launch.yaml`, you'll see:

```
✓ Launch app
✓ Wait for animation to end
✓ Tap on "Allow" (optional)
✓ Assert visible: "Browse"
✓ Assert visible: "Favorites"
✓ Assert visible: "Settings"

✅ Test passed in 18.5s
```

## Test Results

- **Green checkmarks** ✓ - Steps passed
- **Red X** ✗ - Steps failed
- Test duration shown at the end

## Common First-Time Issues

### "Device not found"

```bash
# Check connection
adb devices

# Restart ADB
adb kill-server
adb start-server
```

### "App not installed"

```bash
# Verify app is installed
adb shell pm list packages | grep fastmediasorter

# If not, install it
adb install -r <path_to_apk>
```

### "Element not found"

- Make sure you installed the **debug** build (not release)
- Wait for app to fully load
- Check if you have the latest version

## Next Steps

Once your first test runs successfully:

1. **Run all smoke tests**: `maestro test maestro/smoke/`
2. **Run critical tests**: `maestro test maestro/critical/`
3. **Explore tests**: Check the YAML files in `maestro/smoke/` and `maestro/critical/`
4. **Create custom tests**: Copy an existing test and modify it
5. **Integrate with CI/CD**: Tests run automatically in GitHub Actions

## Interactive Mode

Want to explore your app interactively?

```bash
maestro studio
```

This opens a web-based interface where you can:

- Record interactions as test steps
- Test selectors before writing tests
- Debug failing tests visually

## Getting Help

- **Maestro Docs**: <https://maestro.mobile.dev>
- **Project Tests**: Check `maestro/README.md`
- **Examples**: Look at existing YAML files in `maestro/smoke/`

## Test Coverage

| Suite | Tests | Duration | Status |
|-------|-------|----------|--------|
| Smoke | 4 tests | ~2-3 min | ✅ Ready |
| Critical | 2 tests | ~1-2 min | ✅ Ready |

Happy Testing! 🚀

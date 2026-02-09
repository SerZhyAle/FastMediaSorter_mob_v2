# Maestro E2E Tests - FastMediaSorter v2

This directory contains end-to-end tests for FastMediaSorter v2 using Maestro testing framework.

## 📋 Overview

Maestro is a simple yet powerful mobile UI testing framework that allows you to write tests in YAML format without writing code.

## 🏗️ Directory Structure

```
maestro/
├── config.yaml              # Global Maestro configuration
├── smoke/                   # Quick smoke tests (5-10 min total)
│   ├── app_launch.yaml      # App startup & permissions
│   ├── local_browse.yaml    # Browse local files
│   ├── media_play.yaml      # Video/Audio playback
│   └── image_view.yaml      # Image viewing & editing
├── critical/                # Critical path tests
│   ├── file_operations.yaml # Copy/Move/Delete
│   └── settings.yaml        # Settings persistence
└── README.md                # This file
```

## 🚀 Quick Start

### Prerequisites

1. **Install Maestro CLI**

   ```bash
   # Using Homebrew (macOS/Linux)
   brew tap mobile-dev-inc/tap
   brew install maestro
   
   # Using PowerShell (Windows - Run as Administrator)
   Invoke-WebRequest -Uri "https://get.maestro.mobile.dev/install.ps1" -OutFile install.ps1
   .\install.ps1
   Remove-Item install.ps1
   
   # Using curl (Linux/macOS)
   curl -Ls "https://get.maestro.mobile.dev" | bash
   ```

   **Important**: DO NOT use `npm install -g maestro-cli` - that's a different package!

2. **Start Android Device**
   - Connect physical device via USB with USB debugging enabled
   - Or start Android emulator:

     ```bash
     emulator -avd <your_avd_name>
     ```

3. **Verify ADB Connection**

   ```bash
   adb devices
   # Should show your device/emulator
   ```

4. **Build and Install App**

   ```powershell
   # From project root
   .\dev\build-with-version.ps1
   
   # Or build manually
   .\gradlew.bat assembleStandardDebug
   
   # Install on device
   adb install app_v2/build/outputs/apk/standard/debug/FastMediaSorter_standard_debug_*.apk
   ```

### Running Tests

#### Run Single Test

```bash
# From project root
maestro test maestro/smoke/app_launch.yaml
```

#### Run All Smoke Tests

```bash
maestro test maestro/smoke/
```

#### Run All Critical Tests

```bash
maestro test maestro/critical/
```

#### Run All Tests

```bash
maestro test maestro/
```

#### Interactive Mode (Maestro Studio)

```bash
maestro studio
# Opens browser-based interactive test editor
```

#### Debug Mode

```bash
maestro test --debug maestro/smoke/app_launch.yaml
# Shows detailed execution logs
```

## 📊 Test Suites

### Smoke Tests (~5-10 minutes)

Essential functionality that must work in every build:

| Test | Description | Duration |
|------|-------------|----------|
| `app_launch.yaml` | App launches, handles permissions | ~20s |
| `local_browse.yaml` | Browse and navigate local files | ~30s |
| `media_play.yaml` | Play video/audio files | ~40s |
| `image_view.yaml` | View and edit images | ~40s |

### Critical Path Tests (~5-10 minutes)

Core operations that users rely on:

| Test | Description | Duration |
|------|-------------|----------|
| `file_operations.yaml` | Copy, move, delete files | ~50s |
| `settings.yaml` | Settings persistence across restarts | ~30s |

## 🔧 Configuration

### Environment Variables

You can set test configuration via environment variables:

```powershell
# PowerShell
$env:TEST_SMB_HOST = "192.168.1.100"
$env:TEST_SMB_USER = "testuser"
$env:TEST_SMB_PASSWORD = "testpass"
$env:TEST_SMB_SHARE = "media"

# Then run tests
maestro test maestro/smoke/
```

```bash
# Bash
export TEST_SMB_HOST="192.168.1.100"
export TEST_SMB_USER="testuser"
export TEST_SMB_PASSWORD="testpass"
export TEST_SMB_SHARE="media"

maestro test maestro/smoke/
```

### Timeouts

Default timeouts are defined in `config.yaml`:

- `SHORT_TIMEOUT`: 5s (quick operations)
- `DEFAULT_TIMEOUT`: 30s (normal operations)
- `LONG_TIMEOUT`: 60s (network operations)

You can override them per test if needed.

## 🐛 Troubleshooting

### "Device not found"

```bash
# Check ADB connection
adb devices

# Restart ADB server
adb kill-server
adb start-server
```

### "App not installed"

```bash
# Check if app is installed
adb shell pm list packages | grep fastmediasorter

# Install app
adb install <path_to_apk>
```

### "Element not found"

- Ensure the app is built with **debug** variant (not release)
- Check if app animations are complete
- Verify element IDs in app code match test expectations
- Add `waitForAnimationToEnd` before assertions

### "Test is flaky"

- Add delays between actions: `- waitForAnimationToEnd`
- Increase timeouts in config.yaml
- Use more specific element selectors
- Check for race conditions in app code

### "Permission dialog not appearing"

```bash
# Clear app data to reset permissions
adb shell pm clear com.sza.fastmediasorter.debug

# Uninstall and reinstall
adb uninstall com.sza.fastmediasorter.debug
adb install <path_to_apk>
```

## 📝 Writing New Tests

### Basic Template

```yaml
appId: com.sza.fastmediasorter.debug
---
# Test: <Test Name>
# Description: <What this test does>

- launchApp

- tapOn:
    text: "Button Text"
    
- assertVisible:
    text: "Expected Text"
    
- scroll

- swipe:
    direction: DOWN
```

### Common Commands

```yaml
# Launch app
- launchApp

# Tap element
- tapOn:
    text: "Settings"        # By text
    id: "button_id"         # By resource ID
    point: "50%, 50%"       # By coordinates
    
# Assert element state
- assertVisible:
    text: "Welcome"
- assertNotVisible:
    text: "Error"
    
# Input text
- tapOn:
    id: "edit_text"
- inputText: "Hello World"

# Wait
- waitForAnimationToEnd
- extendedWaitUntil:
    visible:
      text: "Loading"
    timeout: 10000

# Navigation
- scroll                     # Scroll down
- scrollUntilVisible:
    element:
      text: "Item"
- swipe:
    direction: UP
- backPress
```

### Element Selectors

```yaml
# By text (visible text)
tapOn:
  text: "Submit"

# By resource ID (from layout XML)
tapOn:
  id: "com.sza.fastmediasorter:id/button_submit"

# By coordinates (percentage)
tapOn:
  point: "50%, 80%"

# Combined selectors
tapOn:
  text: "Submit"
  below:
    text: "Username"
```

## 🔄 CI/CD Integration

### GitHub Actions Example

Create `.github/workflows/maestro-tests.yml`:

```yaml
name: Maestro E2E Tests

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  maestro-tests:
    runs-on: macos-latest
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v3
        
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          
      - name: Build app
        run: ./gradlew assembleStandardDebug
        
      - name: Install Maestro
        run: brew tap mobile-dev-inc/tap && brew install maestro
        
      - name: Start Android Emulator
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 30
          target: google_apis
          arch: x86_64
          script: echo "Emulator started"
          
      - name: Run Maestro smoke tests
        run: maestro test maestro/smoke/
        
      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: maestro-results
          path: ~/.maestro/tests/
```

## 📚 Resources

- [Maestro Official Documentation](https://maestro.mobile.dev)
- [Maestro GitHub Repository](https://github.com/mobile-dev-inc/maestro)
- [YAML Syntax Reference](https://maestro.mobile.dev/reference/yaml-syntax)
- [Best Practices](https://maestro.mobile.dev/best-practices)
- [Maestro Cloud](https://cloud.mobile.dev)

## 📞 Support

For issues related to:

- **Maestro framework**: Check [Maestro GitHub Issues](https://github.com/mobile-dev-inc/maestro/issues)
- **FastMediaSorter tests**: Create an issue in this repository

## 🎯 Next Steps

1. Install Maestro CLI
2. Run `maestro test maestro/smoke/app_launch.yaml` to verify setup
3. Run full smoke suite: `maestro test maestro/smoke/`
4. Integrate into CI/CD pipeline

Happy Testing! 🚀

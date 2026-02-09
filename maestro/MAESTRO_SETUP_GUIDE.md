# Maestro AI Setup & Implementation Guide

**Last Updated**: January 26, 2026  
**Status**: ✅ Ready to Use  
**Project**: FastMediaSorter v2 Android  

## Quick Setup (5 minutes)

### 1️⃣ Install Maestro CLI

```powershell
# Install Node.js first (if not installed)
winget install OpenJS.NodeJS

# Install Maestro CLI globally
npm install -g maestro-cli

# Verify installation
maestro --version
```

### 2️⃣ Connect Android Device/Emulator

```powershell
# For physical device: Enable USB Debugging
# Settings > Developer Options > USB Debugging

# Verify connection
$adbPath = "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk\platform-tools"
& "$adbPath\adb.exe" devices
```

**Expected output**:

```
List of devices attached
emulator-5554   device
```

### 3️⃣ Build & Install App

```powershell
cd C:\GIT\FastMediaSorter_mob_v2

# Build and install the standard flavor
.\dev\build-with-version.ps1 -Flavor standard

# Or use quick build
.\build-debug.PS1
```

### 4️⃣ Run Smoke Tests

```powershell
cd maestro

# Run all smoke tests (3-4 minutes)
maestro test smoke/

# Or use the convenient runner script
.\run-maestro-smoke-tests.ps1

# Run individual test
maestro test smoke/app_launch.yaml

# Run with debug output
maestro test --debug smoke/app_launch.yaml
```

---

## Installation Troubleshooting

### Issue: Node.js/npm not found

**Solution**: Add Node.js to PATH manually

```powershell
$NodePath = "C:\Program Files\nodejs"
$env:PATH = "$NodePath;$env:PATH"

# Verify
npm --version
```

### Issue: Maestro command not recognized

**Solution**: Use full path to maestro

```powershell
$adbPath = "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk\platform-tools"
& "$adbPath\adb.exe" devices

# For maestro (after npm install -g)
$env:PATH  # Check if C:\Users\<user>\AppData\Roaming\npm is in PATH
```

### Issue: Device not connected

**Solution**:

```powershell
# Verify ADB
adb devices

# For physical device: 
#   1. Enable Developer Options (tap Build Number 7 times)
#   2. Enable USB Debugging
#   3. Connect via USB
#   4. Authorize on device if prompted

# For emulator:
#   1. Start Android Studio
#   2. AVD Manager > Create/Run virtual device
#   3. Verify appears in 'adb devices'
```

---

## Maestro Test Framework

### Test Structure

All smoke tests follow this YAML structure:

```yaml
appId: com.sza.fastmediasorter
---
# Test metadata (optional)
# - name: Test Name
# - tags: [tag1, tag2]

# Launch the app
- launchApp

# Wait for animations to complete
- waitForAnimationToEnd

# Tap on elements
- tapOn:
    text: "Allow"
    optional: true

# Verify elements are visible
- assertVisible:
    id: "com.sza.fastmediasorter:id/browse_tab"

# Take screenshot (for debugging)
- takeScreenshot

# Input text
- inputText: "some text"

# Swipe gestures
- swipe:
    start: [0.5, 0.8]
    end: [0.5, 0.2]
    duration: 1000
```

### Available Smoke Tests

| Test File | Duration | Validates |
|-----------|----------|-----------|
| `app_launch.yaml` | ~20s | App startup, permissions, UI elements |
| `local_browse.yaml` | ~30s | File browsing, folder navigation |
| `media_play.yaml` | ~40s | Video/audio playback, player controls |
| `image_view.yaml` | ~40s | Image viewing, zoom, edit actions |
| `file_operations.yaml` | ~50s | Copy, Move, Delete operations |
| `settings.yaml` | ~30s | Settings changes and persistence |

**Total Runtime**: ~3-4 minutes for all tests

---

## Running Tests

### Option 1: Using Test Runner Script

```powershell
cd maestro

# Run all tests
.\run-maestro-smoke-tests.ps1

# Run specific test
.\run-maestro-smoke-tests.ps1 -Test app_launch

# Run with debug output
.\run-maestro-smoke-tests.ps1 -Test app_launch -Debug

# Use interactive mode (opens Maestro Studio)
.\run-maestro-smoke-tests.ps1 -Interactive

# Skip app rebuild
.\run-maestro-smoke-tests.ps1 -NoInstall
```

### Option 2: Using Maestro CLI Directly

```powershell
cd maestro

# Run all tests in smoke/ directory
maestro test smoke/

# Run specific test
maestro test smoke/app_launch.yaml

# Run with debug output
maestro test --debug smoke/app_launch.yaml

# Interactive mode (Maestro Studio)
maestro studio

# Run with custom timeout
maestro test smoke/app_launch.yaml --timeout 60000
```

### Option 3: Using Interactive Maestro Studio

```powershell
# Open interactive test builder
maestro studio

# Benefits:
# - Visual element inspector
# - Real-time recording
# - Debug individual steps
# - View app hierarchy
```

---

## Test Execution Flow

```
User runs test
       ↓
Maestro connects to device/emulator
       ↓
Installs app (if needed)
       ↓
Launches app
       ↓
Executes YAML steps sequentially
       ↓
Validates assertions
       ↓
Generates report
       ↓
Success ✅ or Failure ❌
```

---

## Debugging Failed Tests

### Enable Debug Output

```powershell
maestro test --debug smoke/app_launch.yaml
```

**Output shows**:

- Each step execution
- Element matching details
- Tap coordinates
- Assertion results

### Take Screenshots for Debugging

Add to test YAML:

```yaml
- takeScreenshot

# Screenshots saved to: ./screenshots/
```

### Interactive Debugging with Maestro Studio

```powershell
maestro studio

# Then:
# 1. Connect to device
# 2. Inspect elements
# 3. Record interactions
# 4. Build tests visually
```

### Common Issues & Solutions

| Issue | Solution |
|-------|----------|
| `Element not found` | Use `takeScreenshot` to verify current state; check element IDs/text |
| `App crash during test` | Check app logs with `adb logcat`; verify test assumptions |
| `Timeout on element` | Increase `commandTimeout` in `config.yaml` |
| `Permission dialog not handled` | Add `tapOn: {text: "Allow", optional: true}` |
| `Test fails intermittently` | Add `waitForAnimationToEnd` or increase waits |

---

## Configuration

### Maestro Global Config (`config.yaml`)

```yaml
appId: com.sza.fastmediasorter

# Command timeout (milliseconds)
commandTimeout: 30000

# Animation wait time
waitForAnimationToEnd: 1000

# Tap settings
tapSettings:
  retryIfNotVisible: true
  longPressWaitMillis: 500

# Assertion settings
assertion:
  strict: true
  timeout: 5000

# Device settings
device:
  screenSize: phone
  keepScreenAlive: true
  recordInteractions: true

# Server for debugging
server:
  enabled: false
  port: 7001
```

---

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Maestro E2E Tests

on: [push, pull_request]

jobs:
  maestro-tests:
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Install Node.js
        uses: actions/setup-node@v3
        with:
          node-version: '18'
      
      - name: Install Maestro
        run: npm install -g maestro-cli
      
      - name: Setup Android Emulator
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 31
          target: google_apis
      
      - name: Build App
        run: ./dev/build-with-version.ps1
      
      - name: Run Maestro Tests
        run: maestro test maestro/smoke/
      
      - name: Upload Test Reports
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: maestro-reports
          path: ./maestro-reports/
```

---

## Best Practices

✅ **DO**:

- Start with smoke tests for quick validation
- Use meaningful element IDs and text for assertions
- Add waits after interactions
- Keep tests independent
- Use optional taps for permission dialogs
- Document test objectives
- Use version control for test files

❌ **DON'T**:

- Create tests that depend on specific file states
- Use hard-coded coordinates (use text/IDs instead)
- Skip permission handling
- Make tests too long (split into multiple tests)
- Use timeouts less than 1000ms for animations
- Commit app changes within tests

---

## Advanced Features

### Recording Tests Automatically

```powershell
maestro record smoke/app_launch.yaml
```

Performs actions on device, Maestro records them as test steps.

### Running Tests in Parallel

```powershell
# Run multiple tests concurrently
maestro test smoke/app_launch.yaml & maestro test smoke/local_browse.yaml
```

### Custom Test Tags

```yaml
---
# name: App Launch Test
# tags: [smoke, critical]
```

Run by tag:

```powershell
maestro test smoke/ --tags smoke
```

### Environment Variables

```yaml
env:
  TEST_USER: "${TEST_USER:-guest}"
  TEST_PASSWORD: "${TEST_PASSWORD:-}"
```

Use in test:

```yaml
- inputText: "${TEST_USER}"
```

---

## Project Structure

```
maestro/
├── README.md                      # Quick reference
├── MAESTRO_SETUP_GUIDE.md        # This file
├── config.yaml                    # Global configuration
├── run-maestro-smoke-tests.ps1   # Test runner script
├── smoke/                         # Smoke test flows
│   ├── app_launch.yaml           # Test 1: Launch & permissions
│   ├── local_browse.yaml         # Test 2: File browsing
│   ├── media_play.yaml           # Test 3: Media playback
│   ├── image_view.yaml           # Test 4: Image viewing
│   ├── file_operations.yaml      # Test 5: Copy/Move/Delete
│   └── settings.yaml             # Test 6: Settings
├── critical/                      # (Future) Critical path tests
└── SETUP_COMPLETE.md             # Setup checklist
```

---

## Performance Metrics

### Expected Test Times

- App Launch: 20-30 seconds
- Local Browse: 25-40 seconds
- Media Playback: 35-50 seconds
- Image View: 35-50 seconds
- File Operations: 45-60 seconds
- Settings: 25-40 seconds
- **Total**: 3-4 minutes

### Device Requirements

- **API Level**: 28+ (Android 9+)
- **RAM**: 2GB+ recommended
- **Storage**: 500MB+ for app and test data
- **Internet**: Not required

---

## Support & Resources

- **Maestro Docs**: <https://maestro.mobile.dev>
- **UI Automation Guide**: <https://maestro.mobile.dev/api-reference/commands>
- **Android Studio Emulator**: <https://developer.android.com/studio/run/emulator>
- **ADB Documentation**: <https://developer.android.com/studio/command-line/adb>

---

## Checklist

- [ ] Node.js installed (`node --version`)
- [ ] Maestro CLI installed (`maestro --version`)
- [ ] Android device/emulator connected (`adb devices`)
- [ ] FastMediaSorter app built (`.\dev\build-with-version.ps1`)
- [ ] Smoke tests run successfully (`maestro test smoke/`)
- [ ] All 6 tests pass
- [ ] Ready for CI/CD integration

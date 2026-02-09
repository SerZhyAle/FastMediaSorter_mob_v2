# Maestro AI E2E Smoke Tests - FastMediaSorter v2

**Created**: January 25, 2026  
**Status**: ✅ IMPLEMENTED  
**Last Updated**: February 7, 2026  

## Overview

Maestro is a mobile testing framework designed for quick, reliable end-to-end testing of Android and iOS apps. This document outlines the implementation of E2E smoke tests to verify core functionality of FastMediaSorter v2.

### Why Maestro?

- **Fast execution** (~5-10 min for all smoke tests)
- **No code required** - Tests written in YAML
- **Reliable UI automation** - Cloud-based visual matching
- **Easy to maintain** - Descriptive, readable test flows
- **CI/CD friendly** - Can run locally or on cloud

## Architecture

```
maestro/
├── config.yaml              # Global Maestro configuration
├── smoke/                   # Quick smoke tests (5-10 min total)
│   ├── app_launch.yaml      # App startup & permissions
│   ├── local_browse.yaml    # Browse local files
│   ├── smb_connect.yaml     # SMB connection flow
│   ├── media_play.yaml      # Video/Audio playback
│   └── image_view.yaml      # Image viewing & editing
├── critical/                # Critical path tests
│   ├── file_operations.yaml # Copy/Move/Delete
│   └── settings.yaml        # Settings persistence
└── README.md                # Maestro testing guide
```

## Test Scope

### Smoke Tests (5-10 minutes)

✅ **Priority 1**: Core user flows that must work

1. **App Launch** - App starts without crashes
2. **Local Browse** - Navigate local files
3. **SMB Connect** - Connect to SMB share (if available)
4. **Media Playback** - Play video/audio
5. **Image Viewing** - View and edit images

### Critical Path Tests (5-10 minutes)

✅ **Priority 2**: Essential operations

1. **File Copy** - Copy files between locations
2. **File Move** - Move files
3. **Settings** - Change and persist settings

### Full Test Suite (20-30 minutes)

⏳ **Future**: Extended coverage

1. Search functionality
2. All storage protocols
3. Cloud integrations
4. Undo/redo operations

## Setup Requirements

### Prerequisites

1. **Maestro CLI** installed
2. **Android device/emulator** running (API 28+)
3. **ADB** accessible
4. **Test credentials** (for SMB/SFTP/FTP tests)

### Installation Steps

```powershell
# 1. Install Maestro (Windows - PowerShell as Administrator)
Invoke-WebRequest -Uri "https://get.maestro.mobile.dev/install.ps1" -OutFile install.ps1
.\install.ps1
Remove-Item install.ps1

# For macOS/Linux (Homebrew)
# brew tap mobile-dev-inc/tap
# brew install maestro

# Note: DO NOT use 'npm install -g maestro-cli' - that's a different package!

# 2. Start Android emulator or connect physical device
adb devices

# 3. Build and install app on device
.\dev\build-with-version.ps1

# 4. Verify maestro can detect device
maestro --version
adb shell "settings get global stay_on_while_plugged_in"  # Keep screen on during tests

# 5. Run smoke tests
cd maestro
maestro test smoke/app_launch.yaml
```

### Configuration Environment Variables

```powershell
# Set test credentials (optional, for SMB/SFTP tests)
$env:TEST_SMB_HOST = "192.168.1.100"
$env:TEST_SMB_USER = "testuser"
$env:TEST_SMB_PASSWORD = "testpass"
$env:TEST_SMB_SHARE = "media"
```

## Implementation Plan

### Phase 1: Setup ✅ COMPLETED

- [x] Create `maestro/` directory structure
- [x] Create `maestro/config.yaml`
- [x] Create README with setup instructions
- [x] Create basic smoke test flows

### Phase 2: Smoke Tests ✅ COMPLETED

- [x] `app_launch.yaml` - Launch and permissions
- [x] `local_browse.yaml` - Browse local files
- [x] `media_play.yaml` - Play video/audio
- [x] `image_view.yaml` - View and edit images

### Phase 3: Critical Path Tests ✅ COMPLETED

- [x] `file_operations.yaml` - Copy/Move/Delete
- [x] `settings.yaml` - Settings persistence

### Phase 4: Integration ✅ COMPLETED

- [x] CI/CD pipeline integration
- [x] GitHub Actions workflow
- [x] Test runner scripts (PowerShell & Bash)
- [ ] Device farm support (optional)

### Phase 5: Enhancement

- [ ] Test report generation
- [ ] Performance benchmarking
- [ ] Video recording on failures
- [ ] Parallel test execution

## Test Flows Specification

### 1. App Launch (`app_launch.yaml`)

**Objective**: Verify app starts without crashing and handles permissions

**Steps**:

1. Launch app
2. Handle permission dialogs (if present)
3. Wait for main UI to load
4. Verify Settings icon visible
5. Verify Browse tab visible

**Expected**: App starts in Browse tab, no crashes

**Duration**: ~20 seconds

### 2. Local Browse (`local_browse.yaml`)

**Objective**: Verify file browsing works

**Steps**:

1. Start from Browse tab
2. Verify file list is visible
3. Scroll through list
4. Tap a folder to open
5. Verify contents load
6. Navigate back

**Expected**: Folders open, contents display, navigation works

**Duration**: ~30 seconds

### 3. Media Playback (`media_play.yaml`)

**Objective**: Verify video/audio playback

**Steps**:

1. Browse to media file
2. Tap video/audio file
3. Wait for player to open
4. Verify playback starts
5. Tap play/pause
6. Verify controls respond

**Expected**: Player opens, playback works, controls responsive

**Duration**: ~40 seconds

### 4. Image Viewing (`image_view.yaml`)

**Objective**: Verify image viewing and basic editing

**Steps**:

1. Browse to image file
2. Tap image
3. Wait for image viewer
4. Verify image displays
5. Pinch to zoom
6. Open edit menu
7. Select rotate
8. Verify rotation applied

**Expected**: Image displays, zoom works, edit menu responds

**Duration**: ~40 seconds

### 5. File Operations (`file_operations.yaml`)

**Objective**: Verify copy/move/delete

**Steps**:

1. Browse to file
2. Long-tap file (open context menu)
3. Tap "Copy"
4. Navigate to destination
5. Tap paste icon
6. Verify file copied

**Expected**: File operations complete, file appears in destination

**Duration**: ~50 seconds

### 6. Settings (`settings.yaml`)

**Objective**: Verify settings persist

**Steps**:

1. Navigate to Settings tab
2. Change a setting (e.g., dark mode toggle)
3. Restart app
4. Verify setting persisted

**Expected**: Settings persist across app restarts

**Duration**: ~30 seconds

## YAML Syntax Reference

### Basic Structure

```yaml
appId: com.sza.fastmediasorter
---
- launchApp

- tapOn:
    text: "Settings"

- assertVisible:
    text: "Preferences"

- scroll

- swipe:
    direction: UP
    duration: 1000
```

### Key Commands

- `launchApp` - Start the app
- `tapOn:` - Tap element (by text, id, point)
- `assertVisible:` - Assert element visible
- `assertNotVisible:` - Assert element hidden
- `scroll` - Scroll in direction
- `swipe` - Swipe in direction
- `input:` - Type text
- `waitForAnimationToEnd` - Wait for UI animation
- `eraseText` - Clear text field
- `backPress` - Press back button
- `runFlow:` - Include another flow

### Element Selection

```yaml
# By text
tapOn:
  text: "Button Text"

# By resource ID
tapOn:
  id: "com.sza.fastmediasorter:id/button_id"

# By coordinates (0-1 scale)
tapOn:
  point:
    x: 0.5
    y: 0.5

# By index
tapOn:
  index: 0
```

## Running Tests

### Local Execution

```powershell
# Run single flow
maestro test maestro/smoke/app_launch.yaml

# Run all smoke tests
maestro test maestro/smoke/

# Run with debugging
maestro test --debug maestro/smoke/app_launch.yaml

# View device screen during test
maestro studio  # Interactive mode
```

### CI/CD Pipeline

```powershell
# Github Actions example
maestro test \
  --format junit \
  --output-dir test-results \
  maestro/smoke/

# Report results
If ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Maestro tests failed"
    exit 1
}
Write-Host "✅ All smoke tests passed"
```

## Cloud Integration (Optional)

### Maestro Cloud

```powershell
# Upload and run on cloud
maestro cloud --apiKey <KEY> maestro/smoke/

# Monitor results
maestro cloud list
```

## Common Issues & Solutions

### Issue: "Element not found"

**Solution**: Add wait, use different selector, check app state

### Issue: "Tap didn't work"

**Solution**: Verify element visible first with `assertVisible`, add delay

### Issue: "Test flaky"

**Solution**: Add `waitForAnimationToEnd`, increase timeouts, verify element state

### Issue: "Permission dialog doesn't appear"

**Solution**: Uninstall app, clear app data, reinstall with `adb shell pm clear`

## Maintenance

### Test Review Schedule

- **Weekly**: Run smoke tests to verify regressions
- **Daily (CI)**: Run smoke tests on each commit
- **Monthly**: Review and update test flows
- **Per Release**: Add new smoke tests for new features

### Test Metrics

Track in CI/CD:

- Total test execution time
- Pass/fail rate
- Flaky tests (failed on 1st run, passed on 2nd)
- Element not found errors
- Performance regressions

## Example: Running Smoke Tests in GitHub Actions

```yaml
name: Maestro E2E Smoke Tests

on: [push, pull_request]

jobs:
  maestro:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup Android
        uses: android-actions/setup-android@v2
        
      - name: Build app
        run: |
          .\dev\build-with-version.ps1
          
      - name: Install Maestro
        run: |
          curl -Ls "https://get.maestro.mobile.dev" | bash
          echo "$HOME/.maestro/bin" >> $GITHUB_PATH
        
      - name: Run smoke tests
        run: maestro test maestro/smoke/
        
      - name: Upload results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: maestro-results
          path: maestro-results/
```

## Next Steps

1. **Phase 1**: Create directory structure and basic config
2. **Phase 2**: Implement smoke test flows (app_launch, local_browse, media_play)
3. **Phase 3**: Test on actual device/emulator
4. **Phase 4**: Integrate into CI/CD
5. **Phase 5**: Document common test patterns and troubleshooting

## References

- [Maestro Documentation](https://maestro.mobile.dev)
- [Maestro GitHub](https://github.com/mobile-dev-inc/maestro)
- [Maestro YAML Syntax](https://maestro.mobile.dev/advanced/yaml-syntax)
- [Android Testing Best Practices](https://developer.android.com/training/testing)

---

**Status**: Ready for Phase 1 implementation
**Next**: Create maestro/ directory structure and config.yaml

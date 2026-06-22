# Maestro Testing - Troubleshooting Guide

This guide helps you resolve common issues when running Maestro tests for FastMediaSorter v2.

## Common Issues

### 1. "maestro: command not found"

**Problem**: Maestro CLI is not installed or not in PATH.

**Solution**:

```bash
# Install Maestro (choose one method)

# Method 1: Homebrew (macOS/Linux)
brew tap mobile-dev-inc/tap
brew install maestro

# Method 2: curl (Linux/macOS)
curl -Ls "https://get.maestro.mobile.dev" | bash

# Method 3: PowerShell (Windows - Run as Administrator)
Invoke-WebRequest -Uri "https://get.maestro.mobile.dev/install.ps1" -OutFile install.ps1
.\install.ps1
Remove-Item install.ps1

# Verify installation
maestro --version
```

**Important**: DO NOT use `npm install -g maestro-cli` - that's a different, unrelated package!

If still not found after installation, restart your terminal and try again. On Windows, also
verify that `%USERPROFILE%\.maestro\bin` is on PATH. The suite runner checks PATH,
`MAESTRO_HOME`, and the standard per-user `.maestro\bin` install location.

### 2. "No devices found"

**Problem**: ADB cannot detect your device/emulator.

**Solution**:

```bash
# Check connected devices
adb devices

# If empty, try:

# For physical device:
# 1. Enable USB debugging in Developer Options
# 2. Reconnect USB cable
# 3. Accept USB debugging prompt on device

# For emulator:
# 1. Start emulator from Android Studio
# 2. Or use command: emulator -avd <avd_name>

# Restart ADB server
adb kill-server
adb start-server
adb devices
```

### 3. "App not installed: com.sza.fastmediasorter.debug"

**Problem**: The debug build of the app is not installed on the device.

**Solution**:

```bash
# Build and install app
./gradlew assembleStandardDebug

# Find APK
find app_v2/build/outputs/apk/standard/debug -name "*.apk"

# Install APK
adb install -r <path_to_apk>

# Verify installation
adb shell pm list packages | grep fastmediasorter
```

Or use the test runner script:

```powershell
.\maestro\run-tests.ps1 smoke
# Script automatically builds and installs app
```

### 4. "Element not found" errors

**Problem**: Maestro cannot find UI elements specified in tests.

**Possible Causes & Solutions**:

#### A. Wrong app variant (Release instead of Debug)

```bash
# Uninstall any existing app
adb uninstall com.sza.fastmediasorter
adb uninstall com.sza.fastmediasorter.debug

# Build and install debug
./gradlew assembleStandardDebug
adb install -r <path_to_debug_apk>
```

#### B. UI hasn't loaded yet

Add wait commands in your test:

```yaml
- launchApp
- waitForAnimationToEnd  # Wait for UI to settle
- extendedWaitUntil:
    visible:
      text: "Browse"
    timeout: 10000
```

#### C. Element selector is incorrect

Use Maestro Studio to find correct selectors:

```bash
maestro studio
# Opens browser interface to inspect app UI
```

#### D. Device has different screen size

Test on multiple screen sizes or use relative coordinates:

```yaml
# Instead of absolute position
- tapOn:
    point: "50%, 50%"  # Center of screen
```

### 5. Test is flaky (passes sometimes, fails other times)

**Solutions**:

#### A. Add more waits

```yaml
# Before each interaction
- waitForAnimationToEnd

# Before assertions
- extendedWaitUntil:
    visible:
      text: "Expected Text"
    timeout: 5000
```

#### B. Increase timeouts

```yaml
- tapOn:
    text: "Button"
  timeout: 10000  # 10 seconds
```

#### C. Make selectors more specific

```yaml
# Instead of
- tapOn:
    text: "OK"

# Use
- tapOn:
    text: "OK"
    below:
      text: "Confirm Action"
```

### 6. "Permission denied" when running tests

**Problem**: App needs runtime permissions that aren't granted.

**Solution**:

#### A. Grant permissions manually first time

```bash
# Grant storage permission
adb shell pm grant com.sza.fastmediasorter.debug android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant com.sza.fastmediasorter.debug android.permission.WRITE_EXTERNAL_STORAGE

# Grant notification permission (Android 13+)
adb shell pm grant com.sza.fastmediasorter.debug android.permission.POST_NOTIFICATIONS
```

#### B. Handle permission dialogs in test

```yaml
- launchApp

# Handle all possible permission dialogs
- tapOn:
    text: "Allow"
  optional: true

- tapOn:
    text: "Allow"
  optional: true

- tapOn:
    text: "While using the app"
  optional: true
```

#### C. Clear app data to reset permissions

```bash
adb shell pm clear com.sza.fastmediasorter.debug
```

### 7. Tests run too slowly

**Solutions**:

#### A. Disable animations on device

```bash
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
```

#### B. Use faster emulator

- Use x86_64 architecture (not ARM)
- Allocate more RAM (4GB+)
- Enable hardware acceleration

```bash
emulator -avd Pixel_3a_API_30 \
  -memory 4096 \
  -cores 2 \
  -gpu swiftshader_indirect \
  -no-boot-anim
```

#### C. Run tests in parallel (if multiple devices)

```bash
# Terminal 1
ANDROID_SERIAL=emulator-5554 maestro test maestro/smoke/

# Terminal 2
ANDROID_SERIAL=emulator-5556 maestro test maestro/critical/
```

### 8. "ADB server version mismatch"

**Problem**: Multiple ADB versions installed.

**Solution**:

```bash
# Kill all ADB servers
adb kill-server

# Find and remove old ADB installations
# macOS/Linux
which -a adb

# Use only the Android SDK version
export PATH="/Users/<user>/Library/Android/sdk/platform-tools:$PATH"

# Start ADB
adb start-server
```

### 9. Tests fail in CI/CD but pass locally

**Possible Causes & Solutions**:

#### A. Different Android API level

- Ensure CI uses same API level as local testing
- Test on multiple API levels locally

#### B. Emulator not fully started

Add wait in CI workflow:

```yaml
- name: Wait for emulator
  run: |
    adb wait-for-device
    adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 1; done'
```

#### C. Screen is locked

```bash
# In CI, unlock screen before tests
adb shell input keyevent 82  # Unlock
adb shell input keyevent 3   # Home
```

### 10. "Cannot connect to Maestro daemon"

**Problem**: Maestro daemon not running or crashed.

**Solution**:

```bash
# Kill any existing Maestro processes
pkill -f maestro

# Restart Maestro
maestro test maestro/smoke/app_launch.yaml
```

### 11. Network tests fail (SMB/FTP/SFTP)

**Solutions**:

#### A. Ensure test server is accessible

```bash
# Test connectivity from device
adb shell ping -c 3 192.168.1.100

# Check if port is open
adb shell "nc -zv 192.168.1.100 445"  # SMB
```

#### B. Set environment variables

```powershell
$env:TEST_SMB_HOST = "192.168.1.100"
$env:TEST_SMB_USER = "testuser"
$env:TEST_SMB_PASSWORD = "testpass"

maestro test maestro/smoke/
```

#### C. Skip network tests for local testing

Comment out or remove network-dependent tests when not needed.

### 12. "APK is corrupt" or "Installation failed"

**Problem**: APK file is corrupted or incompatible.

**Solution**:

```bash
# Clean build
./gradlew clean
./gradlew assembleStandardDebug

# Uninstall old app
adb uninstall com.sza.fastmediasorter.debug

# Install fresh
adb install <new_apk>

# Check APK integrity
unzip -t <apk_file>
```

## Debugging Tips

### 1. Run with debug output

```bash
maestro test --debug maestro/smoke/app_launch.yaml
```

### 2. Record test execution

```bash
maestro test --record maestro/smoke/
# Video saved to ~/.maestro/tests/
```

### 3. Use Maestro Studio

```bash
maestro studio
# Interactive browser-based testing
```

### 4. Check Maestro logs

```bash
# View recent test logs
cat ~/.maestro/tests/<test_name>/maestro.log
```

### 5. Inspect app UI

```bash
# Dump UI hierarchy
adb shell uiautomator dump
adb pull /sdcard/window_dump.xml
```

### 6. Check app logs

```bash
# View app logs during test
adb logcat | grep FastMediaSorter
```

### 7. Take screenshot

```bash
# During test execution
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png
```

## Getting More Help

### Maestro Resources

- Documentation: <https://maestro.mobile.dev>
- GitHub Issues: <https://github.com/mobile-dev-inc/maestro/issues>
- Community Slack: <https://mobile-dev-inc.slack.com>

### FastMediaSorter Resources

- Project README: `/maestro/README.md`
- Quick Start: `/maestro/QUICK_START.md`
- Test Examples: `/maestro/EXAMPLES.md`

## Reporting Issues

When reporting test failures, include:

1. **Maestro version**: `maestro --version`
2. **Android version**: `adb shell getprop ro.build.version.release`
3. **Device/Emulator**: Physical device model or AVD name
4. **Test command**: Full command used to run test
5. **Error output**: Complete error message
6. **Logs**: Attach `~/.maestro/tests/<test_name>/maestro.log`
7. **Screenshots**: If UI-related issue

Example:

```
Maestro version: 1.30.0
Android version: 13 (API 33)
Device: Pixel 6 (physical)
Command: maestro test maestro/smoke/app_launch.yaml
Error: Element "Browse" not found after 30s
Logs: [attach maestro.log]
Screenshots: [attach screenshot.png]
```

---

**Remember**: Most test failures are due to timing issues. Adding `waitForAnimationToEnd` and increasing timeouts solves 80% of problems!

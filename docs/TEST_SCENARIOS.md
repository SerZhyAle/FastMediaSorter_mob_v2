# FastMediaSorter v2 - Test Scenarios

**Date**: November 19, 2025  
**Version**: 2.25.1119.xxxx  
**Purpose**: Verify core functionality with LOCAL, SMB, and FTP resources

---

## Prerequisites

### Setup Required Resources:
1. **LOCAL**: Folder with 20-50 media files (images/videos mix)
2. **SMB**: Network share accessible from Android device (e.g., `\\192.168.1.112\down`)
3. **FTP**: FTP server with media files (optional, can skip if unavailable)

### Enable Logging:
- Settings → General → "Show current session log" (keep available for copying logs)

---

## Test Scenario 1: LOCAL Resource - Basic Operations

### Objective: Verify file operations work correctly on local storage without unnecessary reloads

### Steps:

**1.1 Add Local Resource**
- Main screen → Add Resource → "SCAN" for local folders
- Select "Download" or "Pictures" folder
- Mark as destination (checkbox "In destinations")
- Click "Add to resources"
- **Expected**: Resource added successfully, file count displayed

**1.2 Browse Files (Grid View, No Thumbnails)**
- Long-press or double-tap resource to open Browse screen
- Toggle view: Grid mode (button top-right)
- Verify cell width is 3-4x wider without thumbnails
- **Expected**: Wide cells, file names fully visible (3 lines), no text truncation

**1.3 Copy Operation (No Reload Test)**
- Select 3-5 files (long press first, then tap others)
- Click Copy button (top toolbar)
- Select any destination
- **Expected**: 
  - Toast "Copied N files"
  - NO folder reload (files remain in list)
  - Selection cleared automatically
  - **CRITICAL**: Check log - should NOT see "reloadFiles" or "loadResource" after copy

**1.4 Move Operation (Targeted Removal Test)**
- Select 2-3 different files
- Click Move button
- Select destination
- **Expected**:
  - Toast "Moved N files"
  - Moved files disappear from list INSTANTLY
  - NO full folder reload
  - **CRITICAL**: Check log - should see "removeFiles: Removed N files" NOT "reloadFiles"

**1.5 Delete Operation (Targeted Removal Test)**
- Select 1 file
- Click Delete button → Confirm
- **Expected**:
  - Toast "Deleted 1 file(s)"
  - File disappears INSTANTLY
  - **CRITICAL**: Check log - should see "removeFiles" NOT "reloadFiles"

**1.6 Rename Operation**
- Select 1 file → Rename button
- Change name to "test_renamed.jpg"
- Click Apply
- **Expected**:
  - File renamed successfully
  - List reloaded (acceptable for rename - needs MediaFile object update)

### Log Collection Point 1:
```
Settings → "Show current session log" → Copy to clipboard
Paste log in response with label: "LOG SCENARIO 1 - LOCAL"
```

---

## Test Scenario 2: SMB Resource - Network Operations

### Objective: Verify SMB client doesn't block after operations, no connection starvation

### Steps:

**2.1 Add SMB Resource**
- Main screen → Add Resource → Network folder
- Enter IP: `192.168.1.xxx` (your SMB server)
- Enter credentials (user/password)
- Click "Test" button first
- **Expected**: "Connection successful" message
- Click "SCAN" to find shares
- Select share with media files
- Add to resources

**2.2 Browse SMB Files**
- Open SMB resource (long-press)
- Wait for file list to load
- **Expected**: 
  - Files load without errors
  - Thumbnails appear (if enabled)
  - No "connection blocked" errors

**2.3 Copy from SMB (Source Remains Unchanged)**
- Select 2-3 files from SMB share
- Copy to LOCAL destination
- **Expected**:
  - Copy progress shows
  - NO reload of SMB folder
  - SMB file list unchanged
  - **CRITICAL**: Check log - no "reloadFiles" after copy

**2.4 Move on SMB (If Writable)**
- IF SMB share is writable:
  - Select 1 file
  - Move to another destination
  - **Expected**: 
    - File removed from list instantly
    - Check log: "removeFiles" used, NOT full reload

**2.5 Player Screen - SMB File**
- Single-tap any file to open Player
- Verify image/video loads
- Test Previous/Next navigation (swipe or buttons)
- **Expected**:
  - Files load without blocking
  - Navigation smooth
  - No "connection pool exhausted" errors

**2.6 Command Panel Mode Test**
- In Player, tap "Show command panel" zone (bottom-left)
- Verify top panel: Back | Previous, Next | Rename, Delete, Undo | Slideshow
- Verify spacing between button groups (visual separation)
- Verify Copy to/Move to panels at bottom (collapsed headers)
- **Expected**: Layout matches specification, buttons grouped with spacing

### Log Collection Point 2:
```
Settings → "Show current session log" → Copy to clipboard
Paste log in response with label: "LOG SCENARIO 2 - SMB"
```

---

## Test Scenario 3: FTP Resource (Optional)

### Objective: Verify FTP operations if FTP server available

### Steps:

**3.1 Add FTP Resource**
- Main screen → Add Resource → SFTP/FTP
- Enter FTP server details
- Test connection
- Add resource if successful

**3.2 Basic FTP Operations**
- Browse files
- Copy 1 file to local
- **Expected**: Copy works, no reload of FTP folder

**3.3 FTP Connection Stability**
- Open 5-10 files in Player (rapid navigation)
- **Expected**: No "timeout" or "connection failed" errors

### Log Collection Point 3:
```
Settings → "Show current session log" → Copy to clipboard
Paste log in response with label: "LOG SCENARIO 3 - FTP"
```

---

## Test Scenario 4: Mixed Operations (Integration Test)

### Objective: Verify system stability with multiple resource types

### Steps:

**4.1 Multiple Resources Active**
- Have LOCAL, SMB (and FTP if available) all added
- Switch between Browse screens of different resources
- **Expected**: No crashes, no "resource not found" errors

**4.2 Cross-Resource Copy**
- Open LOCAL resource
- Copy 2 files to SMB destination
- Open SMB resource
- Verify files appeared (may need manual Refresh button)
- **Expected**: Files copied successfully

**4.3 Grid View Toggle Test**
- In Browse screen (any resource type)
- Toggle: List → Grid → List → Grid
- Verify cell width adjusts correctly in grid mode
- **Expected**: 
  - With thumbnails: narrow cells, 3-6 columns
  - Without thumbnails: wide cells, 1-2 columns

**4.4 Filter Test**
- Browse screen → Filter button
- Set filter: Name contains "IMG", Size 100KB-10MB
- Apply
- **Expected**: 
  - Filter warning appears at bottom
  - Only matching files shown
  - Clear filter works

### Log Collection Point 4:
```
Settings → "Show current session log" → Copy to clipboard
Paste log in response with label: "LOG SCENARIO 4 - MIXED"
```

---

## Critical Issues to Watch For

### ❌ MUST NOT HAPPEN:
1. **Full reload after Copy** - check log for "reloadFiles" or "loadResource"
2. **SMB connection blocking** - "connection pool exhausted", "socket timeout"
3. **Grid cells too narrow** - text truncated with ".." in text-only mode
4. **Crashes on file operations** - any unhandled exceptions

### ✅ EXPECTED BEHAVIOR:
1. **Copy**: NO reload, source files remain in list
2. **Move/Delete**: Files removed instantly via `removeFiles()` method
3. **Grid text-only mode**: Wide cells (1-2 columns), full file names visible
4. **SMB stability**: Multiple operations without connection errors
5. **Command Panel**: Buttons grouped with visual spacing

---

## Not a Defect: Screen Capture on Secure Screens

A screenshot of a secure screen comes back black, or as a zero-byte file, on every device. This is `FLAG_SECURE` working, not a rendering bug. `BaseActivity.applySecureFlagIfEnabled` sets it whenever `isSensitiveScreen()` is true and the `secureSensitiveScreens` setting is on, which it is by default (S1045). Settings, Add Resource and Resource Editor all declare themselves sensitive.

Recognise it before spending a session on it (S1284 cost one):

- The capture is black but `uiautomator dump` returns correct text and bounds, and taps at those coordinates work.
- System dialogs and the status bar appear normally in the same capture - they are separate, unprotected windows.
- logcat has no exceptions and HWUI keeps reporting frames.

Confirm in one command, and read the window's own flags rather than guessing:

```powershell
adb -s <device> shell "dumpsys window windows | grep -A 6 '<ActivityName>'"
```

`SECURE` in the `fl=` line settles it. To capture such a screen for a report, turn the `secureSensitiveScreens` setting off first, then back on.

You no longer have to remember any of this while looking at a black PNG: `scripts/devtest/adb.ps1 shot` checks the focused window itself and prints the explanation next to the file it just wrote (`-Json` carries it as `secureWindow`). The check was added by S1506, after this section had already been written and two device-test sessions on the same day still read a secure capture as a broken screen and filed a P90 ticket for it.

---

---

## Test Scenario 5: User-Auditor Agent Multi-Device Profile Scenarios

### Objective: Formal User-Auditor evaluation protocol across 5 distinct hardware device profiles

---

### Profile 5.1: Smartphone (Portrait & Landscape Touch UI)
- **Target Device**: Standard Android Smartphone (Pixel 7 / Galaxy S23, 6.1" - 6.7", 1080x2400)
- **Primary Input**: Single-hand / two-hand multi-touch, edge swipes
- **Audit Steps**:
  1. Verify gesture-based fast media sorting (swipe left/right to move/copy).
  2. Test portrait vs landscape rotation (`res/layout-land/` parity, safe insets, cutouts).
  3. Validate touch target dimensions (>= 48dp) and action button spacing.
  4. Verify Toast / Snackbar feedback visibility without obscuring navigation bars.

---

### Profile 5.2: Tablet (Dual-Pane Multi-Grid UI)
- **Target Device**: Android Tablet (Pixel Tablet / Galaxy Tab, 10.5" - 12.9", sw600dp / sw720dp)
- **Primary Input**: Touch + Drag-and-Drop + Physical Keyboard
- **Audit Steps**:
  1. Open Browse screen in grid view; verify multi-column layout uses extra screen width effectively without excessive whitespace.
  2. Verify drag-and-drop file operations between source panel and destination panel.
  3. Verify split-screen and multi-window behavior (no layout clipping or resource leaks).
  4. Test physical keyboard shortcuts (Space bar for play/pause, Arrow keys for navigation, Delete key for deletion).

---

### Profile 5.3: Automotive / Car Head Unit (High-Contrast Large Touch & D-Pad)
- **Target Device**: Car Head Unit / Android Auto Standalone Head Unit (7" - 10", 1024x600 / 1280x720)
- **Primary Input**: Large Touch Targets + Steering Wheel / Rotary Knob / D-Pad
- **Audit Steps**:
  1. Verify high-contrast visibility under high ambient light conditions.
  2. Test D-Pad / Rotary Knob navigation focus states (`focusable="true"`, `nextFocusDown`, `nextFocusRight` highlight visibility).
  3. Verify large touch target sizes (>= 56dp for automotive touch controls).
  4. Validate launcher mode / quick media sorting mode stability without distraction.

---

### Profile 5.4: Photo Frame / Leanback TV (D-Pad Remote & Slideshow)
- **Target Device**: Android TV / Digital Photo Frame / Smart Display (15" - 55", 1920x1080 / 4K)
- **Primary Input**: D-Pad Remote Controller / Leanback Input
- **Audit Steps**:
  1. Test automatic slideshow playback on start / idle timeout (`launcherScreenBlackoutTimeoutSeconds`).
  2. Verify Leanback / D-Pad remote navigation focus across grid items and player controls.
  3. Validate image rendering quality and scaling modes (FIT_CENTER, CENTER_CROP, no distortion).
  4. Verify absence of screen burn-in elements or static un-dimmed overlays during prolonged playback.

---

### Profile 5.5: Smartwatch / Wear OS (Compact Screen & Rotary Input)
- **Target Device**: Wear OS Smartwatch (1.2" - 1.5" Round / Square, 396x396 / 454x454)
- **Primary Input**: Touch + Rotary Bezel / Crown + Voice / Quick Action Taps
- **Audit Steps**:
  1. Verify `wear/` app startup, standalone browsing, and Bluetooth / Wi-Fi sync with phone `app_v2`.
  2. Test rotary crown / bezel scrolling through file lists and remote player controls.
  3. Verify circular screen cutout clipping (curved text / buttons stay within safe bounds).
  4. Validate quick action taps (Play/Pause, Next/Prev, Quick Sort, Favorite) for single-tap responsiveness on small touch targets.

---

## Log Analysis Keywords

When reviewing logs, look for:

**GOOD** (should see):
- `removeFiles: Removed N files` (after Move/Delete)
- `Grid calculation - showThumbnails=false, itemWidth=XXX, spanCount=1-2` (text mode)
- `SMB Connection Recovery` (if connection issues occur)
- `Copy to.. dialog closed` (no reload after)

**BAD** (should NOT see after Copy):
- `reloadFiles: Clearing cache` 
- `loadResource` called after copy operation
- `MediaStore changed, reloading files` (external trigger, OK if from file observer)

**CRITICAL ERRORS**:
- `connection pool exhausted`
- `socket timeout` (frequent)
- `nullPointerException` in file operations
- `Failed to remove files` (after delete/move)

---

## How to Submit Logs

For each test scenario:
1. Execute all steps in the scenario
2. Go to Settings → General → "Show current session log"
3. Copy log to clipboard
4. Reply with log labeled with scenario number
5. Describe any unexpected behavior observed

**Format**:
```
## SCENARIO X - [NAME]
 
### Observed Behavior:
[Describe what you saw - any errors, unexpected reloads, UI issues]

### Log:
[Paste full log here]
```

---

## Success Criteria

✅ **Test passes if**:
- All operations complete without errors
- Copy operations don't reload source
- Move/Delete operations use targeted removal
- Grid text-only mode shows wide cells
- SMB connections remain stable
- No crashes or data loss

❌ **Test fails if**:
- Copy triggers folder reload
- Files don't disappear after Move/Delete
- Grid cells remain narrow without thumbnails
- Frequent SMB connection errors
- Any crashes or exceptions

---

**Ready to start testing!** Execute scenarios in order and provide logs after each scenario.


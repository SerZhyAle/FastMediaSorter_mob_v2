# FastMediaSorter v2 - User Testing Guide

This document provides step-by-step scenarios for manual testing of FastMediaSorter v2 on both virtual (emulator) and physical Android devices.

## Testing Environment Setup

### Prerequisites
- Android device or emulator with Android 9.0 (API 28) or higher
- Test media files (images, videos) prepared on device storage
- For network features: Access to SMB/network shares (future)

### Test Device Recommendations
- **Minimum**: Android 9.0 (API 28), 2GB RAM
- **Recommended**: Android 12+ (API 31+), 4GB RAM
- **Screen sizes**: Test on both phone (5-6.5") and tablet (7-10")
- **Orientations**: Portrait and Landscape

---

## Test Scenarios

### 1. First Launch and Permissions

#### Scenario 1.1: First Time App Launch
**Steps:**
1. Install the APK on a fresh device (or clear app data)
2. Launch FastMediaSorter v2
3. Observe the welcome/main screen

**Expected Results:**
- App launches without crashes
- Main screen displays with empty resource list
- "No resources added yet" message is visible
- Add Resource button is visible and clickable
- All UI elements render correctly

**Test on:** Emulator, Physical device

---

#### Scenario 1.2: Media Permissions Request
**Steps:**
1. From Main Screen, tap "Add Resource" button
2. Select "Local Folder" type
3. Tap "SCAN" button or "Add Manually"
4. Grant media permissions when prompted

**Expected Results:**
- Permission dialog appears requesting media access
- After granting: scan proceeds or folder picker opens
- After denying: appropriate error message shown
- No app crash on permission denial

**Test on:** Physical device (API 28-32), Emulator (API 33+)

---

### 2. Adding Local Resources

#### Scenario 2.1: Scan Local Folders
**Preparation:**
- Ensure device has media files in standard folders (Camera, Pictures, Download)

**Steps:**
1. Open Main Screen
2. Tap "Add Resource" button
3. Select "Local Folder" resource type
4. Tap "SCAN" button
5. Wait for scan to complete

**Expected Results:**
- Progress indicator shows during scan
- List of found folders appears with:
  - Folder name
  - File count
  - Writable indicator (if not writable, lock icon 🔒 visible)
- Standard Android folders (Camera, Pictures, Downloads) are included
- Each folder has "Add" checkbox (enabled by default)

**Test on:** Physical device, Emulator with prepared media

---

#### Scenario 2.2: Add Local Folder Manually
**Steps:**
1. Open Main Screen
2. Tap "Add Resource" button
3. Select "Local Folder" resource type
4. Tap "Add Manually" button
5. Select a folder using Android folder picker
6. Confirm selection

**Expected Results:**
- Android folder picker dialog opens
- After selection: folder appears in "to add" list
- File count is calculated and displayed
- Writable status is detected correctly
- "Add to resources" button appears

**Test on:** Physical device, Emulator

---

#### Scenario 2.3: Add Resource to Main List
**Preparation:**
- Complete Scenario 2.1 or 2.2

**Steps:**
1. Review the "to add" list
2. Edit resource name if desired
3. For writable folders: toggle "To destinations" checkbox
4. Tap "Add to resources" button
5. Observe Main Screen

**Expected Results:**
- Resources are added to Main Screen list
- Each resource shows:
  - Short name
  - Full path (small font)
  - Resource type icon
  - File count
  - Destination marker (→) if added to destinations
  - Media types indicator (I/V/A/G)
- List is scrollable if > screen height
- Up to 10 destinations can be added

**Test on:** Emulator, Physical device

---

### 3. Main Screen Operations

#### Scenario 3.1: Select Resource
**Preparation:**
- Have at least 3 resources in the list

**Steps:**
1. Tap on a resource in the list (single click)
2. Observe visual feedback
3. Tap on another resource

**Expected Results:**
- Selected resource is highlighted
- Only one resource can be selected at a time
- "Start Player" button becomes enabled when resource selected
- "Copy Resource" button becomes enabled

**Test on:** Emulator, Physical device

---

#### Scenario 3.2: Double-Click to Browse
**Preparation:**
- Have at least one resource with media files

**Steps:**
1. Double-click/tap on a resource quickly
2. Observe navigation

**Expected Results:**
- Browse Screen opens for selected resource
- Media files list is displayed
- Toolbar shows resource name

**Test on:** Physical device (better for double-tap testing)

---

#### Scenario 3.3: Long Press to Browse
**Steps:**
1. Long press on a resource
2. Observe navigation

**Expected Results:**
- Browse Screen opens immediately
- Same as double-click behavior

**Test on:** Physical device, Emulator

---

#### Scenario 3.4: Copy Resource
**Steps:**
1. Select a resource
2. Tap "Copy Resource" button (copy icon)
3. Modify resource name
4. Tap "Add to resources"

**Expected Results:**
- Add Resource Screen opens
- All fields pre-filled from source resource
- Resource type matches original
- User can modify name and settings
- New resource is added as separate item

**Test on:** Emulator, Physical device

---

#### Scenario 3.5: Edit Resource
**Steps:**
1. Tap "Edit" icon (✏️) on a resource
2. Modify resource name or settings
3. Tap "Save"

**Expected Results:**
- Resource Profile Screen opens
- Fields are editable
- Changes are saved to database
- Main Screen updates with new values
- "Test", "Reset", "Back" buttons work correctly

**Test on:** Emulator, Physical device

---

#### Scenario 3.6: Move Resource Position
**Steps:**
1. Tap "Up" arrow on a resource (not first in list)
2. Tap "Down" arrow on a resource (not last in list)

**Expected Results:**
- Resource position changes in list
- Order is persisted after app restart
- Arrows disabled appropriately (top/bottom items)

**Test on:** Emulator, Physical device

---

#### Scenario 3.7: Delete Resource
**Steps:**
1. Tap "Delete" icon (🗑️) on a resource
2. Confirm deletion in dialog
3. Tap "Yes"

**Expected Results:**
- Confirmation dialog appears
- Message includes resource name
- After confirmation: resource removed from list
- If resource was selected: selection cleared
- Resource removed from database

**Test on:** Emulator, Physical device

---

#### Scenario 3.8: Exit Application
**Steps:**
1. From Main Screen, tap "Exit" button (door icon)

**Expected Results:**
- Application closes immediately
- No confirmation dialog
- App can be reopened normally

**Test on:** Emulator, Physical device

---

### 4. Browse Screen Operations

#### Scenario 4.1: View Media Files List
**Preparation:**
- Navigate to Browse Screen from resource with 10+ files

**Steps:**
1. Observe the media files list
2. Scroll through the list
3. Check file information display

**Expected Results:**
- Files displayed in list format by default
- Each item shows:
  - Thumbnail/icon
  - File name
  - File size (MB, KB, GB with 1 decimal)
  - Creation date (YYYY-MM-DD)
  - Play button
  - Checkbox for selection
- List is scrollable
- Resource name and path in toolbar
- Selected file counter visible

**Test on:** Emulator, Physical device

---

#### Scenario 4.2: Toggle Grid/List View
**Steps:**
1. From Browse Screen, tap "Toggle View" button
2. Observe layout change
3. Toggle back to list

**Expected Results:**
- View switches between List and Grid (3 columns)
- Setting is saved per resource
- All file information visible in both modes
- Smooth transition between modes

**Test on:** Tablet emulator, Physical device

---

#### Scenario 4.3: Sort Media Files
**Steps:**
1. Tap "Sort" button
2. Select different sort mode (e.g., Date Descending)
3. Confirm selection

**Expected Results:**
- Sort dialog shows all options:
  - Name Ascending/Descending
  - Date Ascending/Descending
  - Size Ascending/Descending
- Current mode is highlighted
- After selection: files re-ordered immediately
- Sort mode saved for this resource

**Test on:** Emulator, Physical device

---

#### Scenario 4.4: Single File Selection (Checkbox)
**Steps:**
1. Tap checkbox on a file
2. Tap checkbox on another file
3. Tap checkbox again to deselect

**Expected Results:**
- File becomes selected (checkbox checked)
- Background color changes (light blue highlight)
- Counter updates: "1 selected", "2 selected", etc.
- Copy/Move/Rename/Delete buttons become visible
- Buttons visibility depends on isWritable flag

**Test on:** Emulator, Physical device

---

#### Scenario 4.5: Multi-Selection with Range (Long Press)
**Preparation:**
- Navigate to Browse Screen with 20+ files

**Steps:**
1. Long press on file #5
2. Scroll down
3. Long press on file #15
4. Observe selection

**Expected Results:**
- First long press: file #5 selected
- Second long press: all files from #5 to #15 selected (11 files total)
- All selected files highlighted with blue background
- Counter shows "11 selected"
- Selection works in both directions (up/down)

**Test on:** Physical device (better for long press), Emulator

---

#### Scenario 4.6: Play Single File
**Steps:**
1. Tap on a file's thumbnail or name
2. Wait for Player Screen to open

**Expected Results:**
- Player Screen opens immediately
- Selected file starts playing/displaying
- For images: image displayed full screen
- For videos: video player controls visible
- For videos: playback starts automatically

**Test on:** Physical device, Emulator

---

#### Scenario 4.7: Play Button for File
**Steps:**
1. Tap "Play" button (▶️) on a file

**Expected Results:**
- Same as Scenario 4.6
- Player Screen opens with selected file

**Test on:** Emulator, Physical device

---

#### Scenario 4.8: Start Slideshow
**Steps:**
1. From Browse Screen, tap "Slideshow" button
2. Observe playback

**Expected Results:**
- Player Screen opens in slideshow mode
- Starts from first file if none selected
- Starts from selected file if one is selected
- Files advance automatically (default 3 sec interval)
- Slideshow mode indicator visible

**Test on:** Physical device, Emulator

---

#### Scenario 4.9: Rename Single File
**Preparation:**
- Navigate to writable folder (isWritable = true)

**Steps:**
1. Select one file (checkbox)
2. Tap "Rename" button
3. Enter new name in dialog
4. Tap "Apply"

**Expected Results:**
- Rename dialog opens with current name
- File extension included and editable
- After apply: file renamed in filesystem
- List updates with new name
- If name exists: error message, rename skipped
- Toast shows success/failure

**Test on:** Physical device, Emulator

---

#### Scenario 4.10: Rename Multiple Files
**Steps:**
1. Select 3 files
2. Tap "Rename" button
3. Edit names in list
4. Tap "Apply"

**Expected Results:**
- Dialog shows editable list of all selected files
- Each name can be edited individually
- Bulk rename applied to all valid names
- Conflicts handled (skip with message)

**Test on:** Emulator, Physical device

---

#### Scenario 4.11: Delete Files (with Confirmation)
**Preparation:**
- Ensure "Confirm deletion" setting is ON

**Steps:**
1. Select 2-3 files
2. Tap "Delete" button
3. Read confirmation dialog
4. Tap "Delete" to confirm

**Expected Results:**
- Confirmation dialog shows:
  - Message: "Are you sure you want to delete 3 files from [folder name]?"
  - Red/light-red background
  - Delete and Cancel buttons
- After confirmation: files deleted from filesystem
- Files removed from list
- Toast message shown

**Test on:** Physical device (to test actual deletion), Emulator

---

#### Scenario 4.12: Copy Files to Destination
**Preparation:**
- Add at least 2 resources, one marked as destination

**Steps:**
1. Open Browse Screen for non-destination resource
2. Select 2-3 files
3. Tap "Copy" button
4. Select destination from button list
5. Wait for operation to complete

**Expected Results:**
- Copy dialog opens with:
  - Title: "copying 3 files from [source name]"
  - Green/light-green background
  - Destination buttons (1-10, except current folder)
  - Buttons colored by destination order
- Progress bar if operation > 2 seconds
- Toast: "copied 3 files"
- Files remain in source
- Files appear in destination

**Test on:** Physical device, Emulator

---

#### Scenario 4.13: Move Files to Destination
**Preparation:**
- Writable source folder with destinations configured

**Steps:**
1. Select files from writable folder
2. Tap "Move" button
3. Select destination
4. Observe operation

**Expected Results:**
- Move dialog opens (blue/light-blue background)
- After move: files removed from source
- Files appear in destination
- Next file auto-selected
- Toast confirms operation

**Test on:** Physical device, Emulator

---

#### Scenario 4.14: Button Visibility Based on Permissions
**Preparation:**
- Test with both writable and read-only folders

**Steps:**
1. Open read-only folder (e.g., system folder)
2. Select files
3. Observe available buttons
4. Open writable folder
5. Select files
6. Compare button availability

**Expected Results:**
- **Read-only folder:**
  - Copy button: visible
  - Move button: hidden (needs write permission)
  - Rename button: hidden
  - Delete button: hidden
  - Lock icon 🔒 visible in Main Screen
- **Writable folder:**
  - All buttons visible when files selected

**Test on:** Physical device

---

### 5. Player Screen Operations

#### Scenario 5.1: Image Playback
**Steps:**
1. Open image file from Browse Screen
2. Observe display
3. Test gestures

**Expected Results:**
- Image displays full screen
- Maintains aspect ratio
- No distortion or cropping
- Image rotates with device orientation
- Supports pinch-to-zoom (if implemented)
- Swipe gestures work for next/previous

**Test on:** Physical device, Tablet emulator

---

#### Scenario 5.2: Video Playback
**Steps:**
1. Open video file
2. Observe playback
3. Test video controls

**Expected Results:**
- Video plays automatically
- ExoPlayer controls visible:
  - Play/Pause button
  - Seek bar
  - Current time / Duration
- Controls auto-hide after 3 seconds
- Tap video area to show/hide controls
- Maintains aspect ratio

**Test on:** Physical device, Emulator

---

#### Scenario 5.3: Navigate Between Files (Swipe)
**Steps:**
1. Open any media file
2. Swipe left to go to next file
3. Swipe right to go to previous file
4. Test at first and last file

**Expected Results:**
- Smooth transition between files
- Next/previous file loads and displays
- At first file: swipe right has no effect
- At last file: swipe left has no effect
- File preloading for smooth transitions

**Test on:** Physical device

---

#### Scenario 5.4: Slideshow Mode
**Steps:**
1. Start slideshow from Browse Screen
2. Observe automatic progression
3. Count interval timing
4. Tap screen during slideshow

**Expected Results:**
- Files advance automatically every 3 seconds (default)
- Countdown indicator shows: "3..", "2..", "1.."
- Videos play to completion (if setting enabled)
- Tap anywhere stops slideshow
- Returns to Browse Screen after stop

**Test on:** Physical device, Emulator

---

#### Scenario 5.5: Return to Browse Screen
**Steps:**
1. From Player Screen, tap "Back" button or gesture
2. Observe navigation

**Expected Results:**
- Returns to Browse Screen
- Previously selected file still highlighted
- Scroll position maintained
- No data loss

**Test on:** Emulator, Physical device

---

### 6. Data Persistence

#### Scenario 6.1: App Restart
**Steps:**
1. Add 3-5 resources with various settings
2. Mark 2 as destinations
3. Edit resource names
4. Close app completely (force stop)
5. Reopen app

**Expected Results:**
- All resources persist
- Resource order maintained
- Destination flags preserved
- Sort modes saved per resource
- Display modes saved per resource

**Test on:** Physical device, Emulator

---

#### Scenario 6.2: Database Integrity
**Steps:**
1. Add 10+ resources
2. Delete 3 resources
3. Edit 2 resources
4. Restart app
5. Verify data

**Expected Results:**
- No duplicate resources
- Deleted resources don't reappear
- Edited resources show updated values
- No database corruption errors
- File counts remain accurate

**Test on:** Emulator, Physical device

---

### 7. Edge Cases and Error Handling

#### Scenario 7.1: Empty Folder
**Steps:**
1. Create empty folder on device
2. Add it as resource
3. Open in Browse Screen

**Expected Results:**
- Resource adds successfully with 0 files
- Browse Screen shows "No media files found" message
- No crash or error
- All buttons remain functional

**Test on:** Physical device

---

#### Scenario 7.2: Large File List (Performance)
**Preparation:**
- Folder with 500+ media files

**Steps:**
1. Add folder as resource
2. Scan for file count
3. Open Browse Screen
4. Scroll through list
5. Test sorting

**Expected Results:**
- Scan completes in reasonable time (<30 sec)
- List scrolling is smooth (no lag)
- Sorting completes in <5 seconds
- No OutOfMemory errors
- UI remains responsive

**Test on:** Physical device with many files

---

#### Scenario 7.3: Very Long File Names
**Steps:**
1. Create file with 100+ character name
2. Add folder containing this file
3. View in Browse Screen

**Expected Results:**
- File name truncated with ellipsis (...)
- No layout breaking
- Full name visible when tapped/selected
- All operations work normally

**Test on:** Emulator, Physical device

---

#### Scenario 7.4: Special Characters in Names
**Steps:**
1. Test files with names containing:
   - Unicode characters (中文, Español, Українська)
   - Special symbols (!@#$%^&*()_+-=)
   - Emojis (😀📱🎵)

**Expected Results:**
- All characters display correctly
- No encoding issues
- Sorting works correctly
- Rename operations preserve characters
- No crashes

**Test on:** Physical device

---

#### Scenario 7.5: Corrupted Media File
**Steps:**
1. Add corrupted/incomplete media file to folder
2. Scan folder
3. Try to open file in Player

**Expected Results:**
- File appears in list (counted)
- Attempting to play shows error message
- No app crash
- Can navigate to next file
- Error is logged

**Test on:** Physical device

---

#### Scenario 7.6: Storage Permission Revoked
**Steps:**
1. Add resources and browse files
2. Go to device Settings → Apps → FastMediaSorter
3. Revoke storage permissions
4. Return to app
5. Try to browse files

**Expected Results:**
- App detects permission loss
- Shows appropriate error message
- Prompts to re-grant permissions
- No crash
- After re-granting: functionality restored

**Test on:** Physical device (Android 11+)

---

#### Scenario 7.7: Low Storage Space
**Preparation:**
- Device with <100MB free space

**Steps:**
1. Try to copy large files
2. Observe error handling

**Expected Results:**
- Copy operation fails gracefully
- Error message: "Insufficient storage space"
- No partial copies left
- App remains stable

**Test on:** Physical device

---

#### Scenario 7.8: Destination Limit (10 Resources)
**Steps:**
1. Add 10 resources, all marked as destinations
2. Try to add 11th resource with destination checkbox
3. Observe behavior

**Expected Results:**
- Toast message: "Destinations full (maximum 10)"
- Resource added without destination flag
- Checkbox disabled in add screen after 10 destinations
- Clear error communication

**Test on:** Emulator

---

### 8. UI/UX Testing

#### Scenario 8.1: Portrait/Landscape Orientation
**Steps:**
1. Navigate through all screens
2. Rotate device at each screen
3. Observe layout adaptation

**Expected Results:**
- All screens adapt to orientation
- No content cut off
- Buttons remain accessible
- Lists/grids adjust appropriately
- No data loss during rotation

**Test on:** Physical device, Tablet

---

#### Scenario 8.2: Different Screen Sizes
**Test on:**
- Small phone (5-5.5")
- Regular phone (6-6.5")
- Large phone (6.5"+)
- Tablet (7-10")

**Expected Results:**
- All UI elements visible and accessible
- Text remains readable
- Touch targets adequate size (48dp min)
- Proper use of screen space

---

#### Scenario 8.3: Touch Target Sizes
**Steps:**
1. Test all buttons with finger tap
2. Try to tap small icons/checkboxes

**Expected Results:**
- All buttons easily tappable
- Minimum 48dp touch target
- No accidental taps on adjacent buttons
- Comfortable spacing between elements

**Test on:** Physical device

---

#### Scenario 8.4: Loading States
**Steps:**
1. Observe loading indicators during:
   - Resource scanning
   - File list loading
   - File operations (copy/move)
   - Media file loading

**Expected Results:**
- Progress bar or spinner visible
- UI remains responsive
- Can cancel long operations
- Clear indication of what's loading

**Test on:** Emulator (slow), Physical device

---

### 9. Stress Testing

#### Scenario 9.1: Rapid Button Tapping
**Steps:**
1. Rapidly tap "Add Resource" button 10 times
2. Rapidly switch between resources
3. Quickly scroll file list while selecting

**Expected Results:**
- No duplicate operations
- No crashes
- Operations queued or ignored properly
- UI remains responsive

**Test on:** Physical device

---

#### Scenario 9.2: Background/Foreground Switching
**Steps:**
1. Start file operation (copy/move)
2. Switch to another app
3. Return to FastMediaSorter
4. Repeat during slideshow

**Expected Results:**
- Operations continue in background
- State preserved when returning
- Slideshow pauses/stops appropriately
- No memory leaks

**Test on:** Physical device

---

#### Scenario 9.3: Low Memory Conditions
**Preparation:**
- Device with limited RAM (<2GB)
- Multiple apps running

**Steps:**
1. Open resource with many large images
2. Browse through files
3. Monitor memory usage

**Expected Results:**
- App uses memory efficiently
- No OutOfMemory crashes
- Images load with appropriate resolution
- Smooth scrolling maintained

**Test on:** Low-end physical device

---

## Test Execution Checklist

### Pre-Release Testing (Minimum)
- [ ] Fresh install on emulator (API 28)
- [ ] Fresh install on emulator (API 33)
- [ ] Fresh install on physical device
- [ ] All critical scenarios (1-6) passed
- [ ] No crashes in any scenario
- [ ] Data persistence verified
- [ ] Permission handling correct
- [ ] File operations work correctly

### Comprehensive Testing (Recommended)
- [ ] All scenarios completed on emulator
- [ ] All scenarios completed on physical device
- [ ] Tested on 2+ screen sizes
- [ ] Portrait and landscape tested
- [ ] Edge cases verified
- [ ] Performance acceptable
- [ ] UI/UX smooth and intuitive

### Device Matrix (Ideal)
- [ ] Android 9 (API 28) - Emulator
- [ ] Android 10 (API 29) - Physical device
- [ ] Android 11 (API 30) - Physical device
- [ ] Android 12 (API 31) - Emulator
- [ ] Android 13 (API 33) - Physical device
- [ ] Android 14 (API 34) - Emulator

---

## Bug Reporting Template

When bugs are found during testing, report using this format:

```
**Bug Title:** [Short description]

**Severity:** Critical / High / Medium / Low

**Device:** [Brand, Model, Android Version]

**Steps to Reproduce:**
1. Step 1
2. Step 2
3. Step 3

**Expected Result:**
[What should happen]

**Actual Result:**
[What actually happened]

**Screenshots/Logs:**
[Attach if available]

**Reproducibility:**
Always / Sometimes / Rarely

**Additional Notes:**
[Any other relevant information]
```

---

## Test Results Summary Template

```
**Test Date:** YYYY-MM-DD
**Tester:** [Name]
**App Version:** [versionName (versionCode)]
**Device:** [Model, Android Version]

**Scenarios Tested:** X / Y
**Passed:** X
**Failed:** Y
**Blocked:** Z

**Critical Issues:** [Count]
**High Priority:** [Count]
**Medium Priority:** [Count]
**Low Priority:** [Count]

**Notes:**
[General observations, performance notes, UX feedback]

**Recommendation:**
[ ] Ready for release
[ ] Needs fixes before release
[ ] Major issues found - not ready
```

---

## Automated Testing Notes

While this guide focuses on manual testing, consider:
- **Unit Tests:** Core logic in ViewModels and UseCases
- **Instrumented Tests:** Database operations, file operations
- **UI Tests:** Critical user flows with Espresso

Refer to TODO_V2.md "Testing" section for automated testing tasks.

---

## Additional Resources

- **V2_Specification.md** - Full feature specifications
- **TODO_V2.md** - Development progress and known issues
- **Android Logcat** - For debugging crashes and errors
- **Android Studio Profiler** - For performance analysis

---

**Last Updated:** 2025-11-06
**Document Version:** 1.0

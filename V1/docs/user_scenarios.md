# Main Usage Scenarios for FastMediaSorter Mobile

## Application Overview
FastMediaSorter Mobile is an Android application for viewing and sorting media files (images and videos) from network SMB folders and local device storage.

## Typical Usage Scenarios

### Scenario 1: Viewing Photos from Network Storage (SMB)
**Goal:** View a photo collection on NAS or network drive in automatic slideshow mode.

**Steps:**
1. User launches the application
2. On first launch, welcome screen with setup instructions is shown
3. In main screen, user goes to "Network" tab
4. Adds SMB connection (server address, credentials, folder path)
5. Selects connection and presses "Slideshow" button
6. Application tests connection and starts automatic slideshow
7. User can control playback (pause, next/previous, speed)

**Expected Result:** Smooth automatic image flipping with video support.

### Scenario 2: Sorting Photos into Target Folders
**Goal:** Organize photos by moving them to pre-configured categories.

**Steps:**
1. User configures target folders in settings (e.g.: "Family", "Work", "Delete")
2. Selects source folder (local or network)
3. Presses "Sort" button
4. Reviews images one by one
5. For each image chooses action:
   - Move to one of target folders
   - Delete file
   - Skip (go to next)
6. Application performs selected operations

**Expected Result:** Files organized by categories, unnecessary ones deleted.

### Scenario 3: Working with Local Device Photos
**Goal:** View and sort photos from device.

**Steps:**
1. User grants permission to access media files
2. In main screen goes to "Local Folders" tab
3. Selects standard folder (Camera, Downloads) or adds custom one
4. Uses slideshow or sorting functions similar to network folders

**Expected Result:** Local media files available for viewing and organization.

### Scenario 4: Setting Up New Network Connection
**Goal:** Add access to new network folder.

**Steps:**
1. In app settings, user goes to "Network" section
2. Presses add new connection button
3. Enters parameters:
   - Server address (e.g.: \\\\192.168.1.100)
   - Username and password (if required)
   - Folder path (e.g.: /Photos)
4. Tests connection
5. Saves settings

**Expected Result:** New connection available for use in slideshow and sorting.

### Scenario 5: Configuring Target Folders for Sorting
**Goal:** Set up categories for file organization.

**Steps:**
1. In settings goes to "Sort to..." section
2. Adds new target folders with names and colors
3. For each folder specifies:
   - Category name
   - Color for visual distinction
   - Destination path (for network folders)
4. Saves configuration

**Expected Result:** Configured categories available during sorting.

## User Experience Features

### Navigation and Control
- **Gestures:** Swipe for navigation, double tap for sorting
- **Buttons:** Previous/next, pause/play
- **Interval:** Configurable interval between images (1-300 seconds)

### Error Handling
- **Network Issues:** Connection diagnostics with detailed messages
- **Corrupted Files:** Skip with error logging
- **Insufficient Space:** Warnings before operations

### Performance
- **Preloading:** Next image loaded in advance
- **Caching:** Optimization for smooth viewing
- **Background Processing:** Copy/move operations in background

## Technical Limitations
- **SMB Protocol:** Support for basic versions with authentication
- **File Formats:** JPEG, PNG, GIF, MP4, MOV
- **Permissions:** Access to storage and network
- **Android Versions:** Minimum API 28 (Android 9)</content>
<parameter name="filePath">c:\GIT\FastMediaSorter_mobile\docs\user_scenarios.md
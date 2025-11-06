# Navigation Flow Between Screens in FastMediaSorter Mobile

## General Application Structure

The application consists of the following main screens:

1. **WelcomeActivity** - Welcome screen and initial setup
2. **MainActivity** - Main screen with tabs
3. **SlideshowActivity** - Slideshow viewing screen
4. **SortActivity** - File sorting screen
5. **SettingsActivity** - Settings screen

## Navigation Diagram

```
[Application Launch]
    │
    ├─ First Launch ──► WelcomeActivity ──► MainActivity
    │
    └─ Subsequent Launch ─────────────────► MainActivity
                                            │
                                            ├─ Local Tab ───┬─ Folder Selection ──► SlideshowActivity
                                            │               │
                                            │               └─ Folder Selection ──► SortActivity
                                            │
                                            ├─ Network Tab ─┼─ Connection Selection ──► SlideshowActivity
                                            │               │
                                            │               └─ Connection Selection ──► SortActivity
                                            │
                                            └─ Settings Button ─────────────────► SettingsActivity
```

## Detailed Flow Descriptions

### First Launch Flow

1. **WelcomeActivity**
   - Shown automatically on first launch
   - 7 pages with setup instructions
   - "Start Using App" button leads to MainActivity
   - `isWelcomeShown` flag saved in settings

2. **MainActivity** (after welcome)
   - Checks media files permission
   - If permission granted - scans local folders
   - Suggests opening settings for initial configuration

### Main Usage Flow

#### From MainActivity to SlideshowActivity

**Transition Conditions:**
- Folder/connection selected in one of the tabs
- "Slideshow" button pressed

**Process:**
1. Check selected configuration
2. Test connection (for network folders)
3. Load file list
4. Navigate to SlideshowActivity with parameters:
   - `configId`: ID of selected configuration
   - `connectionType`: "SMB" or "LOCAL"

#### From MainActivity to SortActivity

**Transition Conditions:**
- Folder/connection selected
- Target folders configured for sorting OR deletion allowed
- "Sort" button pressed

**Process:**
1. Check for target folders or deletion permission
2. Test connection
3. Navigate to SortActivity with `configId`

#### From MainActivity to SettingsActivity

**Transition Conditions:**
- "Settings" button pressed (gear icon)

**Process:**
1. Direct navigation without additional checks
2. Return possible with `initialTab` parameter to open specific tab

### Internal Navigation in Activities

#### SlideshowActivity

**Controls:**
- Buttons: Previous/Next, Pause/Play
- Gestures: Left/right swipe, double tap for pause
- System: Back button to exit

**States:**
- Automatic slideshow with configurable interval
- Manual control with pause
- File loading error handling

#### SortActivity

**Controls:**
- Assignment buttons (colored buttons for target folders)
- Delete button
- Navigation buttons: Previous/Next
- Gestures: Swipe for navigation
- System: Back button to exit

**States:**
- File viewing with action options
- Copy/move operations execution
- Progress display for long operations

### Return Handling and State Preservation

#### State Saving
- **MainActivity:** Saves selected configuration and tab
- **SlideshowActivity:** Saves current index and file list
- **SortActivity:** Saves current position in list

#### Return from Activities
- **SlideshowActivity → MainActivity:** Saves last session for auto-resume
- **SortActivity → MainActivity:** Updates operation statistics
- **SettingsActivity → MainActivity:** Applies setting changes

### Error and Exception Handling

#### Network Errors
- Connection timeout → Diagnostic dialog with details
- Authentication error → Suggest checking credentials
- Server unavailable → Suggest checking network

#### File System Errors
- Insufficient space → Warning before operation
- Permission denied → Permission diagnostics
- Corrupted files → Skip with logging

#### System Errors
- Out of memory → Cache cleanup and restart
- Operation interruption → State recovery

### Adaptive Navigation

#### Screen Orientation
- **Portrait:** Standard layout
- **Landscape:** Adapted controls
- **Auto-save:** State preserved on rotation

#### Screen Sizes
- **Phone:** Compact navigation
- **Tablet:** Extended controls
- **Foldable:** Adaptation to changing sizes

### Special Modes

#### Recovery Mode
- Auto-resume last session on crash
- Problem diagnostics with state recovery

#### Debug Mode
- Additional logs and diagnostic information
- Access via developer settings</content>
<parameter name="filePath">c:\GIT\FastMediaSorter_mobile\docs\navigation_flow.md
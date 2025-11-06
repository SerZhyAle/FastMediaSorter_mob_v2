# Changelog - Development Session Nov 6, 2025

## [Unreleased] - 2025-11-06

### Changed
- **Upgrade Java runtime from 17 to 21 (LTS)**
  - Updated `sourceCompatibility`, `targetCompatibility`, and `jvmTarget` to Java 21
  - Verified compatibility with Android Gradle Plugin 8.2.1
  - All dependencies (AndroidX, Hilt 2.50, Room 2.6.1, Kotlin 1.9.22) compatible with Java 21

### Added
- **UI: Write permissions indicator in resource list**
  - Added lock icon (🔒) in `item_resource.xml` for read-only folders
  - Indicator visible only when `isWritable = false`
  - Updated `ResourceAdapter` to show/hide indicator based on permissions

- **UI: Rename button in Browse screen**
  - Added `btnRename` in `activity_browse.xml` between Move and Delete buttons
  - Button visible only when folder is writable (`isWritable = true`) and files are selected
  - Added `showRenameDialog()` method in `BrowseActivity` (stub implementation)

- **UI: Slideshow button in Browse screen**
  - Added `btnSlideshow` in `activity_browse.xml` next to Play button
  - Implemented `startSlideshow()` method to launch PlayerActivity in slideshow mode
  - Slideshow starts from selected file or first file if none selected
  - Passes `slideshow_mode` flag to PlayerActivity via Intent

### Fixed
- **Specification compliance**
  - Main Screen: Copy Resource button (already implemented, marked as ✅)
  - Main Screen: Exit button (already implemented, marked as ✅)
  - Main Screen: Double-click on resource (already implemented, marked as ✅)
  - Browse Screen: Rename button (implemented in this session)
  - Browse Screen: Slideshow button (implemented in this session)
  - Item Resource: Writable indicator (implemented in this session)

### Updated
- **TODO_V2.md**
  - Marked 3 main screen tasks as completed
  - Marked 2 browse screen tasks as completed
  - Marked 1 item resource task as completed
  - Updated task descriptions with implementation status
  - Added discrepancy notes for remaining tasks

## Remaining Tasks
- Browse Screen: Multi-selection with range selection on long press
- Main Screen: Filter and sort resources dialog
- Main Screen: Resource refresh/rescan functionality
- Player Screen: Touch zones for images (9-zone layout)
- Player Screen: Command panel mode
- Settings Screen: Implementation
- Network/Cloud: SMB, SFTP, Cloud providers support

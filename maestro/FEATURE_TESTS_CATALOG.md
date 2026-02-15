# FastMediaSorter v2 - Feature Tests Catalog

Comprehensive test catalog for all Maestro feature tests organized by functionality.

## Overview

**Total Feature Tests**: 36 tests
**Organized Categories**: 13 feature areas
**Average Duration**: 20-40 seconds per test
**Total Execution Time**: ~20 minutes for all feature tests

## Quick Reference Table

| Category | Tests | Total Duration | Status |
|----------|-------|----------------|--------|
| Audio | 5 | ~3 min | ✅ Complete |
| Documents | 4 | ~2.5 min | ✅ Complete |
| Images | 5 | ~3 min | ✅ Complete |
| Video | 3 | ~1.5 min | ✅ Complete |
| Files | 6 | ~3 min | ✅ Complete |
| Favorites | 2 | ~45 sec | ✅ Complete |
| Slideshow | 2 | ~1.5 min | ✅ Complete |
| Settings | 4 | ~2.5 min | ✅ Complete |
| Navigation | 1 | ~25 sec | ✅ Complete |
| Translation | 2 | ~1.5 min | ✅ Complete |
| Cloud | 3 | ~3 min | 🔄 Planned |
| Network | 4 | ~3.5 min | 🔄 Planned |
| Widgets | 2 | ~1.5 min | 🔄 Planned |

---

## Audio Features (5 tests, ~3 minutes)

### [audio_player_basic.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/audio/audio_player_basic.yaml)
- **Duration**: ~30 seconds
- **Validates**: Basic playback controls (play/pause/next/previous/volume)
- **Prerequisites**: Audio files on device
- **Key Actions**:
  - Open audio file
  - Test play/pause toggle
  - Navigate to next/previous track
  - Verify volume control visibility

### [audio_lyrics.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/audio/audio_lyrics.yaml)
- **Duration**: ~25 seconds
- **Validates**: Lyrics fetching from api.lyrics.ovh
- **Prerequisites**: Internet connection, audio file with metadata
- **Key Actions**:
  - Open audio file
  - Access lyrics button
  - Verify lyrics display or "No lyrics" message

### [audio_background_music.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/audio/audio_background_music.yaml)
- **Duration**: ~40 seconds
- **Validates**: Background music during image slideshow
- **Prerequisites**: Music resource configured
- **Key Actions**:
  - Enable slideshow background music in settings
  - Select music resource
  - Start image slideshow
  - Verify music plays
  - Test skip to next track

### [audio_photo_background.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/audio/audio_photo_background.yaml)
- **Duration**: ~30 seconds
- **Validates**: Photo background while playing music
- **Prerequisites**: Audio and image files
- **Key Actions**:
  - Open audio player
  - Enable photo background
  - Select image
  - Verify image displays during playback

### [audio_controls.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/audio/audio_controls.yaml)
- **Duration**: ~35 seconds
- **Validates**: Seek bar, repeat modes, shuffle, track info
- **Prerequisites**: Audio files
- **Key Actions**:
  - Test seek bar navigation
  - Toggle repeat and shuffle modes
  - Verify track information display

---

## Document Features (4 tests, ~2.5 minutes)

### [document_text_view.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/documents/document_text_view.yaml)
- **Duration**: ~20 seconds
- **Validates**: Text file viewing (TXT, MD, LOG, JSON, XML)
- **Prerequisites**: Text files on device
- **Key Actions**:
  - Open text file
  - Verify content display
  - Test scroll functionality

### [document_pdf_view.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/documents/document_pdf_view.yaml)
- **Duration**: ~30 seconds
- **Validates**: PDF viewing with zoom, pan, page navigation
- **Prerequisites**: PDF files
- **Key Actions**:
  - Open PDF file
  - Test zoom in/out
  - Navigate pages (swipe up/down)
  - Test pan gestures

### [document_epub_reader.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/documents/document_epub_reader.yaml)
- **Duration**: ~40 seconds
- **Validates**: EPUB reader with navigation, TOC, font size, theme
- **Prerequisites**: EPUB files
- **Key Actions**:
  - Open EPUB file
  - Navigate chapters (swipe left/right)
  - Access table of contents
  - Adjust font size
  - Toggle dark/light theme

### [document_epub_search.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/documents/document_epub_search.yaml)
- **Duration**: ~25 seconds
- **Validates**: In-book search functionality
- **Prerequisites**: EPUB files
- **Key Actions**:
  - Open EPUB file
  - Access search
  - Search for common word
  - Navigate to search result

---

## Image Features (5 tests, ~3 minutes)

### [image_basic_view.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/images/image_basic_view.yaml)
- **Duration**: ~25 seconds
- **Validates**: Image viewing and zoom gestures (2x, 3x, 4x)
- **Prerequisites**: Image files (JPG, PNG, GIF, WEBP)
- **Key Actions**:
  - Open image
  - Test swipe navigation
  - Test zoom levels (tap for 2x, 3x, 4x)
  - Reset zoom

### [image_editing_rotate.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/images/image_editing_rotate.yaml)
- **Duration**: ~30 seconds
- **Validates**: Image rotation left/right and save
- **Prerequisites**: Image files
- **Key Actions**:
  - Open image
  - Access edit menu
  - Rotate left and right
  - Save changes

### [image_editing_flip.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/images/image_editing_flip.yaml)
- **Duration**: ~25 seconds
- **Validates**: Horizontal and vertical flip
- **Prerequisites**: Image files
- **Key Actions**:
  - Open image
  - Flip horizontal
  - Flip vertical
  - Save changes

### [image_filters.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/images/image_filters.yaml)
- **Duration**: ~40 seconds
- **Validates**: Grayscale, sepia, negative filters with undo
- **Prerequisites**: Image files
- **Key Actions**:
  - Apply grayscale filter and undo
  - Apply sepia filter and undo
  - Apply negative filter and undo

### [image_adjustments.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/images/image_adjustments.yaml)
- **Duration**: ~40 seconds
- **Validates**: Brightness, contrast, saturation adjustments
- **Prerequisites**: Image files
- **Key Actions**:
  - Adjust brightness
  - Adjust contrast
  - Adjust saturation
  - Save changes

---

## Video Features (3 tests, ~1.5 minutes)

### [video_playback_controls.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/video/video_playback_controls.yaml)
- **Duration**: ~30 seconds
- **Validates**: Play/pause, seek, progress bar
- **Prerequisites**: Video files
- **Key Actions**:
  - Play video
  - Test play/pause
  - Seek forward/backward on progress bar
  - Verify progress display

### [video_fullscreen.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/video/video_fullscreen.yaml)
- **Duration**: ~25 seconds
- **Validates**: Fullscreen toggle and orientation
- **Prerequisites**: Video files
- **Key Actions**:
  - Play video
  - Toggle fullscreen mode
  - Verify fullscreen display
  - Exit fullscreen

### [video_navigation.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/video/video_navigation.yaml)
- **Duration**: ~35 seconds
- **Validates**: Next/previous navigation and swipe
- **Prerequisites**: Multiple video files
- **Key Actions**:
  - Navigate to next video
  - Navigate to previous video
  - Test swipe navigation

---

## File Operations (6 tests, ~3 minutes)

### [file_copy.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/files/file_copy.yaml)
- **Duration**: ~25 seconds
- **Validates**: File copy to destination
- **Prerequisites**: Destination folders configured
- **Key Actions**:
  - Open file
  - Select copy operation
  - Choose destination
  - Verify copy success

### [file_move.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/files/file_move.yaml)
- **Duration**: ~30 seconds
- **Validates**: File move to destination
- **Prerequisites**: Destination folders configured
- **Key Actions**:
  - Open file
  - Select move operation
  - Choose destination
  - Verify file removed from original location

### [file_delete.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/files/file_delete.yaml)
- **Duration**: ~20 seconds
- **Validates**: Soft-delete to .trash folder
- **Prerequisites**: Files to delete
- **Key Actions**:
  - Open file
  - Delete file
  - Confirm deletion
  - Verify moved to trash

### [file_undo.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/files/file_undo.yaml)
- **Duration**: ~25 seconds
- **Validates**: Undo functionality for file operations
- **Prerequisites**: Files for testing
- **Key Actions**:
  - Perform copy operation
  - Tap undo button
  - Verify operation reversed

### [file_rename.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/files/file_rename.yaml)
- **Duration**: ~30 seconds
- **Validates**: File rename with long press
- **Prerequisites**: Files to rename
- **Key Actions**:
  - Long press file
  - Select rename
  - Input new name
  - Verify renamed

### [file_binary_view.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/files/file_binary_view.yaml)
- **Duration**: ~30 seconds
- **Validates**: "All Files" mode with binary thumbnails
- **Prerequisites**: Binary files (ZIP, APK, ISO)
- **Key Actions**:
  - Enable "All Files" mode
  - Verify binary file thumbnails with extensions
  - Test context menu (share/open with)

---

## Favorites System (2 tests, ~45 seconds)

### [favorites_add.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/favorites/favorites_add.yaml)
- **Duration**: ~25 seconds
- **Validates**: Adding files to favorites
- **Prerequisites**: Any media files
- **Key Actions**:
  - Open file
  - Tap favorite/star button
  - Navigate to Favorites tab
  - Verify file appears

### [favorites_remove.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/favorites/favorites_remove.yaml)
- **Duration**: ~20 seconds
- **Validates**: Removing files from favorites
- **Prerequisites**: Files in favorites
- **Key Actions**:
  - Open favorited file
  - Tap star button to remove
  - Verify removed from Favorites tab

---

## Slideshow Features (2 tests, ~1.5 minutes)

### [slideshow_basic.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/slideshow/slideshow_basic.yaml)
- **Duration**: ~35 seconds
- **Validates**: Slideshow start, auto-advance, pause/resume, stop
- **Prerequisites**: Image files
- **Key Actions**:
  - Start slideshow
  - Verify auto-advance
  - Pause and resume
  - Stop slideshow

### [slideshow_interval.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/slideshow/slideshow_interval.yaml)
- **Duration**: ~40 seconds
- **Validates**: Slideshow interval configuration
- **Prerequisites**: Image files, resource settings
- **Key Actions**:
  - Access resource settings
  - Set slideshow interval (3s, 5s, 10s)
  - Start slideshow
  - Verify timing

---

## Settings Features (4 tests, ~2.5 minutes)

### [settings_general.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/settings/settings_general.yaml)
- **Duration**: ~30 seconds
- **Validates**: General settings with collapsible sections
- **Prerequisites**: None
- **Key Actions**:
  - Navigate to General settings
  - Expand/collapse sections
  - Toggle options
  - Verify persistence

### [settings_media.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/settings/settings_media.yaml)
- **Duration**: ~35 seconds
- **Validates**: Media settings cache and metadata configuration
- **Prerequisites**: None
- **Key Actions**:
  - Navigate to Media settings
  - Expand grouped sections
  - View cache size options
  - Test metadata loading settings

### [settings_playback.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/settings/settings_playback.yaml)
- **Duration**: ~30 seconds
- **Validates**: Playback settings (autoplay, repeat)
- **Prerequisites**: None
- **Key Actions**:
  - Navigate to Playback settings
  - Toggle autoplay
  - Set repeat modes

### [settings_destinations.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/settings/settings_destinations.yaml)
- **Duration**: ~40 seconds
- **Validates**: Destination folder configuration
- **Prerequisites**: None
- **Key Actions**:
  - Navigate to Destinations tab
  - Add destination folder
  - Verify quick sort buttons appear in player

---

## Navigation Features (1 test, ~25 seconds)

### [touch_zones.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/navigation/touch_zones.yaml)
- **Duration**: ~25 seconds
- **Validates**: Left/right touch zones and swipe navigation
- **Prerequisites**: Multiple files
- **Key Actions**:
  - Open file
  - Test left touch zone (previous)
  - Test right touch zone (next)
  - Test swipe gestures

---

## Translation/OCR Features (2 tests, ~1.5 minutes)

### [translation_image_ocr.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/translation/translation_image_ocr.yaml)
- **Duration**: ~40 seconds
- **Validates**: OCR text extraction (ML Kit + Tesseract) and translation
- **Prerequisites**: Image with text, internet connection
- **Key Actions**:
  - Open image with text
  - Access translation feature
  - Verify OCR extraction
  - Select target language
  - View translation

### [translation_lens_mode.yaml](file:///c:/GIT/FastMediaSorter_mob_v2/maestro/features/translation/translation_lens_mode.yaml)
- **Duration**: ~35 seconds
- **Validates**: Lens-style overlay translation mode
- **Prerequisites**: Image with text, internet connection
- **Key Actions**:
  - Open image
  - Enable lens/overlay mode
  - Verify in-place translation overlay

---

## Running Feature Tests

### Run All Feature Tests
```powershell
.\maestro\run-tests.ps1 features
```

### Run Specific Category
```powershell
# Audio tests only
.\maestro\run-tests.ps1 features\audio

# Image tests only
.\maestro\run-tests.ps1 features\images

# File operations only
.\maestro\run-tests.ps1 features\files
```

### Run Individual Test
```powershell
maestro test maestro\features\audio\audio_player_basic.yaml
```

---

## Test Design Principles

All feature tests follow these principles:

1. **Resilient**: Use `optional: true` for elements that may vary
2. **Independent**: Each test can run standalone
3. **Quick**: 15-40 seconds per test
4. **Descriptive**: Clear comments explaining purpose
5. **Flexible**: Handle multiple UI variations

---

## Planned Tests

### Cloud Integration (3 tests)
- Google Drive connection and browsing
- OneDrive integration
- Dropbox file access

### Network Sources (4 tests)
- SMB connection and auto-scan
- SFTP server connection
- FTP browsing

### Widgets (2 tests)
- Resource Shortcut widget
- Continue Reading widget

### Keyboard Navigation (1 test)
- Arrow keys, shortcuts (Ctrl+A/C/X, F2, F5, Tab, Esc)

---

**Last Updated**: 2026-02-15
**Total Tests**: 36 complete, 10 planned
**Framework**: Maestro Mobile E2E Testing

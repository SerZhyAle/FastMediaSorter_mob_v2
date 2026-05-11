# FastMediaSorter v2 — Complete Feature List

*Last updated: 2026-05-11*

This document is the canonical inventory of all user-facing features implemented in the application. It serves as a guide to what the application can do and how each feature works.

**Platform requirements:** Android 8.0+ (API 26) for Standard, Lite, and Photos flavors. The Legacy flavor extends support down to Android 6.0+ (API 23) covering the same features without cloud integrations. Supported devices: phones, tablets, Android TV boxes, and Android head units. Runs on Chrome OS via Google Play (ARC++).

## Table of Contents

- [1. Resource / Source Management](#1-resource--source-management)
- [2. Media Browsing](#2-media-browsing)
- [3. File Operations](#3-file-operations)
- [4. Destination Management](#4-destination-management)
- [5. Image Viewer](#5-image-viewer)
- [6. GIF Viewer](#6-gif-viewer)
- [7. Video Player](#7-video-player)
- [8. VR Edition](#8-vr-edition)
- [9. Audio Player](#9-audio-player)
- [10. Slideshow](#10-slideshow)
- [11. PDF Viewer](#11-pdf-viewer)
- [12. EPUB Viewer](#12-epub-viewer)
- [13. Text Viewer / Editor](#13-text-viewer--editor)
- [14. Translation & OCR (cross-viewer feature)](#14-translation--ocr-cross-viewer-feature)
- [15. Network Sources](#15-network-sources)
- [16. Cloud Integration](#16-cloud-integration)
- [17. Favorites](#17-favorites)
- [18. Home Screen Widgets](#18-home-screen-widgets)
- [19. Settings](#19-settings)
- [20. Settings Search](#20-settings-search)
- [21. Wear OS Companion App](#21-wear-os-companion-app)
- [22. Background & System Services](#22-background--system-services)
- [23. Scheduled File Operations](#23-scheduled-file-operations)
- [24. Apps FMS Can Replace — Competitor Comparison](#24-apps-fms-can-replace--competitor-comparison)

---

## 1. Resource / Source Management

- **Add multiple resource types**: Connect Local folders, SMB (Windows share/NAS), FTP, SFTP, Google Drive, Dropbox, and OneDrive in a single interface.
- **Resource profiles (quick-setup presets)**: Configure new folders using presets (Audio Library, Video Library, Photo Storage, Documents, All Files) that automatically apply optimal sorting, filtering, and display settings.
- **Camera Photos Virtual Folder**: A dedicated virtual folder to browse photos and videos taken by the device's camera.
- **Recent and Downloads**: These default virtual folders are pre-configured with "Show all files" enabled to display documents and non-media files automatically.
- **Virtual resource language sync**: Virtual resources (All Images, All Videos, etc.) are automatically renamed when the app language changes.
- **Per-resource settings**: Customize supported media types, default sort mode, display mode, thumbnail loading, PIN access, and subfolder scanning per folder.
- **Resource ordering**: Rearrange connected resources on the main screen using drag-and-drop.
- **Themed resource icons**: Automatically assigned by type and connection source, or manually selected via the toolbar.
- **Connection test**: Verify network or cloud resource accessibility before trying to open it.
- **Read-only mode**: Protect critical folders by disabling file editing, deleting, and moving operations.
- **PIN protection**: Require a PIN code whenever a specific resource is opened.
- **Network credential management**: Encrypted vault for network passwords with a last-used tracking audit.
- **Last browse position save & restore**: Saves and restores the exact scroll position and last viewed file for every resource, opening directly into subfolders on resume.
- **File list caching**: Persists the file index in a local database to bypass network fetching on subsequent visits.

## 2. Media Browsing

- **Extensive sort modes**: Sort by Name, Date, Size, Type, Artist, Title, Duration, Date Taken, Random, or Manual order (ascending/descending).
- **Sort mode memory**: The sort mode is automatically saved per resource and restored on the next visit.
- **Advanced filter panel**: Filter items by filename, date ranges, size limits, or specific media types.
- **Random file jump in player**: A dedicated dice button in Audio and Photo profiles to jump to a random file without full shuffle.
- **Multi-select**: Select multiple files to batch copy, move, delete, or share.
- **Show subfolders as items**: Treat subdirectories as clickable entries mixed directly into the file list, or uncheck to flatten the view (recursive scan).
- **Subfolder operations**: Select, copy, move, rename, delete, and create subfolders directly in Browse for local, network, and cloud resources.
- **Show hidden/all files**: Toggle visibility of system-hidden files, or bypass media filters to view all binary files and archives.
- **Intelligent thumbnail loading**: Previews for photos and video frames; can be disabled per resource for large network directories.
- **Fast placeholder for unsupported network video**: Containers that cannot be decoded over a network stream show an icon immediately instead of waiting for a timeout.
- **Network thumbnail previews recover automatically**: Transient extraction failures during streaming clear themselves once playback stops.
- **File metadata overlay**: Crucial details like EXIF data, duration, resolution, and size are overlaid on list items.
- **Cancellable scan with progress**: Large network scans show a live file counter and an immediate STOP button.
- **Inline audio mini-player**: Play audio directly from any Browse resource (local, network, cloud) without opening the full player.
- **Full keyboard navigation**: Every screen responds to hardware keyboard input (Enter, Escape, Arrow keys, Backspace).
- **NC-style shortcuts and TV color keys**: F5 copies, F6 moves, F7 creates subfolder, F8 deletes. TV remote color keys map to Delete/Copy/Move/Rename.
- **F1 help dialog**: Press F1 on a keyboard to open a surface-specific shortcut reference. Active items receive a visible focus ring.
- **Gamepad support**: Drives the file browser and players using Xbox, DualSense, or 8BitDo gamepads.
- **Manual drag-to-reorder**: Set a custom display order via drag handles in Manual sort mode.
- **TV remote key remapping**: Reassign color buttons and Channel Up/Down in Settings.
- **DPAD hold-to-scroll acceleration**: Holding DPAD Up/Down switches to page-jump for fast navigation.
- **Extended file-info dialog**: Shows full audio metadata for remote files by streaming only the first ~64 KB.

## 3. File Operations

- **Flexible deleting**: Move files to a recoverable Trash bin or permanently delete them immediately.
- **Standalone player renaming**: Rename files directly from the "Open with" standalone player using DocumentsContract or ContentResolver.
- **Operation undo**: Revert the last copy, move, or delete action with a single tap.
- **Safe Mode**: Enable mandatory confirmation dialogs before moving or deleting files globally.
- **Move to trash toggle**: Choose between moving deleted local files to `.trash` or deleting immediately.
- **Overwrite policies**: Specify how to handle copying or moving files that already exist (overwrite, skip, auto-rename).
- **Cross-protocol transfers**: Copy or move files seamlessly between Local, SMB, FTP, SFTP, and Cloud endpoints.
- **Duplicate file detection**: Locate and remove identical files across all storage using a 3-phase scanning engine (Size -> Hash -> SHA-256). Includes Find and Auto-Delete modes.
- **ZIP archiving**: Pack selected local files into a single background-processed ZIP archive.
- **ZIP extraction on click**: Tap a ZIP archive in Browse to extract it with real-time percentage progress.
- **Select folder for copy/move**: Pick any local directory using the system folder picker, bypassing pre-configured destinations.
- **Microphone recording**: Record audio directly into the current folder (local, network, or cloud).
- **Camera capture**: Take photos or videos with the default camera app directly into the current folder.
- **Copy/move progress**: Shows overall byte-based percentage, current transfer speed, and estimated time remaining (ETA).
- **Player file operation handling**: Moving or deleting the currently playing file stops playback, transfers, and advances. Renaming stops playback and resumes with the new name.
- **Queued player file actions**: Move, delete, and rename in the player are accepted immediately, run one by one in the background, and no longer make the buttons feel stuck while the previous operation is still finishing.

## 4. Destination Management

- **Color-coded destination buttons**: Configure up to 10 distinct shortcut buttons inside the player representing favorite folders.
- **Auto-advance after copy/move**: Automatically jump to the next file as soon as a file operation completes.
- **Collapsible command panel**: Collapse the copy/move destination panel to maximize viewing space.
- **«..» folder-picker button**: Pick any local folder as a destination directly from the player panels.
- **Quick Favorites toggle**: Mark or unmark the current file as a Favorite directly from the player.
- **Downloads pre-configured**: The Downloads folder is automatically added as the first destination on install.

## 5. Image Viewer

- **Crop to fullscreen**: Fills the screen by cropping out black bars when image and device orientations match.
- **Dynamic background effect**: Generates a blurred ambient color background matching the photo's tone.
- **In-place rotation & flipping**: Rotate or mirror an image; changes are destructively saved directly to the file.
- **Image adjustments & Color filters**: Fine-tune brightness, contrast, and saturation, or apply Grayscale, Sepia, and Negative filters natively.
- **Integrated OCR**: Extract text directly from any image using ML Kit and Tesseract.
- **Augmented Reality translation**: Translated text blocks are drawn directly over their original positions on the image.
- **Send to Google Lens**: Quickly bridge any photo to the external Google Lens app.
- **Crop & Compressed copy**: Save cropped fragments or 70% compressed JPEG copies natively via the overflow menu.
- **Draw annotations**: Draw with brushes or shapes and save the merged annotations as a new file.
- **Immersive Draw and Crop**: System bars hide automatically during editing for a full-screen workspace.

## 6. GIF Viewer

- **Speed adjustment**: Dynamically slow down (0.25×) or speed up (4×) GIF playback; speed is saved per file.
- **Frame extraction**: Extract the first frame as a static image, or explode the entire GIF into individual static images.

## 7. Video Player

- **Playback position save & restore**: Exact playback position is saved per file and resumed on the next open. Clears when watched to the end.
- **Playback order modes**: Cycle between Loop List, Play Through, Shuffle, and Repeat One.
- **Resume Next Time**: Auto-navigates through network/cloud resources on cold start to restore the last active video.
- **Picture-in-Picture (PiP)**: Video shrinks into a floating window on Android 12+.
- **Configurable touch zones**: Define screen tap regions for next, prev, play/pause, and seek commands.
- **Video Control dialog**: Unified dialog for volume, audio track, subtitles, HUE rotation, GPU brightness, and playback speed.
- **Sleep timer**: Automatically halts playback after 15 to 120 minutes.
- **Save Frame**: Capture the current video frame as a PNG and save to a configurable destination.
- **3D stereo detection**: Auto-detects SBS/OU stereoscopic video. Offers a "Panel single-eye 3D view" for 2D screens.
- **360° metadata detection**: Recognizes Google Spatial Media boxes in local MP4 files.
- **Adaptive pre-cache**: Speed tests network video and computes an optimal buffer. Offers to save a local copy when bandwidth is sufficient.
- **Remappable controls**: All hardware inputs are user-assignable through Settings.
- **Diagnostic overlays**: Optional VR HUD FPS or flat player FPS bubble.
- **Black Screen mode**: Collapses the screen to solid black while playback continues uninterrupted.
- **Network DVD VOB routing fix**: Correctly routes `.vob` files and handles stream errors clearly.

## 8. VR Edition

- **Dedicated VR flavor**: Features an OpenXR rendering layer for Meta Quest and Android XR headsets.
- **VR stereoscopic playback**: Full stereoscopic viewing of 3D video (SBS/OU, VR180, 360°) and 3D photos out of the box.
- **Per-eye VR renderer**: Captures timestamped stereoscopic SBS PNG frames directly to the device.
- **Dual-group format override dialog**: Exposes separate flat and spherical format override selectors.
- **Immersive mode toggle**: Switch between immersive OpenXR mode and the flat panel player seamlessly.
- **VR Immersive Controls & HUD**: Full operation using controllers, keyboards, or mice. Features a head-locked HUD with progress, buffer, and control indicators.
- **Cinema mode**: Displays standard 2D content on a virtual cinema screen within the immersive session.
- **VR hand tracking**: Switch to OpenXR hand-tracking input automatically when controllers are set aside (aim ray, pinch to click).
- **Multi-Window Mode**: Tear off Browse or Player into separate independent windows (Quest 3+ / DeX).
- **Passthrough Snapshot**: Capture a JPEG from front passthrough cameras (Quest 3).

## 9. Audio Player

- **Background playback**: Persistent foreground service with rich notification controls.
- **Playback order & exit behavior**: Remembered loop/shuffle states. Configure Back button behavior (ask, stop, or keep playing).
- **Intelligent album cover art**: Retrieves album art from ID3 tags or online search. Cached locally to minimize network use.
- **Lyrics search and display**: Synchronized lyrics in a full-screen overlay.
- **YouTube Music search**: Open YouTube Music with a pre-filled search query for the current track.
- **Cast to Chromecast**: Send audio, images, and local video to Cast-compatible devices directly from the player.
- **Visual backgrounds**: Cycle random photos from a selected resource, or use audio visualizers (Canvas bars, waves, spectrum analyzers).
- **Now Playing UI**: Persistent mini bar with a bottom sheet for queue management.

## 10. Slideshow

- **Time-based advancement**: Configurable transition interval (1s to 3600s), adjustable globally or per-resource.
- **Random sequence order**: Shuffle photos and GIFs randomly.
- **Integrated background music**: Assign a folder to play random background music continuously during the slideshow.
- **Play video/audio to end**: Wait until a playing media file finishes before advancing to the next file.

## 11. PDF Viewer

- **Reading modes**: Choose between horizontal page flip and continuous vertical scrolling.
- **Navigation panel**: Expandable side panel with visual page thumbnails.
- **Themes**: Normal, Night, and Sepia reading modes.
- **OCR + Translation**: Recognizes text and overlays translations directly on top of original words.
- **Read Aloud (TTS)**: Speak the current page through the system text-to-speech engine.
- **Print**: Send the current PDF, text file, or image to any printer via the Android Print dialog.

## 12. EPUB Viewer

- **Navigation**: Interactive table of contents panel and advanced full-book search.
- **Formatting**: Toggle between Default, Serif, or Monospace fonts. Adjust line height and horizontal margins.
- **Themes**: Light, Dark, Sepia, or OLED Black themes.
- **Position persistence**: Saves scroll location and last read chapter.
- **In-place translation & TTS**: Translate selected text natively or Read Aloud via TTS.

## 13. Text Viewer / Editor

- **Rendering**: Automatic charset detection, Markdown rendering, and native code syntax highlighting.
- **Reading features**: Line numbers display, reader themes, and gesture-based font sizing.
- **Editing**: In-place text editing with auto-save and undo history for local and remote files.
- **Inline search panel**: Tap-to-search works in both the internal file browser and standalone "Open with" mode.

## 14. Translation & OCR (cross-viewer feature)

- **ML Kit & Tesseract**: Extract text natively from images and PDFs.
- **Automatic Language Identification**: Discerns source language via ML Models before triggering translations.
- **On-device offline translation**: Download specific language models for offline processing.
- **Augmented Reality overlay**: Superimposes translated text into the precise coordinates of the original text.
- **Result typography styling**: Configure font size and family for OCR readouts.

## 15. Network Sources

- **SMB share picker**: Discover shares or enter share names manually.
- **SMB / FTP / SFTP**: Native protocols for browsing, managing, and streaming remote collections. SFTP directory reads are optimized into a single round-trip.
- **Network auto-discovery**: Scans the local subnet (ports 445, 21, 22) to discover NAS endpoints.
- **Built-in Speed test**: Measures read/write speeds to recommend the optimal thread count.
- **Configurable parallelism**: Divide copy jobs into up to 24 simultaneous synchronous connections.
- **SMB Connection pooling & auto-recovery**: Caches sessions to eliminate latency and transparently reopens dead sockets.
- **Periodic background sync**: WorkManager wakes periodically to update the local database with remote changes.
- **Blu-ray Transport Stream (.m2ts)**: Play BDMV files directly from network sources via a BD-TS packet adapter.

## 16. Cloud Integration

- **Google Drive, Dropbox, OneDrive**: Natively integrated folder picking, streaming, and remote file modifications.
- **Unified OAuth authentication**: Secure login via browser OAuth flows with locally encrypted tokens.
- **Rigorous state backups**: Serialize application settings, profiles, and favorites into a JSON payload backed up to Google Drive.

## 17. Favorites

- **Cross-source aggregation**: Compiles favorites from local, network, and cloud into a single list.
- **Interactive home screen widget**: A scrollable widget prioritizing immediate launch of favorite files.

## 18. Home Screen Widgets

- **Resource Launch shortcut**: Instantly open a specific folder or NAS drive.
- **Continue Reading beacon**: Resume your exact last session for documents or video with one press.
- **Random Music & Camera Photos**: Shortcuts to instantly launch shuffled playback or open the camera roll.
- **App Shortcuts**: Long-press the launcher icon for static and dynamic recent resource shortcuts.
- **Quick Settings Audio Tile**: Control background audio playback from the notification shade.

## 19. Settings

- **Onboarding walkthrough**: Language picker, touch-zone map, and dedicated multi-page explanation.
- **Friendly UI copy**: User-facing messages rewritten in a clear, friendly tone.
- **Granular Settings**:
  - **Media Types & Density**: Filter sizes and toggle compact UI elements.
  - **Playback & Destinations**: Configure global sort, slideshow timers, destinations, auto-advancement, and PiP.
  - **Safe Mode & Trash**: Mandate delete confirmations and configure Trash retention.
  - **Network / Cache**: Set sync intervals and allocate graphical memory pools (up to 16 GB).
- **Landscape-adaptive dialogs**: All dialogs are constrained and scrollable in landscape orientation.
- **Keyboard navigation**: Navigate settings entirely by keyboard, including an inline search overlay.
- **Diagnostic tools**: Unified error display with Save/Share actions for stack-trace logs.

## 20. Settings Search

- **Comprehensive full-text indexing**: Fast query search scanning across every settings entry.
- **Direct highlighting navigation**: Bypasses menus to jump directly to the target setting with on-screen highlighting.

## 21. Wear OS Companion App

- **Standalone access**: Browse SMB, FTP, SFTP, and Cloud using your smartwatch independently.
- **Media capabilities**: View comprehensive media lists, run automated image slideshows, and command video/audio playback natively.
- **Two-way sync**: Push network sources and favorites between phone and watch via Wearable Data Layer.

## 22. Background & System Services

- **Automated maintenance**: Scheduled Trash cleanup, orphan temp file purging, and unused credential revocation.
- **MediaStore sync**: Pings central Android endpoints after operations to keep third-party galleries accurate.
- **Database persistence & Caches**: Progress DB for media, scalable Thumbnail caches, and File metadata caches to discard repetitive network reads.
- **Default player system hooks**: Integrate with Android intent routing for media viewing and share-sheet intake.
- **Auto-download incoming links**: Fetches media directly from shared HTTP, HLS, or DASH URLs, utilizing in-app WebView auth for any host (Instagram, TikTok, and beyond).
- **Background link download queue**: After auth is resolved, the app immediately returns the user to the source app (Instagram, browser…) and downloads the file in the background. A progress notification is shown during download; a result notification confirms the saved file name or reports failure. For sign-in-required hosts a "Sign in" action in the result notification re-opens the auth flow.
- Instagram and Threads links: downloads the real video or carousel images instead of the preview thumbnail; if only a preview was found, offers sign-in (or re-sign-in) before saving anything. Threads links on `threads.com` are recognized alongside `threads.net`.
- When no sign-in exists for a host, the app offers to add one with three options: add now, skip this time, or never ask again for this site.
- Sites where the offer was permanently declined appear in the authorization settings list and can be re-enabled by deleting the entry.
- **Standalone player operations**: File actions (Rename, Delete, Favorite, Open in FMS) available directly when opening media from external apps. Includes robust audio focus management and UX parity.

## 23. Scheduled File Operations

- **Scheduled copy/move/delete**: Create background operations that run at set times and repeat intervals (e.g., nightly backups).
- **Multi-flag filters**: Filter processed files by category (Images, Audio, Video, Documents) and time window (last hour, last day).
- **Remote support**: Full support for Local, SMB, SFTP, FTP, and Cloud targets. Validates reachability before running.
- **Logging**: Per-file execution log with success/error status and UI error badges.

## 24. Apps FMS Can Replace — Competitor Comparison

FastMediaSorter consolidates functionality that typically requires multiple separate apps into a single tool:

### Photo Gallery (Replaces Google Photos, Simple Gallery, etc.)
Handles local photos plus full NAS/cloud access, scheduled automation, and cross-protocol file operations.

### Video Player (Replaces VLC, MX Player, Nova)
Combines ExoPlayer codec support, PiP, and gesture controls with direct cloud/network streaming and integrated file management.

### Audio / Music Player (Replaces Poweramp, Musicolet)
Offers background playback, sleep timer, and lyrics alongside the unique ability to stream directly from a NAS or cloud without syncing locally.

### File Manager (Replaces Solid Explorer, MiXplorer)
Provides 24-thread SMB transfers, cross-protocol management, and completely native media players (no opening external intents).

### NAS / Cloud Client (Replaces Cx File Explorer, Google Drive, Dropbox)
Unifies network protocols and major cloud providers with connection pooling, credentials audit, and direct cross-cloud file transfers.

### PDF / EPUB Reader (Replaces Adobe Reader, Moon+ Reader)
Reads documents directly from network or cloud storage, integrating reading themes with native AR-style text translation and OCR.

### Additional Replaced Tools
- **FTP/SFTP Clients**: Built-in alongside SMB.
- **OCR Tools (Google Lens)**: Embedded directly into document and image viewers.
- **Duplicate Finders**: Cross-source, byte-for-byte SHA-256 detection.
- **Slideshow Apps**: Pulls images and background music from any network or cloud source.
- **Android TV Media Centers (Kodi, Plex)**: Provides complete file management, media playback, and slideshows controllable entirely via remote/keyboard without a backend server.

**Bottom line:** FMS provides a consistent, unified experience with deep network and cloud integration that eliminates constant app-switching for users who manage media across local, NAS, and cloud storage.

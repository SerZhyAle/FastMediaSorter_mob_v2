# FastMediaSorter v2 — Complete Feature List

*Last updated: 2026-05-02*

This document is the canonical, up-to-date inventory of all user-facing features implemented in the application. It serves as a comprehensive guide to what the application can do, how each feature works, and why it is useful for the user.

**Platform requirements:** Android 8.0+ (API 26) for Standard, Lite, and Photos flavors. The Legacy flavor extends support down to Android 6.0+ (API 23) covering the same feature set but without cloud integrations.

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

- **Add multiple resource types**: Connect and manage various storage types including Local folders, SMB (Windows share/NAS), FTP, SFTP, Google Drive, Dropbox, and OneDrive. This unifies all your local, network, and cloud files into a single accessible interface.
- **Resource profiles (quick-setup presets)**: Effortlessly set up new folders using tailored presets like Audio Library, Video Library, Photo Storage, Documents, or All Files. These presets automatically apply optimal sorting, filtering, and display settings for the chosen media type.
- **Camera Photos Virtual Folder**: Instantly access and browse all photos and videos taken by your device's camera through a dedicated, automatically configured virtual folder.
- **Virtual resource language sync**: Virtual resources (All Images, All Videos, All Music, etc.) are automatically renamed when the app language changes, provided their name has not been manually edited.
- **Per-resource settings**: Customize how each individual folder behaves with options like supported media types, default sort mode, display mode, thumbnail loading, PIN access, and whether subdirectories are scanned. This allows fine-grained control over how different types of content are presented.
- **Resource ordering**: Rearrange your connected resources on the main screen using a simple drag-and-drop gesture. This lets you position your most frequently accessed folders at the top for quicker access.
- **Themed resource icons**: Every resource is displayed with a themed icon (music note, film reel, image, document, etc.) automatically chosen by type and connection source, with a connection badge in the corner. You can pick a specific icon per resource via the toolbar selector when creating or editing a resource.
- **Connection test**: Instantly verify if a network or cloud resource is accessible before trying to open it. This helps diagnose connectivity or credential issues on the spot without waiting for timeouts.
- **Read-only mode**: Protect critical folders from accidental modifications by enabling read-only mode for specific resources. When active, all file editing, deleting, and moving operations are completely disabled.
- **PIN protection**: Add an extra layer of privacy by requiring a PIN code whenever a specific resource is opened. This keeps sensitive photos or confidential documents safe from prying eyes.
- **Network credential management**: Securely store passwords and keys for your network shares in an encrypted vault. The built-in credential audit tracks when a login was last used, helping you eventually clean up obsolete credentials.
- **Last browse position save & restore**: Never lose your place when switching between folders or closing the app. The system automatically saves and restores your exact scroll position and the last viewed file for every resource.
- **File list caching**: Experience near-instant load times when reopening large network folders. The app persists the file index in a local database to bypass slow network fetching on subsequent visits.

## 2. Media Browsing

- **Extensive sort modes**: Organize files exactly how you need them using sorting options like Name, Date, Size, Type, Artist, Title, Duration, Date Taken, Random, or Manual order. Every sort mode supports ascending and descending directions.
- **Sort mode memory**: The sort mode chosen in Browse or Slideshow is automatically saved per resource and restored on the next visit — no manual reconfiguration needed.
- **Advanced filter panel**: Narrow down massive folders by filtering items using a filename substring, date ranges, size limits, or specific media types. This is essential for quickly locating a particular file in a cluttered directory.
- **Random file jump in player**: Audio Library and Photo Storage profiles show a dedicated dice button in the player command bar, letting you jump to a random file from the current library without enabling a full shuffle mode.
- **Multi-select**: Select continuous ranges or individual files to batch copy, move, delete, or share them simultaneously.
- **Show subfolders as items**: Treat subdirectories as clickable entries mixed directly into your file list. Unchecking this option flattens the view, which is useful when you only care about the media files inside.
- **Subfolder operations**: Select, copy, move, rename, and delete subfolders directly in Browse when 'Show subfolders as items' is enabled; works for local, network (SMB/SFTP/FTP), and cloud resources.
- **Create subfolder from Browse menu**: Create a new subfolder directly from the Browse resource menu (local, network, cloud); available when "Show subfolders as items" is enabled and the resource is writable. The new folder appears in the list immediately without navigating into it.
- **Show hidden files**: Toggle the visibility of system-hidden files and folders (those starting with a dot) depending on whether you need a clean view or administrative access to all data.
- **Show all files mode**: Temporarily bypass all media-type filters to view every single file in a directory. This ensures you can manage and organize binary files or archives alongside your media.
- **Recursive directory scan**: Automatically delve into all underlying subfolders and compile their contents into a single unified list. This allows you to view all files within a complex directory tree simultaneously.
- **Intelligent thumbnail loading**: Enjoy rich visual previews for photos and videos. To maintain performance, thumbnail generation can be manually disabled per resource, which is recommended for extremely large directories.
- **Video thumbnails**: Identify video files quickly by previewing a generated thumbnail of their first frame. This feature can be disabled for network folders to heavily conserve bandwidth and improve loading times.
- **File metadata overlay**: See crucial file details at a glance without opening the properties dialog. Information like EXIF data, video duration, image resolution, and file size is overlaid directly on the list items.
- **Cancellable scan with progress**: Large folder scans show a non-intrusive progress indicator after 5 seconds with a STOP button to cancel long-running operations.
- **Inline audio mini-player**: Start playing music tracks seamlessly directly from the file browser. This avoids disrupting your navigation flow and lets you preview audio files instantly.
- **Full keyboard navigation on all screens**: Every screen — file browser, player, standalone viewer, settings, all dialogs, add-resource form, cloud-picker flows, resource editor, receive-share, and widget configurator — responds to hardware keyboard input. **Enter** activates the focused item, **Escape** exits or dismisses, **Arrow keys** move focus, **Backspace** navigates one folder up in Browse and cloud pickers. Enables a desktop-like workflow on tablets, Android TV, and devices with Bluetooth keyboards.
- **NC-style file-management shortcuts and TV color keys**: On Browse and cloud-picker surfaces, keyboard shortcuts follow the classic Norton Commander layout: **F5** copies, **F6** moves, **F7** creates a subfolder, **F8** deletes. Android TV remote color keys map the same four operations: Red = Delete, Green = Copy, Yellow = Move, Blue = Rename. **Ctrl+F** opens document-search in text/PDF/EPUB viewers; **Ctrl+S** saves edits in the resource editor.
- **F1 help dialog and visible keyboard focus**: Press **F1** on any hardware keyboard to open a surface-specific shortcut reference with a link to the online docs — every screen has its own tailored hint list. The active item also receives a visible focus ring during keyboard navigation.
- **Gamepad support (Xbox / DualSense / 8BitDo)**: A standard Bluetooth or USB gamepad drives the file browser, both standard and VR players. **D-pad / left stick** move focus, **A** opens or confirms, **B** goes back or exits the player, **X** skips to the next file (or toggles multi-select in the browser), **Y** opens the previous file or the context menu, **L1/R1** seek ±10s or switch views, **Start** toggles the HUD / opens search, **Select** toggles on-screen hints. Analog seek on the right stick scales by deflection; dead-zone and rate-limiting keep volume and seek smooth.
- **Manual drag-to-reorder**: In Manual sort mode, drag files using the handle that appears next to each item to set a custom display order. The order is persisted per directory and automatically restored on the next visit.
- **Extended file-info dialog**: The file-info dialog now shows full audio metadata (artist, album, title, year, sample rate, bit depth, channels, lossless marker, ReplayGain, embedded cover art) for FLAC/MP3/M4A/OGG over local, SFTP, SMB, FTP, and SAF — read by streaming only the first ~64 KB of the file, without downloading it in full. The file-information block lays out network paths into host, share, directory, and filename rows, with extension + MIME, a separate last-modified line, and a Copy-path button.

## 3. File Operations

- **Flexible deleting**: Choose between permanently erasing a file or moving it to a recoverable Trash bin. This safety net prevents accidental data loss and can be customized in settings.
- **Standalone player renaming**: Rename a file directly from the "Open with" standalone player (supported for SAF documents with write access and MediaStore files). A simple dialog pre-fills the current filename; changes are applied via DocumentsContract or ContentResolver without reloading the content already in memory.
- **Trash recovery**: Instantly restore erroneously deleted files back to their original folder from the in-app Trash bin, providing peace of mind during massive cleanups.
- **Operation undo**: Revert your last copy, move, or delete action with a single tap. This undo stack acts as an immediate failsafe if you realize you made a mistake managing your files.
- **Safe Mode**: Prevent disastrous accidents by enabling mandatory confirmation dialogs before moving or deleting files. This global master toggle gives you tight control over sensitive file operations.
- **Overwrite policies**: Specify on a per-direction basis how the app should handle copying or moving files that already exist in the destination. This helps automate conflict resolution without stalling your progress.
- **Cross-protocol transfers**: Seamlessly copy or move files between entirely different connection types. Directly transfer data between Local, SMB, FTP, and SFTP endpoints effortlessly, using the app as a robust intermediary.
- **Duplicate file detection**: Locate and permanently remove identical files scattered across your massive local, network, or cloud storage. The highly optimized 3-phase scanning engine (Size -> Partial Hash -> Full SHA-256) guarantees perfect byte-for-byte matches while minimizing slow network reads. Two menu modes available: **Find Duplicates** — scan and review results, then manually select files for deletion via the FAB; **Find and Delete Duplicates** — scan and automatically delete all detected duplicates (keeping the oldest copy per group) without additional confirmation. Both modes pre-select the current resource and move it to the top of the resource picker.
- **ZIP archiving**: Pack any number of selected local files into a single ZIP archive directly from the file browser. Choose a custom name and destination folder; the archive is created asynchronously in background with a real-time file-by-file progress indicator. Duplicate archive names are resolved automatically (e.g., `archive_1.zip`).
- **ZIP extraction on click**: Tap a ZIP archive in Browse to extract it in one flow: confirmation dialog, real-time percentage progress, and a one-tap action to open the extracted folder. Extraction supports local and SD-card SAF resources, applies secure path validation, and auto-resolves destination folder conflicts via `_1.._99` suffixes.
- **Select folder for copy/move**: Tap the "Select folder" button in the copy/move dialog to pick any local directory with the system folder picker, bypassing the pre-configured destination list. The last chosen folder is remembered per resource type. Per-item copy/move buttons are always shown regardless of whether destinations are configured.
- **Camera capture**: Tap the camera icon in the Browse command bar to take a new photo or video with the device's default camera app. The captured file is saved directly to the current resource root (local, SMB, SFTP, FTP, or Cloud) or to the standard DCIM/Camera folder when browsing the "All Videos", "All Photos", or "Camera Photos" virtual collections. After capture a filename dialog lets you rename the file before saving; this dialog can be skipped via Settings → Behaviour → "Don't ask for filename for captured photo and video". The entire command can be hidden globally via Settings → Behaviour → "Disable camera capture button". On devices without a compatible camera app the command is hidden automatically, and launch or save failures surface localized in-app error messages instead of silently failing. After saving the file list refreshes automatically and scrolls to the new entry.

## 4. Destination Management

- **Color-coded destination buttons**: Configure up to 10 distinct, color-coded shortcut buttons displayed directly inside the player. These buttons represent your favorite folders, drastically speeding up the organization process.
- **Auto-advance after copy/move**: Enable the option to automatically jump to the next file as soon as a copy or move operation completes. This creates an incredibly fast, uninterrupted workflow when sorting through a queue of media.
- **Collapsible command panel**: Keep your screen uncluttered by collapsing the copy/move destination panel when it's not needed. This maximizes viewing space while keeping routing tools just a tap away.
- **Quick Favorites toggle**: Immediately mark or unmark the currently viewed file as a Favorite directly from the player screen, allowing you to curate a collection without returning to the file browser.

## 5. Image Viewer

- **Crop to fullscreen**: Optimize standard photos for widescreen displays. This feature automatically fills the screen by cropping out black bars when the image and device orientations are matched.
- **Dynamic background effect**: Enjoy a highly immersive viewing experience. The viewer mathematically analyzes the current image to generate a beautifully blurred ambient color background that seamlessly matches the photo's tone.
- **In-place rotation**: Easily fix misoriented photos by rotating them clockwise or counter-clockwise. The rotation is permanently saved to the file without requiring a heavy external photo editor.
- **Instant flipping**: Horizontally or vertically mirror an image with a single tap. Just like rotation, these changes are destructively applied and saved straight to the original file.
- **Image adjustments**: Fine-tune the brightness, contrast, and saturation of your photos natively. These tweaks are saved directly, allowing minor touchups during your organizing sessions.
- **Color filters**: Quickly stylize images with simple filters like Grayscale, Sepia, or Negative. This provides quick visual variations without launching external apps.
- **Integrated OCR**: Effortlessly extract typed or handwritten text directly from any image. Powered by ML Kit and Tesseract, you can digitize text on photos, receipts, or screenshots instantly.
- **Augmented Reality translation**: Read signs, menus, or documents in foreign languages using a Google Lens-style overlay. Translated text blocks are synthetically drawn precisely over their original positions on the image.
- **Send to Google Lens**: Quickly bridge any photo to the external Google Lens app with a dedicated button for deeper AI analysis, shopping, or advanced visual search.

## 6. GIF Viewer

- **Speed adjustment**: Dynamically slow down (0.25×) or speed up (4×) GIF playback on the fly. This adjusted speed setting is uniquely saved per file for future viewings.
- **First frame extraction**: Easily convert a distracting animated GIF into a static image by extracting and saving just its very first frame.
- **Complete frame extraction**: Deconstruct complex GIFs by extracting every single frame and saving them all as individual static images, allowing you to inspect specific moments.

## 7. Video Player

- **Playback position save & restore**: Stop watching securely in the knowledge that your exact playback position is saved per file. You will perfectly resume from where you left off the next time you open the video.
- **Watched-to-end auto-clear**: When a file plays to its end, the saved position is cleared so the next open starts from zero. Resume from a saved position now applies only to files paused mid-playback.
- **Resume Next Time**: Return to your media instantly. Upon a cold app start, the app automatically navigates through network and cloud resources to restore your absolute last active video dynamically.
- **Picture-in-Picture (PiP)**: Multitask without pausing. On Android 12+ devices, the video automatically shrinks into a floating window when you press the home button, letting you use other apps.
- **Configurable touch zones**: Personalize your player controls. Define exactly which invisible tap regions of the screen trigger previous, next, play/pause, or skip/seek commands.
- **Video Control dialog**: Open a single bottom-bar `Control` button placed immediately after `Next track` to adjust volume, audio track, subtitles, HUE rotation, GPU brightness, and playback speed from one tabbed dialog with reset actions. The same dialog model now works in both the main player and the standalone `Open with` video player.
- **Sleep timer**: Safely fall asleep while watching videos. Set a timer (from 15 up to 120 minutes) with an onscreen countdown badge that automatically halts playback when time expires.
- **Save Frame**: Capture the current video frame as a PNG with a single tap on the overflow menu. The frame is saved to a configurable destination resource (local path); falls back to the Downloads folder if no destination is selected or the resource is unavailable. Works for any video source: local, SMB, SFTP, FTP, and cloud.
- **3D stereo detection**: Automatically detects Side-by-Side (SBS) and Over-Under (OU) stereoscopic video via metadata and heuristics. In the standard edition, detecting 3D content shows a prompt to install the dedicated VR edition for headset playback. Filename tokens recognised: `sbs`, `3dh`, `3dv`, `ou`, `tab`, `360`, `180`, `vr180`, `stereo`, `mono`, `cubemap` and variants. ..correct stereo-mode isolation between files (no bleed from previous file on navigation).
- **Panel single-eye 3D view**: When enabled, side-by-side and over-under stereo videos and full-screen photos are cropped to one eye and stretched to the full screen — comfortable to watch without 3D glasses. A short toast confirms the crop on the first stereo file each session. Default ON for non-VR builds, OFF for VR. Toggle in `Settings → Playback`. Cast output is not affected (original file is sent to the receiver).
- **360° MP4 spatial metadata detection**: Recognizes Google Spatial Media `st3d`/`sv3d` boxes in local MP4 files and prefers them over filename guesses, so equirect 360° mono/SBS/OU video is auto-detected correctly even when the file name is ambiguous.
- **Adaptive pre-cache playback strategy**: For network video (SMB, SFTP, FTP), the player runs a real-time speed test and computes an optimal pre-cache target. A non-intrusive pill overlay shows buffer progress (dark = viable, amber = marginal). When bandwidth is sufficient, the player offers to save a local copy via a "Save local copy" BottomSheet — download progress is shown inline and, on completion, the pill turns green. After playback, a one-tap prompt lets you keep or delete the local copy. Configurable in Settings → General: pre-cache size (Less/Auto/More), cleanup mode (Ask/Auto-delete/Auto-keep), and cache TTL (Off/1d/3d/7d/30d).
- **Remappable controls**: Every keyboard, mouse, gamepad and VR-controller binding for the player is user-assignable through `Settings → Controls & Keybindings`, with per-row / per-group / global reset to factory defaults.
- **VR HUD FPS counter**: A separate setting "Show VR FPS" in Video settings (only effective when VR is globally enabled) draws the current frame rate in the top-right corner of the immersive HUD overlay. Off by default; the toggle takes effect within about a second even inside an active immersive session — no restart needed. The value is averaged over a 500 ms window and freezes at the last valid reading during render-cycle stalls.
- **Diagnostic FPS counter overlay**: A separate setting `Settings → Video → Show FPS over player` enables a small bordered FPS bubble in the top-end corner of the flat 2D player while a video is actually playing. Independent from the existing VR-HUD-FPS setting and available on every flavor with a player; in VR-immersive mode the bubble is suppressed in favour of the existing immersive HUD counter.
- **Resilient poster-frame extraction**: VR180 / 7K videos and low-native-heap devices fall back to cached thumbnail or a localized "Thumbnail unavailable" placeholder; never an empty preview.
- **Black Screen mode**: A toolbar button (enabled in Settings › Behaviour) collapses the screen to solid black while playback continues uninterrupted — ideal for hands-free listening while driving. Volume keys and media controls (play/pause, next, previous) remain active; any other tap on the screen instantly restores the player UI. Assignable keyboard shortcut included.

## 8. VR Edition

- **Dedicated VR flavor**: FastMediaSorter VR is a dedicated `vr` product flavor for Meta Quest and Android XR headsets. It is the same core player as the standard edition, extended with an OpenXR rendering layer for headset playback.
- **VR stereoscopic playback**: The VR edition provides full stereoscopic viewing of both 3D video (SBS/OU) and supported 3D photos, including direct panel-to-immersive handoff for stereoscopic images. Its OpenXR layer factory routes 2D content to a cinema quad, flat SBS/OU content to projection layers, equirect 360°/VR180 content to `Equirect2KHR`, and cylindrical 180° content to `CylinderKHR`.
- **VR180 Fisheye and OU/TAB stereo**: The VR edition correctly renders VR180 fisheye-lens video (suffixes `3dh`, `180x180`) via an equidistant inverse-projection GLSL shader, and Over-Under/TAB stereo (suffixes `3dv`, `OU`, `TAB`) via vertical UV-split on a projection layer — both formats play back out of the box without manual format selection.
- **Explicit immersive fallback feedback**: If immersive startup fails or a file asks for an unsupported immersive path, the VR build now shows an explicit error before falling back instead of silently returning to the standard player flow.
- **Per-eye VR renderer and frame capture**: The VR renderer writes flat per-eye pixels into XR targets with correct SBS/OU/mono splits and an aspect-preserving cinema blit, leaving sphere/cylinder warping to the OpenXR compositor. Save Frame in VR captures a timestamped stereoscopic SBS PNG into `Pictures/FastMediaSorter_VR` and exposes an immediate Open action for the saved file. The shared fullscreen button now stays a safe no-op with a VR toast, while VR system-ui input toggles the headset control overlay instead of Android bars.
- **3D image handling across phone and headset**: SBS/OU image format is detected automatically from the filename; the phone build can show a left-eye crop preview and a `3D` tab in the playback dialog, while the VR flavor renders the same content per eye. Forced format override from VR settings is also applied to images.
- **Dual-group VR format override dialog**: In the VR edition, the `Control -> 3D` dialog shows flat and spherical format groups at the same time. The inactive family stays visible but disabled by default, a dedicated `Override format type` switch unlocks cross-family overrides, manual selections toast briefly, and remembered selections restore per file on reopen when `Remember file format` is enabled.
- **Split VR forced-format settings**: The VR settings screen exposes separate `Forced flat format` and `Forced spherical format` spinners. Legacy installs keep their old `vrForcedFormat` value through compatibility fallback, flat overrides never affect 360°/VR180 content, spherical overrides never affect flat cinema playback, and backup export/import preserves both fields.
- **Immersive mode toggle**: A dedicated button on the video player command bar (VR edition only) switches between immersive OpenXR mode and the flat panel player for any video — including ordinary 2D content. Tapping it re-opens the current file in the opposite mode while preserving playback position. In immersive mode the same button returns to the panel player; the left thumbstick click performs the same action.
- **VR Immersive Controls**: Touch Plus/Pro controllers, Bluetooth keyboard, and Bluetooth mouse are fully operable inside the immersive 3D session. Playback (pause, seek, volume, next/previous file, zoom, re-center), the settings dialog, and the full file operations set (copy, move, delete, rename, info) are accessible without removing the headset. A first-run cheatsheet auto-appears for 4 seconds; long-press Y on the left controller or press F1 on a BT keyboard to bring it back.
- **VR Immersive HUD**: Inside the immersive session a head-locked HUD pops up on every controller action — progress bar with current position, buffer and duration, plus indicators for pause, seek, volume, zoom, file change, recenter, immersive-mode toggle and repeat mode. The HUD is rendered through a dedicated OpenXR composition layer and auto-dismisses after a few seconds of idle. While the dedicated UI composition slot for full panels is not yet available, full file-operations and playback-control panels stay in panel-layout mode — exit immersive to open them; in immersive a short HUD banner explains where to find them and the player no longer pauses on the Y button when the panel would have been invisible.
- **VR HUD button affordance**: VR HUD buttons now display a rounded-rect background, making them visually distinct from text labels.
- **Cinema mode for 2D content in VR**: When navigating to a plain 2D video while inside an immersive session, the app automatically displays it on a virtual cinema screen (QUAD_CINEMA layer) without destroying the XR session or exiting to the standard player.
- **Auto-immersive toggle for stereo content**: The VR settings block exposes an `Auto-enter immersive on stereo content` switch (on by default). When off, opening a stereo file keeps the player on the flat screen — switch to VR via the existing 3DVR button on the player command bar.
- **VR hand tracking**: When controllers are set aside, the VR player automatically switches to OpenXR hand-tracking input (`XR_EXT_hand_tracking` + Meta aim/microgesture extensions). The dominant hand's aiming ray targets UI elements — pinch to click, double pinch to toggle play/pause, thumb swipes to seek or adjust volume. Controllers resume priority instantly when a button is pressed. A cursor dot and audio click feedback replace haptic feedback.
- **Interactive VR control panel**: In immersive VR mode, a full control panel with seek, volume, brightness, audio track selector and stereo-format indicator is available. Controlled by controller or hand ray — no need to exit VR.
- **«Apply and 3D» combo button + immersive prev/next + flat-player exit target**: The playback control dialog has an «Apply and 3D» button that closes the dialog and re-launches immersive in one click. The Exit-from-immersive command keeps your file: it opens the same file at the same position in the flat 2D player rather than dropping you into the file browser. Prev/next inside immersive switches files within the resource without leaving the headset.

## 9. Audio Player

- **Background playback**: Keep the music going even when you leave the app or lock the screen. A persistent foreground service with rich notification controls ensures uninterrupted listening.
- **Configurable exit behavior**: Choose what happens when you press Back while audio plays in background — ask every time (default), always stop, or always keep playing. Set once in Settings → Audio and the choice is remembered permanently.
- **Intelligent album cover art**: Enjoy a visually rich music player that automatically retrieves album art from embedded ID3 metadata tags, or falls back to an online search (with an optional Wi-Fi-only restriction to save mobile data).
- **Local audio metadata cache**: downloaded covers and track info saved to `audio_metadata_cache/` folder; reused on subsequent plays without network requests; included in cache size display and "Clear Cache" action
- **Lyrics search and display**: View synchronized lyrics in a full-screen, distraction-free overlay. The app conducts a smart online search to provide read-along text for your current song.
- **Search in YouTube Music**: Instantly open YouTube Music with a pre-filled search query for the current track. Available as a dedicated button in landscape mode and as a menu item in portrait mode. Uses cached artist/title metadata when available, falls back to the filename.
- **Cast to Chromecast**: Send images, GIFs, audio, and local video to any Chromecast or Google Cast-compatible device directly from the player overflow menu (requires Wi-Fi). Local files are served via an in-process HTTP proxy; network and cloud files are downloaded to the device cache first. Available on all flavors (Standard, Lite, Photos, Legacy).
- **Random photos during playback**: Turn your music into a visual journey by automatically cycling random photos from a selected internal or network resource as a dynamic visual background.
- **Rich empty state animations**: Prevent a boring black screen if no cover art is found. Choose from mesmerizing audio visualizers including Canvas bars, Canvas waves, AVD pulses, and spectrum analyzers that react to the music. Canvas Waves now re-rolls a full 360-degree flow direction on each fresh start, with particles biased to match the scene while staying lightweight for Android 8.1+ devices.
- **Vinyl record indicator**: Identify active playback instantly with a stylish, animated rotating vinyl record icon positioned in the corner of your screen.
- **Sleep timer**: Drift off to music peacefully. This shared sleep timer will automatically pause playback when the defined countdown finishes, ensuring your battery—and your sleep—aren't drained.
- **Resume Next Time**: Seamlessly pickup your listening session. Upon a cold start, the app restores your last active audio track and perfectly reconstructs your entire historical playlist and queue.
- **Now Playing UI**: While audio plays in the background, a persistent mini bar at the bottom of the player shows the current track title and play/pause button. Tapping it opens a full bottom sheet with album art, seek bar, prev/next controls, and a scrollable queue panel where you can tap any track to jump directly to it.
- **Black Screen mode**: A toolbar button (enabled in Settings › Behaviour) collapses the screen to solid black while playback continues uninterrupted — ideal for hands-free listening while driving. Volume keys and media controls (play/pause, next, previous) remain active; any other tap on the screen instantly restores the player UI. Assignable keyboard shortcut included.

## 10. Slideshow

- **Time-based advancement**: Automate your viewing experience by having images and GIFs advance on their own. The transition interval can be configured anywhere from a rapid 1 second to a lingering 3600 seconds.
- **Random sequence order**: Ensure a fresh experience every time by shuffling your photos and GIFs randomly rather than following strict alphabetical or date sorting.
- **Integrated background music**: Elevate your visual slideshows by assigning a dedicated folder to play random background music continuously while images transition.
- **Play video/audio to end**: Intelligently mix static photos with videos and audio tracks. Enabling this forces the slideshow to wait until a playing video or song finishes entirely before moving to the next file, overriding the strict timer.
- **Per-resource interval configuration**: Tailor slideshows to individual folders. Easily set a unique transition interval for a specific directory that safely overrides the app's global default settings.

## 11. PDF Viewer

- **Page mode (flip) and vertical scroll mode**: Choose the reading style that suits your content best. Flip through pages horizontally just like a physical book, or use continuous vertical scrolling for reports and articles.
- **Navigation panel with PDF page thumbnails**: Instantly jump to any specific section of a large document. The expandable side panel shows visual previews of all pages, allowing you to visually navigate long files.
- **Color modes (Normal, Night, Sepia)**: Reduce eye strain and adapt to your environment's lighting. Switch to Night mode in dark rooms or choose Sepia for a warmer, paper-like reading experience.
- **OCR + Translation (Google Lens style overlay)**: Read foreign-language documents without constantly switching to a dictionary app. The app recognizes the text on the page and neatly overlays the translation directly on top of the original words.
- **Text selection mode**: Tap the "T" button in the PDF controls bar to extract the current page's text (via OCR on most devices; natively on Android 15+) into a selectable overlay. Long-press any word to get selection handles, then choose **Translate** (sends only your selection to the translator), **Read Aloud** (speaks the selected fragment via TTS), or **Search in Google** from the floating action menu.
- **Read Aloud (TTS)**: Tap Read Aloud in the command panel to have the current PDF page spoken through the system text-to-speech engine. Works natively on Android 15+ (no OCR delay) and falls back to ML Kit OCR on earlier versions. TTS pauses automatically when you navigate to another page.
- **Large PDF thumbnail support for network files**: Identify your PDFs by their cover before fully downloading them from your remote server or cloud. This optional setting avoids unnecessary network usage when looking for a specific document.
- **Print**: Send the current PDF, text file, or image directly to any printer via the standard Android Print dialog. Available from the command bar (or overflow menu when space is limited) in both portrait and landscape orientations. Remote files are automatically cached locally before printing and the temporary file is deleted afterwards.

## 12. EPUB Viewer

- **Table of contents navigation panel**: Understand the structure of your book instantly. A fully interactive table of contents panel lets you jump to any specific chapter or section immediately.
- **Advanced search**: Locate specific character mentions, quotes, or keywords efficiently. The engine can return up to 500 results found both within your current chapter or across the entire book.
- **Font family selection**: Match the typeface to the genre of your book. Toggle effortlessly between standard Default, classic Serif, or typewriter-style Monospace fonts.
- **Reader themes**: Set the perfect reading ambiance with carefully designed Light, Dark, Sepia, or OLED Black themes. Choose the mode that best suits your environment and reduces eye strain.
- **Line height multiplier**: Control the breathing room of the text. Increase or decrease the line-height multiplier (from 1.0× to 3.0×) to make dense blocks of text much easier to digest.
- **Horizontal margins**: Give your text proper bordering. Adjust horizontal margins natively to bring text closer to the center, heavily improving readability on ultra-wide devices and tablets.
- **Position persistence**: The app precisely saves your exact scroll location and last read chapter, guaranteeing your book opens strictly where you abandoned it last time.
- **In-place translation**: Break language barriers natively. Select the text of a foreign EPUB chapter and have it translated seamlessly directly on your screen.
- **Selection action menu**: Long-press any word in an EPUB chapter to reveal the standard selection handles. The floating action menu includes **Translate** (sends the selected fragment to the translator), **Read Aloud** (speaks the selected fragment via TTS), and **Search in Google** alongside the platform's built-in Copy / Share / Select All items.
- **Read Aloud (TTS)**: Tap Read Aloud in the command panel to hear the entire current chapter spoken by the system text-to-speech engine. TTS pauses automatically when you move to another chapter.

## 13. Text Viewer / Editor

- **Automatic charset detection**: Plain text files, logs, and code decode correctly without garbled symbols regardless of source encoding.
- **Markdown rendering**: View documentation and readme files exactly how they were intended. The powerful Markwon library parses markdown syntax and beautifully renders headers, lists, and tables natively.
- **Syntax highlighting**: Analyze scripts and configuration files effortlessly. Native code highlighting colorizes distinct syntax elements, heavily improving code legibility directly from your phone.
- **Line numbers**: Identify specific points in tall scripts quickly by toggling the sequential line numbers display down the left side of the editor.
- **Reader themes**: Adjust the interface to your lighting. Choose from Light, Dark, Sepia, or System themes to drastically reduce eye fatigue when studying text files.
- **Gesture font sizing**: Intuitively resize text without entering menus. Simply execute a horizontal swipe gesture anywhere across the screen to rapidly scale font size up or down.
- **In-place text editing and saving**: Seamlessly alter text, fix typos, or rewrite code. Changes are saved back strictly to the source file, fully working on both local storage and remote network servers.
- **Auto-save and undo history**: Experiment with edits safely. The editor automatically saves states and features a deep Undo/Redo stack, ensuring a typo never destroys your work.
- **Full translation**: Convert whole text files or partial selections from their native language into your target language smoothly within the viewer interface.
- **Selection action menu**: Long-press any word to get selection handles. The floating action menu includes **Translate** (translates only the highlighted fragment) and **Search in Google**, in addition to the system's Copy / Share / Select All actions.
- **Inline search panel (standalone parity)**: Tap-to-search with Next/Prev navigation works in both the internal file browser and the standalone "Open with" mode for PDF, EPUB (current chapter), and TXT files.

## 14. Translation & OCR (cross-viewer feature)

- **ML Kit OCR (Latin script)**: Extract pure unselectable text natively from images and flattened PDFs using Google's rapid ML Kit framework, bypassing the need for typing out data manually.
- **Expanded Tesseract support**: When standard Latin characters aren't enough, fallback onto the heavy-duty Tesseract engine to pull text from a broader variety of scripts and challenging fonts.
- **Automatic Language Identification**: Skip configuring toggles manually. The app automatically discerns the source language of a document or image block via advanced ML Models before triggering translations.
- **On-device offline translation**: Translate passages without internet. Specific language models are downloaded on demand and process translations locally for extreme privacy and speed.
- **Augmented Reality overlay**: Experience sci-fi level immersion natively. Translated text string blocks are mathematically superimposed into the precise layout coordinates of the original foreign text within PDFs and images.
- **Broad availability**: Utilize this powerful translation tech seamlessly across all relevant views. The exact same translation interactions function uniformly across the Image Viewer, PDF Viewer, Text Editor, and the EPUB Viewer.
- **Standalone mode parity**: Translation toggle (portrait/landscape-aware) works in both the internal file browser and the standalone "Open with" mode — opening a PDF or EPUB from an external app gives the same translator access as opening it from within FastMediaSorter.
- **Explicit target configurations**: Override automated selections securely by forcing specific source and target languages within the settings menu when parsing heavily distorted or mixed-language texts.
- **Result typography styling**: Choose exactly how your OCR results and overlapping translation blocks look by configuring their native font size and dedicated font family.

## 15. Network Sources

- **SMB (Windows Share / NAS)**: Deeply interface with your local network storage safely utilizing standard SMB protocol, unlocking the ability to browse, manage, stream, and edit massive remote collections effortlessly.
- **FTP integration**: Access traditional web servers and legacy systems via rigid File Transfer Protocol, empowering complete and compliant browse and file management pipelines.
- **Secure SFTP**: Connect to highly protected servers leveraging SSH protocols. This guarantees every browse, copy, and streaming operation is cryptographically ciphered.
- **Network auto-discovery**: Bypass frustrating IP typing. The app dynamically scans your current Local Subnet specifically checking ports 445, 21, and 22, streaming discovered Network Attached Storage endpoints to your screen natively in real time.
- **Built-in Speed test**: Eliminate network guesswork definitively. This tool fires synthetic payloads across the connection to accurately measure read/write speeds, ultimately recommending the optimal parallel thread count to maximize data throughput safely.
- **Configurable parallelism**: Harness the full capabilities of wide bandwidth limits. Specifically override single-thread operations by dividing copy jobs into 1, 2, 4, 8, 12, or 24 simultaneous synchronous connections.
- **SMB Connection pooling**: Enhance network agility drastically. The app efficiently caches authenticated SMB sessions, completely eliminating the latency incurred during repeated queries or file copies.
- **Connection throttling**: Protect aging servers from crippling under heavy load. The network layer actively limits requests appropriately to prevent stalling or crashing congested or weak NAS hardware.
- **Periodic background sync**: Prevent navigating outdated file structures. Utilizing Android's WorkManager framework, the app wakes periodically (from 1 to 24 hours) strictly in the background to update the local database with remote changes.
- **Blu-ray Transport Stream (.m2ts) playback**: Blu-ray disc files in BDMV/STREAM format can be played directly from network sources (SFTP, SMB, FTP) via a transparent 192-byte BD-TS packet adapter. If the device cannot decode the stream, an informative dialog explains the reason and suggests transcoding with HandBrake or ffmpeg.

## 16. Cloud Integration

- **Google Drive access**: Break out of phone storage limits completely. Authenticate to utilize Google Drive, unlocking natively integrated folder picking, high-speed streaming, direct downloads, and rigorous file modifications remotely.
- **Dropbox connectivity**: Integrate seamlessly with your Dropbox vaults. Browse nested hierarchies elegantly, stream media live, and copy files directly between disparate locations.
- **OneDrive support**: Harness Microsoft’s ecosystem deeply. Leverage fluid streaming protocols, thorough directory exploration, and intensive file management right from the app.
- **Unified OAuth authentication**: Log in securely and officially within the app utilizing safe browser OAuth flows. Security tokens are rigidly encrypted and persisted locally, meaning you only ever have to log in exactly once.
- **Rigorous state backups**: Guard heavily against data loss. You can serialize your customized application settings, connection profiles, directories, and favorites into a strict JSON payload format tightly vaulted straight into your Google Drive.
- **Seamless cloud restoration**: Recover instantly upon installing on a new device. Connect to Google Drive specifically to download your backup JSON, magically reconstructing your settings, endpoints, and favorites effortlessly.

## 17. Favorites

- **Cross-source aggregation**: Files flagged as Favorites from any local, network, or cloud resource are compiled into a single dedicated list on the main screen — regardless of where they live.
- **Interactive home screen widget**: Bring crucial media directly to your home launcher. Deploy an actively scrollable widget that elegantly lists exclusively favorite files heavily prioritizing immediate launching.

## 18. Home Screen Widgets

- **Favorites interactive list**: Deploy a rapidly accessible panel right on your Android home screen. This scrollable widget exclusively displays your flagged favorite files, allowing you to bypass menus completely and launch media instantly.
- **Resource Launch shortcut**: Condense navigation strictly down to a single tap. Set an actionable widget uniquely mapped to a specific folder or NAS drive, triggering the application to instantly open that specific browser or player view.
- **Continue Reading beacon**: Resume your place profoundly immediately. This clever widget mathematically identifies your absolute last viewed document or video alongside its parent resource, rocketing you strictly back into your last session with one press.
- **Random Music shortcut**: Instantly start shuffled playback from your "All Music" virtual resource with a single home screen tap. The widget launches the audio player in shuffle mode with autoplay — no menus required. Available on flavors with audio support.
- **Camera Photos shortcut**: Open your camera roll directly in grid view from the home screen with one tap. The widget targets the built-in Camera Photos virtual resource and launches the browser in grid mode. Available on flavors with image support.
- **App Shortcuts (long-press)**: Long-press the app icon on your launcher to access static shortcuts (Favorites, Slideshow) and up to 3 dynamic shortcuts for your most recently browsed resources. Jump straight into any folder or NAS share without opening the main screen.
- **Quick Settings Audio Tile**: Control background audio playback directly from the Android notification shade. Add the "FMS Audio" tile to your Quick Settings panel to play, pause, or start shuffled music in a single tap — no need to unlock and navigate to the player. Available on flavors with audio support.

## 19. Settings

The Settings module provides deeply comprehensive control over nearly every facet of the application:

| Area | Key features explained |
|---|---|
| **General** | Configure the overall UX. Explicitly set your preferred UI Language regardless of system default. Trigger settings that keep the screen aggressively awake during app operation, toggle condensed mini-controls securely, or specify a default local network login parameter. |
| **Media Types** | Tailor visibility rigidly. Independently toggle on or off entire categories of files (Images, GIFs, Videos, Audio, Text, PDF, EPUB). Furthermore, enforce strict minimum and maximum file size filters dynamically applied per discrete category. |
| **Images** | Maximize photo fidelity. Force the engine to parse memory-intensive files in complete full resolution (enabling extreme infinite zoom capabilities) or command the view specifically to crop photos forcibly to fill the entirety of your widescreen display. |
| **Audio** | Modulate music behavior explicitly. Toggle querying online for absent album art, explicitly lock those queries specifically to Wi-Fi to shield mobile data, dictate random dynamic photo backgrounds during play, and configure background persistence services. |
| **Text / PDF / EPUB** | Configure optimal reading environments deeply. Activate strict code line numbers, alternate immersive reader themes, enable explicit syntax highlighting parsing or Markdown rendering, dictate precise PDF scrolling dynamics, or rigidly adjust spatial EPUB margins and line spacing variables. |
| **Translation** | Regulate the translation engine firmly. Toggle universal translation capabilities, lock down distinct static Source and Target languages overriding automation, activate physical Google Lens augmentation overlays, or modify literal typography sizing and family rules regarding OCR readouts. |
| **Playback** | Oversee the player environment rigidly. Dictate the global Default Sort schema. Strictly configure automated Slide-show advancing timers, assign concurrent background music tracks, dictate behavior when combined video/image slideshows run to completion, establish the visual size for thumbprint lists, configure picture-in-picture activation, or trigger player warmups. Configure system-association behavior through playback toggles that enable or disable primary media player mode (default open intents + hardware media key hook) and shared media intake from Android Share sheets. |
| **Destinations** | Command the file transfer pipelines heavily. Globally sanction or revoke explicit Copy or Move permissions entirely. Set stringent auto-overwrite policies regarding file conflicts, toggle auto-advancement routines upon transfer completion, explicitly configure up to exactly 10 accessible destination fast-buttons, or sanction the operational undo failsafe system. |
| **Safe Mode** | Govern application risk deeply. Utilize master toggles to forcibly institute strict manual confirmation dialog barriers intercepting every single requested Delete or Move transaction globally. |
| **Trash** | Configure file permanence explicitly. Toggle thoroughly regarding whether deleted objects dynamically route to a recoverable Trash holding zone, or mandate hard, immediate permanent file deletion. Configure prompt requests surrounding trash operations precisely. |
| **Network / Sync** | Dictate connectivity performance completely. Empower automated background WorkManager sync routines prioritizing fresh directory trees, lock the sync timing interval, and deeply dictate the optimal parallel synchronous thread count for maximizing data flow securely. |
| **Cache** | Control graphical memory strictness. Definitively allot specific storage minimums and maximums (from minor 512 MB up to massive 16 GB pools) expressly dedicated solely for caching Image and Video thumbnail outputs. |
| **Density** | Maximize information density across the app. Enable **Compact Elements** mode to reduce the size of thumbnails, fonts, and paddings in the Main resource list, Browse file list, and Player command panels (applying a ~0.8× scaling factor). Perfect for small screens or users who prefer a high-density "professional" interface layout. |
| **Backup** | Manage holistic state persistence securely. Produce strict JSON payloads encapsulating your precise application state ready for local export, facilitate JSON data importing, or automate Backup and Restoration operations utilizing rigorous Google Drive integration. |

- **Settings groups reorganized**: General tab now exposes separate **Permissions & Access** and **App Data & Backups** groups (previously bundled under "Files & Data"); Operations tab gains a dedicated **Safety & Confirmation** group (Safe Mode + Confirm Delete + Confirm Move, moved from General); the former "System" group is renamed **Network & Cache**, the Playback group "Sorting & Slideshow" becomes **Sorting, Slideshow & Playback**, and the duplicated "File Operations" headers are eliminated (Operations tab → "Copy & Move", Playback tab → "File Access in Player"). Error messages referencing the old "File Operations" group are updated accordingly.
- **Landscape-adaptive dialogs**: All dialogs across the app include dedicated landscape layout variants (`layout-land/`). In landscape orientation every dialog is constrained to 320 dp maximum height and made scrollable, with action buttons and close controls pinned at the top of content so they are always immediately reachable regardless of how far the user has scrolled. Applies to all product flavors (Standard, Lite, Photos, Legacy).
- **Keyboard navigation**: Navigate the settings layout entirely by keyboard — **Arrow keys** and **Tab** move focus between rows, **Enter** activates the focused toggle or row, **Ctrl+F** opens the built-in settings search overlay inline, and **Escape** closes it. The same keyboard delegate keeps navigation live inside search results.

## 20. Settings Search

- **Comprehensive full-text indexing**: Stop navigating dense nested configuration menus pointlessly. Swiftly execute a full-text query search that aggressively scans identically across every single settings entry, toggle, and section instantly.
- **Direct highlighting navigation**: Resolve settings adjustments securely and rapidly. Engaging a search result completely bypasses menus, rocketing you strictly to the accurate page while dynamically highlighting the specific sought parameter directly on screen.

## 21. Wear OS Companion App

- **SMB network access**: Liberate your wrist natively. Browse your massive home network storage drives and Windows Shares dynamically using strictly your Wear OS smart watch entirely independently.
- **Remote media list**: Discover your content swiftly specifically on a tiny screen. Accurately browse filtered, comprehensive lists of available media items directly on your smartwatch display.
- **Automated image slideshows**: Convert your watch into a vibrant digital frame. Trigger deeply integrated automated image cycling natively right on your wrist for ambient viewing.
- **Audio player integration**: Control your music library natively. Command the playback, pause, and skipping mechanism of your configured audio natively utilizing the watch interface.
- **Video player capabilities**: View media uniquely natively. Command and render playable video files explicitly directly over your smartwatch's display hardware.
- **One-tap network source sync**: Push all configured SMB/FTP/SFTP sources from your phone to your watch in a single tap via Wearable Data Layer. The watch displays an animated transfer screen and vibrates on completion with a detailed summary (added/updated counts).
- **On-watch source management**: Add SMB, FTP, and SFTP sources directly on the watch with a protocol-aware setup flow, and remove misconfigured sources from the watch list with a long press and confirmation dialog.
- **Polished on-watch controls and localization**: The watch companion now uses localized loading, empty-state, error, and retry labels, replaces emoji-only home shortcuts with native icons, and provides an on-watch slideshow interval stepper for quick tuning.
- **Reactive source list refresh**: Network sources on the watch now refresh automatically after sync, import, and delete operations, and the error screen retries in place instead of forcing navigation away.

## 22. Background & System Services

- **Automated Trash cleanup**: Maintain storage hygiene effortlessly without thinking. Scheduled WorkManager jobs execute periodically precisely identifying and permanently purging any trash files rigidly exceeding their configured retention timeframe.
- **Orphan temp file cleanup**: Recover dead storage seamlessly natively. Boot routines rigidly scan internal temporary repositories identifying and aggressively eliminating abandoned download artifacts and failed upload segments immediately.
- **Pending credential revocation**: Handle network security changes intelligently internally. The application's deferred processing system meticulously queues and permanently purges strictly expired network session instances, safeguarding system stability gracefully.
- **MediaStore sync**: Secure device-wide uniformity inherently. Successive to every file deletion, copy, or move operation, the system rigorously pings central Android MediaStore endpoints, heavily assuring system Gallery and similar third-party apps remain completely accurate visually.
- **Playback DB persistence**: Cement resume operations permanently locally. Database transactions record and rigorously cache definitive progress milestones regarding Audio, Video, and EPUB files strictly.
- **Thumbnail cache**: Rapidly reduce re-rendering friction aggressively. Pre-rendered Glide thumbnails permanently route into customized scalable hardware pools sized mathematically up to 16 Gigabytes, ensuring subsequent loads function instantaneously securely.
- **File metadata cache**: Discard repetitive read iterations natively. Crucial embedded file intelligence, involving heavy EXIF image data or layered ID3 tags, perfectly replicate securely into rapid flat databases, systematically sidestepping excruciating redundant read requests on repeated viewing entirely.
- **Credential audit**: Manage network keys securely definitively. The audit mechanism logs precise timestamp metrics corresponding to remote login validations, automatically drawing specific attention regarding completely stagnant, unused password profiles ready for strict manual deletion explicitly.
- **Default player system hooks**: Integrate with Android intent routing when user-enabled. Runtime component toggles control ACTION_VIEW aliases (audio/video/image/document groups), ACTION_SEND aliases for share-sheet intake, and media-button wake-up wiring through the audio playback service path.
- **Auto-download incoming links (S0003)**: When a plain http(s) URL arrives via the system Share sheet, the app fetches the referenced file (direct media or HTML-embedded media candidate ≥ 1 MiB) into the destination resource you pick — or to system Downloads when no resource is selected or it is unavailable. A second toggle controls whether the saved file opens automatically in the built-in player. Whitelisted MIME types only; non-`http(s)` redirects are blocked. Master toggle and both child controls live under Settings → Share/Receive → Behaviour.
- **Standalone player file operations**: Perform file actions directly when opening media from external apps via "Open with" or Share. Delete the file (with confirmation), share it to other apps, toggle favorite status, or navigate to its folder in the main FMS browser — all from the standalone player toolbar.
- **Rename file from standalone player**: Rename files directly from the standalone "Open with" player, including audio files (MP3, FLAC, OGG, WAV, M4A). Button is shown only for SAF documents with `FLAG_SUPPORTS_RENAME` and MediaStore files with write access. For audio, playback continues uninterrupted after rename — the ExoPlayer media source is hot-swapped to the new URI without restarting the service.
- **Standalone player lifecycle & reliability**: Playback pauses automatically when leaving the app and resumes on return; screen stays awake during video and audio playback; playback errors (codec not supported, file not found, network failure) show a clear localized message instead of silently stopping.
- **Standalone player Picture-in-Picture (Android 12+)**: Press Home while watching a video to keep it in a floating overlay window with play/pause remote actions. Video continues playing without interruption. PiP button visible in player controls on supported devices.
- **Standalone player audio focus management**: Video and audio automatically pause on incoming phone calls, voice assistant activation, and other transient interruptions; permanently stops on call answer. Audio focus is released on exit so other apps (e.g. Spotify) resume immediately.
- **Standalone player video UX parity**: Command buttons (close, share, delete, open-in-FMS) are fully visible in both portrait and landscape on all devices — padded away from status bar and navigation bar. Screen rotates with physical device sensor even when OS auto-rotate is off.
- **Standalone player video controls**: The standalone `Open with` video player now mirrors the main player with the same bottom-bar `Control` dialog, the same HUE + GPU brightness pipeline, the same reset actions, and the same touch gestures. This parity works in both portrait and landscape because both orientations use the same custom ExoPlayer controller layout.

## 23. Scheduled File Operations

- **Scheduled copy / move / delete**: Automate recurring file management tasks by creating scheduled operations that run in the background at a set time and repeat on a chosen interval (minimum 15 minutes). Ideal for automatically moving camera photos to a NAS every night or clearing a downloads folder daily.
- **Multi-flag file-type filter**: Choose any combination of file categories — All files (including non-media), Images, Audio, Video, and Documents — for each scheduled operation. Selecting "All files" processes every file in the resource (respecting the scan-subdirectories setting), while media flags filter by extension. Multiple categories can be active simultaneously.
- **Time window filter**: Further restrict which files are processed: all files, only files created since the last run, files from the last hour, or files from the last day. This prevents reprocessing files that were already handled.
- **Remote and cloud destinations**: Scheduled operations fully support all resource types — local folders, SMB/NAS, FTP, SFTP, Google Drive, OneDrive, and Dropbox — as both source and destination. Target reachability is verified before any file is touched; if the destination is offline the run is logged as an error and retried on the next schedule cycle.
- **Safe atomic MOVE**: Moving files is performed per-file: the source file is copied first, the copy is verified, and only then the original is deleted. A failed copy never removes the source.
- **Enable / disable per operation**: Each scheduled job has its own on/off toggle in the Operations settings tab. Disabling a job cancels its pending WorkManager task without deleting the configuration.
- **Run now**: Trigger any scheduled operation immediately from the settings table without waiting for its next scheduled time.
- **Operations log**: Every run writes a per-file log entry to a persistent on-device log file (rotated at 1 MB). Entries include timestamp, operation type, source, destination, file name, and success or error status. The log is accessible from the Operations settings screen and can be cleared at any time.
- **Error badge**: If the last run of an operation produced any error, a warning badge (⚠) is shown next to that operation in the table, prompting review of the log.
- **Silent mode**: Optionally suppress all notifications for a scheduled operation so background tasks do not disturb the user.
- **Boot persistence**: Scheduled operations are automatically rescheduled after a device reboot so your automation survives restarts without manual intervention.
- **Battery optimization prompt**: On first use, the app offers to disable battery optimization for itself, ensuring operations run reliably on OEM devices (Xiaomi, Huawei, Samsung) that aggressively kill background processes. The same setting is accessible at any time from the General settings screen.

## 24. Apps FMS Can Replace — Competitor Comparison

FastMediaSorter consolidates functionality that typically requires 5–10 separate apps into a single, unified tool. Below is a category-by-category breakdown showing which popular apps FMS can replace and what advantages it offers over each.

### Photo Gallery

**Popular apps:** Google Photos, Samsung Gallery, Simple Gallery Pro, F-Stop Gallery, Piktures, QuickPic, 1Gallery

| Capability | Typical gallery app | FMS |
|---|---|---|
| Browse & view photos | Yes | Yes |
| Grid / list display with thumbnails | Yes | Yes |
| EXIF metadata display | Some | Yes |
| Slideshow | Basic (local only) | Advanced (any source + background music) |
| NAS / SMB browsing | No | Yes (native, with connection pooling) |
| Cloud browsing (Drive, Dropbox, OneDrive) | Google Photos only (own cloud) | Yes (all three + NAS + FTP/SFTP) |
| File operations (copy/move/rename/delete) | Limited | Full cross-protocol |
| PIN protection per folder | Rare | Yes |
| Scheduled auto-transfer | No | Yes |

**Bottom line:** Gallery apps handle local photos well but ignore network storage entirely. FMS provides the same visual browsing experience plus full NAS/cloud access, scheduled automation, and cross-protocol file operations.

### Video Player

**Popular apps:** VLC for Android, MX Player, mpv-android, Nova Video Player, Just (Video) Player

| Capability | Typical video player | FMS |
|---|---|---|
| Codec support | Excellent (VLC/MX) | ExoPlayer (Media3) — broad format coverage |
| Background playback | Some | Yes |
| Subtitle support | Yes | Yes (with app font styling) |
| Gesture controls (brightness, volume, seek) | Yes (MX/VLC) | Yes |
| Picture-in-Picture | Some | Yes (Android 12+) |
| Sleep timer | Rare | Yes |
| Play from SMB/NAS | VLC: basic; MX: plugin | Native, with pooling + 24 threads |
| Play from SFTP/FTP | VLC: basic | Native |
| Play from Google Drive / Dropbox / OneDrive | No (must download) | Yes (direct streaming) |
| File management during playback | No | Yes (copy/move/delete/rename/share) |
| Duplicate finder | No | Yes (SHA-256) |
| Scheduled file operations | No | Yes |

**Bottom line:** VLC and MX Player are excellent standalone players, but they treat network files as a secondary feature and offer zero file management. FMS combines a capable player with full NAS/cloud integration and file operations — no more switching between a player and a file manager.

### Audio / Music Player

**Popular apps:** Poweramp, Musicolet, BlackPlayer, Pulsar Music Player, Vanilla Music, Oto Music

| Capability | Typical music player | FMS |
|---|---|---|
| Local playback | Yes | Yes |
| Background playback | Yes | Yes |
| Sleep timer | Some | Yes |
| Lock screen / notification controls | Yes | Yes |
| Play from SMB/NAS | No (must copy locally first) | Yes (direct streaming) |
| Play from SFTP/FTP | No | Yes |
| Play from cloud (Drive/Dropbox/OneDrive) | No | Yes |
| Inline mini-player in file browser | No (separate app) | Yes |
| File copy/move while browsing | No | Yes (cross-protocol) |
| Equalizer | Yes (Poweramp) | Relies on system EQ |
| Tag editor | Some | No |

**Bottom line:** Dedicated music players offer richer audio features (EQ, gapless, tag editing), but none of them can stream directly from a NAS or cloud drive. If your music library lives on a home server or in cloud storage, FMS eliminates the need to sync files locally first.

### File Manager (with network support)

**Popular apps:** Solid Explorer, MiXplorer, X-plore File Manager, Total Commander, ES File Explorer, Material Files

| Capability | Typical file manager | FMS |
|---|---|---|
| Local file browsing | Yes | Yes |
| SMB / NAS support | Yes (Solid, MiX, X-plore) | Yes (SMBJ, connection pooling, 24 threads) |
| FTP / SFTP | Yes | Yes |
| Cloud (Drive/Dropbox/OneDrive) | Yes (as plugins or built-in) | Yes (built-in, no plugins) |
| Cross-protocol copy/move | Yes | Yes |
| Built-in video player | Basic (system intent) | Full ExoPlayer with gestures, PiP, subs |
| Built-in audio player | Basic or none | Full with background playback, sleep timer |
| Built-in image viewer | Basic | Full with OCR + AR translation overlay |
| Built-in PDF reader | No (opens external) | Yes (themes, translation, OCR) |
| Built-in EPUB reader | No (opens external) | Yes (themes, translation) |
| Duplicate finder (SHA-256) | Rare (X-plore: CRC) | Yes (cross-source, byte-level) |
| Scheduled file operations | No | Yes (cron-style, background) |
| Wear OS companion | No | Yes |
| PIN per resource | Solid: yes; others: no | Yes |

**Bottom line:** Power-user file managers like Solid Explorer and MiXplorer are the closest competitors. They handle file operations and network well, but delegate all media playback and document reading to external apps. FMS keeps everything in-house — you never leave the app to play a video, read a PDF, or view a photo.

### NAS / SMB Dedicated Client

**Popular apps:** Cx File Explorer, FE File Explorer, File Manager +, Owlfiles, nPlayer

| Capability | Typical NAS client | FMS |
|---|---|---|
| SMB browsing | Yes | Yes |
| FTP / SFTP | Some | Yes |
| Cloud integration | Some (FE, Cx) | Yes (Drive, Dropbox, OneDrive) |
| Connection pooling | No | Yes |
| Multi-threaded transfer | No | Yes (up to 24 threads) |
| File list caching | No | Yes (Room DB) |
| Credential vault with audit | No | Yes (encrypted + last-used tracking) |
| Built-in media player | Basic | Full (ExoPlayer, background, PiP) |
| Scheduled automation | No | Yes |
| Wear OS | No | Yes |

**Bottom line:** Dedicated NAS clients provide basic browse-and-download functionality but lack high-performance transfer, credential management, and media playback. FMS is built NAS-first with connection pooling and parallel threads, making it noticeably faster on large transfers.

### Cloud Storage Client

**Popular apps:** Google Drive (official), Dropbox (official), OneDrive (official)

| Capability | Official cloud apps | FMS |
|---|---|---|
| Browse own cloud | Yes (one cloud per app) | Yes (all three in one interface) |
| Cross-cloud transfer | No (must download → re-upload) | Yes (direct, e.g., Drive → Dropbox) |
| NAS integration | No | Yes (cloud ↔ NAS transfers) |
| FTP/SFTP integration | No | Yes |
| Built-in media player | Basic preview | Full ExoPlayer |
| PDF/EPUB reader | Basic preview (Drive) | Full reader with themes + OCR |
| Scheduled sync/backup | Drive: limited auto-backup | Full cron-style operations |
| PIN protection | No | Yes (per resource) |

**Bottom line:** Each official cloud app only accesses its own service. FMS unifies Google Drive, Dropbox, and OneDrive into a single browsing interface and enables direct cross-cloud and cloud-to-NAS transfers without downloading files locally.

### PDF / EPUB Reader

**Popular apps:** Adobe Acrobat Reader, Moon+ Reader, ReadEra, Librera Reader, Google PDF Viewer

| Capability | Typical reader | FMS |
|---|---|---|
| PDF rendering | Yes | Yes |
| EPUB rendering | Moon+, ReadEra, Librera | Yes |
| Reading themes (light/dark/sepia) | Yes | Yes |
| Open from local storage | Yes | Yes |
| Open directly from NAS (SMB/SFTP/FTP) | No (must download first) | Yes (streams from source) |
| Open from cloud (Drive/Dropbox/OneDrive) | Limited (Drive only via some) | Yes (all clouds) |
| OCR overlay | No (separate app needed) | Yes (built-in, offline-capable) |
| AR translation overlay | No | Yes ("Google Lens"-style) |
| In-app text translation | Some (Moon+) | Yes |
| File management (copy/move/delete) | No | Yes |

**Bottom line:** Dedicated readers offer a polished reading experience but require files to be downloaded first. FMS reads PDFs and EPUBs directly from any network or cloud source and adds OCR + translation on top — no extra apps needed.

### FTP / SFTP Client

**Popular apps:** AndFTP, Admin Hands, Termius (file transfer), Turbo FTP

| Capability | Typical FTP client | FMS |
|---|---|---|
| FTP connection | Yes | Yes |
| SFTP connection | Yes | Yes |
| SMB / NAS | No (FTP/SFTP only) | Yes |
| Cloud storage | No | Yes (Drive, Dropbox, OneDrive) |
| Media playback | No (download → open externally) | Yes (stream directly) |
| Batch operations | Basic | Yes (cross-protocol) |
| Scheduled transfers | No | Yes (cron-style) |
| Credential vault | Basic | Encrypted + audit trail |

**Bottom line:** FTP/SFTP clients are single-purpose tools. FMS handles FTP and SFTP alongside SMB and three cloud providers, with inline media playback and scheduled automation on top.

### OCR & Translation

**Popular apps:** Google Lens, Google Translate (camera), Text Scanner (OCR), Microsoft Lens

| Capability | Typical OCR app | FMS |
|---|---|---|
| OCR from camera | Yes | No (file-based only) |
| OCR from image file | Google Lens: yes | Yes (built-in) |
| OCR from PDF | No (separate app) | Yes (directly in PDF viewer) |
| AR translation overlay | Google Lens: yes | Yes (same "Lens-style" UX) |
| Works on files from NAS / cloud | No (must download first) | Yes (processes in-place) |
| Offline OCR | Some | Yes (ML Kit on-device) |
| Integrated with file browser | No (standalone app) | Yes (accessible from any viewer) |

**Bottom line:** Google Lens is powerful but operates as a standalone camera app. FMS embeds the same OCR + AR translation concept directly into its image and PDF viewers, working on files from any source without downloading them first.

### Duplicate Finder

**Popular apps:** Files by Google, Duplicate Files Fixer, SD Maid, Duplicate File Remover

| Capability | Typical duplicate finder | FMS |
|---|---|---|
| Find duplicates on local storage | Yes | Yes |
| Find duplicates on NAS (SMB) | No | Yes |
| Find duplicates on FTP / SFTP | No | Yes |
| Find duplicates on cloud | No | Yes |
| Cross-source matching (e.g., local vs. NAS) | No | Yes |
| Matching algorithm | Usually file name or size | SHA-256 byte-for-byte |
| Integrated file operations (delete/move) | Some | Yes (full cross-protocol) |

**Bottom line:** Most duplicate finders only scan local storage and use weak matching (name/size). FMS uses SHA-256 checksums and can compare files across local, NAS, and cloud sources — finding true duplicates regardless of where they are stored.

### Slideshow / Digital Photo Frame

**Popular apps:** Fotoo, Photo Slides, Frameo, Simple Gallery (slideshow mode)

| Capability | Typical slideshow app | FMS |
|---|---|---|
| Local photo slideshow | Yes | Yes |
| Slideshow from NAS / SMB | Fotoo: yes (limited) | Yes (native, fast) |
| Slideshow from cloud | No | Yes (Drive, Dropbox, OneDrive) |
| Background music from any source | No (local only or none) | Yes (any connected source) |
| Configurable transitions & timing | Some | Yes |
| Double-duty as file manager | No | Yes |

**Bottom line:** FMS turns any Android device into a digital photo frame that pulls images from local storage, NAS, or cloud — with background music from any source. No need for a dedicated slideshow app.

---

### Summary: What FMS Replaces

| App category | Example apps you can uninstall | FMS equivalent |
|---|---|---|
| Photo gallery | Google Photos, Simple Gallery, F-Stop, Piktures | Media Browsing + Image Viewer + Slideshow |
| Video player | VLC, MX Player, mpv, Nova | Video Player (ExoPlayer, PiP, gestures) |
| Music player | Musicolet, Pulsar, BlackPlayer | Audio Player (background, sleep timer) |
| File manager | Solid Explorer, MiXplorer, X-plore | Full file ops + all protocols |
| NAS client | Cx File Explorer, FE File Explorer, Owlfiles | Native SMB/FTP/SFTP with pooling |
| Cloud client | Drive / Dropbox / OneDrive official apps | All three clouds unified |
| PDF reader | Adobe Reader, ReadEra | PDF Viewer + OCR + translation |
| EPUB reader | Moon+ Reader, Librera | EPUB Viewer + themes + translation |
| FTP client | AndFTP, Turbo FTP | FTP/SFTP built-in |
| OCR tool | Google Lens, Text Scanner | OCR + AR translation in viewers |
| Duplicate finder | Files by Google, Duplicate Files Fixer | SHA-256 cross-source finder |
| Slideshow / frame | Fotoo, Photo Slides | Slideshow + background music from any source |

**One app instead of twelve.** FMS does not aim to be the absolute best in every single category — a dedicated equalizer app will always have more audio DSP options, and a dedicated photo editor will always have more filters. But for users who manage media across local storage, NAS, and cloud, FMS eliminates the constant app-switching and provides a consistent, unified experience with a depth of integration no single-purpose app can match.

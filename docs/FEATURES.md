# FastMediaSorter v2 - Complete Feature List

*Last updated: 2026-05-30*

This document is the canonical inventory of all user-facing features implemented in the application. It serves as a guide to what the application can do and how each component works.

**Platform requirements:** Android 8.0+ (API 26) for Standard flavor. The Legacy flavor extends support down to Android 6.0+ (API 23) covering the same features without cloud integrations. Supported devices: phones, tablets, Android TV boxes, and Android head units. Runs on Chrome OS via Google Play (ARC++).

---

## 1. Sources & Storage
- **Multiple resource types** `[Standard / VR]`: Connect Local folders, SMB network folders (Windows share/NAS), FTP, SFTP, and cloud drives (Google Drive, Dropbox, OneDrive) in a single interface.
- **Resource profiles (quick presets)** `[Standard / VR]`: Configure new folders using presets (Audio Library, Video Library, Photo Storage, Documents, File Manager Mode) that automatically apply optimal sorting, filtering, and display settings.
- **Per-resource parameters** `[Standard / VR]`: Customize supported media types, sort mode, thumbnail loading, PIN access, and subfolder scanning individually for each folder.
- **Intelligent caching** `[Standard / VR]`: Persists file indexes and directory structures in a local database to bypass network fetching on subsequent visits to large network or cloud directories.
- **Connection test & Security** `[Standard / VR]`: Verify network or cloud resource accessibility before trying to open it. Protect folders with a PIN code, and support a "Read-only" mode to prevent accidental changes.
- **Browse position saving** `[Standard / VR]`: Saves and restores the exact scroll position and last viewed file for every resource, opening directly into subfolders on resume.

## 2. Media Browsing
- **Extensive sort modes** `[Standard / VR]`: Sort by Name, Date, Size, Type, Artist, Title, Duration, Date Taken, Random, or Manual order (ascending/descending) with memory per resource.
- **Filter panel & search** `[Standard / VR]`: Instantly filter items by filename substring, date ranges, size limits, or specific media types.
- **Hidden files & filter bypass** `[Standard / VR]`: Toggle visibility of system-hidden files, or activate File Manager Mode to bypass media filters and manage any file or archive (ZIP, APK, EXE, etc.).
- **Office document handoff** `[Standard / VR]`: Open DOC, DOCX, RTF, and ODT files in an installed external application, with full integration in media filters across local, network, and cloud resources.
- **Intelligent thumbnails** `[Standard / VR]`: Previews for photos and video frames. Video thumbnails automatically skip a black initial frame (checking offsets at 5 s, 15 s, and 30 s).
- **Random file selection** `[Standard / VR]`: Quickly jump to a random file inside a folder using a dedicated dice button without changing playback order.
- **Multi-window & DeX** `[Standard Only]`: Open folders or players in a separate parallel window on supported platforms (Chromebook, Samsung DeX).

## 3. File Operations
- **Cross-protocol transfers** `[Standard / VR]`: Copy or move files seamlessly between Local, SMB, FTP, SFTP, and Cloud endpoints in any combination.
- **Flexible deleting & Undo** `[Standard / VR]`: Move files to a recoverable Trash bin (recoverable for up to 5 minutes) or permanently delete them immediately with the option to undo the last operation.
- **Batch processing** `[Standard / VR]`: Multi-select items for batch copying, moving, deleting, or sharing.
- **Duplicate file finder** `[Standard / VR]`: Locate and remove identical files across all storage using a 3-phase scanning engine (Size -> Hash -> SHA-256) with review and auto-delete modes.
- **Archive management** `[Standard / VR]`: Pack selected files into a single background ZIP archive; extract archives into the current folder with percentage progress.
- **Camera & Mic capture** `[Standard / VR]`: Take photos or record audio directly into the current folder (local, network, or cloud).
- **Smooth progress indication** `[Standard / VR]`: Progress bar displays byte percentage, transfer speed, and estimated time remaining (ETA) for all operations.

## 4. Destination Management
- **Instant sorting panel** `[Standard / VR]`: Configure up to 10 distinct shortcut buttons inside the player representing favorite folders for quick moving or copying.
- **Auto-advance** `[Standard / VR]`: Automatically jump to the next media file in the queue as soon as a copy or move operation completes.
- **Quick Favorites toggle** `[Standard / VR]`: Mark or unmark the current file as a Favorite directly from the viewer, persisting it in a global cross-source list.

## 5. Image & GIF Viewer
- **Crop to fullscreen** `[Standard / VR]`: Fills the screen by cropping out black bars when image and device orientations match.
- **Dynamic background** `[Standard / VR]`: Generates a blurred ambient color background matching the photo's dominant color palette.
- **Quick operations** `[Standard / VR]`: Rotate, flip, and crop an image with changes saved destructively directly to the source file.
- **Color adjustments & Filters** `[Standard / VR]`: Fine-tune brightness, contrast, and saturation, or apply Grayscale, Sepia, and Negative filters natively.
- **GIF controls** `[Standard / VR]`: Adjust GIF playback speed from 0.25× to 4× with file-specific memory; save the first frame as static or explode the animation into individual static images.
- **Send to Telegram** `[Standard / VR]`: Instantly send files to an installed Telegram client from both the browser and the player (button appears automatically if Telegram is installed).

## 6. Drawing & Annotations Editor
- **Drawing tools** `[Standard / VR]`: Full-screen canvas with brush, oval, rectangle, eraser, and text annotation tools.
- **Palette & Sizes** `[Standard / VR]`: Choose from 16 colors with adjustable brush size, text size, and opacity.
- **Blank canvas creation** `[Standard / VR]`: Create a new empty `.jpg` drawing directly from the Browse toolbar in local, network, or cloud folders.
- **Export & integration** `[Standard / VR]`: Undo changes and export finished drawings directly to Google Keep or other external apps.

## 7. Video Player
- **Session save & restore** `[Standard / VR]`: Remembers the exact playback position and restores active network/cloud sessions upon cold start.
- **Playback modes** `[Standard / VR]`: Cycle between Loop List, Play Through, Shuffle, and Repeat One.
- **Picture-in-Picture (PiP)** `[Standard Only]`: Shrinks video into a floating window when exiting to the home screen (on Android 12+).
- **Touch zones & gestures** `[Standard Only]`: Configurable screen tap regions for next/prev, play/pause, volume adjustment, and seeking.
- **Unified Control dialog** `[Standard / VR]`: Manage audio track, internal subtitles, HUE rotation, GPU brightness, speed, and sleep timer (15-120 minutes) in a single dialog.
- **Video screenshots** `[Standard / VR]`: Capture the current video frame and save it as a PNG file.
- **3D & 360° support** `[Standard / VR]`: Recognizes 360° metadata in MP4. Detects Side-by-Side (SBS) and Over-Under (OU) video with a single-eye crop mode for 2D screens.
- **Big Buttons Mode** `[Standard Only]`: Scales control buttons to full screen width with 2× height for car head units or easy one-handed use.
- **Diagnostic overlays** `[Standard / VR]`: Integrated FPS overlay bubble and "Black Screen" mode for blind listening in dark environments.

## 8. VR Edition & OpenXR
- **Dedicated VR build** `[VR Only]`: Full OpenXR engine integration for Meta Quest and Android XR headsets (API 26+).
- **Immersive stereoscopic render** `[VR Only]`: Stereoscopic per-eye rendering for 3D video (SBS/OU, VR180, 360°) and 3D photos with manual format overrides.
- **Stereo screenshots** `[VR Only]`: Captures SBS PNG screenshots maintaining the exact perspective of both eyes.
- **VR Navigation & HUD** `[VR Only]`: Head-locked floating HUD menu with aiming rays, physical button mapping, and Quest Hand Tracking gestures.
- **Virtual cinema** `[VR Only]`: Play standard 2D video and slideshows on a massive, customizable virtual screen inside the VR scene.
- **Passthrough snapshot** `[VR Only]`: Capture mixed-reality photos using front-facing cameras (on Quest 3).
- **VR Entry point** `[VR Only]`: Instantly trigger the immersive OpenXR layer from the flat player using a floating `VR` action badge.

## 9. Audio Player
- **Background playback** `[Standard Only]`: Runs on a persistent foreground service with quick media notification drawer controls.
- **Smart album covers** `[Standard / VR]`: Extracts cover images from ID3 tags or retrieves them online with local caching.
- **Lyrics display** `[Standard / VR]`: Full-screen lyrics overlay with automatic online lyrics search.
- **MIDI playback** `[Standard / VR]`: Plays MID/MIDI files as regular audio tracks across local, network, and cloud storage.
- **YouTube Music integration** `[Standard / VR]`: Bridges current track details into a YouTube Music search in one tap.
- **Chromecast streaming** `[Standard Only]`: Cast audio, images, and local video to compatible Cast receivers directly.
- **Visualizations** `[Standard / VR]`: Render Canvas visualizers (waves, frequency bars, pulses) or play a slideshow of photos from a designated folder in the background.

## 10. Slideshow Mode
- **Custom intervals** `[Standard / VR]`: Adjust image switching interval from 1 second to 1 hour.
- **Background music** `[Standard / VR]`: Designate any folder to play random music continuously during the slideshow.
- **Smart gating** `[Standard / VR]`: Pauses slideshow advancement on video files, waiting until they finish playing.
- **Shuffle mode** `[Standard / VR]`: Shuffles images, GIFs, and videos randomly in the slideshow queue.

## 11. PDF & EPUB Reader
- **PDF reading features** `[Standard / VR]`: Horizontal page flipping or continuous vertical scrolling, thumbnail slider navigation, and reading themes (Light, Night, Sepia).
- **EPUB custom formatting** `[Standard / VR]`: Configure Serif, Sans, or Monospace fonts, adjust line spacing, margins, and custom themes (including OLED black).
- **Read Aloud (TTS)** `[Standard / VR]`: Synthesizes current pages or chapters through the system text-to-speech engine.
- **Print support** `[Standard Only]`: Print PDF documents, notes, and photos natively via the Android Print service.

## 12. Text Editor
- **Rendering & syntax** `[Standard / VR]`: Auto-detects charsets, renders Markdown, and highlights code syntax.
- **In-place editing** `[Standard / VR]`: Modify `.txt` and `.md` files with auto-save and undo history (Undo/Redo) for local and remote files.
- **Quick notes** `[Standard / VR]`: Create new text notes directly from the Browse toolbar with auto-saving and name conflict resolution.
- **Action panel** `[Standard / VR]`: Dedicated control buttons (Save, Close, Share, Send to Keep) with unsaved change highlights.
- **Font auto-fit** `[Standard / VR]`: Scales text size automatically to fit the screen, with swipe-based manual size lock.

## 13. Offline OCR & Translation
- **Local OCR engine** `[Standard / VR]`: Extract text from images and PDFs completely offline using ML Kit and Tesseract.
- **Language identification** `[Standard / VR]`: Automatically recognizes the source language before performing translation.
- **AR translation overlay** `[Standard / VR]`: Draws translated text precisely over original coordinates on images and PDFs.
- **Quality models on-demand** `[Standard / VR]`: Download improved Cyrillic/Ukrainian Tesseract models (`tessdata_best`) directly in Settings with SHA-256 validation.

## 14. Network & Cloud Integration
- **NAS auto-discovery** `[Standard / VR]`: Scans the local subnet (ports 445, 21, 22) for active SMB, FTP, and SFTP endpoints.
- **Smart folder import** `[Standard / VR]`: Directly pick discovered SMB folders from host scans when setting up resources.
- **Built-in Speed test** `[Standard / VR]`: Measures bandwidth to recommend optimal copy thread counts.
- **High-performance streaming** `[Standard / VR]`: Connection pooling, socket auto-recovery, and native BD-TS packet parsing for `.m2ts` files on the fly.
- **Google Drive, Dropbox, OneDrive** `[Standard Only]`: Secure OAuth authorization, direct streaming, remote editing, and backup of app settings to Google Drive in JSON.

## 15. Smart Widgets & Integration
- **Resource shortcuts** `[Standard Only]`: Single-tap home screen widgets to open a specific folder or NAS drive immediately.
- **Continue Reading beacon** `[Standard Only]`: Interactive widget to quickly resume your exact last document or video session.
- **Launcher shortcuts** `[Standard Only]`: Long-press app icon to access recent folder history.
- **Intent hooks** `[Standard / VR]`: Configures the app as a default media viewer and Share target.

## 16. Settings & Navigation
- **Settings search** `[Standard / VR]`: Full-text instant indexing search with yellow spotlight targeting of matched settings.
- **Complete DPAD support** `[Standard / VR]`: Scales all preferences for TV remotes, keyboards, mice, and gamepads with DPAD acceleration.
- **TV button remapping** `[Standard Only]`: Custom assignments for remote color buttons and Channel Up/Down actions.
- **Diagnostic logs** `[Standard / VR]`: Integrated stack-trace inspector with sharing actions for troubleshooting.
- **Wear OS Companion** `[Standard Only]`: Smartwatch application supporting independent NAS/Cloud browsing, photo viewing, and phone playback control.

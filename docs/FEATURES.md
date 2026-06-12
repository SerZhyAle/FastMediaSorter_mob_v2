# FastMediaSorter v2 - Complete Feature List

*Last updated: 2026-06-09*

This document is the canonical inventory of all user-facing features implemented in the application. It serves as a guide to what the application can do and how each component works.

**Platform requirements:** Android 8.0+ (API 26) for Standard flavor. The Legacy flavor extends support down to Android 6.0+ (API 23) covering the same features without cloud integrations. Supported devices: phones, tablets, Android TV boxes, and Android head units. Runs on Chrome OS via Google Play (ARC++).

---

## 0. Setup & Configuration
- **First-run device profile setup** `[Standard / VR]`: Select a device profile (phone, tablet, TV, head unit, media player, photo frame, VR headset, etc.) on first launch with auto-detection recommendations. The chosen profile applies optimized default settings (content types, layouts, security, screen behavior) tailored to the device's usage.
- **Onboarding functionality setup** `[Standard / VR]`: During first-run setup, turn on the capabilities you need (file manager, audio, video, documents, text recognition, translation) and download optional elements inline - each feature activates as soon as its download finishes, with no trip to Settings.

## 1. Sources & Storage
- **Multiple resource types** `[Standard / VR]`: Connect Local folders, network locations (SMB/NAS, FTP, SFTP), and cloud drives (Google Drive, Dropbox, OneDrive) in a unified interface.
- **Intelligent caching & sync** `[Standard / VR]`: Persists index databases to bypass slow network fetches on subsequent visits, with a built-in connection test and PIN-code protection for folders.

## 2. Media Browsing
- **File Manager Mode** `[Standard / VR]`: Toggle visibility of dot-hidden files or bypass media filters entirely to manage any file type or archive (ZIP, APK, EXE, etc.) across local and network resources.
- **Protected archive extraction** `[Standard / VR]`: Open password-protected ZIP archives - enter the password when prompted and the contents extract in place; an archive that uses an unsupported protection method is reported with a clear message instead of failing silently.
- **Office & PDF document handoff** `[Standard / VR]`: Filter and open DOC, DOCX, RTF, ODT, or protected PDF files in external apps or fallback viewers with integrated password indicators.
- **External file viewing** `[Standard / VR]`: When you open a file from another app, page through the neighbouring files in the same folder (next, previous, random, slideshow), and use *Open in FastMediaSorter* to jump straight into the in-app player on that file.
- **External image editing** `[Standard / VR]`: When you open an image from another app, edit it right there - crop, save a cropped copy, make a compressed copy, and toggle screen rotation - the same image actions as the in-app player.

## 3. File Operations
- **Cross-protocol transfers** `[Standard / VR]`: Copy or move files seamlessly between Local, SMB, FTP, SFTP, and Cloud endpoints in any combination with background progress and speed/ETA diagnostics.
- **Duplicate file finder** `[Standard / VR]`: Scans storage to locate and remove identical files using a 3-phase matching engine (Size -> Hash -> SHA-256) with auto-delete or manual review options.
- **Direct Camera & Voice capture** `[Standard / VR]`: Take photos, record voice notes, or capture videos with the system/in-app camera directly to local, network, or cloud folders.

## 4. Destination Management
- **Instant sorting panel** `[Standard / VR]`: Set up to 10 favorite target folders inside the player as shortcuts to copy or move the current file instantly with auto-advance to the next item.

## 5. Image & GIF Viewer
- **Crop & Color adjustment filters** `[Standard / VR]`: Destructively rotate, flip, and crop images directly on the source, or apply adjustments (brightness, contrast, saturation) and color filters (Sepia, Negative).
- **Animated GIF speed controls** `[Standard / VR]`: Fine-tune GIF playback speed (0.25x to 4x) and export individual frames as static images.
- **Send to Telegram** `[Standard / VR]`: Instantly share media files to a local Telegram client directly from the browser or player.

## 6. Drawing & Annotations Editor
- **Drawing & annotations canvas** `[Standard / VR]`: Create blank canvases or annotate photos using brushes, geometric shapes, and text layers, exporting results directly to Google Keep.

## 7. Video Player
- **Session save & restore** `[Standard / VR]`: Remembers exact playback coordinates and restores remote active network/cloud sessions upon app cold start.
- **Video screenshot capture** `[Standard / VR]`: Capture video frames in high quality and save them as PNG/JPG to any local or network resource.
- **Picture-in-Picture & D-pad focus** `[Standard Only]`: Runs videos in a floating window (Android 12+) and supports complete D-pad/remote focus navigation for TV boxes and head units.

## 8. VR Edition & OpenXR
- **Dedicated VR build & OpenXR engine** `[VR Only]`: Immersive stereoscopic rendering (SBS/OU, VR180, 360°), virtual cinema screen for flat files, head tracking HUD, and passthrough snapshot capture on Quest 3.

## 9. Audio Player
- **Background audio service & Casting** `[Standard Only]`: Runs playback on a persistent background service with notification drawer controls and direct Chromecast casting support.
- **Lyrics display & MIDI integration** `[Standard / VR]`: View lyrics, play MID/MIDI files on network resources, and bridge current tracks into YouTube Music searches in one tap.

## 10. Slideshow Mode
- **Slideshow with background music** `[Standard / VR]`: Rotate images/GIFs with custom intervals (1s to 1h), play background tracks from a selected resource, and auto-pause slides on video files.

## 11. PDF & EPUB Reader
- **E-book reader formatting** `[Standard / VR]`: View DRM-free EPUBs with customizable fonts, margins, line spacing, themes (including OLED black), and chapter maps.
- **Read Aloud (TTS) & Text selection** `[Standard / VR]`: Synthesizes page text via Text-to-Speech (TTS) and copy text fragments using long-press drag handles.

## 12. Text Editor
- **In-place Markdown editor** `[Standard / VR]`: Edit `.txt` and `.md` files directly on local or remote storage with live syntax highlighting, Markdown rendering, and auto-save.
- **Embedded text calculator** `[Standard / VR]`: Parse and solve math equations from selected reader/OCR/lyrics text blocks without writing results back.

## 13. Offline OCR & Translation
- **Offline OCR & Translation engine** `[Standard / VR]`: Extract text from images/PDFs and translate it completely offline using ML Kit and Tesseract with custom models.
- **Photo OCR capture flow** `[Standard / VR]`: In-app camera translation with area cropping, language selector on the crop screen, and editable results saved as `.txt`.
- **On-demand delivery** `[Standard / VR]`: OCR, translation, the FFmpeg DTS decoder and the audio-player background videos are off by default and installed on demand the first time you enable them; declining the download leaves the feature unavailable without affecting the rest of the app, and once installed they survive app updates and cache clears.

## 14. Network & Cloud Integration
- **NAS auto-discovery & speed test** `[Standard / VR]`: Scans subnets for SMB/FTP/SFTP, tests network speed to optimize copy thread counts, and supports high-performance streaming.
- **SFTP key auth & host-key pinning** `[Standard / VR]`: SFTP resources support SSH private-key authentication with optional passphrase, and host-key fingerprint pinning to detect server impersonation. Key auth is also available for predefined resources shipped in the bundled XML config.
- **Cloud OAuth storage** `[Standard Only]`: Authenticates Google Drive, Dropbox, and OneDrive with remote editing and settings backup.

## 15. Smart Widgets & Integration
- **Icon-style home widgets** `[Standard / VR]`: 1x1 quick launch buttons (Voice recorder, Quick capture, Camera OCR), resizable widgets (Scheduled tasks, Audio Now Playing, Photo Frame), and settings integration.

## 16. Settings & Navigation
- **Settings search & customization** `[Standard / VR]`: Full-text settings search with spotlight targeting, custom light/dark theme selection, and system diagnostic info.
- **Complete DPAD & TV remote remapping** `[Standard / VR]`: TV remote key assignments, DPAD acceleration, and Wear OS Companion app support.
- **Downloadable Extensions manager** `[Standard / VR]`: A settings screen listing every optional module (OCR engines, translation, audio visualizations, FFmpeg DTS decoder) and OCR language pack with its status, size, and download/remove actions to manage device storage.
- **Unified settings backup & restore** `[Standard / VR]`: Back up your sources, favorites, schedules, network passwords, and saved site sign-ins in one format — to a local file or Google Drive — and restore everything after a reinstall.

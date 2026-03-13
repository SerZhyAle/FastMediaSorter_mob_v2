# FastMediaSorter v2 — Complete Feature List

*Last updated: 2026-03-13*

This document is the canonical, up-to-date inventory of all user-facing features implemented in the application. Update this file whenever a new feature is added or an existing one is changed.

---

## 1. Resource / Source Management

- Add resources of types: **Local folder**, **SMB** (Windows share / NAS), **FTP**, **SFTP**, **Google Drive**, **Dropbox**, **OneDrive**
- Edit resource settings after creation
- Delete a resource
- **Resource profiles** (quick-setup presets): Audio Library, Video Library, Photo Storage, Documents, All Files
- **Per-resource settings**: supported media types, default sort mode, display mode, command panel visibility, thumbnail loading, PIN access lock, read-only flag, comment/note, scan subdirectories, remember file list, subfolder-as-items mode, show hidden files
- **Resource ordering** — manual drag-and-drop reorder on main screen
- **Filter resources** on main screen (search/filter bar)
- List / Grid view switch on the main screen
- **Connection test** for network/cloud resources
- **Read-only mode** — disables all file operations for a specific resource
- **PIN protection** per resource (access gate on open)
- Network **credential management** — encrypted storage; credential audit tracks last-used date and surfaces unused entries
- **Last browse position** save & restore (scroll position + last viewed file)
- **File list caching** — persist file list in DB for faster subsequent loads

---

## 2. Media Browsing

- **List** and **Grid** display modes
- **Sort modes**: Name ↑↓ / Date ↑↓ / Size ↑↓ / Type ↑↓ / Artist ↑↓ / Title ↑↓ / Duration ↑↓ / Date Taken ↑↓ / Random / Manual
- **Filter panel**: by filename substring, date range, size range, media type
- **Multi-select** for batch operations
- **Subfolder navigation** — browse into subfolders with a back-stack
- **Show subfolders as items** — separate clickable entries for subdirectories
- **Show hidden files** (files/folders starting with `.`)
- **Show all files** mode — bypass media-type filter, show everything including binary files
- **Recursive directory scan** of subdirectories
- Thumbnail loading (auto-disabled for > 10 000 files; can be disabled manually per resource)
- **Video thumbnails** extracted from first frame (optional; can be disabled for slow networks)
- **File metadata overlay** in browse list (EXIF data, duration, resolution, size)
- **Scan progress indicator** with cancellable STOP button (appears after 5 s)
- **Pagination** for large catalogs (switches to paged loading automatically)
- **Inline audio mini-player** — play audio without leaving the browse list
- **Keyboard navigation** support in file browser

---

## 3. File Operations

- **Copy** to a configured destination (local, SMB, FTP, SFTP)
- **Move** to a configured destination
- **Delete** — permanent or to Trash (configurable)
- **Rename** file in-place
- **Restore** deleted file from Trash
- **Undo** last copy / move / delete operation
- **Batch operations** on multi-selected files
- **Share** — open file with external app
- **Safe Mode** — confirmation dialogs before delete / before move (master toggle in Settings)
- **Overwrite on copy/move** option per direction
- Copy/Move **progress dialog** with byte-level progress and speed indicator
- **Cross-protocol transfers**: SMB↔Local, FTP↔Local, SFTP↔Local, SMB↔SMB, FTP↔FTP, SFTP↔SFTP, Local↔FTP, Local↔SFTP, Local↔SMB

---

## 4. Destination Management

- Up to **10 color-coded destination buttons** displayed in the player
- Any configured writable resource can act as a destination
- **Go to next file after copy** option
- Collapsible copy / move panel in player
- **Mark / unmark file as Favorite** from player

---

## 5. Image Viewer

- Full-screen display with **pinch-to-zoom**
- **Crop to fullscreen** (fills screen when image and device orientation match)
- **Dynamic background effect** — blurred ambient color behind the image
- **Rotate image** clockwise / counter-clockwise (saved to file)
- **Flip image** horizontal / vertical (saved to file)
- **Adjust brightness / contrast / saturation** (saved to file)
- **Apply color filters**: Grayscale, Sepia, Negative (saved to file)
- **OCR** — extract text from image (ML Kit Latin recognizer + Tesseract)
- **Translation overlay** — Google Lens style: translated text blocks drawn over original positions
- **Send to Google Lens** app

---

## 6. GIF Viewer

- Animated GIF playback
- **Speed adjustment** 0.25× – 4× (saved to file)
- **Extract first frame** — save as a static image
- **Extract all frames** — save each frame as an individual image

---

## 7. Video Player

- ExoPlayer-based playback
- **Full-screen mode** with system UI auto-hide
- **Playback position** save & restore per file (resume from where you left off)
- **Picture-in-Picture** mode (Android 12+ / API 31+), auto-enter on home button press
- **Configurable touch zones**: tap regions for previous / next / play-pause / seek
- **Touch zones hint overlay** shown on first launch
- **Sleep timer** (15 / 30 / 45 / 60 / 90 / 120 min) with countdown badge

---

## 8. Audio Player

- ExoPlayer-based playback
- **Background playback** — persistent foreground service with notification; playback continues when the app is minimized or the screen is locked
- **Notification media controls** (play/pause, previous/next)
- **Album cover art** — embedded metadata tags + online search via iTunes API (optional; Wi-Fi-only option)
- **Lyrics search and display** — full-screen lyrics overlay (online search)
- **Random photos during playback** — cycle photos from a selected resource as visual background
- **Empty state / visualizer animations** when no cover art: AVD pulse, canvas bars, canvas waves, spectrum visualization
- **Vinyl record indicator** — small animated rotating vinyl in the corner while playing
- **Sleep timer** (shared with video player)
- Track metadata display: artist, title, album, duration

---

## 9. Slideshow

- Auto-advance images and GIFs on a configurable interval (**1 – 3600 seconds**)
- **Random playback order**
- **Background music** from a selected resource during image/GIF slideshow
- **Play video/audio to end** in mixed-type slideshows (advance on playback end instead of timer)
- Countdown display (3 – 2 – 1) before slide advance
- Configurable slideshow interval per resource (overrides global setting)

---

## 10. PDF Viewer

- Render and display multi-page PDF documents
- **Page mode** (flip) and **vertical scroll mode**
- Navigation panel with **PDF page thumbnails**
- **Color modes**: Normal, Night, Sepia
- **Zoom**
- **OCR + Translation** of PDF page content (Google Lens style overlay)
- Large PDF thumbnail support for network files (optional setting)

---

## 11. EPUB Viewer

- Parse and render EPUB e-books (epub4j)
- **Chapter navigation** (previous / next)
- **Table of contents** navigation panel
- **In-chapter and cross-chapter search** (up to 500 results)
- **Font size** adjustment
- **Font family** selection (Default, Serif, Monospace)
- **Reader themes**: Light, Dark, Sepia, System (follows device dark mode)
- **Line height** multiplier (1.0 – 3.0×)
- **Horizontal margin** adjustment
- **Last read chapter position** save & restore
- **Translation** of EPUB chapter text

---

## 12. Text Viewer / Editor

- View plain text and code files with **automatic charset detection**
- **Markdown rendering** (Markwon library)
- **Syntax highlighting** for code files
- **Line numbers** display toggle
- **Reader themes**: Light, Dark, Sepia, System
- **Font size adjustment** via horizontal swipe gesture
- **In-place text editing and saving** (when resource is writable; local and network files)
- **Auto-save** with **undo / redo** stack
- **Translation** of text content
- **Copy all text to clipboard**

---

## 13. Translation & OCR (cross-viewer feature)

- **ML Kit OCR** (Latin script) for text extraction from images/PDF
- **Tesseract** for additional script support
- **Automatic source language detection** (ML Kit Language Identification)
- **On-device translation** to a configurable target language (ML Kit Translation; models downloaded on demand)
- **Google Lens style overlay** — translated text blocks positioned at original coordinates (images and PDF)
- Available in: Image viewer, PDF viewer, Text viewer, EPUB viewer
- Configurable **source language** (auto or explicit) and **target language**
- OCR result display with configurable **font size** and **font family**
- Option to copy recognized/translated text

---

## 14. Network Sources

- **SMB** (Samba / Windows shares / NAS) — full browse and file operations
- **FTP** — full browse and file operations
- **SFTP** — full browse and file operations
- **Network auto-discovery** — scans local subnet (ports 445 / 21 / 22), streams results in real time
- **Network speed test** — measures read/write Mbps, recommends optimal thread count; results saved to resource
- **Configurable parallelism** for network operations: 1, 2, 4, 8, 12, or 24 threads
- **Connection pooling** for SMB (reuse authenticated sessions across operations)
- **Connection throttling** — prevents overloading slow or congested network sources
- **Periodic background sync** of network file lists (WorkManager, configurable interval 1 – 24 h)

---

## 15. Cloud Integration

- **Google Drive** — folder picker, browse, stream/download, file operations
- **Dropbox** — folder picker, browse, stream/download, file operations
- **OneDrive** — folder picker, browse, stream/download, file operations
- **OAuth authentication** flow in-app with secure token persistence
- **Backup settings + resources to Google Drive** (JSON payload, versioned schema)
- **Restore from Google Drive backup**

---

## 16. Favorites

- Mark / unmark any file as **Favorite** from the player
- Dedicated **Favorites list** accessible from the main screen
- **Favorites home screen widget** (scrollable list of favorite files)

---

## 17. Home Screen Widgets

- **Favorites widget** — shows list of favorite files with a direct launch action
- **Resource Launch widget** — one-tap launch of a configured resource into the browser/player
- **Continue Reading widget** — resumes the last viewed file in the last used resource

---

## 18. Settings

| Area | Key options |
|---|---|
| General | UI language, prevent screen sleep, small controls mode, default network login/password |
| Media types | Enable / disable: images, GIFs, videos, audio, text, PDF, EPUB; minimum and maximum size filters per type |
| Images | Load full resolution (for zoom support), crop to fullscreen |
| Audio | Online cover search (iTunes API), Wi-Fi only for online search, random photos during playback, background playback service, empty-state animation mode |
| Text / PDF / EPUB | Line numbers, reader themes, Markdown render, syntax highlight, PDF scroll mode, PDF color mode, EPUB line height, EPUB horizontal margin |
| Translation | Enable translation, source language, target language, Google Lens overlay, Google Lens app button, OCR result font size/family |
| Playback | Default sort mode, slideshow interval, background music for slideshow, play-to-end in slideshow, thumbnail icon size, command panel visibility, touch zones overlay, PiP enable, video frame thumbnails, player warmup |
| Destinations | Enable copy, enable move, overwrite on copy/move, go-to-next after copy, max destination count (1 – 10), enable undo |
| Safe Mode | Master toggle for confirm-delete and confirm-move dialogs |
| Trash | Recoverable trash vs. permanent delete, confirm-delete prompt |
| Network / Sync | Background sync enable, sync interval (h), parallel threads count |
| Cache | Glide disk cache size (512 MB – 16 GB) |
| Backup | Export settings to JSON file, import settings from JSON file, Google Drive backup & restore |

---

## 19. Settings Search

- Full-text **search across all settings entries** — instantly highlights and navigates to any setting

---

## 20. Wear OS Companion App

- Browse **SMB network sources** directly from the watch
- Browse **media file list** on the watch
- **Image slideshow** on the watch
- **Audio player** on the watch
- **Video player** on the watch
- **Settings** screen on the watch
- In-app permission request flow on the watch

---

## 21. Background & System Services

- **Trash cleanup** — scheduled WorkManager job removes files older than the retention period
- **Orphan temp file cleanup** — removes leftover download temp files on startup
- **Pending credential revocation** — deferred cleanup of expired or revoked credential sets
- **MediaStore sync** after file operations (keeps system Gallery and other apps in sync)
- **Playback position** DB persistence — resume position saved for audio, video, and EPUB files
- **Thumbnail cache** (Glide, configurable size 512 MB – 16 GB)
- **File metadata cache** (DB-backed; avoids repeated EXIF / ID3 reads for the same files)
- **Credential audit** — tracks last-used date of each credential set, surfaces stale entries for cleanup

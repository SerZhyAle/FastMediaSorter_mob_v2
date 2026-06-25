---
layout: default
title: "FastMediaSorter v2 - Complete Feature List"
permalink: /docs/FEATURES.html
---
# FastMediaSorter v2 - Complete Feature List

*Last updated: 2026-06-24*

This document is the curated showcase of standout user-facing features. The complete developer inventory of every implemented capability lives in `docs/ALL_FEATURES.jsonl`.

**Platform requirements:** Android 8.0+ (API 26) for Standard flavor. The Legacy flavor extends support down to Android 6.0+ (API 23) covering the same features. The Lite flavor is local-files-only, with no network sources (SMB/FTP/SFTP) or cloud drives. Supported devices: phones, tablets, Android TV boxes, and Android head units. Runs on Chrome OS via Google Play (ARC++).

---

## 0. Setup & Configuration
- **First-run device profile setup** `[Standard / VR]`: Select a device profile (phone, tablet, TV, head unit, media player, photo frame, VR headset, etc.) on first launch with auto-detection recommendations. The chosen profile applies optimized default settings (content types, layouts, security, screen behavior) tailored to the device's usage.
- **Onboarding functionality setup** `[Standard / VR]`: During first-run setup, turn on the capabilities you need (file manager, audio, video, documents, text recognition, translation) and download optional elements inline - each feature activates as soon as its download finishes, with no trip to Settings.
- **One-tap "Enable all"** `[Standard / VR]`: On the first welcome page, "Enable all" sets the universal profile, switches on every available function, requests all permissions one dialog at a time, offers the app as default player for every supported media type, and finishes setup in a single step.

## 1. Sources & Storage
- **Multiple resource types** `[Standard / VR]`: Connect Local folders, network locations (SMB/NAS, FTP, SFTP), and cloud drives (Google Drive, Dropbox, OneDrive) in a unified interface.
- **Share resources between devices** `[Standard / VR]`: Export a configured resource - including its access credentials - to a small file, then import it on another device by opening the file, sharing it, or via Settings -> Backup & Restore. The file holds passwords in plain text, so share it only with people you trust.
- **Drag to reorder resources** `[Standard]`: Long-press and drag entries in your resource list to set the exact order they appear in.

## 2. Media Browsing
- **File Manager Mode** `[Standard / VR]`: Toggle visibility of dot-hidden files or bypass media filters entirely to manage any file type or archive (ZIP, APK, EXE, etc.) across local and network resources.
- **Protected archive extraction** `[Standard / VR]`: Open password-protected ZIP archives - enter the password when prompted and the contents extract in place; an archive that uses an unsupported protection method is reported with a clear message instead of failing silently.
- **Office & PDF document handoff** `[Standard / VR]`: Filter and open DOC, DOCX, RTF, ODT, or protected PDF files in external apps or fallback viewers with integrated password indicators.
- **External file viewing** `[Standard / VR]`: When you open a file from another app, page through the neighbouring files in the same folder (next, previous, random, slideshow), and use *Open in FastMediaSorter* to jump straight into the in-app player on that file. In fullscreen mode - tap the Fullscreen button in the command bar to hide the system bars and command panel for immersive viewing; tap the Fullscreen button again (or use the keyboard shortcut) to exit.
- **External image editing** `[Standard / VR]`: When you open an image from another app, edit it right there - crop, save a cropped copy, make a compressed copy, and toggle screen rotation - the same image actions as the in-app player.
- **File details sheet** `[Standard]`: Open a bottom sheet showing a file's metadata - size, dates, path, resolution or duration - straight from the browser.
- **Drag multi-select** `[Standard / VR]`: Select many items at once by dragging across them with touch, or band-select with a mouse, in both Browse and the Duplicates list.

## 3. File Operations
- **Cross-protocol transfers** `[Standard / VR]`: Copy or move files seamlessly between Local, SMB, FTP, SFTP, and Cloud endpoints in any combination with background progress and speed/ETA diagnostics.
- **Duplicate file finder** `[Standard / VR]`: Scans storage to locate and remove identical files using a 3-phase matching engine (Size -> Hash -> SHA-256) with auto-delete or manual review options.
- **Direct Camera & Voice capture** `[Standard / VR]`: Take photos, record voice notes, or capture videos with the system/in-app camera directly to local, network, or cloud folders.
- **Samsung-style in-app camera** `[Standard]`: A built-in camera with a single entry point and an in-screen photo/video switch - frame the shot, take stills or record video, and save straight to your target folder without leaving the app.
- **Soft delete & Trash restore** `[Standard / VR]`: Deleted files move to a per-folder trash snapshot instead of vanishing; undo restores them in one tap and old trash clears automatically in the background.
- **Download by link** `[Standard]`: Paste a URL into the Download-by-link dialog and the file downloads straight to your chosen folder, no browser needed.
- **Fallback save** `[Standard]`: When a chosen destination is unavailable, the file is saved to a configured fallback location instead of failing.
- **Screen capture** `[Standard]`: Take a screenshot of the device screen from the Operations settings, or with an edge gesture, using Android's system capture consent, and save it to your chosen folder.
- **App panel: system shortcuts and app features** `[Standard]`: The quick-launch panel can now hold not only other apps but also parts of the operating system and the app's own features and resources (calculator, mini-game, photo-OCR-translate, streams, a specific resource), and it arrives pre-filled with a useful set by default.
- **Edge-gesture actions** `[Standard]`: Assign an edge swipe to open the app or its quick-launch panel, in addition to the screenshot gesture.
- **Copy or move an opened file** `[Standard]`: When you open a file from another app, copy or move it straight to a configured destination folder from within the viewer.

## 4. Destination Management
- **Instant sorting panel** `[Standard / VR]`: Set up to 30 favorite target folders (default 10) inside the player as shortcuts to copy or move the current file instantly with auto-advance to the next item.
- **Number-key sort shortcuts** `[Standard]`: Trigger destination operation buttons with number keys on a keyboard or TV remote for fast keyboard-driven sorting.

## 5. Image & GIF Viewer
- **Crop & Color adjustment filters** `[Standard / VR]`: Destructively rotate, flip, and crop images directly on the source, or apply adjustments (brightness, contrast, saturation) and color filters (Sepia, Negative).
- **Animated GIF speed controls** `[Standard / VR]`: Fine-tune GIF playback speed (0.25x to 4x) and export individual frames as static images.
- **Send to..** `[Standard / VR]`: One high-priority command gathers every way to send the current file out - system Share, Email, Telegram and other messengers, Google Keep, Google Lens, Print, Open in another app - into a single list. The list shows only the receivers you enabled in settings that fit the current file type, so the file menu stays short instead of listing a dozen separate items. Works from the browser, the player, and standalone viewers.
- **Standalone image viewer actions** `[Standard / Lite / Photos / Legacy]`: From the standalone image viewer, print the picture or copy and move it to another folder - without opening the full app.

## 6. Drawing & Annotations Editor
- **Drawing & annotations canvas** `[Standard / VR]`: Create blank canvases or annotate photos using brushes, geometric shapes, and text layers, then send the result through the unified Send to.. menu (Google Keep, Lens, and the other enabled receivers).
- **Custom brush color & size** `[Standard]`: Pick any brush color from a full color picker and adjust brush thickness while drawing or annotating.

## 7. Video Player
- **Session save & restore** `[Standard / VR]`: Remembers exact playback coordinates and restores remote active network/cloud sessions upon app cold start.
- **Video screenshot capture** `[Standard / VR]`: Capture video frames in high quality and save them as PNG/JPG to any local or network resource.
- **Picture-in-Picture & D-pad focus** `[Standard Only]`: Runs videos in a floating window (Android 12+) and supports complete D-pad/remote focus navigation for TV boxes and head units.
- **Blu-ray Transport Stream (.m2ts)** `[Standard / VR]`: Plays BD-TS files from local, SMB, SFTP, FTP, and cloud sources - the 192-byte BD packet format is auto-detected, while plain 188-byte MPEG-TS files with a .m2ts extension play without unnecessary stripping. Unsupported audio tracks (TrueHD, DTS-HD MA) are reported with a one-time notification listing the detected codecs.
- **Chromecast video casting** `[Standard]`: Cast the current video to any Chromecast device through a local proxy, straight from the player overflow menu.
- **Video frame to clipboard** `[Standard / VR]`: Optionally copy each captured video frame to the system clipboard alongside saving it, ready to paste into another app.
- **Player gesture controls** `[Standard / VR]`: Swipe vertical sliders for brightness and volume, and customize the 3x3 grid of tap zones that map to player actions.
- **Auto-fullscreen on landscape** `[Standard / Lite / Photos / Legacy]`: Rotating the device to landscape switches the player to fullscreen automatically and surfaces the command panel; rotating back restores the windowed view.

## 8. VR Edition & OpenXR
- **Dedicated VR build & OpenXR engine** `[VR Only]`: Immersive stereoscopic rendering (SBS/OU, VR180, 360°), virtual cinema screen for flat files, head tracking HUD, and passthrough snapshot capture on Quest 3.

## 9. Audio Player
- **Background audio service & Casting** `[Standard / VR]`: Runs playback on a persistent background service with notification drawer controls and direct Chromecast casting support.
- **Lyrics display & MIDI integration** `[Standard / VR]`: View lyrics, play MID/MIDI files on network resources, and bridge current tracks into YouTube Music searches in one tap.
- **Cover-art visualizations** `[Standard / VR]`: When a track has no cover art, the player fills the screen with an animated backdrop - choose breathing bars, waves, pulse rings, a plain note, or downloadable looping video backgrounds; picking the video option offers the one-time download, and if no clips are present the music just keeps playing with no backdrop.
- **Sleep timer** `[Standard / VR]`: Set a countdown that pauses playback automatically (with optional volume fade-out) so audio stops while you drift off.

## 10. Slideshow Mode
- **Slideshow with background music** `[Standard / VR]`: Rotate images/GIFs with custom intervals (1s to 1h), play background tracks from a selected resource, and auto-pause slides on video files.

## 11. PDF & EPUB Reader
- **E-book reader formatting** `[Standard / VR]`: View DRM-free EPUBs with customizable fonts, margins, line spacing, themes (including OLED black), and chapter maps.
- **Read Aloud (TTS) & Text selection** `[Standard / VR]`: Synthesizes page text via Text-to-Speech (TTS) and copy text fragments using long-press drag handles.
- **PDF page to Google Lens** `[Standard / VR]`: Send the current PDF page straight to Google Lens to search or extract its text.
- **Print from the standalone viewer** `[Standard / VR]`: Print documents and text directly from the standalone viewer.

## 12. Text Editor
- **In-place Markdown editor** `[Standard / VR]`: Edit `.txt` and `.md` files directly on local or remote storage with live syntax highlighting, Markdown rendering, and auto-save.
- **Send text to Google Keep** `[Standard / VR]`: From the read-only text viewer, send the current text straight to Google Keep as a note - available in both the in-app player and the standalone text viewer, shown only when Keep is installed.
- **Embedded text calculator** `[Standard / VR]`: Parse and solve math equations from selected reader/OCR/lyrics text blocks without writing results back.
- **Text viewer reader themes** `[Standard / VR]`: Choose a reading theme - light, dark, sepia, or OLED black - in the read-only text viewer.

## 13. Offline OCR & Translation
- **Offline OCR & Translation engine** `[Standard / VR]`: Extract text from images/PDFs and translate it completely offline using ML Kit and Tesseract with custom models.
- **Photo OCR capture flow** `[Standard / VR]`: In-app camera translation with area cropping, language selector on the crop screen, and editable results saved as `.txt`.
- **On-demand delivery** `[Standard / VR]`: OCR, translation, the FFmpeg DTS decoder and the audio-player background videos are off by default and installed on demand the first time you enable them; declining the download leaves the feature unavailable without affecting the rest of the app, and once installed they survive app updates and cache clears.

## 14. Network & Cloud Integration
- **NAS auto-discovery & speed test** `[Standard / VR]`: Scans subnets for SMB/FTP/SFTP, tests network speed to optimize copy thread counts, and supports high-performance streaming.
- **SFTP key auth & host-key pinning** `[Standard / VR]`: SFTP resources support SSH private-key authentication with optional passphrase, and host-key fingerprint pinning to detect server impersonation. Key auth is also available for predefined resources shipped in the bundled XML config.
- **Cloud OAuth storage** `[Standard / VR]`: Authenticates Google Drive, Dropbox, and OneDrive with remote editing and settings backup.
- **Toggle remote sources** `[Standard / VR]`: Turn individual SMB, (S)FTP, and cloud sources on or off in settings or on the welcome screen. A disabled source disappears from selection and the app stops its background activity; existing resources are hidden, not deleted, and return when you turn the source back on.

## 15. Smart Widgets & Integration
- **Icon-style home widgets** `[Standard / VR]`: 1x1 quick launch buttons (Quick Audio Recorder, Quick capture, Camera OCR), resizable widgets (Scheduled tasks, Audio Now Playing, Photo Frame), and settings integration.

## 16. Settings & Navigation
- **Email crash report to author** `[Standard / VR]`: When a real error - not a routine "unavailable" message - is shown in the error dialog, a button emails the error details to the author with the app log attached, so a problem can be reported in one step.
- **Crash report after a restart** `[Standard / VR]`: If the app closes unexpectedly, the next launch offers to email the crash report - with the app log attached - to the author, so even a hard crash can be reported.
- **Settings search & customization** `[Standard / VR]`: Full-text settings search with spotlight targeting - now also finds dropdown and selection settings and hides rows for features your edition lacks - plus system diagnostic info.
- **Custom color themes** `[Standard / Lite / Photos / Legacy]`: Build and apply your own color theme - choose accent and surface colors instead of only the built-in light and dark options.
- **Complete DPAD & TV remote remapping** `[Standard / VR]`: TV remote key assignments, DPAD acceleration, and Wear OS Companion app support.
- **Downloadable Extensions manager** `[Standard / VR]`: A settings screen listing every optional module (OCR engines, translation, audio visualizations, FFmpeg DTS decoder) and OCR language pack with its status, size, and download/remove actions to manage device storage.
- **Unified settings backup & restore** `[Standard / VR]`: Back up your sources, favorites, schedules, network passwords, and saved site sign-ins in one format - to a local file or Google Drive - and restore everything after a reinstall.
- **Set as default app from settings** `[Standard / VR]`: Make FastMediaSorter the default handler for images, audio, video, and documents right from the playback settings page, without opening the welcome screen; only the buttons for functions enabled in your build are shown.
- **Full gamepad & joystick navigation** `[Standard]`: Drive every screen with a gamepad or joystick, with remappable browser actions for controller users.
- **Settings reference page** `[Standard]`: Browse an in-app reference describing every setting, searchable from the settings screen.
- **Collapsible settings groups** `[Standard / VR]`: Settings sections fold into tidy collapsible groups so long screens stay easy to scan.

## 17. Usage Statistics
- **Local usage statistics** `[Standard / VR]`: An opt-in, off-by-default summary of your own activity - files sorted, space freed, time in the player and more - stored only on your device. Enable it in General settings to show the Statistics window, then send the summary to the author with one button or export it as a text file; turning collection off wipes the detailed activity, while the first-launch date and launch count are kept.

## 18. Bonus Mini-Game
- **Kryvavitsa and the Monster** `[Standard]`: A hidden, opt-in turn-based grid puzzle - enable it in Settings, then guide the monster to the exit past roaming enemies across levels with score and a turn counter. Launch it from the menu or a home-screen widget; plays with touch, keyboard, D-pad, or swipe.

## 19. Internet Streams
- **Internet Streams** `[Standard / Legacy / noLegal / VR / Lite (progressive-audio only)]`: Dedicated Streams screen for internet audio, video, and RTSP sources. Add a stream URL manually, import a remote `.m3u` playlist, or download a curated station catalog via Extensions.
- **Inline audio playback & ICY metadata** `[Standard / Legacy / noLegal / VR / Lite]`: Play audio streams directly within the list. A sticky bottom mini-control surfaces Icecast/Shoutcast ICY now-playing metadata (station/track name) without leaving or hiding the streams list.
- **Category & language filters** `[Standard / Legacy / noLegal / VR]`: Filter the streams list by category, language, and type (audio/video/RTSP) using searchable pickers. The language picker pins English, Russian, and Ukrainian to the top with flag icons.
- **Background playback & exit prompts** `[Standard / Legacy / noLegal / VR]`: Manage streams using background audio settings. Leaving the streams list prompts to keep playing or stop the active stream when background playback is disabled.
- **Local streams pinning** `[Standard / Legacy / noLegal / VR / Lite]`: Pin your favorite stream sources to the top of the list with a distinct icon for quick access.
- **Stream-tailored player controls** `[Standard / Legacy / noLegal / VR]`: The stream player shows a trimmed control set fit for live playback, with channel-to-channel navigation and no copy/move panels.
- **Live stream casting** `[Standard / Legacy / noLegal]`: Cast a live video stream to a Chromecast device straight from the stream player.
- **Home-screen shortcut & card actions** `[Standard / Legacy / noLegal / VR]`: Pin a stream to the home screen for one-tap launch, and open a per-card actions menu directly from the streams list.
- **Stream defaults & input parity** `[Standard / Legacy / noLegal / VR]`: Set a default sort order and media-type filter for the streams list, with full TV-remote and mouse navigation parity.


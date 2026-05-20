PLATFORM: Reddit - r/androidapps
URL: https://www.reddit.com/r/androidapps/
FORMAT: Reddit Markdown
TONE: Straightforward app intro, dev disclosure, feature-first

---
TITLE:
[DEV] FastMediaSorter v2 - file manager + media player + PDF/EPUB reader that actually works with NAS, SFTP, and all major clouds in one app

---
BODY:

Developer here. FastMediaSorter v2 is an all-in-one media management app for Android that I've been building for the past two years. The short version: it's a file manager, media player, document reader, and automation tool - with native support for NAS/SMB, SFTP, FTP, Google Drive, Dropbox, and OneDrive all in a single interface.

**What it replaces (for my own use case):**

Before building this I was juggling: Solid Explorer for file management, VLC for video, a separate music app, Moon+ for EPUB, Adobe Reader for PDF, and AndFTP for SFTP access. Now it's one app.

**Core capabilities:**

*Storage*
- Local storage, SMB (NAS/Windows shares), FTP, SFTP
- Google Drive, Dropbox, OneDrive
- Cross-protocol operations - copy directly from SFTP to Google Drive without touching local storage

*Media playback*
- ExoPlayer-based video with gestures, subtitle support, PiP (Android 12+), playback speed control
- Audio with background playback, lock screen controls, sleep timer
- Inline audio mini-player directly in the file browser

*Viewers*
- Image viewer with OCR + AR translation overlay (works on files from NAS/cloud, no download required)
- PDF reader with themes and in-app translation
- EPUB reader with themes and translation
- GIF and text file viewer

*File management*
- Batch copy/move/rename/delete across all sources
- Scheduled file operations (background, cron-style - e.g., auto-move photos to NAS nightly)
- Duplicate finder with SHA-256 matching across sources
- Recycle bin / trash with restore

*Other*
- Wear OS companion (browse NAS, control playback from watch)
- Home screen widgets
- PIN protection per resource
- Encrypted credential storage with audit trail

**Flavors (different APKs):**
- Standard: full feature set, Android 8.0+
- Lite: video + photos only, Android 8.0+
- Legacy: full features minus cloud, Android 6.0+

**Links:**
- Google Play: https://play.google.com/store/apps/details?id=com.sza.fastmediasorter
- GitHub: https://github.com/SerZhyAle/FastMediaSorter_mob_v2
- APK: https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp

Open to questions and feedback.

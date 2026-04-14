https://news.ycombinator.com/

PLATFORM: Hacker News — Show HN
URL: https://news.ycombinator.com/
FORMAT: Plain text, HN conventions (no markdown rendered in submission body — title only for submission, context in first comment)
NOTE: HN Show HN is title + URL only. The "post" is really the first comment you leave on your own submission.

---
SUBMISSION TITLE:
Show HN: FastMediaSorter – Android file manager with native SMB/SFTP/FTP + cloud in one app

SUBMISSION URL:
https://github.com/SerZhyAle/FastMediaSorter_mob_v2

---
FIRST COMMENT (posted by you immediately after submission):

I built this over the past two years because I couldn't find an Android app that treated network storage (SMB/NAS) and cloud storage as first-class citizens alongside local files.

The core problem: apps in this space either do file management well (Solid Explorer, MiXplorer) but delegate media playback to other apps, or do playback well (VLC) but have minimal file management. Nothing does both natively across all storage types.

FastMediaSorter combines:

- Native SMB via SMBJ (connection pooling, up to 24 parallel transfer threads, file list caching in Room DB)
- SFTP via SSHJ, FTP via Apache Commons Net
- Google Drive, Dropbox, OneDrive (direct API, no intermediate download for playback)
- ExoPlayer-based video/audio player — streams directly from any source
- PDF and EPUB readers that open files from NAS or cloud without downloading first
- OCR + AR translation overlay on images and PDFs (ML Kit, on-device)
- Scheduled file operations using WorkManager (cron-style, survives reboots)
- SHA-256 duplicate detection across any combination of local/network/cloud sources
- Wear OS companion app

Architecture: MVVM + Clean Architecture, Hilt DI, Kotlin 100%, minSdk 26 (API 23 for Legacy flavor).

The most technically interesting part was making the media player work seamlessly across sources — ExoPlayer's DataSource abstraction handles local and HTTP well, but SMB required a custom DataSource implementation that wraps SMBJ's random-access file handle. SFTP was similar.

Play Store: https://play.google.com/store/apps/details?id=com.sza.fastmediasorter
APK: https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp

Limitations worth being upfront about: no DLNA/UPnP, no media server mode (Plex/Jellyfin client), no video transcoding, system-level EQ only.

Happy to discuss the SMB/ExoPlayer integration or the scheduling implementation.

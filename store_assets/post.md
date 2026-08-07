https://xdaforums.com/t/app-8-0-fastmediasorter-v2-multi-protocol-media-manager-smb-sftp-ftp-cloud.4785566/

[APP][8.0+] FastMediaSorter v2 - Multi-Protocol Media Manager (SMB/SFTP/FTP/Cloud)

Tired of apps that can't talk to your NAS?

Tired of switching between five different apps just to move files, play video, and read a PDF - all from your home server?

FastMediaSorter v2 (FMS) is a single app that handles browsing, playing, organizing, and transferring media across local storage, NAS, and cloud - with no compromises on performance.

---
WHAT MAKES IT DIFFERENT FROM OTHER FILE MANAGERS / GALLERY APPS?

Most apps treat network storage as an afterthought. FMS is built from the ground up for mixed-protocol workflows:
- Move files directly from SFTP to Google Drive without touching local storage
- Auto-schedule transfers at night (e.g., push daily photos to NAS while charging)
- Photoframe with backgroound music from any android device 
- Find duplicates with SHA-256 byte-for-byte matching across any source

---
CORE FEATURES

Storage Support
- Local storage, SMB (NAS/Windows shares), FTP, SFTP
- Google Drive, Dropbox, OneDrive
- Cross-protocol batch copy/move/rename

Player & Viewer
- ExoPlayer-based video/audio with background playback and Sleep Timer
- Full EPUB & PDF reader with themes and in-app translation
- OCR + AR translation overlay for images and PDFs ("Google Lens"-style, offline-capable)

Organization
- Scheduled file operations (cron-style, runs in background)
- Duplicate finder with SHA-256 matching
- Batch rename with pattern templates

Privacy & Security
- PIN protection per network resource
- Encrypted credential vault (no plaintext passwords on disk)
- Zero trackers, no analytics

Wear OS Companion
- Browse NAS/cloud from wrist
- Remote playback control

---
TECHNICAL DETAILS

Language        : Kotlin (100%)
Architecture    : MVVM + Clean Architecture + Hilt DI
Media engine    : ExoPlayer (Media3)
Image loading   : Glide with custom network loaders
Network         : SMBJ / JSch / Apache Commons Net
SMB performance : Connection pooling + up to 24 parallel transfer threads
UI              : Material 3, optional Compact Elements mode (high-density)
Min SDK         : Android 8.0 (Standard) / Android 6.0 (Legacy flavor)

---
DOWNLOAD

Google Play  : https://play.google.com/store/apps/details?id=com.sza.fastmediasorter
APK          : https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp
GitHub       : https://github.com/SerZhyAle/FastMediaSorter_mob_v2
Project site : https://serzhyale.github.io/FastMediaSorter_mob_v2/index.html

---
FEEDBACK & QUESTIONS

I'm actively developing this and looking for real-world feedback from power users:
- What protocols or cloud providers are missing?
- Any performance issues on specific NAS hardware (Synology, QNAP, TrueNAS)?
- Feature requests welcome - especially around automation workflows

Drop a reply or open an issue on GitHub. PRs also welcome.

TAGS: media manager, file manager, smb, sftp, ftp, google drive, dropbox, onedrive
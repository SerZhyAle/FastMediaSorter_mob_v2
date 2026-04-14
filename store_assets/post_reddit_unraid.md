PLATFORM: Reddit — r/unraid
URL: https://www.reddit.com/r/unraid/
FORMAT: Reddit Markdown
TONE: Unraid-specific, practical, show the exact workflow

---
TITLE:
Android app for managing media on your Unraid shares — native SMB with streaming, file ops, and scheduled automation (dev here)

---
BODY:

Dev here. Built this specifically because I couldn't find an Android app that treated SMB shares as a first-class citizen rather than a bolt-on feature.

**The Unraid workflow this solves:**

Most of us have Unraid user shares set up for media — `/mnt/user/Media`, `/mnt/user/Photos`, etc. Accessing those from Android typically means:
- VLC (can browse SMB but no file management)
- Solid Explorer (file management but its video player is garbage)
- Separate cloud app for offsite copies

FastMediaSorter handles all of this in one app.

**What it does with your Unraid shares:**

*Connect:* Add your Unraid server as an SMB resource — just enter the IP, share name, and credentials. The app uses SMBJ (SMB2/SMB3) so it negotiates the right dialect automatically. No QuickConnect or extra software needed on the Unraid side.

*Browse & stream:* Navigate your shares exactly like local storage. Play video files directly from the share with ExoPlayer — no download step, full gesture support (swipe for brightness/volume, horizontal for seek), subtitle support, PiP on Android 12+.

*File operations:* Copy/move files between shares, or from shares to local storage, or to cloud (Google Drive, Dropbox, OneDrive). Cross-protocol is direct — no local staging. Useful if you have an offsite backup strategy mixing cloud with Unraid.

*Scheduled automation:* Set up background jobs that run on a schedule:
- Move photos from phone local storage → Unraid share nightly
- Copy new files from one share to another on a timer
- Clean up old files automatically
- Works after reboot, handles battery optimization on most OEMs

*Duplicate detection:* SHA-256 matching across local + Unraid + cloud. Useful after importing large photo/video collections when you want to verify no duplicates crept in.

**Performance:**
- Connection pooling — SMB sessions are reused, not re-created per file
- Up to 24 parallel transfer threads for bulk moves
- File list caching — large directories open near-instantly after first load

**Tested on Unraid 6.12 and 6.11.** Standard user shares work out of the box. If you use private shares with specific user permissions, those work too as long as the credentials you enter have read (or read/write) access.

**Not in scope:**
- No Unraid-specific features (no array monitoring, no plugin integration, no Community Applications)
- No DLNA/UPnP
- No media server mode (Plex/Emby client)
- This is direct SMB access only

**Download:**
- Google Play: https://play.google.com/store/apps/details?id=com.sza.fastmediasorter
- APK: https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp
- GitHub: https://github.com/SerZhyAle/FastMediaSorter_mob_v2

Happy to help troubleshoot if you have issues connecting to a specific share setup. Also curious if there are Unraid-specific workflows I'm missing.

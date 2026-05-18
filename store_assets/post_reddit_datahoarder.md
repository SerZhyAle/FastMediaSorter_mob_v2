PLATFORM: Reddit - r/DataHoarder
URL: https://www.reddit.com/r/DataHoarder/
FORMAT: Reddit Markdown
TONE: Practical, storage-problem-focused, dev disclosure

---
TITLE:
I built an Android app for managing large media collections across NAS + cloud - SHA-256 duplicate detection, cross-protocol transfers, scheduled automation

---
BODY:

Dev here. I think this community might actually get the most use out of this.

**FastMediaSorter v2** is an Android file manager built specifically for people who have media spread across local storage, NAS, and multiple cloud providers - and need to actually manage it, not just browse it.

**The features most relevant here:**

**Duplicate detection**
SHA-256 byte-for-byte matching across any combination of sources. You can run it comparing local storage vs. SMB share, or NAS vs. Google Drive. Not filename matching, not size matching - full checksum. Finds true duplicates regardless of where they're stored.

**Cross-protocol file operations**
Copy/move files between any two connected sources without staging locally. Supported combinations:
- Local ↔ SMB/NAS
- Local ↔ SFTP/FTP
- Local ↔ Google Drive / Dropbox / OneDrive
- SMB ↔ SFTP (direct, no local temp)
- NAS ↔ Cloud (direct)

Up to 24 parallel transfer threads. Connection pooling on SMB so you're not paying reconnect overhead per file.

**Scheduled automation**
Cron-style background jobs:
- Source + destination (any protocol combination)
- File type filter (images / video / audio / docs / all)
- Time window filter (all files / since last run / last hour / last day)
- Atomic MOVE: copy → verify → delete source. A failed copy never deletes the original.
- Runs after reboot, survives battery optimization on most OEMs

**File list caching**
The app indexes large SMB directories into a local Room database. Subsequent opens of a 50k-file NAS folder are near-instant.

**What it's not:**
- Not a deduplication tool with auto-merge logic - it shows you duplicates, you decide what to do
- No RAID management, no server-side anything
- Direct SMB access only (no Plex/Jellyfin client mode)

**Supported sources:** Local, SMB (NAS/Windows shares), FTP, SFTP, Google Drive, Dropbox, OneDrive

**Links:**
- Google Play: https://play.google.com/store/apps/details?id=com.sza.fastmediasorter
- GitHub: https://github.com/SerZhyAle/FastMediaSorter_mob_v2
- APK: https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp

Curious whether the scheduled automation covers your workflow. What file management tasks do you currently do manually on Android?

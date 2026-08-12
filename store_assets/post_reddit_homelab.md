PLATFORM: Reddit - r/homelab
URL: https://www.reddit.com/r/homelab/
FORMAT: Reddit Markdown
TONE: Homelab-appropriate, focus on the "mobile client for your homelab" angle
NOTE: r/homelab allows self-promotion in posts tagged [Project]. Use flair "Project" or "Discussion".
      Keep it brief - homelabbers are busy, they skim. Lead with the use case.

---
TITLE:
[Project] Built an Android app as a proper mobile client for homelab NAS - SMB/SFTP/FTP + cloud, inline media player, scheduled automation

---
BODY:

I run a Synology at home + some cloud storage for offsite backups. My problem: there was no single Android app that let me manage files across all of these without constantly switching apps.

So I built one. Two years of development, now stable enough to share.

**FastMediaSorter v2** - what it does:

- Connects to SMB (NAS/Samba), SFTP, FTP, Google Drive, Dropbox, OneDrive
- Browse and stream video/audio directly from any source (ExoPlayer, no download required)
- Copy files between any two sources directly - e.g., SFTP to Google Drive without touching local storage
- Scheduled background operations (WorkManager): move files on a cron-style schedule, filter by type/date
- SHA-256 duplicate detection across local + NAS + cloud
- Wear OS companion: browse and control playback from watch
- Encrypted credential vault, PIN per resource, read-only mode

**Performance-relevant bits (since this sub cares):**
- SMB connection pooling via SMBJ - sessions reused across operations
- Up to 24 parallel transfer threads on bulk moves
- File list indexed in local Room DB - big NAS directories open instantly on re-entry
- Atomic MOVE: copy → verify → delete. Failed copy never touches the source.

**Architecture:** Kotlin 100%, MVVM + Clean Arch, Hilt, ExoPlayer Media3, SMBJ/JSch/Apache Commons Net.

Tested on Synology DSM 7, QNAP QTS, TrueNAS SCALE, and plain Samba on Linux. Works over LAN and VPN (WireGuard tested).

**What it doesn't do:**
No DLNA, no media server, no transcoding, no homelab monitoring of any kind. Pure file/media client.

Google Play: https://play.google.com/store/apps/details?id=com.sza.fastmediasorter
GitHub: https://github.com/SerZhyAle/FastMediaSorter_mob_v2

Curious if there are homelab-specific access patterns the app doesn't handle well.

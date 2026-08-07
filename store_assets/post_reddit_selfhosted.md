https://www.reddit.com/r/selfhosted/comments/1slh5us/i_built_an_android_app_specifically_for_managing/

PLATFORM: Reddit - r/selfhosted
URL: https://www.reddit.com/r/selfhosted/
FORMAT: Reddit Markdown
TONE: Community member, dev disclosure up front, invite feedback
FLAIR: App  ← select this via "Add flair" button before posting

---
TITLE:
I built an Android app specifically for managing media across NAS/SMB + cloud - FastMediaSorter v2

---
BODY:

I'm the developer. Sharing because this community's use case is exactly what the app is designed for.

**The problem I kept running into:** most Android apps either handle local files well or network files passingly, but not both. VLC can play from SMB but can't manage files. Solid Explorer can manage SMB files but its media player is an afterthought. Google Photos doesn't touch a NAS at all.

So I built FastMediaSorter over the last two years - an app where SMB/NAS is a first-class citizen, not a plugin.

**What it does that's relevant to this sub:**

- Connects natively to SMB (SMBJ library), SFTP (JSch), FTP, Google Drive, Dropbox, OneDrive
- Streams video/audio directly from NAS - no download-first step
- Cross-protocol file operations: copy from SFTP directly to Google Drive, or SMB to local, or any combination
- Scheduled operations: move files on a cron-style schedule (e.g., push camera roll to NAS at 2am nightly)
- File list caching in local Room DB - large SMB directories open near-instantly on second visit
- Connection pooling - SMB connections are reused, not re-established per file
- Up to 24 parallel transfer threads for bulk moves
- Encrypted credential vault with last-used audit (helps identify stale NAS accounts)
- PIN protection per resource (useful if sharing a device)
- Wear OS companion: browse NAS and control playback from watch

**What it's NOT:**
- Not a Plex/Jellyfin client (no media server, direct SMB access only)
- No video transcoding
- No DLNA
- Equalizer is system-level only

**Platform requirements:** Android 8.0+ (most flavors), Android 6.0+ (Legacy flavor, same feature set including cloud and network shares)

**Links:**
- Google Play: https://play.google.com/store/apps/details?id=com.sza.fastmediasorter
- GitHub: https://github.com/SerZhyAle/FastMediaSorter_mob_v2
- APK (direct): https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp

Happy to answer questions about the SMB implementation, performance on specific NAS hardware, or anything else. Tested on Synology, QNAP, and TrueNAS SCALE.

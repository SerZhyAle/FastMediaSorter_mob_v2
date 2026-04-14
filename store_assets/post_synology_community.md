PLATFORM: Synology Community
URL: https://community.synology.com/
SECTION: Mobile Apps / Third-party apps
FORMAT: Plain text (forum post)
TONE: Technical, NAS-specific, practical setup instructions

---
TITLE:
[Android App] FastMediaSorter – native SMB client with media player, scheduler, and duplicate finder for Synology NAS

---
BODY:

I'm the developer of FastMediaSorter, an Android app built around direct SMB access to NAS — including Synology DSM. Posting here because the app is specifically designed for the kind of home server + multiple-device workflow many people in this community use.

WHAT IT DOES

FastMediaSorter connects to your Synology NAS over SMB and lets you browse, play, and manage files without downloading them first. It also supports SFTP, FTP, Google Drive, Dropbox, and OneDrive — all in a single interface.

Core capabilities relevant for Synology users:

- Stream video and audio directly from NAS via SMB (ExoPlayer-based, no download step)
- Open PDFs and EPUBs stored on NAS, read them in-app with themes and translation
- Copy / move files between Synology and other sources (local storage, cloud) directly
- Scheduled background operations: e.g., move camera roll to NAS nightly, or sync a folder on a schedule
- SHA-256 duplicate detection across NAS and local storage
- File list caching — large shared folders open near-instantly after the first load
- Connection pooling — connections to NAS are reused across operations
- Up to 24 parallel transfer threads for bulk moves
- Encrypted credential storage with last-used audit log
- PIN protection per resource (useful on shared devices)

CONNECTING TO SYNOLOGY DSM

In FastMediaSorter, add a new resource and choose SMB. Fill in:

  Server : your NAS IP or hostname (e.g., 192.168.1.100 or nas.local)
  Share  : the name of the shared folder (e.g., video, photos, media)
  User   : your DSM username
  Pass   : DSM password (stored encrypted on device)
  Port   : 445 (default, leave as-is for most setups)

SMB dialect: the app uses SMBJ, which negotiates SMB2/SMB3 automatically. Works with DSM 6.x and DSM 7.x.

If you're using Synology's firewall, ensure TCP 445 is allowed from your device's subnet.

QuickConnect is NOT required — direct LAN IP works and is recommended for performance.

TESTED HARDWARE

Confirmed working on:
- Synology DS920+ (DSM 7.2)
- Synology DS418play (DSM 6.2 and 7.1)
- QNAP TS-253D
- TrueNAS SCALE (Bluefin)
- Windows 11 shares (SMB3)

KNOWN LIMITATIONS

- No DLNA / UPnP support
- No Synology Photos integration (connects to file shares only, not DSM packages)
- No video transcoding (ExoPlayer decodes locally)
- SMB over VPN: works, but connection timing may need adjustment depending on tunnel latency

DOWNLOAD

Google Play  : https://play.google.com/store/apps/details?id=com.sza.fastmediasorter
APK (direct) : https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp
GitHub       : https://github.com/SerZhyAle/FastMediaSorter_mob_v2

If you run into issues connecting to a specific DSM version or share configuration, post the details and I'll help troubleshoot. Also interested to hear if there are Synology-specific workflows the app doesn't cover yet.

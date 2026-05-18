PLATFORM: Indie Hackers
URL: https://www.indiehackers.com/
SECTION: Products → Submit your product  OR  Post in relevant group (e.g., "Android Developers")
FORMAT: Markdown
TONE: Honest founder story, metrics if available, lessons learned - IH audience wants the business/building story

---
TITLE:
I spent 2 years building an Android app that replaces 8 separate apps for NAS + cloud media management

---
BODY:

### What I built

[FastMediaSorter v2](https://play.google.com/store/apps/details?id=com.sza.fastmediasorter) - an Android app that connects to NAS (SMB/SFTP/FTP) and cloud storage (Google Drive, Dropbox, OneDrive) with a full media player, document readers, file management, and automation built in.

The short version: it replaces VLC + a file manager + a PDF reader + an EPUB reader + an FTP client + cloud apps - if your use case involves media stored across a home server and cloud.

### The problem I was solving

I have a Synology NAS at home and use Google Drive for work. Managing media between them on Android meant switching between 5-6 apps constantly. None of them did everything:
- VLC plays from SMB but can't manage files
- Solid Explorer manages SMB files but its media player is basically useless
- Google Photos ignores NAS entirely
- Each cloud has its own separate app

I couldn't find a single app where every storage type was treated equally. So I built one.

### Two years of development - what that looks like

This started as a "how hard can it be" project. The answer: harder than expected.

The most technically interesting problems:
- Getting ExoPlayer to stream from SMB required a custom DataSource implementation wrapping SMBJ's random-access file handle
- SMB connection pooling - naive implementations create a new connection per file, which makes large directory thumbnailing unbearably slow
- WorkManager + scheduled file operations that survive battery optimization on Chinese OEM devices (Xiaomi/Huawei kill background processes aggressively)
- Supporting 4 product flavors (Standard/Lite/Photos/Legacy) from a single codebase with feature flags - this paid off for the Play Store segmentation

Current feature count: 22 feature areas, 100+ individual features. Things I didn't expect to build: an EPUB reader, OCR with AR translation overlay, a Wear OS companion app, home screen widgets, duplicate detection via SHA-256 across network sources.

### Distribution

- Google Play (primary)
- Direct APK on Google Drive
- GitHub (source not fully open but code is visible)

### What I'm learning

The hardest part isn't the technical implementation - it's understanding where the audience actually is. Power users who use NAS + cloud storage on Android are a niche, but they're a vocal one. Communities like r/selfhosted, XDA Developers, and homelab forums have been more useful for early feedback than general Android app communities.

Play Store reviews are a mixed bag. The people who leave detailed reviews are exactly the target audience. The one-star reviews are usually "this isn't a simple photo gallery."

### What's working / what isn't

**Working:** The NAS streaming use case. People who find the app through searches for "android smb client" or "android nas manager" convert well and stick around.

**Not working (yet):** Discovery. The app competes in keywords like "file manager" and "video player" where it's invisible against VLC (300M+ installs) and Solid Explorer. The niche angle (NAS + cross-protocol) is the right positioning but hard to execute on Play Store SEO alone.

**Next:** Better onboarding for the network setup flow. Current feedback: the features are there but the first 5 minutes is confusing for users who haven't set up a network resource before.

### Links

- [Google Play](https://play.google.com/store/apps/details?id=com.sza.fastmediasorter)
- [GitHub](https://github.com/SerZhyAle/FastMediaSorter_mob_v2)
- [APK direct](https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp)

Happy to discuss any of the technical or distribution challenges. And if you've built niche Android utilities - curious how you found your audience.

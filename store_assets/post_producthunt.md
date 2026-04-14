PLATFORM: Product Hunt
URL: https://www.producthunt.com/
FORMAT: Product Hunt launch page fields
NOTE: Product Hunt requires: Name, Tagline (60 chars max), Description, Maker comment, Topics/tags

---
NAME:
FastMediaSorter

TAGLINE (60 chars max):
One app for NAS, cloud, and local media — no switching

TOPICS / TAGS:
Android, Productivity, File Management, Media, Storage

---
DESCRIPTION (shown on product page):

FastMediaSorter v2 is an Android app that unifies local storage, NAS (SMB/SFTP/FTP), and cloud (Google Drive, Dropbox, OneDrive) into a single interface — with a full media player, document readers, and file automation built in.

**The problem it solves:**
Managing media across a home NAS and multiple cloud services on Android typically requires 4-6 separate apps. FastMediaSorter consolidates that into one.

**Key capabilities:**
- Browse and stream media from NAS, SFTP/FTP, and all major cloud services directly — no download step
- Full ExoPlayer-based video player (PiP, gestures, subtitle support, background audio)
- EPUB and PDF readers with themes, OCR, and in-app translation
- Cross-protocol file operations — copy directly from SFTP to Google Drive
- Scheduled automation — move files on a cron-style background schedule
- SHA-256 duplicate detection across local + network + cloud sources
- Wear OS companion app
- PIN protection and encrypted credential vault per resource
- Up to 24 parallel transfer threads on SMB with connection pooling

Supports Android 8.0+ (Android 6.0+ via Legacy flavor).

**Links:**
Google Play: https://play.google.com/store/apps/details?id=com.sza.fastmediasorter
GitHub: https://github.com/SerZhyAle/FastMediaSorter_mob_v2

---
MAKER COMMENT (first comment, posted by you after launch):

Hey Product Hunt! I'm the developer of FastMediaSorter.

I started building this two years ago out of personal frustration: I have a Synology NAS and use Google Drive for work, and managing media between them on Android meant constantly switching between apps — each doing one thing well but nothing doing everything.

The goal was a single app where every storage type is a first-class citizen: you browse SMB the same way you browse local storage, play video the same way regardless of whether the file is on your phone, NAS, or Drive, and copy files between any two sources without staging locally.

What I'm most interested in hearing from you:
- Does the NAS / cross-protocol workflow fit how you actually use storage?
- What's missing that would make this replace more apps for you?
- Any UX friction that stood out?

Happy to answer anything — especially questions about the SMB/network implementation.

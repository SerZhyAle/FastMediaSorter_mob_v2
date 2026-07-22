---
layout: default
title: "FastMediaSorter v2 - Complete Feature List"
permalink: /docs/FEATURES.html
---
# FastMediaSorter v2 - Complete Feature List

*Last updated: 2026-07-22*

This document is the curated showcase of standout user-facing features. The complete developer inventory of every implemented capability lives in `docs/ALL_FEATURES.jsonl`.

**Platform requirements:** Android 8.0+ (API 26) for Standard flavor. The Legacy flavor extends support down to Android 6.0+ (API 23) covering the same features. The Lite flavor is local-files-only, with no network sources (SMB/FTP/SFTP) or cloud drives. Supported devices: phones, tablets, Android TV boxes, and Android head units. Runs on Chrome OS via Google Play (ARC++).

---

## 1. Replace network-drive setup tools
- **Scan a code to open your PC's folders** `[Standard / Photos / Legacy / VR]`: Run the free [Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/) companion on your PC, choose shared folders, scan its QR code, and those folders appear on Android as ready-to-use resources with the server key pinned automatically. No IPs, ports, usernames, passwords, or manual SMB/SFTP setup. If the phone has no camera, import the same access from a tiny `.fmscfg` file, including Telegram/email attachments. See [Open PC Folders by Scanning a Code](howto/scenario-companion-share.md).
- **One app for local, NAS, SFTP, FTP, and cloud drives** `[Standard / VR]`: Browse local folders, SMB/NAS shares, FTP/SFTP servers, and Google Drive, Dropbox, or OneDrive in one interface instead of splitting your library across separate apps.
- **Share a configured resource with another device** `[Standard / VR]`: Export a ready-to-use source, then import it on another device from a file, share sheet, or backup flow instead of re-entering the whole connection by hand.
- **Share a resource as a QR code** `[Standard]`: Hand an SFTP resource to another device by showing a QR code the recipient scans - no file transfer or manual re-entry.

## 2. Replace copy apps and duplicate cleaners
- **Cross-protocol transfers** `[Standard / VR]`: Copy or move files between Local, SMB, FTP, SFTP, and Cloud in any direction with background progress, speed, and ETA.
- **Duplicate file finder** `[Standard / VR]`: Find and remove identical files with a staged matcher (size -> partial hash -> SHA-256) for large photo, music, or download libraries.
- **Soft delete with restore** `[Standard / VR]`: Deleted files go to an app-managed trash snapshot first, so mistakes can be undone instead of becoming permanent immediately.

## 3. Replace capture, quick-launch, and send-out utilities
- **Direct capture into the folder you actually use** `[Standard / VR]`: Take photos, record voice notes, or capture video directly into local, network, or cloud destinations instead of first dumping everything into the camera roll.
- **Screen capture, screen recording, and quick audio recording** `[Standard]`: Launch screenshot, screen video, or audio recording from the app or an edge gesture, then stop it from a notification or the floating in-app indicator.
- **Edge-gesture quick actions** `[Standard]`: Turn the screen edges into instant shortcuts for capture, OCR-translate, recording, or opening the quick-launch panel/app.
- **Unified "Send to.." hub** `[Standard / Lite / Photos / Legacy / VR]`: One command gathers Share, Telegram, email, Lens, Keep, print, and other enabled targets into a single clean list instead of scattering export actions across menus.
- **Home-screen camera widget** `[Standard]`: Add a launcher widget that opens capture directly, so a photo lands in your chosen folder in one tap without opening the app first.

## 4. Replace a basic file viewer with a sorting workstation
- **Instant sorting panel** `[Standard / VR]`: Keep up to 30 favorite destination folders in the player and sort the current file in one tap, with optional auto-advance to the next item.
- **Open a file from another app and keep browsing nearby files** `[Standard / VR]`: When another app hands off one file, continue with next, previous, random, or slideshow inside the same folder instead of getting stuck on a single item.
- **File Manager Mode** `[Standard / VR]`: Show hidden files and bypass media-only filters to handle mixed folders, archives, APKs, EXEs, and other non-media content.

## 5. Replace several media viewers and editors
- **Image viewer with real file actions** `[Standard / VR]`: Crop, rotate, flip, adjust colors, speed-control GIFs, and export GIF frames without switching to a separate image utility.
- **Video frame capture to any destination** `[Standard / VR]`: Save a clean frame from video as PNG/JPG directly to local or network storage.
- **2D/360/VR playback in a dedicated VR build** `[VR Only]`: Play SBS/OU, VR180, and 360 content, or watch normal 2D files on a giant virtual screen in the OpenXR build.
- **Animated WebP and APNG playback** `[Standard]`: Short WebP and APNG animations now play in the image viewer instead of showing a single frozen frame.

## 6. Replace OCR, translation, and note extraction tools
- **Offline OCR and offline translation** `[Standard / VR]`: Extract text from images and PDFs, then translate it locally without sending content to a cloud OCR service.
- **Photo-to-text and screenshot-to-translation flow** `[Standard / VR]`: Capture, crop, recognize, translate, and save the result as editable text in one flow.
- **In-place text and Markdown editing on remote storage** `[Standard / VR]`: Edit `.txt` and `.md` files directly on local or network resources, with Markdown rendering and auto-save.

## 7. Replace separate stream and radio apps
- **Dedicated Internet Streams screen** `[Standard / Legacy / noLegal / VR / Lite (progressive-audio only)]`: Store internet radio, video streams, and RTSP sources in a proper library instead of juggling links in a browser or notes app.
- **Inline radio playback with live ICY metadata** `[Standard / Legacy / noLegal / VR / Lite]`: Play audio streams directly in the list while keeping the station catalog visible.
- **Pinned streams on the main window** `[Standard / Legacy / noLegal]`: Put favorite live channels directly above the resource list for one-tap access from the home screen of the app.
- **Smarter stream playback** `[Standard / Legacy / VR]`: Streams step down to a lighter quality automatically when the connection keeps stalling, and the frame you were watching becomes the channel's thumbnail.

## 8. Replace setup migration and utility clutter
- **Unified settings backup and restore** `[Standard / VR]`: Back up sources, favorites, schedules, passwords, and sign-ins to a local file or Google Drive, then restore the whole setup after reinstalling or moving devices.
- **App panel for apps, tools, and internal actions** `[Standard]`: Build a quick-launch panel that mixes Android apps, system shortcuts, captures, OCR tools, streams, and chosen resources in one place.
- **Hidden bonus mini-game** `[Standard / Lite / Photos / Legacy]`: A built-in turn-based puzzle for anyone who likes finding unexpected extras in utility apps.

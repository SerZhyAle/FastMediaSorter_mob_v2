---
layout: default
title: "📖 How-To Guides"
permalink: /docs/HOW_TO.html
---
# 📖 How-To Guides

Step-by-step instructions for common tasks.

This guide now has two layers:

- **Scenario Groups** for richer real-life workflows and combinations of features.
- **Core Task Reference** for direct one-feature recipes further below.

[Русский](HOW_TO_RU.md) | [Українська](HOW_TO_UK.md)

---

## Note: Feature Availability by Flavor

Some features are only available in specific flavors. The table below is derived from [FLAVOR_MATRIX.md](FLAVOR_MATRIX.md), which is generated from the build itself; the XR / noLegal surface is intentionally kept as one column because it depends on headset hardware and sideload build rules.

| Feature | Standard | Lite | Photos | Legacy | XR / noLegal |
|---------|----------|------|--------|--------|--------------|
| Network folders (SMB, SFTP, FTP) | ✓ | ✗ | ✓ | ✓ | ✓ |
| Cloud storage (Google Drive, OneDrive, Dropbox) | ✓ | ✗ | ✓ | ✓ | ✓ |
| Audio playback & lyrics | ✓ | ✓ | ✗ | ✓ | ✓ |
| Background audio playback | ✓ | ✗ | ✗ | ✓ | ✓ |
| Internet Streams (radio, HLS/DASH, RTSP) | ✓ | ✗ | ✗ | ✓ | ✓ |
| Document viewer (PDF, Text) | ✓ | ✗ | ✗ | ✓ | ✓ |
| EPUB reader | ✓ | ✗ | ✗ | ✓ | ✓ |
| Translation & OCR | ✓ | ✗ | ✗ | ✓ | ✓ |
| Image editing | ✓ | ✓ | ✓ | ✓ | ✓ |
| Home-screen (launcher) mode | ✓ | ✗ | ✗ | ✗ | ✓ |

Home-screen mode is the one row where the combined last column splits: it ships in the **noLegal** sideload build but not in the VR/XR build, where the headset provides its own home environment.

Two rows for audio, because these are two separate build decisions: **Lite plays local audio files** including lyrics, but stops when the app leaves the foreground - it has no background playback service. Lite has no Internet Streams screen at all, so radio and HLS/DASH/RTSP are not merely limited there, they are absent.

If a feature is marked with "✗", choose the **Standard** or **XR / noLegal** build that matches your hardware and distribution path.

---

## Table of Contents

### Scenario Groups

#### Home media, TV and living room flows

1. [Turn a NAS into a living-room media shelf](#turn-a-nas-into-a-living-room-media-shelf)
2. [Run a slideshow with background music for a room display](#run-a-slideshow-with-background-music-for-a-room-display)
3. [Use FMS on Android TV Box](#how-to-use-fms-on-android-tv-box)
4. [OpenXR VR Immersive Cinema](#openxr-vr-immersive-cinema)

#### Travel, reading and document workflows

5. [Prepare a folder for travel without stable internet](#prepare-a-folder-for-travel-without-stable-internet)
6. [Read cloud documents and EPUBs on the go](#read-cloud-documents-and-epubs-on-the-go)
7. [Translate signs, scans and screenshots with OCR](#translate-signs-scans-and-screenshots-with-ocr)
8. [Hand network files off to specialist apps](#hand-network-files-off-to-specialist-apps)
9. [Quick Math & Text Calculations](#quick-math-and-text-calculations)
10. [Cloud Markdown & Code Notes](#cloud-markdown-and-code-notes)

#### Power-user and mixed media workflows

11. [Sort a family photo archive with Quick Sort](#sort-a-family-photo-archive-with-quick-sort)
12. [Capture the Screen with Edge Gestures](#capture-the-screen-with-edge-gestures)
13. [Create Slideshow with Background Music](#how-to-create-slideshow-with-background-music)
14. [Read E-Books (EPUB)](#how-to-read-e-books-epub)
15. [Auto-Translation](#auto-translation)
16. [Home-Screen Smart Widgets](#home-screen-smart-widgets)

### Core Task Reference

17. [Connect to Network Drive (SMB)](#how-to-connect-to-network-drive-smb)
18. [Connect to SFTP/FTP Server](#how-to-connect-to-sftpftp-server)
19. [Import a Windows Companion Share (scan a code or import a file)](#how-to-import-a-windows-companion-share)
20. [Connect to Cloud Storage](#how-to-connect-to-cloud-storage)
21. [Set Up Quick Sort Folders](#how-to-set-up-quick-sort-folders)
22. [Use Touch Zones](#how-to-use-touch-zones)
23. [Edit Photos](#how-to-edit-photos)
24. [Create Slideshow](#how-to-create-slideshow)
25. [Protect Folder with PIN](#how-to-protect-folder-with-pin)
26. [Empty Trash](#how-to-empty-trash)
27. [Backup Settings](#how-to-backup-settings)
28. [View Text and PDF Files](#how-to-view-text-and-pdf-files)
29. [Open Network Files in External Apps](#how-to-open-network-files-in-external-apps)
30. [View Song Lyrics](#how-to-view-song-lyrics)
31. [Record Your Screen](#how-to-record-your-screen)
32. [Record a Voice Note](#how-to-record-a-voice-note)
33. [Use the In-App Camera](#how-to-use-the-in-app-camera)
34. [Find and Delete Duplicate Files](#how-to-find-and-delete-duplicate-files)
35. [View Your Usage Statistics](#how-to-view-your-usage-statistics)
36. [Use an SD Card or Connected Drive](#how-to-use-an-sd-card-or-connected-drive)
37. [Use the App as Your Home Screen](#how-to-use-the-app-as-your-home-screen)
38. [Choose Where Captures and Downloads Are Saved](#how-to-choose-where-captures-and-downloads-are-saved)
39. [Receive Files Shared from Another App](#how-to-receive-files-shared-from-another-app)
40. [Use the Built-In Programs](#how-to-use-the-built-in-programs)

---

## Scenario Groups

These sections are intentionally more varied than the core reference blocks below. Each scenario mixes a fast path with context, trade-offs, and the situations where FastMediaSorter is especially strong.

> **⭐ Featured: bring your PC's folders to your phone with one scan.** Run the free [Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/) companion on your PC, pick the folders with your videos, music, documents, or photos, and it shows a code on screen. On the phone, tap **Add**, choose **Import by barcode**, point the camera at the code - the PC folders are connected instantly, with no address, port, or password typing. Full walkthrough: [Open PC Folders by Scanning a Code](howto/scenario-companion-share.md) &bull; quick recipe: [Import a Windows Companion Share](#how-to-import-a-windows-companion-share).

> **⌚ Wear OS Smartwatches:** Using a Wear OS watch? See our step-by-step guides for [Listen to Music on Your Watch](howto/scenario-watch-music.md) and [Connect Smartwatch to NAS & PC Shares](howto/scenario-watch-network.md).

## Home media, TV and living room flows

## Turn a NAS into a living-room media shelf

**Available in:** Standard, Photos, Legacy, XR/noLegal

**Quick Path**

1. Add your NAS as an SMB resource.
2. Run **Scan Network** if you do not want to type the IP manually.
3. Open the resource from a TV box, tablet, or phone.
4. Start browsing videos, photos, or documents directly from the NAS.

**Scenario Walkthrough**

- Keep one SMB resource for the whole family library and separate child folders by use: Movies, Family Photos, Scans, Manuals.
- Run **Test Connection** once during setup so the resource is stable before you rely on it from the sofa.
- If the NAS is used from a TV box, pair it with a Bluetooth keyboard or TV remote for fast navigation.
- If browsing feels slow, open the resource settings and run the built-in speed check before changing anything else.

**When It Helps**

- You want one central media source instead of copying the same files to several devices.
- You want the same library to work for slideshow, document reading, and playback.

**Avoid This**

- Do not start with hostname troubleshooting. Use an IP address first, then optimize later.
- Do not expect the Lite flavor to browse SMB shares - that build has no network sources at all.

## Run a slideshow with background music for a room display

**Available in:** Standard, Lite, Legacy, XR / noLegal (Photos has no audio support)

**Quick Path**

1. Add one image source and one music source.
2. In **Settings → Media → Images**, enable **Play music during slideshow**.
3. Pick the music resource.
4. Open a photo folder and press **Play**.

**Scenario Walkthrough**

- Use a local image folder or a fast NAS share for the smoothest transitions.
- Keep a separate music resource for calm background tracks so slideshow audio is predictable.
- If the folder contains both images and videos, remember that music pauses automatically when a video starts.

**When It Helps**

- You want a TV box, tablet, or old phone to act as a digital frame for a room.
- You want one setup that can rotate family photos, event shots, or travel albums without manual queue building.

**Avoid This**

- Do not use a very slow network share for both images and music if smooth playback matters.

## OpenXR VR Immersive Cinema

**Available in:** Standard, Lite, Legacy, `vr`, noLegal (single-eye 3D); `vr` and noLegal (full headset immersion - both builds ship the immersive view, and it opens when the app detects an OpenXR headset and the VR master switch is on)

**Quick Path - enable, configure, watch 3D**

1. **Single-eye 3D (every flavor, nothing to enable):** open any SBS/OU/180°/360° file - it's auto-detected and cropped to one eye so it looks right on a normal flat screen. This is controlled by **Settings > Playback > "Show 3D content from one eye"** (default ON). To force a specific format instead of relying on auto-detect, open the player's Control dialog on a `vr`/XR-noLegal build and pick a mode from the 3D tab - **Auto-detect**, **Side-by-Side (SBS)**, **Over-Under (OU)**, or **Mono (Disabled)**; the choice is remembered for that file.
2. **Full immersion on a Quest (`vr` or XR/noLegal build):** with the headset on, tap the VR badge in the player while a 3D file is open, choose **Open in VR Cinema** from a file's overflow menu in Browse, or open **Settings > Media** and tap **Test Immersive** to try a sample. Any of the three opens a per-eye OpenXR view of that content.
3. **Watch:** inside the immersive view a HUD strip carries the controls - a position bar you drag with the controller ray to seek (elapsed and total time beside it), plus the pickers that apply to this file: audio track only when there is more than one, subtitles only when the file has them, stereo depth only for stereo content. **HIDE** and **EXIT** sit at opposite ends of the strip; hiding it removes it completely and a trigger pull brings it back without activating whatever is underneath. The thumbstick seeks 10 seconds a step; hold **grip** while pushing it to step between files instead - next and previous walk the whole resource list, not just the file you opened. On the first immersive entry after install a legend lists every controller binding; any press closes it, and the **HELP** button on the strip brings it back at any time.

**Scenario Walkthrough**

- Single-eye 3D needs no headset at all - it's the easiest way to revisit old SBS/OU footage on a phone or tablet.
- Full immersion needs a Quest or other OpenXR headset and a build that ships it - the `vr` build or the XR/noLegal sideload build (see the [VR Sideloading Guide](VR_SIDELOAD.md)).
- 360°/180° photos and video render as a sphere/hemisphere around you once inside the immersive view; flat 2D files just play flat.

**When It Helps**

- You want to revisit archived SBS/OU/360°/180° footage without a separate VR media app.
- You have a Quest and want to try full immersion on your own files today, accepting that navigation is next/previous only for now.

**Avoid This**

- Do not expect the Meta Horizon Store / Google Play `vr` build to enter immersive mode yet - that part is still in development.
- Seeking, track and subtitle selection and stereo depth are on the HUD strip in the headset. File operations are not - drop back to the flat panel to copy, move or delete.

## Play Internet Radio on a Car Head Unit or Audio Player

**Available in:** Standard, Legacy, XR / noLegal - the Streams screen is absent in Lite and Photos

**Quick Path**

1. Open the main window dropdown and tap **Streams**, or go to **Settings > Media > Streams** and enable the toggle if it is off.
2. Tap **⋮** at the end of the toolbar, choose **Add stream**, and paste any radio station URL (http:// or https://, .m3u8, rtsp://).
3. Tap the station row - audio starts in the sticky bottom mini-control. The list stays scrollable.
4. For a larger catalog, tap **Import** and enter a remote `.m3u` URL, or download the curated FastMediaSorter catalog from the **Extensions** screen.

**Scenario Walkthrough**

- The curated catalog arrives with topic and language chips; filter by genre or language via the filter button (dot indicator when active). The AND/OR toggle lets you match stations that fit all criteria or any one of them.
- The two small icons right of the search field split radio from video in one tap: tap the audio or the video glyph to keep only that kind, tap the lit one again to show everything.
- Pin your favourite stations to the top with the pin icon - order is independent of global Favorites.
- Switch the toolbar view toggle to **Grid** to see channels as tiles with their last captured frame - handy for browsing video streams at a glance. Your choice of list or grid is remembered next time you open Streams.
- If a stream is cast-friendly and your phone is on Wi-Fi, tap **Cast** in the player to send it to a Chromecast on the same network. RTSP streams can't be cast.
- ICY now-playing metadata (station name, current track) shows in the bottom mini-control.
- A station you added yourself can be sent to your Wear OS watch: open the row's **⋮** menu and tap **Send to watch** (the command appears when the Wear Companion option is on). The transferred station stays on the watch through catalog refreshes; if the same address later appears in the online catalog, the catalog entry takes over.
- Video and RTSP streams open in the fullscreen player; pressing Back returns to the Streams list with scroll position preserved.
- Background audio behaviour follows **Settings > Playback > Background audio playback**: with it off, audio stops when you leave the screen and the app offers a Stop / Keep playing choice.

**When It Helps**

- Android car stereos, audio players, and media boxes where you want internet radio without a separate app (TuneIn, RadioDroid, VLC network streams).
- IPTV-lite use: HLS/DASH VOD streams play in the fullscreen player.

**Avoid This**

- Do not expect live HLS/DASH offset (live-edge) playback - only VOD HLS/DASH is supported in this release.
- Do not use the Lite or Photos flavor for Streams; neither build has a Streams entry, so no protocol works there.

## Travel, reading and document workflows

## Prepare a folder for travel without stable internet

**Available in:** Standard, Lite, Photos, Legacy, XR / noLegal (PDF and EPUB reading needs Standard, Legacy, or XR / noLegal)

**Quick Path**

1. Create or choose one local folder for the trip.
2. Copy the media, PDFs, EPUBs, or notes you need into it before leaving Wi-Fi.
3. Open that folder once in FastMediaSorter so thumbnails and last positions are ready.
4. Use the folder offline during the trip.

**Scenario Walkthrough**

- Keep travel media in one local folder even if the originals normally live on NAS or cloud.
- Mix formats on purpose: boarding PDFs, reading EPUBs, screenshots, and offline music can live side by side.
- Use the filter panel if you want to switch between only images, only documents, or only audio while offline.

**When It Helps**

- Flights, trains, hotels, and rural areas where cloud streaming is unreliable.
- Situations where you want one offline pack instead of searching across several apps.

**Avoid This**

- Do not wait until the last minute to test whether the files really open without internet.

## Read cloud documents and EPUBs on the go

**Available in:** Standard, Legacy, XR / noLegal - Lite and Photos cannot read documents or EPUBs at all; cloud storage is additionally absent in Lite

**Quick Path**

1. Add your cloud provider in **Cloud Storage**.
2. Open the folder that contains PDFs or EPUBs.
3. Tap the file directly from the cloud resource.
4. Continue reading from your last saved position later.

**Scenario Walkthrough**

- Use this when your working documents already live in Google Drive, OneDrive, or Dropbox and you do not want a separate reader workflow.
- PDFs are best for fixed-layout files like tickets, manuals, and scanned contracts.
- EPUB is better for long-form reading where adjustable font size and chapter navigation matter more than layout fidelity.

**When It Helps**

- You move between work documents and personal reading without leaving the app.
- You keep travel or client files in the cloud but still want a reading-first interface.

**Avoid This**

- Do not expect cloud reading in Lite - that build has neither cloud storage nor document support. Photos and Legacy do have cloud storage, but only Legacy can open documents.
- Do not treat slow mobile data as a guaranteed reading experience for very large files.

## Translate signs, scans and screenshots with OCR

**Available in:** Standard, Legacy, XR / noLegal

**Quick Path**

1. Open an image, PDF, or text file.
2. Show the command panel.
3. Tap **Translate**.
4. Confirm the model download on first use if needed.

**Scenario Walkthrough**

- For Latin-script text, the app usually starts with ML Kit for speed.
- For Cyrillic-heavy material, the app can switch to Tesseract for better recognition quality.
- Screenshots, receipts, menus, and scanned pages work especially well when the source text is reasonably sharp.

**When It Helps**

- You are travelling, reading foreign manuals, or decoding screenshots from chats and apps.
- You need translation in place instead of copying text into a separate tool first.

**Avoid This**

- Do not judge OCR quality from a blurred night photo or a badly cropped scan.

## Hand network files off to specialist apps

**Available in:** Standard, Photos, Legacy, XR/noLegal

**Quick Path**

1. Open a file from SMB, SFTP, or FTP.
2. Tap **ⓘ Info**.
3. Tap **Download and Open**.
4. Pick the specialist app from the Android chooser.

**Scenario Walkthrough**

- Use this when FastMediaSorter is the best browser for remote storage, but another app is the best editor or viewer for one file type.
- Typical handoff cases are office documents, advanced PDFs, codec-heavy videos, and niche media formats.
- The downloaded copy remains in `Downloads`, so you can reopen it later even if the remote source goes offline.

**When It Helps**

- You want one remote file hub without giving up best-in-class specialist tools.

**Avoid This**

- Do not expect cloud handoff through this exact flow yet.

## Quick Math & Text Calculations

**Available in:** Standard, Legacy, XR / noLegal

**Quick Path**

1. Open any PDF document, EPUB e-book, text file, or run OCR translation on an image.
2. Long-press to select any text block containing numbers or math equations.
3. From the floating text action menu, tap the **Calculator** button.
4. The calculator evaluates the mathematical formula instantly in a popup overlay.

**Scenario Walkthrough**

- Select a text line containing numbers with operator symbols (like `(45 + 12) * 3`) in a PDF or an OCR translate result.
- Use the built-in scientific calculator's function menu for complex operations (trigonometry, roots, powers, logs).
- The calculator keeps calculation history between sessions and supports memory slots (M+/M-/MR/MC) for quick data tracking.

**When It Helps**

- You are reading a manual, screenshot scan, or document and need to quickly solve formulas or sum up currency/numbers without switching to a different calculator app.

**Avoid This**

- Do not paste raw alphabetic strings; only valid numbers, parentheses, and math operators can be parsed.

## Cloud Markdown & Code Notes

**Available in:** Standard, Photos, Legacy, XR / noLegal (local, network, and cloud); Lite (local folders only)

**Quick Path**

1. Browse to any local folder, home NAS (SMB), FTP/SFTP server, or cloud drive (Google Drive).
2. Tap the **New Note (📝)** button in the folder toolbar.
3. Type your content inside the editor. The app highlights Markdown tags and code syntax.
4. Tap **Save** (or let it auto-save) to write the changes directly to the remote source.

**Scenario Walkthrough**

- Keep a `.md` journal file in your Google Drive or home NAS and edit it from any device using in-place editing.
- Create new notes in key resources with automatic name conflict resolution (e.g. `Note_1.txt`, `Note_2.txt`).
- View rendered Markdown layouts in read-only mode, or export notes directly to external services like Google Keep.

**When It Helps**

- You want to maintain simple notes, code snippets, or todo lists directly on your central network/cloud drives without local copy-paste workflows.

**Avoid This**

- Do not expect cloud storage note creation on Lite - that build has no cloud storage and no network sources.

## Power-user and mixed media workflows

## Sort a family photo archive with Quick Sort

**Available in:** Standard, Lite, Photos, Legacy, XR / noLegal

**Quick Path**

1. Add your destination folders to **Quick Sort**.
2. Open the source folder with unsorted family photos.
3. Use numbered buttons or touch zones while reviewing images.
4. Send keepers to destination folders immediately.

**Scenario Walkthrough**

- Create destination folders by outcome, not by date alone: `Best`, `Print`, `Send to family`, `Archive`.
- Review in fullscreen so you can decide quickly and move or copy without dropping back to the file list.
- If several people curate the same archive, keep one consistent destination naming scheme before a large sorting session.

**When It Helps**

- You have a backlog from birthdays, trips, school events, or old phone imports.
- You want a fast triage flow instead of dragging files manually in a file manager.

**Avoid This**

- Do not start sorting before destinations are named clearly.
- Do not use Move immediately if you are still unsure which folders should stay as the long-term archive.

## Capture the screen with edge gestures

**Available in:** Standard, XR/noLegal

**Quick Path**

1. Go to **Settings → Operations → Edge screen gestures → Gesture overlay** and turn it on.
2. While viewing any file, swipe in from the left edge to open the capture menu.
3. Pick an action - the strip closes and the action runs.

**What the strip can do**

- Take a **screenshot** of the current screen - view it, edit it, share it, send it to another app, or run OCR translation on it, plus a silent-capture option.
- **Take a photo** with the camera, then send it, edit it, or run OCR-translate on it without leaving the app.
- Start a **screen**, **video**, or **audio/voice** recording - see [How to Record Your Screen](#how-to-record-your-screen) and [How to Record a Voice Note](#how-to-record-a-voice-note).
- **Open an app or panel** you use often.
- **Crop-and-share** a region of the current image.

**Good to know**

- While the strip is on, a swipe from the left edge opens the capture menu instead of turning the page.
- The strip is built for one-handed capture while browsing - leave it off if you rely on left-edge page swipes.
- Android confirms the capture or recording every time you use this gesture, even for the silent screenshot option - that's a system safeguard, not something the app controls.

**When It Helps**

- You want a screenshot, a quick photo, or a recording without leaving the file you are viewing.

## Core Task Reference

## How to Add or Import an Internet Stream

**Available in:** Standard, Legacy, XR / noLegal (all protocols) - the Streams screen is absent in Lite and Photos

**Add a single URL:**

1. Open **Streams** from the main window dropdown.
2. Tap the **⋮** button at the end of the toolbar, then **Add stream**.
3. Paste the stream URL (http/https radio, .m3u8, rtsp://). Tap **Save**.
4. Tap the row to start playback.

**Import a remote .m3u playlist:**

1. In the Streams screen, tap **⋮ > Import from URL**.
2. Enter the remote .m3u address. Tap **Import**.
3. All stations from the file appear in the list.

**Download the curated FastMediaSorter catalog:**

1. Open **Settings > Extensions** (or the Welcome onboarding Streams row).
2. Tap **Download** next to the Streams catalog entry.
3. After download, the catalog rows appear in Streams with topic/language chips and are searchable and sortable.

---

## How to Connect to Network Drive (SMB)

**What you need:**

- NAS or Windows PC with shared folder
- Both devices on same Wi-Fi network
- Username and password for the share

**Available in:** Standard, Photos, Legacy, XR / noLegal flavors

**Steps:**

1. **Tap "+" button** on main screen
2. Select **"Network folder SMB"**
3. Fill in details:
   - **Auto-Discovery (New):**
     1. Tap **"Scan Network"** button
     2. Wait for devices to appear in the list
     3. Select your device from the list
     4. The IP address will be filled automatically

   - **Manual Input:**

     ```
     Server/Path: \\192.168.1.100\photos
     Username: john
     Password: ****
     Display Name: Home NAS (optional)
     ```

4. Tap **"Test Connection"** to verify
5. Tap **"Save"**

**Server address formats:**

- Windows: `\\192.168.1.100\share`
- Linux/Mac: `smb://192.168.1.100/share`
- With port: `smb://192.168.1.100:445/share`

**Tips:**

- Use IP address (not hostname) for reliability
- Enable SMB v2/v3 on NAS for security
- Default SMB port: 445

**Troubleshooting:**
→ See [TROUBLESHOOTING.md](TROUBLESHOOTING.md)

---

## How to Connect to SFTP/FTP Server

**What you need:**

- Server with SSH (SFTP) or FTP enabled
- Port 22 (SFTP) or 21 (FTP) open
- Username and password (or key for SFTP)

**Steps:**

1. **Tap "+" button** on main screen
2. Select **"SFTP / FTP"**
3. Choose Protocol: **SFTP** or **FTP**
4. Fill in details:

   ```
   Host: 192.168.1.100
   Port: 22 (SFTP) / 21 (FTP)
   Username: username
   Password: ****
   Remote Path: /home/user/photos (optional)
   ```

5. Tap **"Connect"**

**Advanced:**

- **SSH Key authentication:** Currently not supported (password only)
- **Custom port:** Change port number if server uses non-default

**Troubleshooting:**
→ See [TROUBLESHOOTING.md](TROUBLESHOOTING.md)

---

## How to Import a Windows Companion Share

**What it is:** the companion is a feature of [Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/) (formerly FastMediaSorter LITE) - the free Windows media sorter from the same author. It shares chosen PC folders over SFTP and exports a ready-made connection - no manual server setup, no host/port/key typing on the phone. You bring it to the phone by **scanning a QR code** on the PC screen, or by **importing a `.fmscfg` file**.

**Available in:** Standard, Photos, Legacy, XR/noLegal (barcode scan needs a camera; the file method works everywhere, including VR)

> Prefer a guided, screenshot-friendly version? See the scenario guide [Open PC Folders by Scanning a Code](howto/scenario-companion-share.md).

**Get Fast Media Sorter for Windows:**

- Website: [serzhyale.github.io/FastMediaSorter_Lite](https://serzhyale.github.io/FastMediaSorter_Lite/)
- Publishing folders (guide): [How to publish PC folders to Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html)
- GitHub: [latest release](https://github.com/SerZhyAle/FastMediaSorter_Lite/releases/latest) (installer or portable ZIP)
- winget: `winget install SerZhyAle.FastMediaSorter`
- Microsoft Store: search for "FastMediaSorter LITE" (still listed under the former name)

**On the PC:**

1. Install and run **Fast Media Sorter for Windows**, open the **Share** tab in settings.
2. Pick the folder(s) to share - the app starts the SFTP server, generates keys and sets up autostart by itself.
3. It shows a **QR code** on screen. It can also **Save .fmscfg** if you prefer a file.

**On the phone - Method A, scan the code (fastest):**

1. **Tap the "+" button** on the main screen.
2. Tap **"Import by barcode"** - it sits next to the four resource-type cards and in the SFTP form header.
3. Point the camera at the QR on the PC (tap **Torch** in a dark room), then confirm the **Import access** dialog.
4. Done - one read-only resource per shared folder appears, with the server key pinned automatically.

**On the phone - Method B, import the file:**

1. On the PC, use **Save .fmscfg** and transfer the file to the phone (email, Telegram, or a shared location).
2. **Tap "+"** -> **"SFTP / FTP"** -> **"Import from file"** and pick the `.fmscfg` file. If it arrived as a Telegram/email attachment, just tap the attachment.
3. Confirm the **Import access** dialog - the read-only resources appear.

**Note:** the QR code and the config file both embed the access password - treat them like a key, do not publish the screenshot or the file. The **Import by barcode** entry is hidden on camera-less devices and VR headsets; use Method B there.

---

## How to Connect to Cloud Storage

**Supported Providers:**

- Google Drive
- OneDrive
- Dropbox

**Steps:**

1. **Tap "+" button** on main screen
2. Select **"Cloud Storage"**
3. Select provider: **Google Drive**, **OneDrive**, or **Dropbox**
4. Tap **"Sign in.."** button
5. Follow the browser/app authentication flow
6. Grant required permissions
7. **Select folders** to sync
8. Tap **"Done"**

**Notes:**

- Files are **streamed**, not downloaded
- Requires internet connection
- Edits sync automatically
- You can disconnect anytime: Edit folder → Remove

**Privacy:**

- No password stored (uses OAuth tokens)
- Tokens can be revoked in your cloud provider's security settings

---

## Check Network Speed

**Supported for:** SMB, SFTP, FTP, Cloud (Google Drive)

**Automatic Check:**
When you add a new network resource, the app automatically runs a speed test in the background. Results (Read/Write speed) are saved to the resource settings.

**Manual Check:**

1. Go to **Manage Resources**
2. Edit a network resource (pencil icon)
3. Scroll down to the bottom
4. Tap **"Speed"** button
5. Wait ~15 seconds for "Analyzing speed.."
6. See results:
   - **Read Speed (Mbps)**
   - **Write Speed (Mbps)**
   - **Recommended Threads** (for optimal performance)

---

## How to Set Up Quick Sort Folders

**Method 1: From Settings**

1. **Settings** → **Operations** tab → **Quick Sort destinations**
2. Tap **"Add to Quick Sort"**
3. Select an existing folder from list
4. Folder gets assigned number (0-9) and color
5. Repeat for up to 30 folders

**Method 2: From Folder Settings**

1. Main screen → **Long-press on folder**
2. Tap **"Edit"** (pencil icon)
3. Enable **"Mark for Quick Sort"**
4. Tap **"Save"**

**Using Quick Sort:**

While viewing files:

- Tap **numbered button** (0-9) on command panel
- OR tap **bottom-left corner** (COPY zone)
- OR tap **bottom-center corner** (MOVE zone)

File is instantly copied/moved to that folder!

**With a keyboard or TV remote:** connect one and the destination buttons get a digit badge - press the matching number key to fire that destination instantly, no tapping needed.

---

## How to Use Touch Zones

**What are Touch Zones?**

The screen is divided into 9 invisible areas for quick actions:

```
┌─────────┬─────────┬─────────┐
│  BACK   │  COPY   │ RENAME  │
│   (1)   │   (2)   │   (3)   │
│         │         │         │
├─────────┼─────────┼─────────┤
│  PREV   │  MOVE   │  NEXT   │
│   (4)   │   (5)   │   (6)   │
│         │         │         │
├─────────┼─────────┼─────────┤
│ COMMAND │ DELETE  │  PLAY   │
│   (7)   │   (8)   │   (9)   │
│         │         │         │
└─────────┴─────────┴─────────┘
```

**Legend:**

1. **BACK** - Return to file list
2. **COPY** - Copy file to destination
3. **RENAME** - Rename current file
4. **PREV** - Go to previous file
5. **MOVE** - Move file to destination
6. **NEXT** - Go to next file
7. **COMMAND** - Open command menu
8. **DELETE** - Delete current file
9. **PLAY** - Start/Stop slideshow

**Enable Overlay (recommended for beginners):**

1. Settings → Playback
2. Enable **"Always show touch zones overlay"**
3. Now you'll see a semi-transparent grid

**Try it:**

1. Open any photo
2. **Tap top-right corner** → Next file
3. **Tap top-left corner** → Previous file
4. **Tap middle-right corner** → Delete file
5. **Tap middle-left corner** → Copy file

**Disable if not needed:**
Settings → Playback → "Always show touch zones overlay" = OFF

Then use **command panel buttons** instead.

---

## How to Edit Photos

**Supported operations:**

- Rotate (90°, 180°, 270°)
- Flip (horizontal, vertical)
- Filters (Grayscale, Sepia, Negative)
- Adjust (Brightness, Contrast, Saturation)

**Steps:**

1. **Open a photo** in full-screen viewer
2. Tap **"Edit"** button (or middle-left touch zone)
3. **Choose operation:**
   - Rotate: Tap rotate icon
   - Flip: Tap flip icon
   - Filter: Select from list
   - Adjust: Use sliders
4. Tap **"Save"**

**Notes:**

- Original file is **overwritten** (no undo!)
- Works for **local and network files**
- Supports: JPG, PNG, WEBP

---

## How to Create Slideshow

**Steps:**

1. **Open any folder** with photos
2. Tap **first photo** to open viewer
3. Tap **"Play"** button (or bottom-right touch zone)
4. Slideshow starts automatically

**Customize speed:**

1. **Edit folder settings:**
   - Main screen → Long-press folder → Edit
2. Change **"Slideshow Interval":**
   - Fast: 2 seconds
   - Normal: 5 seconds
   - Slow: 10 seconds
3. Tap **"Save"**

**Controls during slideshow:**

- **Tap screen** → Pause/Resume
- **Swipe left/right** → Skip files
- **Tap "Stop"** → Exit slideshow

---

## How to Create Slideshow with Background Music

**Requirements:**

- At least one folder/resource with audio files (MP3, FLAC, etc.)
- **Available in:** Standard, Lite, Legacy, XR / noLegal (Photos has no audio support)

**Setup:**

1. **Settings** → **Media** tab → **Images**
2. Enable **"Play music during slideshow"**
3. Tap **"Select Music Source"** button
4. Choose a resource that contains your music files
5. Tap **"Save"** or close settings

**Playing slideshow with music:**

1. **Open any folder** with photos/images
2. Tap **first photo** to open viewer
3. Tap **"Play"** button (or bottom-right touch zone)
4. Slideshow starts with background music playing

**How it works:**

- Music plays randomly from the selected music resource
- When a track ends, next random track starts automatically
- Music continues playing during image transitions
- Music stops when you exit slideshow or pause

**Notes:**

- Music plays only for **images and GIFs** (not for videos/audio)
- When slideshow shows a video, music automatically pauses
- Music resumes when returning to images
- Works with local and network music sources (SMB, SFTP, FTP)

**Customize music selection:**

- Add multiple music files to your music resource folder
- App will randomly shuffle through all audio files
- Organize music in subfolders if music resource has "Include Subfolders" enabled

**Troubleshooting:**

- If no music plays: Check that music resource contains at least one audio file
- If music stutters on network: Use local folder or faster network connection
- For SMB music: Ensure SMB resource uses `file://` protocol (see TROUBLESHOOTING.md)

---

## How to Protect Folder with PIN

**Steps:**

1. Main screen → **Long-press on folder**
2. Tap **"Edit"** (pencil icon)
3. Scroll to **"PIN Code"** field
4. Enter **4-6 digit PIN** (e.g., 1234)
5. Tap **"Save"**

**Now:**

- Opening this folder requires PIN
- Prevents unauthorized access
- Applies to browsing and editing

**Remove PIN:**

- Edit folder → Clear PIN field → Save

**Forgot PIN?**

- No recovery option (by design for security)
- You'll need to remove and re-add the folder

---

## How to Work with Folders (select, copy, move)

When subfolders are shown as separate items in the list, a folder row behaves like a file row.

**Turn folder rows on:** **Settings** → **General** → **Show subfolders separately**. The same switch exists per resource in the resource editor.

**Steps:**

1. Tap the checkbox on a folder row, or long-press the row, to select a single folder. A short tap still opens the folder.
2. Use the row's **⋮** menu, or the selection action bar, to choose **Copy**, **Move**, **Rename** or **Delete**.
3. Pick the destination. Files and folders in the same selection travel together in one operation.
4. The destination gets the whole structure - every subfolder and file inside the source folder.

**Across resource types:** a folder can be copied or moved between the device, SMB, SFTP, FTP and cloud resources - the structure is recreated on the receiving side.

**What is refused, and why:** a destination inside the folder itself, or the folder's own current location, is rejected before anything is copied; a destination picked through the system folder chooser that has no real file path cannot receive folders. The message names the reason so you can pick another destination.

**Cancelling:** a folder transfer shows progress and can be stopped. Whatever was already written stays at the destination - check the folder before starting again. A move deletes each source item only after its copy succeeded, so nothing is lost in between.

**Sending it to the background:** you do not have to sit and watch the progress dialog. Dismiss it and the transfer keeps running, staying visible in Browse as a strip along the bottom showing the operation, the percentage and the file it is on right now. Tap that strip to bring the full progress dialog back, cancel included.

---

## How to Empty Trash

Deleted files go to `.trash/` folders and stay there until manually emptied.

**Method 1: Clear All Trash**

1. **Settings** → **Operations** tab → **File deletion and trash**
2. Tap **"Clear Trash"**
3. Confirm deletion
4. All `.trash/` folders across all resources are emptied

**Method 2: Per-Folder**

1. Use a file manager app
2. Navigate to folder (e.g., `/storage/emulated/0/DCIM/Camera`)
3. Find `.trash/` subfolder
4. Delete manually

**Warning:** This is **permanent deletion**! Files cannot be recovered.

---

## How to Backup Settings

**Export Settings:**

1. **Settings** → **General** tab → **Backups, restore and settings export**
2. Tap **"Export All Settings to File"**
4. Choose location (e.g., Downloads)
5. File saved as `fastmediasorter_backup.xml`

**Restore Settings:**

1. **Settings** → **General** tab → **Backups, restore and settings export**
2. Tap **"Import Settings from File"**
4. Select backup file
5. Tap **"Restore"**
6. App restarts with restored settings

**What's included:**
✅ Quick Sort folders
✅ Display preferences
✅ Slideshow intervals
✅ Network credentials (encrypted)
✅ Favorites
✅ Safe Mode settings

**NOT included:**
❌ Thumbnail cache
❌ Trash contents  

---

## How to View Text and PDF Files

**1. Enable Support:**

1. **Settings** → **Media** tab → **Documents**
2. Enable **"Support text files (.txt, .md, .log, .json, .xml)"** and **"Support PDF documents"**
3. **Rescan** your folders to find the new files.

**2. Filter by Media Type:**

1. Tap the **Filter icon** (funnel) on the main screen (top right).
2. Use checkboxes to select media types:
   - Images
   - Videos
   - Audio
   - GIFs
   - **Text** (New)
   - **PDF** (New)
3. Tap **"Apply"** to see only selected files.

**3. Text Viewer:**

- Tap any **.txt, .md, .log, .json, .xml** file.
- **Scroll** to read.
- **Copy text:** Long press to select and copy.

**4. PDF Viewer (New Features):**

- Tap any **.pdf** file.
- **Navigation Control Bar (Bottom):**
  - **Previous/Next:** Large buttons at the edges.
  - **Zoom In (+):** Magnify page.
  - **Zoom Out (-):** Shrink page.
- **Gestures:**
  - **Swipe UP:** Go to Next page.
  - **Swipe DOWN:** Go to Previous page.
  - **Pinch:** Zoom in/out naturally.
  - **Double-tap:** Reset zoom.
  - **Zoom carries over:** The next page opens at the zoom and position you were reading at; double-tap brings the whole page back.
- **Pan:** Drag to move around when zoomed in.
- **Select text by long-pressing (Android 15+):** press and hold a word to select it straight from the page's own text layer - no OCR pass, no waiting. If the same word appears several times on the page, the one under your finger is selected, not the first one. Drag the handles to extend the selection, then copy or translate it.

---

## How to Read E-Books (EPUB)

**Requirements:**

- **Settings** → **Media** tab → **Documents** → **Support EPUB e-books** must be enabled (on by default)
- Supported format: `.epub` (DRM-free)

**Features:**

- **Chapter Navigation:** Swipe left/right or use command panel buttons
- **Table of Contents:** Tap the list icon (📋) to jump to a specific chapter
- **Font Size:** adjustable (14px - 32px)
- **Search:** Find text within the current book
- **Themes:** Automatically adapts to Light/Dark mode

**Controls:**

1. **Open an EPUB file** from the file list
2. **Tap screen** to toggle command panel
3. **Use bottom controls:**
   - `Previous` / `next`: navigate chapters
   - `- A` / `+ A`: decrease/increase font size
   - `Search` (🔍): search text
   - `TOC` (📋): open table of contents
4. **Swipe gesture:** switch chapters naturally

**Note:** Works seamlessly with local files and network streams (SMB/SFTP/Cloud). Large books (>50MB) over slow networks might take a few seconds to load initially.

---

## How to Open Network Files in External Apps

**Available for:** SMB, SFTP, FTP files

**Use case:** You want to open a document, photo, or video from your network drive in a specialized external app (e.g., MS Office, Adobe Acrobat, VLC Player).

**Steps:**

1. **Browse to the file** on your network resource
2. **Tap the file** to open it in the player/viewer
3. **Tap the ⓘ (Info) button** in the top toolbar
4. **Tap "Download and Open"** button
5. **Wait for download** - progress dialog shows percentage
6. **Choose app** from the Android app chooser

**What happens:**

- File is downloaded to your `Downloads` folder
- Progress is shown in a dialog (0-100%)
- After download completes, Android shows app chooser
- You can open the file in any compatible app

**Supported protocols:**

- ✅ SMB/CIFS network shares
- ✅ SFTP servers
- ✅ FTP servers
- ❌ Cloud storage (not yet implemented)

**Tips:**

- Downloaded files remain in `Downloads` folder
- You can delete them manually later via file manager
- Works with all file types (images, videos, documents, etc.)
- For large files, download may take several minutes

**Example use cases:**

- Edit a network document in MS Word
- Play network video in VLC Player
- View network PDF in Adobe Acrobat
- Share network photo via messaging apps

---

## How to View Song Lyrics

**Requirements:**

- Audio file (MP3, FLAC, etc.) with Artist and Title metadata.
- **Internet connection** is required (uses api.lyrics.ovh).

**Steps:**

1. **Play an audio file** in the full-screen player.
2. Tap the **"Lyrics"** button in the top command panel (or command menu).
   - *Note: Button is only visible for audio files.*
3. Wait for the search to complete.
4. Lyrics will be displayed in a scrolling dialog.

**Search Logic:**

1. App searches by **Artist + Title** tag.
2. If tags are missing, it tries to parse the **Filename**.

---

## Auto-Translation

Automatically translate text from images, PDF, and text files using a **Hybrid OCR System** (Google ML Kit + Tesseract).

**Key Features:**

- **Hybrid Engine:** Uses Google ML Kit for fast Latin script recognition and **Tesseract** for high-quality Cyrillic (Russian, Ukrainian) recognition.
- **Offline:** Works entirely on-device (after initial model download).
- **Smart Overlay:** Translated text overlays the original text in readable paragraphs.

**Setup:**

1. **Settings** → **Media** tab → **Other**
2. Enable **"Enable Translation"**
3. Select **Source Language**:
   - **"Auto" (Recommended):** Automatically selects the best engine (Tesseract for Cyrillic, ML Kit for others).
   - **Specific Language:** Forces a specific model (e.g., "Russian" forces Tesseract).
4. Select **Target Language** (e.g., English).

**How to use:**

1. Open an **Image**, **PDF**, or **Text** file.
2. Tap the screen to show the **Command Panel**.
3. Tap the **"Translate"** button (A→文 icon).
4. **First run:**
   - If using ML Kit: Confirm downloading the language model (~30MB).
   - If using Tesseract (Cyrillic): Confirm downloading OCR data (~15MB).
5. The translated text will appear in an overlay.

**Note:** Tesseract initialization (for Cyrillic) might take 1-2 seconds longer than ML Kit.

## Home-Screen Smart Widgets

**Available in:** all flavors - the widget set ships in every build; each widget follows its own capability, so the voice-recorder widget needs a build with microphone support (not Lite or Photos) while the photo-frame and resource widgets work everywhere

**Quick Path**

1. Go to your Android Home Screen, long-press, and select **Widgets**.
2. Drag a **FastMediaSorter** widget (like 1×1 Quick Voice Recorder or Camera OCR) to your screen.
3. Configure the destination folder and capture settings, then tap **Save**.
4. Use the widget to execute tasks in one tap directly from your Home Screen.

**Scenario Walkthrough**

- Use 1×1 widgets as dedicated launcher icons to start background actions instantly (e.g., tap once to start recording voice, tap again to save it to your NAS).
- Set up a **Scheduled Tasks widget** to monitor background file transfers or trigger a "Run All" operation.
- Place a **Random Photo Frame widget** to display a rotating slideshow of family photos fetched directly from an SMB share.

**When It Helps**

- You want quick shortcuts on your home screen for daily captures (receipts, voice memos) without opening the main app interface.
- You need clear widgets to control media or trigger scheduled operations instantly.

**Avoid This**

- Do not attempt to add widgets if your Android launcher restricts custom widget creation.

---

## How to Use the App as Your Home Screen

FastMediaSorter can take over your device's home screen and show its own desktop instead - your folders, a clock, the weather, your apps, and a taskbar along one edge. If you have ever used a Windows desktop it will feel familiar: things stay where you put them, and a Start button opens the menu. This is called launcher mode, and it ships in the **Standard** and **noLegal** builds only.

**Turning it on:**

1. Open **Settings → General** and turn on **Make this app the home screen**.
2. Android asks you to confirm. On Android 10 and newer it is a single question - "Allow FastMediaSorter to be your Home app?" - so just allow it. On older versions the classic choice appears the next time you press Home: pick FastMediaSorter and tap **Always**, or **Just once** if you only want to try it for now.
3. Press Home. The desktop appears, already filled with about a dozen useful things - a clock, the weather, your folders, a search box - so day one is not an empty grid.

On a brand-new install there is a shortcut: tick **Use as home screen** on the first welcome page. That does not interrupt the setup with a system dialog - Android's confirmation comes up the first time you open **Settings → General** afterwards.

**What lives on the desktop:**

| Cell type | What it does |
|-----------|--------------|
| Resource shortcut | Opens a folder you added - and you choose whether it opens in browse, slideshow or play mode |
| Gadget | A clock with seconds (tap for alarms), the weather where you live, what is playing right now, a translator, and two dozen more |
| App shortcut | Starts any installed app; long-press lists that app's own quick actions |
| Contact cell | Opens a person's card, calls them, sends an SMS, or opens their messenger conversation |
| App widget | The same widgets the app offers for the Android home screen, placed here instead |

**The taskbar and the Start menu:**

- The taskbar sits along the bottom edge and holds the Start button, the apps you used recently, the ones you pinned, and a small tray with the clock, battery, network and SIM signal.
- Prefer it along the top? **Settings → General → System launcher settings → Taskbar → Taskbar position** switches between **Bottom** and **Top**. The Start menu follows the bar and drops down from above when the bar is up there.
- The Start button opens the menu: open FastMediaSorter, your resources, add a resource, Android settings, app settings, launcher settings, edit desktop contents, and at the end restart, power off and **Exit launcher mode**. Restart and power off only work if your device lets an ordinary app do that - on most phones they will simply do nothing.

**Your apps:** the app grid groups apps into sections, each with a small header. Tap a header to collapse a section you rarely open; collapsed headers tuck in next to each other, so the desktop gets shorter instead of leaving gaps. A fresh desktop also gets a **GOOGLE** section holding the Google apps you already have installed. Long-press any app in the list for **Put on desktop** and **Pin to taskbar**.

**Rearranging it:**

- Long-press an empty square of the desktop. Four choices appear: **Add an item..**, **Edit the desktop**, **Wallpaper**, **Launcher settings**. The new cell lands exactly on the square you pressed.
- **Add an item..** opens a picker: an app, a feature, one of your folders, a radio stream, a person, a system action, a scheduled operation, a gadget, or an action. Among the gadgets are the now-playing card - it shows whatever is playing on the device and takes you to that player with one tap - and the translator cell.
- **Edit the desktop** turns on edit mode, the same as **Edit desktop contents** in the Start menu. While editing: drag a cell to move it, drag a gadget's corner handle to resize it, tap **+** to add something, and choose **Remove from desktop** on a cell to take it off. Tap **Done** when you are finished.
- Sharing the device with someone? Turn on **Lock desktop** in launcher settings - the long-press then does nothing, so the layout cannot be nudged by accident.
- Other apps can put their own shortcuts here, exactly as they would on any other home screen.

**Portrait and landscape are two separate desktops.** What you arrange upright is not what you get when you turn the device sideways - each orientation keeps its own layout and its own collapsed sections. The app mentions this once, the first time you rotate a desktop you have arranged. The settings themselves - taskbar position, density, wallpaper - are shared by both.

**Fitting more, or less, on the screen:** **Settings → General → System launcher settings → Desktop → Grid density** offers **Sparse**, **Standard**, **Dense** and **Very dense** - roomier cells, or more shortcuts per screen.

**Going back to your old home screen** - any of these three:

- Open the Start menu, choose **Exit launcher mode**, and confirm.
- Turn **Make this app the home screen** off in **Settings → General**.
- Go straight to Android's own list of home apps: **Settings → General → System launcher settings → System → Change home screen**.

Your desktop layout is kept either way, so switching the mode back on brings it back exactly as you left it.

**One honest warning.** A few devices refuse to remember the choice. Some cheap aftermarket car radios and other built-in Android boxes force their factory home screen back every time they start up, whatever you picked. That is the device's own firmware overriding you, not a fault in the app, and no app can work around it. If yours behaves that way, pick FastMediaSorter as the home app again after a restart - and if it still will not stick, that device simply does not allow it.

---

## How to Use FMS on Android TV Box

FastMediaSorter runs on any Android TV box or set-top box (Xiaomi Mi Box, Nvidia Shield, Amazon Fire TV, generic Android boxes). No touchscreen required - the app is fully operable via TV remote or Bluetooth keyboard.

**What you need:**

- Android TV box running Android 8.0+ (Standard/Lite/Photos) or Android 6.0+ (Legacy flavor)
- TV remote with D-pad, or a Bluetooth keyboard
- Optional: home NAS (SMB), USB drive, or SD card with media

**Navigation with a TV remote:**

| Button | Action |
|--------|--------|
| D-pad Up/Down/Left/Right | Move focus between items |
| OK / Enter | Open item or confirm |
| Back | Go to previous screen |
| Backspace | Navigate one folder up in Browse |
| Red | Delete selected file(s) |
| Green | Copy selected file(s) |
| Yellow | Move selected file(s) |
| Blue | Rename selected file |
| Channel Up / Channel Down | Previous / next file in player |

**Steps:**

1. Install the app from Google Play or sideload an APK. Standard flavor is recommended.
2. On the main screen, press **OK** on the (+) button to add a resource.
3. Choose **Local Folder** for USB/SD storage, or **Network folder** to connect to a NAS via SMB.
4. After adding the resource, navigate into it with D-pad + OK to browse files.
5. Open any video, image, or audio file - the player works fully via remote.
6. To start a slideshow, open an image folder and navigate to the **Slideshow** button in the command bar.
7. To add background music to the slideshow, go to **Settings → Media → Images**, enable **Play music during slideshow**, and select your music resource.

**Tips:**

- Hold D-pad Up/Down to accelerate scrolling through long file lists.
- Press **F1** on a Bluetooth keyboard to open a surface-specific shortcut reference on any screen.
- TV remote color keys can be reassigned in **Settings → Operations → Controls & Keybindings**.

---

## How to Record Your Screen

**Available in:** Standard, XR/noLegal

**Steps:**

1. Start it from the main-screen overflow menu (**Screen video recording**), the Quick Launch panel, or the edge gesture's **Start screen recording** action.
2. Confirm Android's prompt to share your screen or just this app - it appears every time you start a recording and can't be skipped.
3. A small pill in the corner shows **Recording screen**, with pause/resume and stop controls. A notification also offers **Stop**.
4. Tap **Stop** when you're done.

**What happens:**

- The recording captures everything on screen, including other apps you switch to, together with audio.
- The finished video is saved to your device's Movies folder.

**Note:** The Android confirmation step is a system safeguard for anything that records your screen - it's not something the app can turn off.

---

## How to Record a Voice Note

**Available in:** Standard, Legacy, XR / noLegal

**Steps:**

1. Start a recording from the **Voice recording** item in the overflow menu, the **Quick Recorder** home-screen widget, or the edge gesture's **Start audio recording** action.
2. Speak - a **Recording..** indicator (or a floating pill over whatever app is in front) shows it's running.
3. Tap **Stop and save** (or tap the widget/gesture again) to finish.

**What happens:**

- The recording saves to the microphone destination you've chosen in Settings, or to your device's Recordings folder if none is set.
- Starting a voice note from the widget or the edge gesture works even while you're using another app - a small floating control stays on top so you can stop it without switching back.

**Where to set the save folder:** Settings → Operations → Voice recorder.

---

## How to Use the In-App Camera

**Available in:** Standard, Lite, Photos (photo only), Legacy, XR/noLegal

**Steps:**

1. In Browse, open the toolbar or overflow menu and tap **Capture with camera** (photo) or **Record video**.
2. Switch between **Photo** and **Video** right on the camera screen if you change your mind.
3. Set your zoom with a preset chip (0.5x/1x/2x..) or the slider underneath - both stay in sync.
4. Tap the aspect button to shape the frame - **4:3**, **16:9** or **Full screen**. The viewfinder itself changes, so what you see is what the saved photo will be, and the choice is remembered next time you open the camera (16:9 until you change it).
5. Tap the shooting-scenario button to pick how the shot is taken - normal, night, portrait, selfie, macro or sport. Macro jumps to the dedicated close-focus lens, selfie flips to the front camera, sport keeps the exposure short so motion freezes. Only the scenarios your device can actually deliver are listed, the active one is named on the button, and changing the lens by hand returns the camera to normal.
6. Tap the shutter (or the record button) to capture. The result saves straight to the resource - local or network - you were browsing.

**Tips:**

- The edge gesture's **Start video recording** action opens the camera already in Video mode and starts recording as soon as the preview is ready - quick, but that particular shortcut saves to your device's Movies folder rather than the browsed resource.
- Turn on **Geotag photos** next to the camera settings to embed the GPS location into each captured JPEG - it is off until you enable it. **File Info** then shows the capture date and the photo's EXIF GPS spot as a tappable link that opens in your maps app or browser.

**Where to find camera settings:** Settings → Operations → Photography.

---

## How to Find and Delete Duplicate Files

**Steps:**

1. Open a folder in Browse, then open the **overflow menu** (⋮) in the toolbar.
2. Tap **Find Duplicates** to review matches yourself, or **Find and Delete Duplicates** to remove them right away.
3. For **Find Duplicates**, the app pre-selects every copy except the oldest one in each group - adjust the selection, then tap **Delete Selected** and confirm.
4. **Find and Delete Duplicates** removes the same pre-selected copies right after the scan, with no confirmation step - use **Find Duplicates** first if you want to double-check before anything is deleted.

**Clean up by size instead:**

1. From the same overflow menu, tap **Delete by Size..**
2. Choose **Smaller than** or **Larger than**, set a size, and tap **Analyze**.
3. Review the count and space it would free, then tap **Delete Files** to confirm.

**Notes:**

- The scan matches files by content in three passes - size, then a quick hash, then a full SHA-256 check - so renamed duplicates are still caught.
- Deleting by size shows how much space you will free before anything is removed; network and cloud sources skip the trash, so that delete is immediate and permanent.

---

## How to View Your Usage Statistics

**Steps:**

1. Go to **Settings → General → Statistics collection** and turn it on.
2. Tap **Statistics** (it appears right below the toggle) to open the dashboard.

**What you'll see:**

- Summary cards for files sorted, space freed, and time spent playing media.
- A by-type breakdown (images, videos, audio, documents..).
- Collapsible sections with more detail: operations, capture, viewing, editing, sources, and general usage.

**Share a report:**

- **Send to author** opens your email app with a summary attached, addressed to the developer.
- **Export** shares the same summary through the regular Android share sheet, so you can save or send it anywhere.

**Note:** Everything stays on your device until you choose to send or export it - see the FAQ for the privacy details.

---

## How to Use an SD Card or Connected Drive

A memory card or a USB drive the phone has mounted holds resources exactly like built-in storage.

**Steps:**

1. Open **Add resource** and start adding a local folder. The **Removable media** section appears only while something is connected, and lists every volume with its name and free space.
2. Tap the volume. If the app cannot reach it by path, it explains why and opens the system folder chooser - pick the same volume there and grant access to the folder you want.
3. The resource joins the list with a removable-medium icon, so a card resource is recognisable at a glance.

**Moving and copying:** whole folders travel to a card and back with their full subfolder structure, the same way they do between the device and a network resource.

**Not enough room:** a copy or move that does not fit is refused before it starts, and the message names the medium and how much space is missing - free space there or pick another destination.

**When the medium is ejected:** its resources are marked unavailable rather than removed. Connect the card again and they work without being set up a second time.

**On Android 6:** the system does not report mounted volumes to apps, so the removable section stays empty on those devices.

---

## How to Choose Where Captures and Downloads Are Saved

Photos from the in-app camera, screenshots, snapshots and auto-downloaded files each write to a folder you pick, and that folder does not have to be one of your resources.

**Steps:**

1. Open the setting for what you are saving - capture, screenshot, snapshot or auto-download.
2. Choose the destination folder. The system folder browser opens, so you can point at any local folder, including one you have never added to the app.
3. That folder becomes the write destination for that setting only. It stays out of your general resource list, so picking a scratch folder for screenshots does not clutter the main screen.

**Tips:**

- Each of the four settings has its own destination - screenshots and camera photos can land in completely different places.
- Pointing several of them at one folder is fine if you would rather have everything in a single spot.

### Capture file names

New captures use the `prefix_yyMMdd_HHmmss` pattern. The stable prefixes are `photo`, `screenshot`, `audio`, `video`, `screen_video`, and `video_frame`, so the filename identifies its source. If a name already exists in the destination folder, the app adds the ` (2)` suffix before the extension. A filename entered manually in the camera remains an override and is not changed.

---

## How to Receive Files Shared from Another App

Any app's share sheet can send files to FastMediaSorter, which then copies them where you want.

**Steps:**

1. In the other app, share the file or files and pick **FastMediaSorter**.
2. Choose the destination folder in the receive screen.
3. Start the copy.

**You do not have to wait for it.** The copy keeps running after the receive screen closes, with a notification showing progress while it works and a result notification when it finishes. Leave the app, lock the device, carry on - the transfer is not tied to that screen staying open.

Available in the Standard, Lite, Photos and Legacy builds.

---

## How to Use the Built-In Programs

**Available in:** all flavors - the programs menu and the panel ship in every build, but each program follows its own capability: Network Monitor needs Standard or noLegal, the Wear companion needs Standard, Legacy or noLegal, and the mini-game is absent from XR and noLegal. The calculator, the front flashlight and System information are in every build.

Besides browsing and playing files, the app carries a set of small built-in programs - a calculator, a screen lamp, a network monitor, a voice recorder and more. They are off by default: each one is switched on by its own setting, and most of the dedicated switches sit together in **Settings → Operations**.

**Quick Path**

1. Go to **Settings → Operations** and turn on what you want - for example **Calculator**, **Front flashlight**, **Network Monitor**, **Mini-game** or **System information**.
2. Open the main window dropdown menu. The programs you enabled are listed there.
3. Tap one to run it.

**Where a program shows up**

A program can be offered on up to four surfaces, and every surface takes its contents and its order from the same single list, so they never drift apart:

- **Programs menu** - the main-window dropdown, and the programs panel that repeats it.
- **App-launch panel** - the quick-access overlay.
- **Home-screen widget** - only for the programs that have one; pin it from the app's own widget picker.
- **Launcher desktop** - when you use the app as your home screen, switching a program on adds its cell automatically.

**What is in the set**

In the order they appear:

- **Quick capture** - take a photo straight into the app.
- **Voice recording** - record a voice note.
- **Calculator** - a scientific calculator with history and memory slots.
- **Network Monitor** - live readings for the active link, Wi-Fi, mobile, Bluetooth and location.
- **Photo OCR translate** - shoot text and translate it.
- **Screen video recording** - record the screen.
- **Download by link** - fetch a file from a pasted link.
- **Mini-game** - the small game built into the app.
- **System information** - a device report reachable without opening Settings.
- **Wear Companion** - the watch screen, in builds that carry the watch bridge.
- **Front flashlight** - turns the screen itself into a lamp: it opens white at full window brightness, a vertical swipe changes the brightness, a small top-left button picks and remembers another colour, and a single tap closes it. Only the window brightness is touched, so your device setting is unchanged afterwards.

The panel and the launcher additionally carry direct camera shortcuts - take a photo and send it on, take a photo and edit it, take a photo and translate it, start a video recording, and open the camera folder.

**When It Helps**

- You want a calculator or a torch without leaving the app, or without hunting for a separate app on a crowded phone.
- You run the app as your home screen and want a one-tap cell for a tool you reach for often.

**Avoid This**

- Do not look for the front flashlight in the dropdown menu - it is offered on the app-launch panel and the launcher desktop only.
- Do not expect every program in every build - the list above is the full set, and a build without the underlying capability simply does not show that entry.

---

## Need More Help?

- 📖 **Quick Start:** [QUICK_START.md](QUICK_START.md)
- ❓ **FAQ:** [FAQ.md](FAQ.md)
- 🔧 **Troubleshooting:** [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- 🐛 **Report Issue:** [GitHub](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

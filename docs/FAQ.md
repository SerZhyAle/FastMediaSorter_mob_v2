---
layout: default
title: "❓ Frequently Asked Questions (FAQ)"
permalink: /docs/FAQ.html
---
# ❓ Frequently Asked Questions (FAQ)

---

## General Questions

### What is FastMediaSorter?
FastMediaSorter v2 is an Android app for quickly organizing photos, videos, and audio files from local folders, network drives (SMB/SFTP/FTP), and cloud storage (Google Drive, OneDrive, Dropbox).

### Is it free?
Yes! FastMediaSorter v2 is completely free and open-source.

### What Android version do I need?
Android 8.0 (API 26) or newer. The **Legacy** flavor supports Android 6.0 (API 23) or newer.

### Does it require internet?
**No** for local files. **Yes** for network drives and cloud storage.

### Does the app have widgets?
Yes! FastMediaSorter v2 offers **two types of widgets**:
1. **Resource Shortcut Widget**: Quickly open any of your added folders (Local, Network, or Cloud) directly from your home screen.
2. **Continue Reading Widget**: Launches the app and immediately starts slideshow mode - perfect for quick photo viewing sessions.

---

## File Operations

### Where do deleted files go?
Deleted files move to a `.trash/` folder in the same location (soft-delete). They're not permanently deleted until you:
- Tap **"Empty Trash"** in Settings → Quick Sort, OR
- Manually delete the `.trash/` folder

### Can I undo a delete/move?
**Yes!** Tap the **"Undo" button** (or bottom-right touch zone) within a few seconds after the operation.

> ⚠️ **Note:** Undo is not available for network file deletions (they are hard-deleted immediately).

### What's the difference between Copy and Move?
- **Copy:** Creates a duplicate, original stays in place
- **Move:** Relocates the file, removes from original location

### What is File Manager Mode?
**File Manager Mode** allows you to use the app as a full-featured file browser across all directories. In this mode, the app bypasses standard media filters and displays all files (including ZIP, RAR, APK, EXE, PDF, etc.). You can perform standard file operations like copying, moving, renaming, sharing, and deleting. For unsupported binary files, a bottom sheet is automatically opened, allowing you to manage the file or open it using external applications.


---

## Network & Cloud

### How do I connect to my home NAS (network drive)?
1. Settings → Add Folder → **SMB/Network Drive**
2. **Option A - Automatic:** Tap **"Scan Network"** to automatically discover available devices on your network
3. **Option B - Manual:** Enter server address: `\\192.168.1.100\share` or `smb://192.168.1.100/share`
4. Enter username and password
5. Tap "Connect"

**Common issues and fixes:**

| Problem | What to try |
|---------|------------|
| "Connection refused" | Open Windows Firewall → allow **TCP port 445** inbound. Or temporarily disable the firewall to test |
| "Wrong password" | Try leaving Username blank (guest access). If you use a Microsoft account, enter your **full email** as the username |
| "Host not found" | Make sure phone and PC are on the **same Wi-Fi router**. AP Isolation (a router security setting) can block device-to-device traffic - disable it in router settings |
| Scan finds nothing | Disable VPN on phone. Enable **Network Discovery** in Windows (Control Panel → Network and Sharing Center → Advanced sharing settings). Then try entering IP manually |
| Very slow browsing | Edit the resource → run **Speed Test**. If below 5 Mbps, switch phone to 5 GHz Wi-Fi band. Disable video thumbnails for slow connections |
| Works on Wi-Fi but not mobile data | Expected - SMB is a local network protocol only, it cannot work over mobile data |

→ Full walkthrough: [SMB Setup Guide](howto/scenario-smb-setup.md)

### How do I connect to Google Drive?
1. Settings → Add Folder → **Google Drive**
2. Tap "Sign in with Google"
3. Grant permissions when prompted
4. Your Drive folders will appear

**Note:** Files are NOT downloaded automatically - they stream on-demand.

### How do I connect to OneDrive?
1. Settings → Add Folder → **OneDrive**
2. Tap "Sign in with Microsoft"
3. Grant permissions when prompted
4. Your OneDrive folders will appear

### How do I connect to Dropbox?
1. Settings → Add Folder → **Dropbox**
2. Tap "Sign in with Dropbox"
3. Grant permissions when prompted
4. Your Dropbox folders will appear

### Can I use SFTP or FTP?
**Yes!** Select **SFTP** or **FTP** when adding a folder:
- **SFTP:** Secure, requires SSH server (port 22)
- **FTP:** Less secure, older protocol (port 21)

### Why are thumbnails not loading for network files?
Network thumbnails generate **on-demand** to save bandwidth. Scroll slowly or wait a few seconds for them to appear.

If thumbnails never load at all:
- Check that the connection is active: tap the resource → if the folder opens, the connection is fine
- Edit the resource → make sure **"Load thumbnails"** is enabled
- For very slow connections: disable thumbnails entirely to avoid timeouts (Edit resource → disable thumbnails)

### Connection keeps dropping / files fail to open mid-playback
- Check that your phone's Wi-Fi is stable (not switching between 2.4 and 5 GHz bands)
- Some routers disconnect idle SMB sessions - edit the resource → enable **"Reconnect on error"** if available
- For video playback over SMB: run Speed Test (Edit resource → Speed Test). You need at least 10 Mbps for 1080p video

---

## Quick Sort & Destinations

### What are "Quick Sort" folders?
Quick Sort folders are pre-configured target folders for fast file sorting. You can assign up to 30 folders with numbered buttons.

### How do I set up Quick Sort?
**Method 1:** Settings → Quick Sort → "Add to Quick Sort"  
**Method 2:** Edit any folder → Enable "Mark for Quick Sort"

### How do I use Quick Sort while viewing files?
1. Open a photo/video in full-screen
2. Tap a **numbered button** (0-9) on the command panel, OR
3. Tap the **bottom-left corner** (COPY zone) or **bottom-center** (MOVE zone)

### Quick Sort buttons are not showing
Make sure you have added at least one destination folder first: Settings → Quick Sort → **"Add to Quick Sort"**. Buttons only appear when at least one destination is configured.

### I accidentally sent a file to the wrong folder
Tap **Undo** immediately (bottom-right of the command panel) - available for a few seconds after each operation. If you missed the window, go to the destination folder and move the file back manually.

---

## Touch Zones

### What are "Touch Zones"?
Touch Zones are invisible areas on the screen that trigger actions when tapped. The screen is divided into a 3x3 grid:

```
┌─────────┬─────────┬─────────┐
│ PREV    │ COMMAND │  NEXT   │
├─────────┼─────────┼─────────┤
│ COPY    │  INFO   │ DELETE  │
├─────────┼─────────┼─────────┤
│ MOVE    │  MENU   │  PLAY   │
└─────────┴─────────┴─────────┘
```

### How do I see Touch Zones?
Settings → Playback → Enable **"Always show touch zones overlay"**

### Can I disable Touch Zones?
Yes, just use the **command panel buttons** instead. Touch Zones are optional.

---

## Input & Controls

### Does it support physical keyboards and gamepads?
**Yes!** Full keyboard, mouse, and gamepad input is available across all screens. Press **F1** on any screen to see the active key bindings for that surface.

### How do I remap controls / change keybindings?
Settings → **Input** → **Keybindings** - reassign any action to a different key, button, or gamepad input. The app ships with 70 built-in defaults; tap **Reset** to restore them. Conflicts are highlighted automatically.

### How do I download a media file from a URL?
Share any `http(s)` link to FastMediaSorter via the Android **Share sheet** (from a browser, messenger, or any app). FastMediaSorter will download the file and offer to save it to any of your configured resources.

---

## Performance & Storage

### How do I find a specific file by name?
Use the **Filter** panel in Browse: tap the filter icon in the toolbar, type any part of the filename in the name field - the list updates instantly. No separate search bar is needed; the filter fully covers this scenario.

### Why is the app slow with 5000+ files?
The app uses **pagination** to load files in batches. For very large collections:
- Enable "Disable thumbnails" for that folder
- Use filters to narrow down results
- Sort by Date (newest first) - this loads recent files first and avoids scanning the entire folder upfront

### The app crashes or freezes
1. Force-close and reopen the app
2. If it crashes on a specific folder: that folder may contain a corrupted file - try opening files one by one to identify it
3. Clear cache: Settings → General → **"Clear Cache"** - this resolves most stability issues after updates
4. If crashes persist: report via GitHub Issues (link at the bottom of this page) - attach a description of what you were doing when it crashed

### How much storage does the thumbnail cache use?
**Default:** 2 GB (configurable in Settings)

### How do I clear the cache?
Settings → General → **"Clear Cache"**

---

## Favorites

### How do I mark files as favorites?
Tap the **star icon** while viewing a file.

### Where can I see all my favorites?
Main menu → **"Favorites"** tab

---

## Security & Privacy

### Can I password-protect folders?
**Yes!** Edit folder → Set **PIN Code** (4-6 digits)

### Is my data collected?
**No.** FastMediaSorter does NOT collect or send any personal data.

---

## Auto-Translation

### How does translation work?
We use a **Hybrid OCR System**:
- **Google ML Kit:** For fast, accurate recognition of Latin-based languages (English, German, etc.).
- **Tesseract:** For high-quality recognition of Cyrillic languages (Russian, Ukrainian).

### Why is "Auto" mode recommended?
"Auto" mode automatically detects the source language and selects the best engine. It prevents errors like confusing English 'C' with Russian 'С'.

### Does it work offline?
**Yes.** You only need internet once to download the language models (approx. 30MB for ML Kit, 15MB for Tesseract).

### Why is translation sometimes slower?
If the app detects Cyrillic text, it initializes the Tesseract engine, which is more powerful but takes 1-2 seconds longer to start than ML Kit.

### What is lens-style translation mode?
**Lens-style mode** displays translations as an overlay on top of the original image, similar to Google Lens. This allows you to see the translated text in its original context and position. You can enable it in Settings → Documents → Translation → "Lens-style overlay".

**Standard mode** shows translations in a separate text view below the image.

---

## Slideshow Background Music

### How do I add background music to slideshows?
1. Add a folder containing audio files as a resource
2. Go to Settings → **Audio** section
3. Enable **"Slideshow Background Music"**
4. Select your music resource from the dropdown
5. Start any slideshow - music will play automatically!

### Can I use music from network drives or cloud storage?
**Yes!** The app automatically handles network files by downloading them to cache before playback. This works with SMB, SFTP, FTP, Google Drive, OneDrive, and Dropbox.

### How do I skip tracks during slideshow?
Tap the **track name** displayed during the slideshow to skip to a different random track from your music resource.

### Does it work with all flavors?
**No.** Background music is only available in:
- **Standard** (full audio support)
- **Legacy** (local audio only)

The **Lite** and **Photos** flavors do not include audio features.

---

## Wear OS

### Does FastMediaSorter work on Wear OS smartwatches?
**Yes!** FastMediaSorter v2 includes a Wear OS companion app that allows you to browse and play local media files directly from your smartwatch.

### What features are available on Wear OS?
The Wear OS app currently supports:
- Browsing local folders
- Viewing images
- Playing videos
- Playing audio files
- Basic navigation controls

**Note:** Network and cloud features are not yet available on Wear OS.

---

## EPUB E-Books

### How do I enable EPUB support?
Settings → **Documents** section → Enable **"Support EPUB"**

**Note:** Restart the app after enabling for changes to take effect.

### How do I read an EPUB book?
1. Add a folder containing .epub files as a resource
2. Open the folder - you'll see EPUB files with "E" badge
3. Tap any EPUB file to open it in the reader

### Can I navigate between chapters?
**Yes!** Use:
- **Previous/Next buttons** at the bottom
- **Swipe left/right** to change chapters
- **TOC button** (📋 icon) to open table of contents

### Can I adjust font size?
**Yes!** While reading, use the **-A/+A buttons** at the bottom to decrease/increase font size (14-32px range). Settings are saved per-book.

### Can I search text in EPUB?
**Yes!** Tap the **Search button** (🔍) to open search panel. Type your query and navigate through matches with Prev/Next buttons.

### Does it work with network/cloud files?
**Yes!** EPUB files are automatically downloaded to cache when opened from SMB/SFTP/FTP/Cloud storage.

### Does it remember my reading position?
**Yes!** The app saves the last chapter you were reading. When you reopen the book, it continues from where you left off.

### What about dark/light theme?
EPUB viewer automatically adapts to your app theme (Settings → Appearance → Theme).

---

## Scheduled Operations

### What are Scheduled Operations?
Time-based automation rules that run Copy, Move, or Delete operations between any of your resources (local folders, NAS, cloud) on a repeating schedule - even when the app is closed.

### Where do I set up Scheduled Operations?
Settings → **Operations** → **Scheduled Operations** section. Tap **"+"** to add a new rule.

### Will it run if my app is closed?
**Yes.** Operations are scheduled via Android **WorkManager**, which runs them in the background regardless of whether the app is open.

### Why didn't a scheduled operation run at the exact time?
Android may defer WorkManager tasks by a few minutes to optimize battery. For more reliable timing, grant the app **Battery Optimization** exemption (Settings → General → Battery Optimization). The minimum interval is 15 minutes.

### Scheduled operation ran but copied 0 files
This is usually correct - it means all files were already present in the destination (the operation uses "skip existing" by default). To verify: check the operation log and look at the "skipped" count vs. "copied" count.

If you expected new files to be copied but they weren't:
- Make sure the **Source** is set to the right folder (e.g., "Camera Photos" virtual resource - not a manual path that might be wrong)
- Check that the destination resource (SMB / cloud) was reachable at the scheduled time - if Wi-Fi was off, the run is skipped and retried next time

### Can I see what was processed?
**Yes.** Tap **"View Log"** in the Scheduled Operations section to see a timestamped history of every run including per-file results.

---

## Still have questions?

Didn't find an answer above, or something isn't working as described? **Please reach out** - every message gets read and most issues get fixed.

- � **How-To Guides** (step-by-step tasks): [HOW_TO.md](HOW_TO.md)
- 🚀 **Quick Start:** [QUICK_START.md](QUICK_START.md)
- 🔧 **Troubleshooting:** [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
- �📧 **Email:** [sza@ukr.net](mailto:sza@ukr.net) - for anything: setup help, bug descriptions, feature wishes
- 🐛 **Bug report:** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues) - preferred for reproducible bugs; include Android version and what you were doing
- 📖 **Full docs:** [Documentation Portal](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

> **Want a feature that isn't there yet?** Write - many features in the app were added because someone asked. If it makes sense for the use case, it gets built.


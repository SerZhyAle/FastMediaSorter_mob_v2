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
Android 9.0 (API 28) or newer.

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

---

## Network & Cloud

### How do I connect to my home NAS (network drive)?
1. Settings → Add Folder → **SMB/Network Drive**
2. **Option A - Automatic:** Tap **"Scan Network"** to automatically discover available devices on your network
3. **Option B - Manual:** Enter server address: `\\192.168.1.100\share` or `smb://192.168.1.100/share`
4. Enter username and password
5. Tap "Connect"

**Common issues:**
- Make sure your phone is on the same Wi-Fi network
- Check if SMB is enabled on your NAS
- Try port 445 (default SMB port)

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

## Performance & Storage

### How do I find a specific file by name?
Use the **Filter** panel in Browse: tap the filter icon in the toolbar, type any part of the filename in the name field — the list updates instantly. No separate search bar is needed; the filter fully covers this scenario.

### Why is the app slow with 5000+ files?
The app uses **pagination** to load files in batches. For very large collections:
- Enable "Disable thumbnails" for that folder
- Use filters to narrow down results

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

## Still have questions?

- 📧 **Email:** [sza@ukr.net](mailto:sza@ukr.net)
- 🐛 **Bug reports:** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)
- 📖 **Full docs:** [Documentation Portal](https://serzhyale.github.io/FastMediaSorter_mob_v2/)


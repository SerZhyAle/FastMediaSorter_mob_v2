---
layout: default
title: "❓ Frequently Asked Questions (FAQ)"
permalink: /docs/FAQ.html
lang: en
---

<sub class="doc-stamp">26.10.06 16:15</sub>

<div lang="en" markdown="1">

# ❓ Frequently Asked Questions (FAQ)

{% include lang-switcher.html doc="FAQ" dir="/docs/" current="en" %}

---

## General Questions

### What is FastMediaSorter?
FastMediaSorter combines media playback and file management for local, network and cloud resources. Launcher, Streams and watch integration depend on the edition. File access requires your permissions; replacing the home screen requires your explicit choice in Android.

### Is it free?
Yes! FastMediaSorter v2 is completely free and open-source.

### What Android version do I need?
Minimum installation versions in the current source configuration are listed below. A compatible headset/runtime is additionally needed for immersive VR/XR use; **noLegal is not restricted to headsets**. A source variant does not guarantee a published APK.

- standard / noLegal / lite / photos: Android 8.0 / API 26
- legacy / foss: Android 6.0 / API 23
- vr: Android 10 / API 29
- xr: Android 8.0 / API 26
- Wear OS app: API 28
- WFF v4 watchface: Wear OS 6 / API 36

[Android / SDK](TECHNICAL_REQUIREMENTS.html)

### Does it require internet?
Local files do not need internet. SMB/SFTP/FTP on your LAN need a reachable network, not public internet. Cloud services and internet streams need internet; feature availability depends on the edition.

### Does the app have widgets?
Yes! FastMediaSorter v2 ships a variety of home-screen widgets - find them via long-press on the home screen → Widgets → FastMediaSorter. They include resource shortcuts, slideshow launchers, and more.

### Can the app replace my home screen?
In **Standard** and **noLegal**, open **Settings → General → Primary startup window**, pick **Device home screen**, then choose FastMediaSorter as Android's Home app. **Desktop as primary window** opens the desktop without replacing your system launcher.

[Edition capability matrix (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### How do I stop the app being my home screen?
Choose **Exit launcher mode** in the desktop Start menu, select another primary startup window in the app, or choose another Home app in Android's default-app settings. Your desktop layout is retained; the Android settings path varies by device.

[Edition capability matrix (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Why did my tablet go back to its old home screen after a restart?
First check Android's default Home app and the app's primary startup window. Choose **Always** if offered. Firmware policies, an app update or a reset of defaults can change this selection; a restart alone does not identify the cause. Include the device model and Android version when reporting it.

### Can I put my own folders and playlists on the desktop?
Yes - that is what the desktop is for. Long-press an empty square and choose **Add an item..**, then pick what you want: one of your folders, a radio stream, an app, a person, or a gadget like the clock or the weather. The new cell lands on the square you pressed, and for a folder you also choose whether it opens in browse, slideshow or play mode. To move things around afterwards, choose **Edit the desktop** from the same long-press menu. See [HOW_TO](HOW_TO.html#how-to-use-the-app-as-your-home-screen) for the full walkthrough.

---

## File Operations

### Where do deleted files go?
With trash enabled, supported ordinary local paths use `.trash/`. **Permanent deletion**, document-tree URIs (`content://`), SMB/SFTP/FTP/cloud resources and protected `/Android/media/` paths do not use this soft-delete policy. Emptying trash is irreversible. Do not assume every deletion can be restored.

### Can I undo a delete/move?
Use the visible **Undo** action immediately, only when the app offers it. Availability depends on the operation, screen and paths. Permanent or remote/document-tree deletion cannot be undone through local trash; network/cloud file transfers are not universally reversible. Undo is not a backup.

### What's the difference between Copy and Move?
- **Copy:** Creates a duplicate, original stays in place
- **Move:** Relocates the file, removes from original location

### What is All Files mode?
All Files mode removes media-type filters **within resources you can access**. It does not bypass Android permissions or unlock protected folders. Unsupported formats can be managed or passed to another compatible app; listing APK/EXE/archive files does not imply that they can be executed or extracted.

### How do I find and remove duplicate files?
Open a folder, tap the overflow menu, and choose **Find Duplicates** to review matches yourself, or **Find and Delete Duplicates** to remove them right away. There's also **Delete by Size..** for a quick cleanup sweep based on file size alone. The automatic option skips confirmation, so use **Find Duplicates** first if you want to double-check before anything is deleted. Matching is content-based - size, then a quick hash, then a full SHA-256 check - so renamed copies are still found.

---

## Network & Cloud

### How do I connect to my home NAS (network drive)?
1. Tap **"+"** → **Network** → **SMB / Network Drive**
2. **Option A - Automatic:** Tap **"Scan Network"** to automatically discover available devices on your network
3. **Option B - Manual:** Enter server address: `\\192.168.1.100\share` or `smb://192.168.1.100/share`
4. Enter username and password
5. Tap "Connect"

Use an authorized NAS/Windows account and a reachable SMB share. Allow TCP **445** only on a trusted private network and the required subnet; **do not disable the firewall or expose SMB to the internet**. Check share permissions, address, VPN routes and guest-network isolation. Remote access can work through a properly configured private VPN, including over mobile data.

[SMB Setup Guide](howto/scenario-smb-setup.html)

### How do I connect to Google Drive?
1. Tap **"+"** → **Cloud** → **Google Drive**
2. Tap "Sign in with Google"
3. Grant permissions when prompted
4. Your Drive folders will appear

Files open on demand, but viewing, thumbnails or playback may download data into the app's cache. This is not automatic synchronization of your whole Drive.

### How do I connect to OneDrive?
1. Tap **"+"** → **Cloud** → **OneDrive**
2. Tap "Sign in with Microsoft"
3. Grant permissions when prompted
4. Your OneDrive folders will appear

### How do I connect to Dropbox?
1. Tap **"+"** → **Cloud** → **Dropbox**
2. Tap "Sign in with Dropbox"
3. Grant permissions when prompted
4. Your Dropbox folders will appear

### Can I use SFTP or FTP?
**Yes!** Select **SFTP** or **FTP** when adding a folder:
- **SFTP:** Secure, requires SSH server (port 22)
- **FTP:** Less secure, older protocol (port 21)

### Can I share PC folders with the app?
**Yes** - Fast Media Sorter for Windows publishes chosen PC folders over SFTP and shows a QR code / `.fmscfg` config. On the phone, use **Import from companion** or **Scan QR code** on the Add Resource screen. See the PC-side guide: [How to publish PC folders to Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html).

[Edition capability matrix (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### Why are thumbnails not loading for network files?
Network thumbnails generate **on-demand** to save bandwidth. Scroll slowly or wait a few seconds for them to appear.

If thumbnails never load at all:
- Check that the connection is active: tap the resource → if the folder opens, the connection is fine
- Edit the resource → make sure **"Load thumbnails"** is enabled
- For very slow connections: disable thumbnails entirely to avoid timeouts (Edit resource → disable thumbnails)

### Connection keeps dropping / files fail to open mid-playback
Check Wi-Fi stability, server availability, credentials and VPN routes. Test the resource speed and reduce thumbnail work on slow links. Required bandwidth depends on the file's bitrate and codec, not resolution alone; **10 Mbps is not a universal 1080p requirement**.

---

## Quick Sort & Destinations

### What are "Quick Sort" folders?
Quick Sort folders are pre-configured target folders for fast file sorting. You can assign up to 10 folders with numbered buttons.

### How do I set up Quick Sort?
**Method 1:** Settings → Management → Quick Sort destinations, then tap **"Add to Quick Sort"**  
**Method 2:** Edit any folder → Enable "Mark for Quick Sort"

### How do I use Quick Sort while viewing files?
Open a file and select a destination on the command panel. Check whether the selected action is **Copy** or **Move** before confirming. Touch-zone positions vary with media type and player mode; do not assume the lower-left corner always copies.

### Can I use number keys instead of tapping?
Yes - connect a hardware keyboard, gamepad, or TV remote and your Quick Sort buttons get numbered (0-9) automatically. Press the matching digit to copy or move the file to that destination instantly, same as tapping the button.

### Quick Sort buttons are not showing
Make sure you have added at least one destination folder first: Settings → Management → Quick Sort destinations, then **"Add to Quick Sort"**. Buttons only appear when at least one destination is configured.

### I accidentally sent a file to the wrong folder
Use **Undo** immediately if it is offered for that operation. Otherwise inspect the source and destination before moving the file back manually. A copy leaves the source intact; avoid deleting either copy until you have verified the result.

---

## Touch Zones

### What are "Touch Zones"?
Touch zones depend on media type and player mode: images can use a 3×3 grid, while video/audio reserve space for playback controls and use pause/resume in the center. Command-panel mode uses three columns; documents use swipe navigation without tap zones. Show the overlay to inspect the active map.

### How do I see Touch Zones?
Settings → Player → **"Always show touch zones overlay"**

### Can I disable Touch Zones?
You can turn off the nine-zone grid and use the command panel. This does **not** disable every gesture: the three-column fallback retains navigation and media-specific controls; documents have their own swipe navigation.

---

## Screen & Voice Capture

### What is the left-edge gesture strip?
It's a quick-capture menu you open with a diagonal swipe from the left edge of the screen. Turn it on in **Settings → Management → Edge screen gestures → Gesture overlay**. From the menu you can take a screenshot, snap a photo, crop and share the current image, open an app or panel shortcut, or start a screen, video, or voice recording - all without leaving what you're looking at. Available in Standard and XR/noLegal.

### How do I record a quick voice note?
Three ways: the **Voice recording** item in the overflow menu, the **Quick Recorder** home-screen widget, or the edge gesture's **Start audio recording** action. However you start it, a floating **Stop** control stays on screen - even over another app - until you tap it to save.

---

## Input & Controls

### Does it support physical keyboards and gamepads?
Keyboard, mouse and gamepad controls depend on the active screen and device. **F1** opens the available binding help on supported surfaces; it is not a guarantee that every key or controller works in every dialog.

### How do I remap controls / change keybindings?
Use **Settings → Management → Controls & Keybindings** to edit supported action bindings. **Reset** restores built-in defaults and conflicts are highlighted. The available actions evolve with the app; rely on the current list rather than a fixed count of 70.

### How do I download a media file from a URL?
Share a supported `http(s)` URL through Android's Share sheet. A direct downloadable file is not the same as a web page, login-protected video or DRM stream. Download support and writable destinations depend on the edition, URL and granted resource access.

---

## Performance & Storage

### How do I find a specific file by name?
Use the **Filter** panel in Browse: tap the filter icon in the toolbar, type any part of the filename in the name field - the list updates instantly. No separate search bar is needed; the filter fully covers this scenario.

### Why is the app slow with 5000+ files?
Large folders require listing, metadata and thumbnail work. Narrow the resource, use filters and reduce thumbnails on slow storage or networks. Sorting by date does **not** guarantee that the whole folder need not be scanned; performance depends on the source and file types.

### The app crashes or freezes
Reopen the app and check available storage, permissions and resource connectivity. Cache clearing can help with stale thumbnails, but is not a general crash fix. For reproducible failures, report the app version/edition, Android version, affected resource and steps; redact credentials and private paths from logs.

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
A resource **PIN** restricts access through the app's UI. It does **not encrypt files** or prevent another app or an authorized server user from opening them. Use device/storage encryption for protection outside FastMediaSorter.

### Is my data collected?
The app does not automatically send usage statistics to the author. Optional statistics stay local unless you export or send them. Cloud sign-in, streams and weather contact the providers you choose; those services receive the requests needed for the feature. See the privacy policy for details.

[Privacy policy](PRIVACY_POLICY.html)

### Do contact shortcuts on the launcher desktop need access to my contacts?
**No.** Pinning a person to the launcher desktop asks for no contacts permission at all. You pick the person in Android's own contact picker, the app reads that one record once, and keeps it as a snapshot on the cell - it never gets to browse your address book. Calling uses the number you chose in the picker, so the cell dials exactly that number.

An optional **Contacts** permission group does exist, requestable on demand, under **Settings → General → Permissions & Access**. Denying it changes nothing about the behaviour above - shortcuts keep working the same no-permission way.

### Does the app save GPS location in my photos?
Only if you turn it on. In **Settings → Management → Photography**, enable photo capture, then turn on **Geotag photos** underneath it - the app asks for location permission right away, not at shutter time. A geotagged photo's File Info screen shows the capture date and the GPS spot from the photo's EXIF data as a tappable link that opens your maps app or browser.

### Can I see how I use the app?
Yes - it's opt-in and off by default: turn on **Statistics collection** in **Settings → General** to open a local usage dashboard: files sorted, space freed, playback time, and more, broken down by media type. Nothing is sent automatically; **Send to author** or **Export** only shares a summary if you choose to.

---

## Auto-Translation

### How does translation work?
Two steps, both on your device:
- **Tesseract** reads the text from the picture, in every supported language (English, Russian, Ukrainian, Bulgarian, Belarusian).
- **Google ML Kit** then translates what was read.

Availability depends on the edition and device class. ML Kit translation is allowed here only on phones, tablets, Chromebooks and desktop-class devices, not TV, automotive, watches or XR headsets. Separate OCR recognition requires API 26+, at least 3 GB RAM and a device not flagged low-RAM.

### What does the "Auto" source language do?
"Auto" reads the text with the English model and then works out the language of what was read for the translation. For Cyrillic text, pick the source language explicitly (for example **Russian** or **Ukrainian**) - otherwise letters are read as their Latin look-alikes.

### Does it work offline?
**Yes.** You only need internet once to download the text model for your source language and the translation model for your language pair.

### Why is translation sometimes slower?
The first use of a language loads its text model, and large or detailed pictures take longer to read. Later runs on the same language start faster.

### What is lens-style translation mode?
The overlay places translated blocks over the image; standard mode shows separate text. Find **Translation result in blocks** under **Settings → Media → Other** on supported devices.

---

## Slideshow Background Music

### How do I add background music to slideshows?
1. Add a folder containing audio files as a resource
2. Go to **Settings → Media → Images**
3. Enable **"Play music during slideshow"**
4. Select your music resource from the dropdown
5. Start any slideshow - music will play automatically!

### Can I use music from network drives or cloud storage?
**Yes!** The app automatically handles network files by downloading them to cache before playback. This works with SMB, SFTP, FTP, Google Drive, OneDrive, and Dropbox.

### How do I skip tracks during slideshow?
Tap the **track name** displayed during the slideshow to skip to a different random track from your music resource.

### Does it work with all flavors?
Slideshow music needs audio support. Standard, noLegal, Legacy, VR, XR and FOSS support audio and persistent playback; Lite supports local audio without the persistent background service. Photos has no audio. Network/cloud sources are additionally limited by the edition.

[Edition capability matrix (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

---

## Internet Streams

### Does FastMediaSorter play internet radio?
The **Streams** screen supports HTTP(S) radio with ICY metadata, HLS/DASH and RTSP sources, subject to source and codec compatibility. It is available in **Standard, noLegal, Legacy, VR and XR**, not **Lite, Photos or FOSS**. Unsupported services and DRM are not made playable just by pasting a URL.

[Edition capability matrix (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### How do I open the Streams screen?
Tap **Streams** in the main window dropdown (visible when Streams is enabled). You can also reach it via **Settings > Media > Streams** where the master toggle lives.

### How do I add a radio station?
In the Streams screen, tap **⋮** at the end of the toolbar, choose **Add stream** and paste the station URL. Tap Save. The station appears in the list immediately.

### Can I import a playlist?
Yes - tap **⋮ > Import from URL** and enter a remote `.m3u` address. The same menu holds **Update FastMediaSorter catalog** for the curated list (with topic and language chips), which is also available from **Settings > Extensions** or the Welcome onboarding screen.

### A stream is not playing - what do I do?
If a stream fails, a dialog appears with **Retry**, **Remove**, and **Cancel** options. Cross-protocol 301 redirects are handled automatically. If the host is dead or very slow, the catalog import times out quickly rather than hanging.

### Does radio keep playing when I leave the Streams screen?
With persistent background audio enabled and supported, radio can continue after leaving Streams, according to your Stop / Keep playing / Ask exit preference. With it disabled, foreground-only audio stops when the screen leaves the foreground. Check Settings → Player.

### Can I see live thumbnails for streams?
Switch the Streams toolbar toggle to **Grid** view - each channel shows as a tile with its last captured frame, so you can spot what's playing at a glance. The tile stays visible even after you close and reopen the app, then refreshes with a new capture once the stream is live again.

### Can I cast a stream to my TV?
Yes, for video streams - tap **Cast** in the player and pick a Chromecast on the same Wi-Fi network. RTSP streams can't be cast; the button only appears for formats the Chromecast receiver supports.

---

## Wear OS

### Does FastMediaSorter work on Wear OS smartwatches?
There is a separate **Wear OS app** (minimum API 28). Install its APK on the watch, not the phone. Phone companion support is currently in **Standard/noLegal** and requires compatible package identities/signatures. The separate **WFF v4 watch face** requires Wear OS 6 / API 36.

[Edition capability matrix (EN)](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/blob/main/docs/FLAVOR_MATRIX.md)

### What can I do on the watch?
The current Wear source includes local/network media, phone transfers, streams, voice recording and small tools. Supported settings/resources can sync with a compatible paired phone; not every phone setting is mirrored. The watch has no independent cloud client. **Published builds can have fewer features than current source**; check the download/release description instead of assuming a store or APK build contains everything.

---

## EPUB E-Books

### How do I enable EPUB support?
Settings → Media → **Documents** → **"Support EPUB e-books"**

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
**Yes!** While reading, use the **-A/+A buttons** at the bottom to decrease/increase font size (6-144px range). Settings are saved per-book.

### Can I search text in EPUB?
**Yes!** Tap the **Search button** <img src="icons/doc/ic_search.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> to open search panel. Type your query and navigate through matches with Prev/Next buttons.

### Does it work with network/cloud files?
**Yes!** EPUB files are automatically downloaded to cache when opened from SMB/SFTP/FTP/Cloud storage.

### Does it remember my reading position?
**Yes!** The app saves the last chapter you were reading. When you reopen the book, it continues from where you left off.

### What about dark/light theme?
EPUB viewer automatically adapts to your app theme (Settings → General → Color theme).

---

## Scheduled Operations

### What are Scheduled Operations?
Scheduled rules automate supported Copy, Move or Delete operations on accessible resources. Permissions, credentials and source/destination availability still apply. Test with **Copy** first; scheduled deletion is not automatically reversible.

### Where do I set up Scheduled Operations?
Settings → **Management** → **Scheduled operations by schedule**. Tap **"+"** to add a new rule.

### Will it run if my app is closed?
WorkManager can run tasks after you leave the app, but execution is not guaranteed after an Android **force stop**, while the device is off, or when permissions/constraints prevent it. Reopen the app after a force stop and check the rule and log.

### Why didn't a scheduled operation run at the exact time?
WorkManager is not an exact alarm. Battery restrictions and network/device conditions can delay a run beyond a few minutes. The app's minimum interval is **15 minutes**; an optimization exemption does not guarantee an exact start time.

### Scheduled operation ran but copied 0 files
Check the log: zero copied files can mean existing files were skipped, no files matched, the resource was unavailable or permissions failed. Verify the source, filters, destination and credentials before changing a rule. Do not assume every zero-file run is successful.

### Can I see what was processed?
**Yes.** Tap **"View Log"** in the Scheduled Operations section to see a timestamped history of every run including per-file results.

---

## Weather block

### Where does the weather come from?
The desktop weather block uses **Open-Meteo.com** - a free, keyless weather service. Weather data by Open-Meteo.com (CC-BY 4.0).

### Does the app track my location?
The **weather block** uses the place you enter, not automatic GPS tracking. Refresh requests send that place to the weather service. This is separate from optional photo geotagging, which requires location permission when enabled.

---
## Still have questions?

Didn't find an answer above, or something isn't working as described? **Please reach out** - every message gets read and most issues get fixed.

- 📖 **How-To Guides** (step-by-step tasks): [HOW_TO.md](HOW_TO.html)
- 🚀 **Quick Start:** [QUICK_START.md](QUICK_START.html)
- 🔧 **Troubleshooting:** [TROUBLESHOOTING.md](TROUBLESHOOTING.html)
- 📧 **Email:** [sza@ukr.net](mailto:sza@ukr.net) - for anything: setup help, bug descriptions, feature wishes
- 🌐 **Author's page:** [sza.od.ua](https://sza.od.ua)
- 🐛 **Bug report:** [GitHub Issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues) - preferred for reproducible bugs; include Android version and what you were doing
- 📖 **Full docs:** [Documentation Portal](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

> **Want a feature that isn't there yet?** Write - many features in the app were added because someone asked. If it makes sense for the use case, it gets built.

</div>

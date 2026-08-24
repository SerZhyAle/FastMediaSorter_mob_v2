---
layout: default
title: "What's New in FastMediaSorter v2"
permalink: /docs/WHATS_NEW.html
---

# What's New in FastMediaSorter v2

**Current release: 2.60.8241.708** (August 2026) - Fix Release

> Fix: granting All files access no longer leaves the permission dialog on screen

---

## What's Fixed

- Granting All files access no longer leaves the **Permissions required** dialog on screen (S1992).

---

## Previous Release: 2.60.8232.251 (August 2026)

> Changes since version 2.60.8222.348

---

## What's New

- **Send files to the watch** - the paired watch is a resource you can copy media into.
- **Rectangular watch tiles** - grid cells use the full screen instead of round ones.

## What's Fixed

- Resource rows on the watch respond to taps again.
- Recent resources no longer fill the whole first row of the watch home grid.
- The watch startup splash shows the watch branding, not the phone one.
- The legacy edition's watch companion now matches the watch app identity.

---

## Previous Release: 2.60.8222.348 (August 2026)

> Changes since version 2.60.8151.948

---

## What's New

- **Wear OS companion** - browse, play and sort your phone's media from the watch.
- **Watch mini-programs** - calculator, network monitor and a game, on your wrist.
- **Watch players** - audio and video with shuffle, bezel volume and screen-off mode.
- **Streams on the watch** - open, manage and receive channels sent from the phone.
- **Launcher desktop gadgets** - media windows, now-playing art, translator and scroll thumb.
- **Launcher notifications** - other apps' alerts in the top bar, dismissible from the panel.
- **Widget backdrop opacity** - desktop cells from fully transparent to an opaque card.
- **Network Monitor** - home-screen widget, launcher tile and Bluetooth device list.
- **Stream home-screen widget** - start a channel straight from your home screen.
- **Channel previews** - see every live video channel before you commit.
- **Station logo pack** - cached artwork for every station in the catalog.
- **Front flashlight** - turns the screen itself into an adjustable lamp.
- **System information** - a device report reachable as its own program.
- **Resource grid cell size** - pick how large the browse tiles are.
- **Branded startup splash** - a consistent opening on phone and watch.
- **Immersive HUD settings** - tune the VR heads-up display from its own panel.
- **Calculator long-press** - a second action on every key.
- **Unified capture filenames** - one naming scheme for everything the app saves.
- **Credential cleanup** - remove saved logins no resource still uses.

## What's Fixed

- Saved photo now matches what the viewfinder showed.
- Player opens reliably instead of occasionally failing to start.
- Rotating the screen no longer blanks the standalone player.
- Settings search now opens the target section directly.
- Start button and Start panel are readable in both themes.
- Channels keep their pin and history across catalog drops.
- Channel favourites survive a cosmetic address change.
- The watch honours the SFTP host key pinned on phone.
- Watch network browsing shows the media type you asked for.
- Album art downloads now work on the watch.
- FTP connection testing now works on the watch.
- Wear screens no longer clip content on round displays.
- Widget placement makes room, and explains a refusal.

---

## Previous Release: 2.60.8151.948 (August 2026)

> Changes since version 2.60.8122.034

---

## What's New

- **Immersive VR browser** - aim with a ray, start playback in the headset.
- **Cast stereo video** - one eye sent to the TV, correctly cropped.
- **Subtitle styling** - font, size and colour in the panel player.
- **Viewfinder aspect** - 4:3, 16:9 or full screen, and the shot matches.
- **Per-lens camera memory** - zoom and scenario remembered for each lens.
- **Command bar overflow** - extra actions move into the three-dots menu.
- **Edge swipe in fullscreen** - brings the player controls back.
- **Send to other apps** - now for cloud and direct web files.
- **Adaptive stream quality** - remembered per channel, and probed back up.
- **Taskbar on either edge** - top or bottom, your choice.
- **Compact section headers** on the launcher desktop.

## What's Fixed

- Pinned shortcuts survive a launcher reset.
- Resource-type tabs always fit the screen.
- The settings search button stays reachable in landscape.
- The steps permission row appears only where it works.
- Zoom presets are hidden for a lens without zoom.
- Settings, transfers and file flags survive app updates.
- The Google account stays connected after an update.
- An unreachable SFTP server is not retried on every thumbnail.
- Startup no longer blocks on preferences or image setup.
- Rotating while saving an account no longer crashes.
- An immersive playback error shuts down cleanly.

---

## Previous Release: 2.60.8122.034 (August 2026)

> Changes since version 2.60.8042.332

---

## What's New

- **Home-screen launcher** - shortcuts, contact cells, titled sections, a desktop that scrolls.
- **Desktop gadgets** - clock, sensors, map, signal strip and Now Playing.
- **Taskbar status area** - configurable indicators, Wi-Fi and Bluetooth tiles.
- **All installed apps** - a full-screen list straight from the desktop.
- **Web search from the desktop** - type once, open in the browser.
- **Launcher settings** - grouped sections and a one-tap reset.
- **Channel catalog** - community radio and world webcams, grouped by rubric.
- **About this channel** - description, artwork and quality at a glance.
- **Channel pictures update** without waiting for an app release.
- **Stream shortcuts** start playback in the background.
- **Thirteen interface languages.**
- **Themed launcher icon** follows the system palette.
- **SD cards and external drives** appear in browsing.
- **Image rotation and editing** in the separate player window.
- **Network Monitor** - see what the app connects to.
- **Transfer progress on every screen.**
- **Document translation** reachable from the Office menu.
- **Wear OS SFTP connection test.**

Note: the step-counter desktop tile ships only in the sideload build for now.

## What's Fixed

- Streams report a lost network instead of failing silently.
- The channel grid stays quiet with no connection.
- File counts are truthful, and an empty folder explains itself.
- Dialogs keep their state across rotation.
- The camera opens without freezing the interface.
- Permissions read the same wording everywhere.
- Stream quality steps down instead of stalling repeatedly.

---

## Previous Release: 2.60.8042.332 (August 2026)

> Changes since version 2.60.7270.415

---

## What's New

- **In-headset controls** - seek bar, track pickers, hide and exit, controller legend.
- **Stereo from file metadata** - VR video without a filename tag plays correctly.
- **Launcher desktop** - app widgets, contact shortcuts, own status area, wallpaper through cells.
- **PDF reading** - swipe to turn pages, long-press to select real text.
- **Browse toolbar** - path button with segment menu, active filter summary, grouped file menu.
- **Background transfers** - a copy sent to the background stays visible and tappable.
- **Folder transfers** - select folders like files, copy and move them across protocols.
- **Shooting scenarios** - one button for night, portrait, selfie, macro and sport.
- **Local write destination** - send captures, screenshots and downloads to any local folder.
- **Device profiles** - one pick now seeds reader, download, player, streams and launcher.
- **Welcome walkthrough** - app roles, protocol examples, and an animated brand backdrop.
- **Calculator preview** - the running result appears while you type.
- **Accessible settings lists** - dropdowns open to D-pad, keyboard and screen readers.
- **Optional contacts permission** - pin a person without granting it.
- **Full-frame images** - fullscreen and slideshow no longer crop to fill.
- **Edge gesture bands** - strips sit on the edges Android leaves free.
- **Animated thumbnails** - animated WebP and APNG show a still first frame.

## What's Fixed

- Ordinary widescreen films are no longer detected as 3D.
- PDF zoom no longer resets when the page turns.
- Deleted or moved files leave the player navigation list.
- Leaving the channel grid no longer closes the app.
- Camera settings survive a theme or language change.
- The edge-gesture dialog keeps its layout after rotation.
- Built-in resource names follow the language switch.
- The recording indicator appears on wide landscape layouts.
- Command bar icons and labels line up on wide screens.
- The brand backdrop shows a still frame with animations off.
- Restoring an old backup no longer re-enables open-in-player.
- Credentials stay masked in exported diagnostic logs.

---

## Previous Release: 2.60.7270.415 (July 2026)

> Changes since version 2.60.7260.335

---

## What's New

- **Per-channel track memory** - a stream keeps the audio track and subtitles you chose.
- **Stream track languages** - set a default audio and subtitle language for streams.
- **Stream tile artwork** - every channel tile shows a picture.
- **Station logos** - channels without a preview show their station logo.
- **Extension updates** - a downloadable extension offers its newer version.

## What's Fixed

- Declining the home-screen role no longer leaves the request unresolved.

---

## Previous Release: 2.60.7260.335 (July 2026) - Fix Release

> Fix: false storage-sync error when refreshing Favorites

## What's Fixed

- Refreshing Favorites no longer records a false storage sync error in the diagnostic log.

---

## Previous Release: 2.60.7260.252 (July 2026)

> Changes since version 2.60.7221.704

---

## What's New

- **Desktop wallpaper** - the branded animation, a flat surface, or your own picture.
- **Weather gadget** - current conditions for a place you name, no location permission.
- **Desktop edit lock** - long-press to edit the desktop, lock it when finished.
- **App quick actions** - long-press an app to run its published shortcuts.
- **Now-playing for streams** - artist, title and the programme on air.
- **Stream topic filter** - isolate a catalog rubric such as Webcam in the list.
- **Channel preview atlas** - video channels show a preview before the first watch.
- **Pinned streams section** - pinned channels get their own collapsible section on top.
- **Radio resume** - a station playing at exit starts again on the next launch.
- **Full camera hardware** - every physical lens, true zoom floor, macro, full resolution.
- **Camera report in System info** - diagnose a camera problem from the phone itself.
- **Two-column settings in landscape** - collapsed groups pair up, expanded ones stay wide.
- **Local folder in scheduled operations** - use an ad-hoc folder without registering it.

## What's Fixed

- Adding a stream URL that already exists no longer crashes.
- Manual stream edits keep the chosen Audio or Video type.
- Stream thumbnails no longer flicker or re-probe unreachable channels.

---

## Previous Release: 2.60.7221.704 (July 2026)

> Changes since version 2.60.7191.740

---

## What's New

- **Home-screen launcher** - resizable gadgets, a working clock, a scrollable app grid.
- **Launcher desktop** - seeded shortcuts and richer cell actions out of the box.
- **Adaptive streaming** - video quality steps down automatically under repeated stalls.
- **Stream thumbnails** - the frame you watched becomes the channel preview.

## What's Fixed

- Playback controls hide options that do nothing on live streams.
- Quick-launch tile icons are readable on the light theme.
- VR input labels are readable in the keybindings screen.
- The empty channel picker now opens Streams settings.

---

## Previous Release: 2.60.7191.740 (July 2026)

> Changes since version 2.60.7160.058

---

## What's New

- **Launcher mode** - use FastMediaSorter as your device home screen.
- **HEIC, HEIF & AVIF** - open modern phone photo formats.
- **Archives & disk images** - handle them from Open-with and Share.
- **Bigger edge-gesture catalog** - more actions in a grouped picker.
- **Fullscreen exit button** - leave fullscreen video with one tap.
- **Region-locked stream badge** - spot geo-blocked streams at a glance.
- **Empty streams panel hint** - guidance when no streams are set up.
- **Windows companion promo** - shown when adding network sources.
- **Smarter companion import** - add-or-update resources with a summary.

## What's Fixed

- Camera preview now matches the saved photo.

---

## Previous Release: 2.60.7160.058 (July 2026)

> Changes since version 2.60.7132.046

---

## What's New

- **Camera launch widget** - snap a photo straight from your home screen.
- **Animated WebP & APNG** - short animations now play in the image viewer.
- **Share resource as a QR code** - hand off an SFTP resource by scanning it.
- **Pin stream tiles** - keep favourite streams on top with a pinned badge.
- **Private screens** - settings and resource screens hide in Recents and block screenshots.
- **Edge-gesture settings** - configure screen-edge gestures from one dialog.

## What's Fixed

- Video player shows controls on open and on center-tap.
- Draw editor Save-as always writes a real file extension.
- File transfer fails fast when the destination is unreachable.
- Streams search and filter reset when re-entering the screen.
- Screenshot-gesture edge bands stay flush after rotation.
- OCR crop handle stays clear of the bottom action bar.
- Main-screen top panels line up their buttons in a uniform grid.
- Scheduled-operations master toggle stays in sync with its children.

---

## Previous Release: 2.60.7132.046 (July 2026)

> Changes since version 2.60.7070.937

---

## What's New

- **Windows Companion** - discover, pair, and import shared folders and SFTP access from a desktop companion app.
- **Shared-folder resources** - import a companion-shared folder as writable, or share/import SFTP access as a file.
- **Companion config import** - schema-v2 config keeps working with older app versions too.
- **Sharing receivers** - Messenger, Viber, and short-video social apps can share straight into FastMediaSorter.
- **Screenshot OCR** - crop and translate text directly from a screenshot.
- **Screen Capture edge gestures** - configurable swipe zones across all four screen edges.
- **Quick-launch panel** - close button, camera/video gesture actions, and search-filter on long picker lists.
- **Contrast mini-game** - new mode with a start-of-level exit arrow.
- **Branded splash screen** on launch.
- **Resource statistics** persist after browsing, not just on entry.
- **Unified Resource Profile dialog** for viewing and editing resource details.
- **Stream URLs with embedded credentials** - manually-added HTTP streams parse Basic-Auth from the URL.

## What's Fixed

- VR install prompt no longer appears when opening 3D content on the VR edition.
- SFTP resource with a missing path shows a clear "not found" message.
- OCR and translation now work on Google Play builds.
- Audio permissions no longer silently disabled on minified release builds.
- Filter dialog, Settings search, and text fields stay reachable above the keyboard.
- Live streams that silently freeze now auto-recover.
- Edge-gesture strips only show on zones you configured, not every zone.
- Focus indicator for remote/D-pad now covers the whole app.

---

## Previous Release: 2.60.7070.937 (July 2026)

> Changes since version 2.60.7042.357

---

## What's New

- **Document viewer** - tap a PDF link to open it and select text with long-press.
- **More OS shortcuts** - extra Android settings tiles in the quick-launch panel.

## What's Fixed

- Audio permission no longer silently disabled on release builds.
- Stopping a quick voice recording no longer crashes.
- Enable-all onboarding resumes after the app restarts.

---

## Previous Release: 2.60.7042.357 (July 2026)

> Changes since version 2.60.7040.526

---

## What's New

- **Reorder pinned streams** - move a favorite up, down or to top from the streams list or grid menu.
- **TV/D-pad navigation** - every focused control is now highlighted, and the first remote press always lands on a real control.

## What's Fixed

- Streams landscape view no longer wastes vertical space on a separate control bar.
- Playback-control sliders (volume, hue, brightness, speed) now track the finger correctly in portrait.
- Live streams keep playing instead of pausing when entering Picture-in-Picture.

---

## Previous Release: 2.60.7040.526 (July 2026)

> Changes since version 2.60.7031.316

---

## What's New

- **Quick capture app panel** - camera, voice, screen recording and link download in the quick-launch panel.
- **Floating recording indicator** - stop a quick voice recording from an on-top control over any app.

## What's Fixed

- Wider device support - now available on more TVs, tablets and landscape-only devices.
- OS Interaction and Destinations settings toggles stack full-width in portrait.
- Quick capture records voice, video and photo with pause, resume and discard.
- Screen video recording captures the whole screen with microphone audio and pause/resume.
- Edge-gesture photo capture saves headlessly to the device camera folder.

---

## Previous Release: 2.60.7031.316 (July 2026)

> Changes since version 2.60.6270.802

---

## What's New

- **Main-window panels** - Programs and Streams panels, collapsible panel groups, colour-coded panels, header and context menus, configure shortcut.
- **Streams upgrades** - inline audio playback, favoriting live channels, country metadata with filter/sort, persistent last-frame thumbnails.
- **Screen Capture edge gestures** - quick photo (plain, edit, send-to, OCR-translate) and one-tap start for audio/video/screen recording.
- **Camera controls** - zoom presets and slider, night mode, opt-in GPS geotagging, fixed controls with Send To and capability-driven settings.
- **Player interface** - fullscreen video by default, redesigned player settings dialog, travelling D-pad/TV focus frame.
- **Background/inline audio** - proper audio focus handling; survives split-screen multi-window instead of stopping.
- **Mini-game visual modes** - alternate visual styles for the built-in mini-game.
- **Browse background mode** - copy and move operations continue while browsing elsewhere.
- **Exit button minimizes** - minimizes instead of closing while background functions are active.

## What's Fixed

- Browser sign-in no longer risks a crash when the screen rotates mid-save.
- Standalone document viewer no longer leaks a PdfRenderer, file descriptor or WebView.
- Read Aloud (TTS) no longer keeps playing or leaks its engine on screen recreation.
- SMB connection-degradation recovery now reacts correctly under parallel load.
- Network video thumbnail suspension no longer clears early, causing a false permanent-failure state.
- Enable-all onboarding no longer silently reverts OCR/translation toggles.
- Deprecated standalone player no longer crashes on teardown.
- Standalone player pause survives background/resume; SAF rename no longer restarts playback.
- Player correctness fixes: BD-TS local video, Now Playing polling, VR launch, stream recovery.
- Double-tapping the same inline audio stream no longer orphans a background player.

---

## Previous Release: 2.60.6270.802 (June 2026)

> Changes since version 2.60.6251.711

---

## What's New

- **Fullscreen stream player** - watch a live channel full-screen with stream-tailored controls.
- **Stop inline streams** - tap a playing tile again to stop it.
- **Pin favorite streams** - long-press to pin, then filter to pinned-only.
- **Streams remember their view** - filter, sort and list position persist between visits.
- **Landscape stream grid** - multi-column list with status dots and a per-tile menu.
- **Smoother stream startup** - health probe plus bandwidth-adaptive buffering.
- **Clearer stream waiting state** - separate buffering and reconnecting labels.
- **Edge-gesture screen capture** - opt-in left-edge strip and Quick Settings tile.
- **Visible gesture strip** - optional grey guide marks the left-edge swipe zone.
- **Width-based landscape layout** - wide screens use the landscape layout from 600dp.

## What's Fixed

- Offline streams now fail softly instead of breaking playback.
- Database reset shows a recovery notice instead of failing silently.
- Duplicate parenthetical names in stream titles are suppressed.

---

## Previous Release: 2.60.6251.711 (June 2026)

> Changes since version 2.60.6242.232

---

## What's New

- **Screen capture** - consent-based screenshot to edit, OCR-translate, share or save.
- **Stream grid view** - live channels as tiles with current-frame previews and favicons.
- **Streams quick start** - add or import streams right from the empty catalog.
- **Compact playback controls** - context-aware tabs, speed presets and volume in one dialog.
- **Crop in editor** - crop the image to a rectangle before annotating or sharing.
- **Send to resource** - copy the current file to a destination folder from any player.
- **Resource launch widget** - a 1x1 home-screen icon that opens a chosen folder.

## What's Fixed

- Network video thumbnails load more smoothly during fast scrolling.

---

## Previous Release: 2.60.6242.232 (June 2026)

> Changes since version 2.60.6222.158

---

## What's New

- **Stream player controls** - trimmed, stream-tailored controls with channel navigation.
- **Cast live streams** - send a live video stream to Chromecast.
- **Stream shortcuts and actions** - home-screen shortcuts plus a per-card actions menu.
- **Streams settings** - default sort and media-type filter, with TV-remote and mouse support.
- **Screen-capture app panel** - OS shortcuts and the app's own features in one panel.
- **Edge gestures** - open the app or its panel with an edge swipe.
- **9-zone player grid** - optional touch grid mapping zones to playback actions.
- **Smarter player layout** - auto-fullscreen on landscape; controls adapt to orientation.
- **Statistics onboarding toggle** - Welcome offers a statistics switch, on by default.
- **Welcome gesture setup** - enable edge gestures from the Welcome flow.
- **Richer usage statistics** - more tracked metrics with a clearer headline.
- **Cleaner settings rows** - unified value rows, consistent chevrons and arrows.

## What's Fixed

- Dropped live streams now auto-recover instead of prompting channel removal.

---

## Previous Release: 2.60.6221.755 (June 2026)

> Changes since version 2.60.6200.317

---

## What's New

- **Internet Streams** - browse, filter and play live TV and radio channels.
- **Stream playback polish** - channel name in the title, per-channel status, and background audio with an exit choice.
- **Streams setup** - enable streams per device profile with a Welcome introduction.
- **In-app camera** - Samsung-style capture with an in-screen photo and video switch.
- **Custom color themes** - build and apply your own color theme.
- **Screenshot capture** - take a screenshot from Operations settings or an edge gesture.
- **Print and transfer from viewers** - print documents, text and images; copy or move opened files to a folder.
- **Smarter settings search** - finds dropdown settings and hides rows your edition lacks.

## What's Fixed

- Network folder scans no longer hang and now end with a clear timeout message.
- Receiving a shared download link no longer crashes the share screen.

---

## Previous Release: 2.60.6200.317 (June 2026)

> Changes since version 2.60.6180.134

---

## What's New

- **Quick capture menu** - record a voice note, video, or photo from the main menu.
- **Download by link** - paste a link from the clipboard to fetch media.
- **Chromecast video** - cast video to a Chromecast, not just audio.
- **New drawing canvas** - start a blank canvas straight from Browse.
- **Custom brush color and size** - pick any color and adjust brush thickness.
- **Drag to reorder resources** - rearrange your resource list by dragging.
- **File details sheet** - view file metadata in a bottom sheet from Browse.
- **Video player gestures** - vertical brightness and volume sliders, configurable touch zones.
- **Text viewer themes** - choose a reader theme for text files.
- **Fallback save** - auto-save to a backup location when the destination is unavailable.
- **Controller, keyboard and TV navigation** - full gamepad support, number-key shortcuts, remappable browser actions.
- **Settings reference and groups** - in-app settings reference with collapsible section groups.
- **Crash email prompt** - after a crash, the next launch offers to email the report.
- **Mini-game** - play Kryvavitsa and the Monster from its launcher widget.

## What's Fixed

- Player time statistic now records real on-screen time instead of zero.
- PDF thumbnails no longer vanish while browsing.
- Player controls no longer hide behind the navigation bar in standalone players.
- The stop-scan button is back in the landscape network discovery dialog.
- Camera capture no longer crashes when closed mid-capture.
- Camera OCR now themes correctly in light mode.
- D-pad navigation now works on the welcome carousel.

---

## Previous Release: 2.60.6180.134 (June 2026)

> Changes since version 2.60.6141.930

---

## What's New

- **Send to network and cloud** - share files from SMB, SFTP, FTP, and cloud, fetched on demand.
- **Usage statistics** - opt-in dashboard of capture, viewing, editing, and operation metrics.
- **Favorites backup** - export favorites to a file and import them back later.
- **Photo and frame to clipboard** - optionally copy each captured photo or extracted frame.
- **Crash report prompt** - after an unexpected stop, offer to send a diagnostic report.
- **All Files pinned first** - the All Files entry stays at the top of the list.

## What's Fixed

- Audio from SFTP no longer stops after a slow folder scan.
- Missing OCR engine packs now show guidance instead of silent failure.
- Settings search now maps backup options correctly.

---

## Previous Release: 2.60.6141.930 (June 2026)

> Changes since version 2.60.6050.126

---

## What's New

- **Downloadable Extensions** - install OCR, translation, DTS, and audio backgrounds on demand.
- **Smarter setup** - onboarding now includes profiles, functionality toggles, permissions, and Enable All.
- **Remote source toggles** - disable SMB, (S)FTP, or cloud sources without deleting resources.
- **Standalone viewer parity** - shared images now support OCR, translation, drawing, crop, and fullscreen.
- **All Files resource** - first-run file-manager entry opens newest-first full browsing.
- **Quick camera capture** - 1×1 widget saves photos or videos to a chosen target.
- **SFTP key authentication** - connect with private keys, passphrases, and host-key pinning.
- **Audio visualizations** - downloadable background video set with automatic safe fallback.

## What's Fixed

- Broken audio tracks now skip instead of stopping playback.
- Recent Media no longer shows a false cancelled-scan error.
- Too-short microphone recordings no longer save broken files.
- Report-a-problem email now fills the support address.
- Drawing toolbar no longer hides behind the system navigation bar.

---

## Previous Release: 2.60.6050.126 (June 2026)

> Changes since version 2.60.6031.424

---

## What's New

- **Camera OCR languages** - choose OCR and translation language on the crop screen.
- **Re-translate OCR results** - switch target language without re-capturing.
- **Icon-style home widgets** - compact 1×1 widgets look like clean launcher icons.
- **Camera OCR widget** - launch Camera OCR from its own home-screen widget.
- **Add widgets from Settings** - pin a widget to the home screen in-app.
- **Capture & OCR panel** - one home-screen panel for Camera Photos and OCR.
- **Audio Now Playing widget** - control background audio from the home screen.
- **Random Photo Frame widget** - rotating random photo from a chosen source.
- **Quick Recorder widget** - one tap records voice, the next tap saves.
- **Scheduled Tasks widget** - view and run scheduled file operations from home.

## What's Fixed

- Network video playback no longer freezes on buffering.
- Opening network media no longer crashes on bad entries.
- Camera OCR crop frame now renders and aligns correctly.

---

## Previous Release: 2.60.6031.424 (June 2026)

> Changes since version 2.60.6010.151

---

## What's New

- **Device profiles** - first-run setup tailors defaults to your device type.
- **Color theme** - pick Auto, Light, or Dark regardless of system.
- **System info** - view, copy, and share a device and app summary.
- **Calculator upgrade** - scientific functions, memory keys, history, and expressions.
- **Calculator from text** - send selected OCR, translation, or document text in.
- **Text selection & copy** - long-press to select and copy in documents.
- **Richer language picker** - searchable list with flags, native names, and Czech.
- **Office document filter** - filter Office files and recheck all types instantly.
- **Camera OCR crop** - select a region before recognizing and translating text.
- **3D/VR defaults** - tune auto-detection and the default immersive mode (VR).
- **Smoother remote & mouse control** - improved D-pad, wheel scroll, and focus reach.

## What's Fixed

- Mouse left-click now reaches buttons, dialogs, and dropdowns.
- Camera OCR and Game Help controls stay within safe areas.

---

## Previous Release: 2.60.6010.151 (June 2026)

> Changes since version 2.60.5310.007

---

## What's New

- **Camera OCR translation** - capture, recognize, translate, and save text.
- **Text editor calculator** - calculate values without leaving text editing.
- **Mini game and widgets** - launch calculator, camera OCR, and game actions.
- **Release tooling guards** - digest build failures and detect flavor drift.

## What's Fixed

- Android TV launcher title now matches the banner and Play listing.
- Google Drive backup restore uses scoped GMS auth routing.

---

## Previous Release: 2.60.5310.007 (May 2026)

> Changes since version 2.60.5302.057

---

## What's New

- **Password-protected ZIPs** - extract encrypted archives after one-time prompt.
- **Protected document fallback** - locked EPUB and Office files fail clearly.

## What's Fixed

- Protected PDF renderer failures now show a clear message.

---

## Previous Release: 2.60.5302.057 (May 2026)

> Changes since version 2.60.5220.333

---

## What's New

- **Office documents** - open DOC, DOCX, RTF, and ODT with an installed viewer.
- **Send to Telegram** - share selected files to a Telegram client in one tap.
- **MIDI playback** - play MID/MIDI files like regular music tracks.
- **File Manager Mode** - browse and manage all file types, including archives.
- **VR content launch** - start video into immersive VR cinema from the player (S0292, VR flavor).

## What's Fixed

- DeX and multi-window file operations reachable via overflow menu (S0293).
- VR video no longer displays upside-down on Quest 3 (S0290).
- VR pointer rays no longer flicker from stale tracking (S0291).

---

## Previous Release: 2.60.5220.333 (May 2026)

> Changes since version 2.60.5202.303

---

## What's New

- **Cyrillic OCR** - Tesseract `tessdata_best` Cyrillic models with automatic fallback (S0287).
- **Settings Search Expansion** - broader coverage of settings across standard, VR, photos, and noLegal flavors.
- **VR Diagnostics Surface** - DiagnosticXR Activity with HUD canvas, raycast targeting, and input dispatch.
- **TV Navigation Overhaul** - unified D-pad focus and mouse-wheel routing across screens (S0289).

## What's Fixed

- Finger taps unresponsive on Quest 3 and touchpad TVs (S0289).
- OCR action could be re-triggered while recognition was in progress (S0288).

---

## Previous Release: 2.60.5202.303 (May 2026)

> Changes since version 2.60.5180.136

---

## What's New

- **16KB Page Alignment** - aligns native libraries to 16KB boundaries for Android 15+.
- **AI Audit Dashboard** - publishes consensus evaluation results to GitHub Pages.
- **Landscape Layout Tuning** - optimizes spacing and pairs setting rows on wide screens.

## What's Fixed

- Resolves MediaStore local copying regression on Android versions pre-Q (S0280).

---

## Previous Release: 2.60.5180.136 (May 2026)

> Changes since version 2.60.5172.102

---

## What's Changed

- **Unified expandable group headers** - Settings, AddResource, ResourceEditor, Scheduled Operations, Player Copy/Move panels, Duplicates, and Keybinding now share one header style; General Settings > About is now a static divider
- **Simplified player controls** - stereo content type, rendering mode, and IPD removed on non-VR flavors (S0241)
- **OCR on high-RAM low-heap devices** - text recognition restored on Quest 3 (7 GB RAM, 512 MB heap)
- **VR dead code purge** - 16 main-side files cleaned, 2 VR-only permissions removed (S0241 Phase 03)

## What's Fixed

- "Low memory, close player" snackbar no longer false-fires on capable devices (S0213)
- VR-only Browse routing branch removed on non-VR flavors (S0241)
- Google Drive sign-in now navigates to folder picker after Credential Manager flow completes

---

## Previous Release: 2.60.5172.102 (May 2026)

> Changes since version 2.60.5160.425

---

## What's New

- **Create text notes from Browse** - write notes on local, SMB, SFTP, FTP, or cloud resources with a 5-action save panel (S0189)
- **Central Google account** - one primary account via Credential Manager powers Drive and future Google integrations (S0200)
- **Universal input router** - D-pad, car steering, Bluetooth headset, and hardware keys mapped to semantic actions (S0230)
- **VR image and GIF viewer** - 3D/VR toolbar icon opens images in immersive view with auto-detected stereo layout (S0238, VR flavor)
- **IzzyOnDroid distribution** - fastlane metadata, changelog pipeline, and README badge prepared for catalog submission (S0215)

## What's Changed

- **System-managed rotation** - orientation lock removed; reverse-portrait, tablet, and foldable layouts now work (S0222)
- **Adaptive Copy/Move panels** - single-row layout on wide screens; button font scales with measured width (S0227)
- **SMB first-scan speedup** - listing appears before metadata pass; per-file 1500ms budget; concurrency 2 → 8 (S0237)
- **Draw editor toolbar** - collapsed to one row; save actions moved into overflow; filled palette swatch (S0192)
- **Welcome feature tiles** - 4 → 6 entries for symmetric 3-column tablet and VR layouts

## What's Fixed

- Instagram photo posts and carousels download correctly (S0223)
- Player no longer jumps to wrong file after delete-with-advance (S0226)
- SMB idle timeout no longer fires repeat bursts after one window (S0228)
- SFTP browse no longer fails with "inputstream is closed" after idle (S0219)
- Low-memory pre-playback toast suppressed in normal usage (S0207)
- SMB audio metadata noise (Handler-on-dead-thread, EOFException) cleared (S0229)
- YouTube community-post shares get a specific unsupported message (S0225)
- Carousel batch download notifications use accurate slide counts (S0224)
- Create-note and create-folder dialogs no longer trigger Google sign-in prompt
- OneDrive, Google Drive, and Dropbox sign-in restored on noLegal and VR sideload builds (S0232, S0233, S0235)
- Network-to-local copy works in public collections on Android 10+ without all-files access (S0231)
- Google sign-in reports PlayServicesOutdated with an update Play Services CTA
- noLegal and VR flavors no longer show "VR Headset Required" for text, audio, PDF, and EPUB
- noLegal on phones falls back to 2D player when no OpenXR runtime is available
- Legacy `cloud:/` path content visibility restored (S0236)
- Per-file overflow Delete no longer fails with "Files not selected"
- Welcome screen TAB key traverses to language pickers correctly (S0230)

---

## Previous Release: 2.60.5160.425 (May 2026)

> Changes since version 2.60.5150.150

---

## What's New

- **Draw editor v2** - oval and text annotation tools, 16-color palette dialog, in-place save, export to Google Keep
- **File size in browse list** - size shown in media item info row for video, image, and audio files

## What's Fixed

- Image-edit toolbar buttons (crop, compress, draw, open in window) no longer hidden in overflow for image viewer
- Link downloads no longer show a spurious sign-in prompt when a valid session is already active

---

## Previous Release: 2.60.5150.150 (May 2026)

> Changes since version 2.60.5142.201

---

## What's New

- **Big Buttons Mode refined** - 100dp controls, icon-dominant layout, adaptive 5..10 top-panel slots (spec S0208)
- **Slideshow auto-stop on network loss** - slideshow halts on consecutive failures with an informational message and resume via the existing button (spec S0188)
- **Permissions button relocated** - standalone tonal button below all card groups in General settings, before doc links
- **Device summary in settings** - compact device and Android line next to the app version for screenshot-based diagnostics

## What's Fixed

- Toolbar overlap with the system status bar on Android 8.x OEM car head units (spec S0204)
- NetworkLifecycleBootstrapper crash when initialised from a background thread (spec S0195)
- SFTP teardown no longer surfaces as an error in the log on cancel (spec S0205)
- Big Buttons Mode: audio player overflow menu now sized to the widest item

---

## Previous Release: 2.60.5142.201 (May 2026) - Fix Release

> Fix: vr-permission-bridge-fragment-public

## What's Fixed

- Fix IllegalStateException crash on VR passthrough camera capture when requesting the headset camera permission (spec S0203)

---

## Previous Release: 2.60.5130.151 (May 2026)

> Changes since version 2.60.5030.230

---

## What's New

- **Adaptive player command bar** - buttons automatically scale to screen width; overflow items move to a "more" menu
- **Sort progress indicator in Browse** - a loading indicator is shown when sorting large libraries (12,000+ files)
- **m2ts / BD-TS playback** - format detected by byte signature; routed through a dedicated decoder
- **Black-frame-free video thumbnails** - up to three retries to find a non-black frame when generating previews
- **SFTP audio position saved** - playback position is remembered and restored between sessions
- **SMB: auto-reconnect** - a stalled connection is restarted automatically; connection closes in background on app minimize
- **"Recents" resource without folder filter** - shows all recent media files regardless of their location
- **Network device discovery 3× faster** - ports probed in parallel; real-time scan progress shown in the UI
- **Link download: broken file detection** - a corrupted download is detected automatically; Instagram and TikTok CDN Referer/User-Agent replayed

## What's Fixed

- AVI files on network resources no longer cause a 10-second stall when loading thumbnails
- "End of String" speed-test error no longer surfaces in the UI
- Race condition generating video thumbnails over SMB during active playback - fixed
- SFTP: ArrayIndexOutOfBoundsException on seek in channel (JSch update 0.2.16 → 0.2.26)
- Volume buttons (Mute / 50% / MAX) did not fit the portrait player command bar - fixed
- Network device discovery dialog showed blank content and crashed on open - fixed
- Player: low-memory toast threshold lowered to 10 MB; repeat toasts suppressed within one session
- Browse: cancelling a background operation no longer invalidates the folder cache or appears as an error in logs
- Log noise removed: cancelled Glide requests, test credentials, and internal copy operations no longer logged

---

## Previous Release: 2.60.5030.230 (May 2026)

> Changes since version 2.60.422.246

---

## What's New

- **Keybinding remapper** - reassign any control in Settings; conflict detection, hierarchical reset; 70 built-in defaults
- **Full keyboard, mouse & gamepad support** - all screens (Browse, Player, Settings, dialogs); F1 help overlay per surface; D-pad list navigation
- **Camera capture in Browse** - take a photo and save it directly to the current resource
- **Play Random in Browse** - one-tap random playback for single-type libraries; Ctrl+P shortcut
- **Quick start from resource icon** - tap the resource type badge to instantly begin playback or slideshow
- **Link auto-download** - share any http(s) URL to the app; the media file is downloaded automatically
- **Single-eye 3D mode** - new setting to crop stereo content to one eye for flat-screen viewing (video + images)
- **Settings restructured** - "Files & Data" split into "Permissions" + "App Data"; "File Operations" → "Copy & Move"; Safe Mode moved to Operations

## What's Fixed

- Video hue/brightness effects not applying on track change (Media3 effects-deferral fix)
- RANDOM sort not reshuffling on repeated taps
- Camera upload failing on network resources (SMB / FTP / SFTP)
- ScheduledOperationsWorker freeze on WAKE_LOCK race condition
- Print crash on Android 8 (API 26-27)
- SMB scan errors now shown as a Snackbar with error count instead of being silently discarded
- WebView for EPUB now created lazily (prevents OOM on low-memory devices)
- ExoPlayer recreated every 4 tracks to prevent native heap OOM in long sessions
- Default player chooser: fixed self-resolution loop (app was probing its own file)

---

## Previous Release: 2.60.422.246 (April 2026)

> Changes since version 2.60.4150.019

---

## Player

- **Control dialog** - new unified dialog (Volume, Brightness, Speed, Subtitles, 3D) for both video and audio. Left-side vertical section selector, changes apply live. Quick 50%/MAX volume presets and highlighted Mute button
- **Playback speed range** extended to 3.0x (added 2.5x and 3.0x steps)
- **Video color and brightness** - Hue and Brightness via Media3 GPU effects, persisted across video files
- **Save Frame** - snapshot of the current video frame to PNG or JPG, saved to any resource (local or network). Format and destination resource configured in Video Settings
- **Print** - send documents (PDF, TXT, images) to a printer directly from the player; network and cloud files are cached first
- **Random file** - dice button to jump to a random file in the list; shown in audio and photo library profiles
- **Filename overlay auto-hide** - overlay fades out automatically: TEXT 5s, PDF/EPUB 10s, others 15s. Timer resets on pause and zoom
- **File Info enriched** for video: aspect ratio, audio channels, bitrate. For audio: album and sample rate
- **3D content detection** by filename (SBS/OU/360) with a toast notification
- **Back navigation fixed** in the text reader - no blank screen on exit

---

## SMB / Network

- **Adaptive SMBJ timeout** - 3 tiers (5s/10s/20s) based on measured connection speed; far fewer false disconnects on slow NAS
- **Broken pipe fix** - stale pooled TCP connection is invalidated before re-opening a file
- **Watchdog 12s (open) + 15s (read)** in SMB: ExoPlayer no longer hangs in STATE\_BUFFERING on silent network drops
- **Error classification fixed**: wrapped exceptions are now recognised correctly (was "no network" instead of "timeout")
- **Integer overflow fixed** when reading files larger than 2 GB over SMB

---

## Adding Resources

- **Collapsible sections** in the SMB/SFTP add-resource form (Conditions, Media Types, Additional)
- **Quick Setup profile** now saved to the database (was ignored before)
- **Read-only SMB** - adding a read-only resource shows an informational message instead of an error
- **"Remember file list"** option now present in the add form, matching the editor

---

## File Browser

- **Resource drag-to-reorder** - drag and drop to manually sort resources; switches to MANUAL mode automatically
- **RANDOM reshuffle** - tapping RANDOM sort again produces a new random order
- **Dice icon** for the active RANDOM sort mode
- **Filter fix**: changing a filter no longer resets an already loaded SMB file list

---

## Audio

- **Playback notification** - track name and cover art shown in the system notification
- **Tap notification body** to navigate back to the player from the background
- **Now Playing bar** controlled by a dedicated setting; hidden for audio by default
- **Audio metadata** (Artist/Album/Title) cached in the database and shown instantly on open

---

## Settings

- **"Resume on next launch"** - new toggle in Playback Settings
- **Frame snapshot settings** - destination resource and format (PNG/JPG) in Video Settings
- **Back navigation fixed** from Settings to the main screen
- **Language spinner fixed**: no longer fires on screen open

---

## Performance & Stability

- **Adaptive pre-cache** for network files - buffer size calculated automatically from connection speed and file bitrate. Configure in General Settings → Prefetch Cache
- **Stream Offload** - download a network file to local cache with a progress dialog and optional cleanup prompt after playback
- **DTS/DTS-HD support** via a custom FFmpeg build; software-decoded on any device

---

## UI

- **Action buttons in file list** enlarged and aligned to the bottom row - no longer overlap the filename
- **MD3 icons** for all player controls and menus - system `@android:drawable/*` drawables replaced
- **PDF page indicator in landscape** - fixed disappearing indicator
- **PDF thumbnails button** added to the command bar (portrait and landscape)
- **Language badge** on translation buttons (PDF/TXT/EPUB/Images) - restored
- **"How-To Guides" button** in General Settings opens usage scenarios
- **Cold start black screen** - eliminated by removing `windowDisablePreview`

---

## Share / Receive Files

- **"Share to FastMediaSorter"** - the app now receives files via the Android Share sheet and offers to copy them to a selected resource

---

*Previous versions: [Development Changelog](../dev/CHANGELOG.md)*

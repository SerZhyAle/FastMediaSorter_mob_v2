---
layout: default
title: "FastMediaSorter v2 - Complete Feature List"
permalink: /docs/FEATURES.html
---
# FastMediaSorter v2 - Complete Feature List

*Last updated: 2026-08-23*

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
- **Unified "Send to.." hub** `[Standard / Lite / Photos / Legacy / VR / noLegal]`: One command gathers Share, Telegram, email, Lens, Keep, print, and other enabled targets into a single clean list instead of scattering export actions across menus.
- **Home-screen camera widget** `[Standard]`: Add a launcher widget that opens capture directly, so a photo lands in your chosen folder in one tap without opening the app first.
- **Every lens the device really has** `[Standard / Lite / Photos / Legacy / VR / noLegal]`: Capture offers each physical lens, its true zoom floor including sub-1x, a working macro mode, and the sensor's full resolution, while System info reports the whole camera layout so a problem can be diagnosed from the phone itself.
- **Pick the shot before you take it** `[Standard / Lite / Photos / Legacy / VR / noLegal]`: One button offers night, portrait, selfie, macro, and sport, listing only the scenarios your device can actually deliver and naming the active one.
- **Send captures where you want them** `[Standard / Lite / Photos / Legacy / VR / noLegal]`: Assign any local folder as the write destination for captures, screenshots, snapshots, and auto-downloads, separately from your browsing sources.
- **Frame the shot the way it will save** `[Standard / Lite / Photos / Legacy / VR / noLegal]`: Choose 4:3, 16:9, or full screen and the viewfinder itself changes, so the picture you see is the picture you get, and every lens keeps its own zoom and scenario for next time.

- **Front flashlight** `[Standard / Lite / Photos / Legacy / VR / noLegal]`: The screen itself becomes a lamp - it opens white at full brightness, a vertical swipe dims it, a corner button remembers another colour, and one tap closes it without touching your device brightness setting.
## 4. Replace a basic file viewer with a sorting workstation
- **Instant sorting panel** `[Standard / VR]`: Keep up to 30 favorite destination folders in the player and sort the current file in one tap, with optional auto-advance to the next item.
- **Open a file from another app and keep browsing nearby files** `[Standard / VR]`: When another app hands off one file, continue with next, previous, random, or slideshow inside the same folder instead of getting stuck on a single item.
- **File Manager Mode** `[Standard / VR]`: Show hidden files and bypass media-only filters to handle mixed folders, archives, APKs, EXEs, and other non-media content.
- **Move whole folders, not just files** `[Standard / Lite / Photos / Legacy / VR / noLegal]`: Select subfolders the way you select files and copy or move them across protocols, and a transfer sent to the background stays on a tappable strip you can reopen.
- **SD cards and plugged-in drives** `[Standard / Lite / Photos / Legacy / VR / noLegal]`: Removable storage and mounted external drives appear as browsable sources instead of staying invisible.

## 5. Replace several media viewers and editors
- **Image viewer with real file actions** `[Standard / VR]`: Crop, rotate, flip, adjust colors, speed-control GIFs, and export GIF frames without switching to a separate image utility.
- **Video frame capture to any destination** `[Standard / VR]`: Save a clean frame from video as PNG/JPG directly to local or network storage.
- **2D/360/VR playback in the sideload noLegal build** `[noLegal Only]`: Play SBS/OU, VR180, and 360 content, or watch normal 2D files on a giant virtual screen in the OpenXR build.
- **Controls that stay in the headset** `[noLegal Only]`: A HUD strip carries a seek bar you drag with the controller ray plus the track, subtitle, and depth pickers that apply to the file, the thumbstick seeks and steps between files, and a legend lists every binding.
- **Read PDFs by touch** `[Standard / Legacy / VR / noLegal]`: Turn pages with a swipe, long-press to select the page's own text without waiting for an OCR pass, and keep your zoom and framing through the turn.
- **Animated WebP and APNG playback** `[Standard]`: Short WebP and APNG animations now play in the image viewer instead of showing a single frozen frame.
- **Rotate and edit in the separate window** `[Standard / Lite / Photos / Legacy / VR / noLegal]`: Turn a picture either way and reach the editing commands from the standalone player window, not only from the main screen.
- **Stereo video on the television** `[Standard / Lite / Photos / Legacy / noLegal]`: Cast a side-by-side or over-under file and the TV shows one eye at full width instead of a squashed double picture.
- **Subtitles the way you read them** `[Standard / Lite / Legacy / VR / noLegal]`: Set the subtitle font, size, and colour in the windowed player instead of living with the default.
- **Browse inside the headset** `[VR / noLegal]`: Walk the library on a virtual screen, point with the controller ray, and start playback without taking the headset off.

- **Your phone's media on the watch** `[Wear OS]`: Browse the paired phone's folders and favourites as a grid with thumbnails, filter by media type, and open a file straight on the wrist.
- **A real player on the watch** `[Wear OS]`: Audio and video play on the watch with shuffle, bezel volume, a draggable position bar, paging controls, and a screen-off mode that keeps the sound going.
- **The watch as a place to put files** `[Wear OS]`: Add the paired watch as a resource beside local and cloud ones, copy or move files onto it from any phone screen, and receive what the watch sends back into a destination you choose, up to 32 MB per file.
- **Rectangular tiles on the watch** `[Wear OS]`: Every grid on the watch draws rectangular cells - a thumbnail fills its cell with no plate underneath, and an item without one shows its type glyph in a thin frame.
## 6. Replace OCR, translation, and note extraction tools
- **Offline OCR and offline translation** `[Standard / VR]`: Extract text from images and PDFs, then translate it locally without sending content to a cloud OCR service.
- **Photo-to-text and screenshot-to-translation flow** `[Standard / VR]`: Capture, crop, recognize, translate, and save the result as editable text in one flow.
- **In-place text and Markdown editing on remote storage** `[Standard / VR]`: Edit `.txt` and `.md` files directly on local or network resources, with Markdown rendering and auto-save.

## 7. Replace separate stream and radio apps
- **Dedicated Internet Streams screen** `[Standard / Legacy / VR / noLegal]`: Store internet radio, video streams, and RTSP sources in a proper library instead of juggling links in a browser or notes app.
- **Inline radio playback with live ICY metadata** `[Standard / Legacy / VR / noLegal]`: Play audio streams directly in the list while keeping the station catalog visible.
- **Pinned streams on the main window** `[Standard / Legacy / VR / noLegal]`: Put favorite live channels directly above the resource list for one-tap access from the home screen of the app.
- **Smarter stream playback** `[Standard / Legacy / VR / noLegal]`: Streams step down to a lighter quality automatically when the connection keeps stalling, and the frame you were watching becomes the channel's thumbnail.
- **See what is on air right now** `[Standard / Legacy / VR / noLegal]`: Radio shows the artist and title, video channels show the programme currently broadcasting, and both reach the notification and the lock screen.
- **Find a channel without scrolling** `[Standard / Legacy / VR / noLegal]`: Filter by topic alongside category, language, and country, recognise a video channel from a downloadable preview atlas before the first watch, fall back to the station logo where no preview exists, and keep pinned channels in their own collapsible section.
- **Radio picks itself back up** `[Standard / Legacy / VR / noLegal]`: A station that was playing when you closed the app starts again on the next launch.
- **Each channel remembers its tracks** `[Standard / Legacy / VR / noLegal]`: Pick an audio track or subtitles once and that channel opens the same way next time, with a default audio and subtitle language for every other stream.
- **Thousands of channels out of the box** `[Standard / Lite / Legacy / VR / noLegal]`: Community radio and webcams from around the world arrive as a ready catalog, sorted into translated rubrics instead of a flat list of links.
- **About this channel** `[Standard / Legacy / VR / noLegal]`: Open a card with the station's description, artwork, and stream details before deciding to listen.
- **Start a stream without opening the list** `[Standard / Legacy / VR / noLegal]`: A stream shortcut on the home screen begins playback in the background, no channel screen in the way.
- **Live streams on Wear OS** `[Wear OS]`: The watch app now plays live radio and video streams straight from its own channel list, over the watch's own network, with no phone nearby.

- **Stream widget on the home screen** `[Standard / Legacy / VR / noLegal]`: Start a channel straight from the home screen without opening the app first.
## 8. Replace setup migration and utility clutter
- **Unified settings backup and restore** `[Standard / VR]`: Back up sources, favorites, schedules, passwords, and sign-ins to a local file or Google Drive, then restore the whole setup after reinstalling or moving devices.
- **App panel for apps, tools, and internal actions** `[Standard]`: Build a quick-launch panel that mixes Android apps, system shortcuts, captures, OCR tools, streams, and chosen resources in one place.
- **Hidden bonus mini-game** `[Standard / Lite / Photos / Legacy / VR / noLegal]`: A built-in turn-based puzzle for anyone who likes finding unexpected extras in utility apps.
- **Thirteen interface languages** `[Standard / Lite / Photos / Legacy / VR / noLegal]`: The interface speaks thirteen languages, picked per app without changing the whole device.
- **See what the app connects to** `[Standard / noLegal]`: A network monitor lists the app's own connections, so a stalled transfer or stream is traceable instead of guesswork.
- **Screen rotation switch on Wear OS** `[Wear OS]`: The watch app now has its own screen-rotation switch, so you can stop the watch screen from turning with your wrist without changing any system setting.
- **Accessibility Service quick disable before banking** `[noLegal]`: 1-tap disabling of Accessibility Service from Settings, Android Quick Settings Tile, or 1x1 homescreen AppWidget for safe banking app execution.

- **Mini-programs on the watch** `[Wear OS]`: The calculator, the network monitor and the mini-game each get their own watch screen, so the wrist is not just a remote.
- **System information as a program** `[Standard / Lite / Photos / Legacy / VR / noLegal]`: The full device report sits in the programs menu instead of behind Settings, and the watch has its own screen for it.
## 9. Replace your home-screen launcher
- **Use the app as your home screen** `[Standard / noLegal]`: Launcher mode turns FastMediaSorter into the device home screen, with resizable gadgets, a working clock, and a labeled scrollable app grid.
- **Make the desktop yours** `[Standard / noLegal]`: Choose the branded waves-and-particles animation, a flat empty surface, or a picture of your own including an animated GIF, cropped to fill the screen.
- **Weather without a location permission** `[Standard / noLegal]`: A desktop gadget shows current conditions for a place you name, using keyless Open-Meteo data instead of your device location.
- **App quick actions on the desktop** `[Standard / noLegal]`: Long-press an installed app to list the shortcuts it publishes and start the one you want directly.
- **Edit the desktop, then lock it** `[Standard / noLegal]`: Long-press empty space to rearrange things, and switch on a lock so a finished desktop survives accidental taps.
- **Widgets and your own status area** `[Standard / noLegal]`: Place the app's home-screen widgets onto the launcher desktop, and choose whether Android's status bar stays or the launcher shows its own clock and indicators.
- **Pin a person, not a permission** `[Standard / noLegal]`: A contact cell opens that person in the system contacts app without granting a contacts permission - grant it only if you want their name and photo on the cell.
- **Interactive Google Maps live frame** `[Standard / noLegal]`: Place a fully interactive Google Maps live frame widget on the launcher desktop with gesture-driven panning and zooming.
- **Everything you pinned, in one place** `[Standard / noLegal]`: Shortcuts other apps pin land on the launcher desktop, each with the full action menu behind a long press.
- **Taskbar along the edge you prefer** `[Standard / noLegal]`: Keep the launcher taskbar at the bottom or move it to the top edge, and the desktop lays itself out around it.
- **Technical and sensor gadgets** `[Standard / noLegal]`: Put clock, sensors, signal strength, and a current-position map straight on the desktop, alongside a Now Playing cell that follows whichever app is playing.
- **Every installed app on one screen** `[Standard / noLegal]`: A full-screen list of everything installed, reachable from the desktop without a drawer hunt.
- **Idle dimming and blackout mode** `[Standard / noLegal]`: The launcher dims 4 seconds before screen timeout and pauses animated wallpapers and widgets to save power, resuming on any touch.
- **Search the web and switch radios from the desktop** `[Standard / noLegal]`: Type a query and it opens in the browser, and the Wi-Fi and Bluetooth tiles turn the radios on or off in place.
- **Other apps' notifications on the desktop** `[Standard / noLegal]`: The launcher top bar shows what other apps are reporting, and the Active signals panel lets you dismiss them without leaving the desktop.
- **See the wallpaper through your gadgets** `[Standard / noLegal]`: Desktop cells draw anywhere from fully transparent to an opaque card, and edit mode always shows the full card so a cell stays easy to grab.

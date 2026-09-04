---
layout: default
title: "FastMedia Wear - Smartwatch Media Companion & Standalone App"
permalink: /docs/wear/
---
# <img src="../icons/doc/ic_display.png" alt="" width="24" height="24" style="vertical-align:text-bottom"> FastMedia Wear OS Portal

[📱 Main App Home](../README.md) | [📖 All Step-by-Step Guides](../howto/index.md) | [Русский](index-ru.md) | [Українська](index-uk.md)

Welcome to the **FastMedia Wear OS Web Portal** - your complete guide to the standalone media player, network resource browser, and smartwatch companion for Wear OS. FastMedia Wear pairs seamlessly with the [FastMediaSorter Main Application](../README.md) for phones and tablets.

---

## 🌟 Capabilities Overview

FastMedia Wear turns your smartwatch into a full-featured standalone media hub and phone companion. Designed from the ground up for round and square smartwatches with Wear OS 2.0+, it offers fluid media playback, independent Wi-Fi network streaming, and phone resource integration.

### 🎵 Audio Player
- **Full Media Playback:** Play local audio files, cloud tracks, and network streams directly from your wrist.
- **Rotary Bezel Volume Control:** Easily adjust system media volume using the rotating watch crown or bezel with real-time visual feedback.
- **Album Art & Visualizer:** Displays high-resolution album covers or dynamic brand wave-and-particle backgrounds when art is unavailable.
- **Shuffle & Auto-Advance:** Continuous playback with automatic track advance and persistent shuffle modes.
- **Background Audio Playback:** Turn on the Background playback switch to keep local audio and streams playing after you minimize the app, with controls in the media notification. When you reopen the app, the home screen carries a row naming what is playing: tap it to return to that track where it left off, or tap the stop button beside it to end playback without opening anything else. Video and slideshows still stop when the app leaves the screen.

### 🎥 Photo & Video Viewer
- **Round-Safe Video Player:** Watch videos scaled for smartwatch displays with automatic advancing in slideshow mode.
- **Photo Slideshows:** Browse images and photo galleries with smooth paging and grid view options.
- **Network Thumbnail Previews:** Rapid embedded preview thumbnail extraction for network SMB/SFTP files.

### 🌐 Network & Cloud Access (SMB / FTP / SFTP)
- **Direct Wi-Fi Streaming:** Connect your watch directly to home NAS, PC shared folders (SMB), and remote FTP/SFTP servers over Wi-Fi.
- **Host Key Pinning & Security:** Enforces SFTP host key pins configured on your phone to protect credentials.
- **Connection Testing:** In-app connection probe to test network share availability directly from the watch.
- **Endpoint Resolution & Clear Errors:** A network resource sent from your phone stays accessible when the host computer moves to another address, as the watch automatically tries all known endpoints. When a connection cannot be established, the watch states the exact cause (connection refused, timeout, invalid credentials, unknown host) instead of a generic failure.

### 📲 Paired Phone Integration
- **Phone Media Browsing:** Recents, Videos, Audio, Images, Documents, All and Browse - the same categories, under the same names and colours, that your watch's own storage and your network shares offer. **All** is a flat list of media files, newest first and without folders; **Browse** walks the folders of your smartphone and shows everything in them. If no resource on your phone is set up to hold a category, opening it says so instead of showing an empty list.
- **Selective Resource Transfer:** Choose specific NAS/cloud resources on your phone to make available on your watch.
- **Remote Log Diagnostics:** Send watch diagnostic logs to the developer via the paired phone app.
- **Open on the Watch from the Phone:** Pick your watch in the phone's "Send to.." menu and the photo, GIF, video or track you have open there opens on the watch. A file kept on a network or cloud source is fetched first, with progress you can cancel. The watch app has to be open at the time - if it is closed, the phone says so instead of leaving you guessing. The phone also tells you apart the watch being unreachable, the watch staying silent, a type the watch cannot show, and a file above the 32 MB limit. Documents, text and EPUB do not offer the watch at all.
- **File Actions on the Watch:** Long press a file to start selecting, tap other files to add them, or use Select all. The selection can then be sent to your paired phone, moved there, deleted, or renamed. Deleting and renaming also work on a photo you took on the watch or a voice note you recorded there, not only on files the app itself keeps - the watch asks the system to confirm each such change first, since those files belong to the watch's own storage, and nothing happens if you decline. Your recorded notes also have their own entry in the local group now, and the same long press works right there in the note list - where renaming asks for no confirmation at all, the recording being the watch's own, so you can give a note a name you will recognise instead of leaving it named after the second it was made. Anything you browse over SMB, FTP or SFTP is read-only, and the phone decides where a sent file lands. Sending a file to the phone confirms whether it landed in a local folder or was queued for remote upload, naming the destination, and if a background upload fails later, the watch receives a notification.
- **Send to.. from the Watch:** The same "Send to.." list your phone offers - email, messengers, printing, the clipboard, the system share - is now the first entry of the watch's file menu, and it opens over the menu instead of taking you to another screen. The list is the one you already curated on the phone: a receiver you switched off there is simply not on the watch, and one you add later shows up without any second list to maintain. A receiver the watch can handle by itself works with no phone nearby. One it cannot is marked "via phone" and stays in the same place in the list even when the phone is out of reach - the watch tells you the phone is away before it starts, not after a wait. When the phone handles it, the file travels over and the phone raises a notification you tap to finish the send, and the watch says exactly that rather than pretending the message is already gone. A receiver that does not exist on your watch is not shown at all.
- **Open on the Phone:** A file your phone holds can be handed back to it for a proper look. Tap a file the watch cannot show - a PDF, a text file, an archive - or long press any file, then pick "Open on phone": if the phone app is already on screen the file opens there at once, and if it is not, the phone raises a notification naming the file - one tap opens it. Nothing has to be copied to the watch first, so the action is there on the very first tap instead of after a wait, and a file the watch has no player for is never fetched at all. If the phone's notifications are switched off it cannot show anything, and the watch says exactly that, so you know the fix is on the phone.

### 🧮 Mini-Programs Suite
- **Watch Calculator:** Grid-based math calculator with on-screen operation history.
- **Network Monitor:** Opens on a summary of the active link with your local and external address, and a button panel where every section shows a live fact. Each section is its own screen you tap into and leave with the usual edge swipe: Wi-Fi frequency, standard and a restartable signal trend; satellites by constellation with coordinates, accuracy and fix time; traffic rates with resettable counters; an on-demand reachability check. Addresses copy to the clipboard, and each section opens its own system settings screen.
- **Mini-Game:** On-watch entertainment usable completely offline without a phone connection. Every board is drawn at random, so two games in a row never repeat and restarting a level gives you a fresh one; leaving the game and coming back returns the board and the position you left. Each new board opens with a short arrow pointing from you to the nearest exit, then fades on its own. A back arrow at the left middle of the screen leaves the game and a small skip-turn button opposite it lets the shadows move while you stay put; both are also in the menu a long press opens.

### 🧩 Wear OS Tiles
- **Three Dedicated Tiles:** Add Network Resource, Stream, and Favourites tiles to your Wear OS tile carousel.
- **Quick Assignment:** Point an unassigned Resource or Stream tile to your target directly from the watch.
- **Standalone & Offline:** Launch assigned network shares, streams, or favourites list instantly from your watch face, completely independent of the phone or network state.

---

## 📸 Interface & Layout Showcase

- **Wallpaper choices:** Navigation screens can use the branded animation, a stationary branded frame, or a photo already sent from the phone. Settings screens stay free of wallpaper.

FastMedia Wear adapts dynamically to every smartwatch screen shape and density.

```
       .-----------------.
      /   [12:45] 🔋 85%  \
     |     FastMedia       |
     |   .-------------.   |
     |  | 🎵 Music     |   |
     |  | 📁 SMB NAS   |   |
     |  | ⭐ Favourites|   |
     |   '-------------'   |
      \   [ Settings ]    /
       '-----------------'
```

- **Round-Safe Screen Margin:** All UI components, buttons, and scrolling indicators sit strictly inside the round screen glass.
- **Grid & List View Modes:** Switch between single-column lists and 2-column or 3-column rectangular thumbnail grids.
- **D-Pad & Touch Support:** Optimized touch targets (minimum 48 dp) and hardware rotary crown support.
- **Universal Back Affordance & Navigation:** Every Wear OS screen provides a visible universal back affordance button positioned at the middle of the left edge, alongside standard edge-swipe gestures and physical hardware back buttons. On navigation screens and players, tapping the back affordance steps back to the previous screen. On the main home screen, the affordance dynamically adapts to playback state: showing a close icon (×) to exit the app when idle, or a minimize double-chevron («) when background audio is actively playing.

---

## 🔗 Phone & Watch Integration

FastMedia Wear operates both as an independent standalone smartwatch app and as a companion to the [FastMediaSorter Main Phone App](../README.md).

| Feature | Standalone Watch App | Paired with Phone App |
|---------|----------------------|-----------------------|
| Direct Wi-Fi SMB/SFTP Playback | ✅ Yes | ✅ Yes |
| Local Watch Audio & Media | ✅ Yes | ✅ Yes |
| Mini-Programs (Calculator, Game) | ✅ Yes | ✅ Yes |
| Phone Storage Access | - | ✅ Yes (Read-only via Wi-Fi/BT) |
| Resource Preset Sync | - | ✅ Yes (One-tap Transfer) |
| Log Diagnostics | - | ✅ Yes (Relayed via Phone) |

### Learn More About the Phone & Tablet App
- [Main Application Documentation](../README.md) - Complete overview of FastMediaSorter for Android phones, tablets, and TV.
- [First Launch & Device Profiles](../QUICK_START.md#first-launch-choose-your-device-profile-30-seconds-) - Pre-configure your phone for Photo Frame, In-Car Music, or Home Cinema.
- [All Step-by-Step Scenario Guides](../howto/index.md) - 15+ guides for network shares, camera backups, and media organization.

---

## 📥 Downloads & App Stores

FastMedia Wear and FastMedia Mobile App are available across major app stores and direct download channels:

### Smartwatch & Mobile App Packages
- [<img src="../icons/doc/ic_info.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> **Google Play Store**](https://play.google.com/store/apps) - Download phone and Wear OS companion apps directly to your devices.
- [<img src="../icons/doc/ic_resource_smb.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> **F-Droid Open Source**](https://f-droid.org) - Open-source builds for Android devices and Wear OS watches.
- [<img src="../icons/doc/ic_download.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> **Direct APK Release**](../DOWNLOADS_EN.md) - Direct APK downloads for phone and smartwatch sideloading.

---

## 📚 Step-by-Step Guides

- [Put FastMedia on Your Smartwatch](../howto/wear-install.md) - Step-by-step installation and pairing guide.
- [Listen to Music on Smartwatch](../howto/scenario-watch-music.md) - How to play tracks, manage volume, and keep audio going in the background or with the screen dark.
- [Connect Watch to Network Shares](../howto/scenario-watch-network.md) - Connect your watch directly to PC and NAS SMB/SFTP shares.
- [TV on Your Smartwatch](../howto/scenario-watch-tv.md) - Fill the channel list, search and filter it, pin what you watch and reach it from a tile.

---
layout: default
title: "FastMedia Wear - Smartwatch Media Companion & Standalone App"
permalink: /docs/wear/
---
# <img src="../icons/doc/ic_display.png" alt="" width="24" height="24" style="vertical-align:text-bottom"> FastMedia Wear OS Portal

[📱 Main App Home](../README.md) | [📖 All Step-by-Step Guides](../howto/index.md) | [Русский](index-ru.md) | [Українська](index-uk.md)

Welcome to the **FastMedia Wear OS Web Portal** — your complete guide to the standalone media player, network resource browser, and smartwatch companion for Wear OS. FastMedia Wear pairs seamlessly with the [FastMediaSorter Main Application](../README.md) for phones and tablets.

---

## 🌟 Capabilities Overview

FastMedia Wear turns your smartwatch into a full-featured standalone media hub and phone companion. Designed from the ground up for round and square smartwatches with Wear OS 2.0+, it offers fluid media playback, independent Wi-Fi network streaming, and phone resource integration.

### 🎵 Audio Player
- **Full Media Playback:** Play local audio files, cloud tracks, and network streams directly from your wrist.
- **Rotary Bezel Volume Control:** Easily adjust system media volume using the rotating watch crown or bezel with real-time visual feedback.
- **Album Art & Visualizer:** Displays high-resolution album covers or dynamic brand wave-and-particle backgrounds when art is unavailable.
- **Shuffle & Auto-Advance:** Continuous playback with automatic track advance and persistent shuffle modes.
- **OLED Screen-Off Battery Saver:** Blanks the display while keeping audio playback active to save battery during workouts and commuting.

### 🎥 Photo & Video Viewer
- **Round-Safe Video Player:** Watch videos scaled for smartwatch displays with automatic advancing in slideshow mode.
- **Photo Slideshows:** Browse images and photo galleries with smooth paging and grid view options.
- **Network Thumbnail Previews:** Rapid embedded preview thumbnail extraction for network SMB/SFTP files.

### 🌐 Network & Cloud Access (SMB / FTP / SFTP)
- **Direct Wi-Fi Streaming:** Connect your watch directly to home NAS, PC shared folders (SMB), and remote FTP/SFTP servers over Wi-Fi.
- **Host Key Pinning & Security:** Enforces SFTP host key pins configured on your phone to protect credentials.
- **Connection Testing:** In-app connection probe to test network share availability directly from the watch.

### 📲 Paired Phone Integration
- **Phone Media Browsing:** Browse videos, audio, images, documents, and folders hosted on your paired smartphone.
- **Selective Resource Transfer:** Choose specific NAS/cloud resources on your phone to make available on your watch.
- **Remote Log Diagnostics:** Send watch diagnostic logs to the developer via the paired phone app.
- **Open on the Watch from the Phone:** Pick your watch in the phone's "Send to.." menu and the photo, GIF, video or track you have open there opens on the watch. A file kept on a network or cloud source is fetched first, with progress you can cancel. The watch app has to be open at the time - if it is closed, the phone says so instead of leaving you guessing. The phone also tells you apart the watch being unreachable, the watch staying silent, a type the watch cannot show, and a file above the 32 MB limit. Documents, text and EPUB do not offer the watch at all.
- **File Actions on the Watch:** Long press a file to start selecting, tap other files to add them, or use Select all. The selection can then be sent to your paired phone, moved there, deleted, or renamed. Deleting and renaming apply to files the app itself keeps on the watch - anything you browse over SMB, FTP or SFTP is read-only, and the phone decides where a sent file lands.

### 🧮 Mini-Programs Suite
- **Watch Calculator:** Grid-based math calculator with on-screen operation history.
- **Network Monitor:** Live status monitoring of watch Wi-Fi, Bluetooth, and network interface status.
- **Mini-Game:** On-watch entertainment usable completely offline without a phone connection.

### 🧩 Wear OS Tiles
- **Three Dedicated Tiles:** Add Network Resource, Stream, and Favourites tiles to your Wear OS tile carousel.
- **Quick Assignment:** Point an unassigned Resource or Stream tile to your target directly from the watch.
- **Standalone & Offline:** Launch assigned network shares, streams, or favourites list instantly from your watch face, completely independent of the phone or network state.

---

## 📸 Interface & Layout Showcase

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
- [Main Application Documentation](../README.md) — Complete overview of FastMediaSorter for Android phones, tablets, and TV.
- [First Launch & Device Profiles](../QUICK_START.md#first-launch-choose-your-device-profile-30-seconds-) — Pre-configure your phone for Photo Frame, In-Car Music, or Home Cinema.
- [All Step-by-Step Scenario Guides](../howto/index.md) — 15+ guides for network shares, camera backups, and media organization.

---

## 📥 Downloads & App Stores

FastMedia Wear and FastMedia Mobile App are available across major app stores and direct download channels:

### Smartwatch & Mobile App Packages
- [<img src="../icons/doc/ic_info.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> **Google Play Store**](https://play.google.com/store/apps) — Download phone and Wear OS companion apps directly to your devices.
- [<img src="../icons/doc/ic_resource_smb.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> **F-Droid Open Source**](https://f-droid.org) — Open-source builds for Android devices and Wear OS watches.
- [<img src="../icons/doc/ic_download.png" alt="" width="18" height="18" style="vertical-align:text-bottom"> **Direct APK Release**](../DOWNLOADS_EN.md) — Direct APK downloads for phone and smartwatch sideloading.

---

## 📚 Step-by-Step Guides

- [Put FastMedia on Your Smartwatch](../howto/wear-install.md) — Step-by-step installation and pairing guide.
- [Listen to Music on Smartwatch](../howto/scenario-watch-music.md) — How to play tracks, manage volume, and save battery.
- [Connect Watch to Network Shares](../howto/scenario-watch-network.md) — Connect your watch directly to PC and NAS SMB/SFTP shares.

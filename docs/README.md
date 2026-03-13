# FastMediaSorter v2 🚀

![Status](https://img.shields.io/badge/Status-Production%20Ready-success?style=flat-square)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple?style=flat-square&logo=kotlin)
![Android](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)
![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)

**📖 Other Languages:** [🇷🇺 Русский](Readme_RU.md) | [🇺🇦 Українська](Readme_UK.md)

## About the Project

**FastMediaSorter v2** is a powerful Android application for quick and convenient sorting of media files (images, videos, GIFs, audio, documents). It is designed as a single center for managing files from various sources: local device folders, network drives (SMB, SFTP, FTP), and cloud storage (Google Drive, OneDrive, Dropbox).

The key idea of v2 is to combine viewing, playback, and organization of files in one intuitive interface, eliminating the shortcomings and limitations of the previous version.

### 🚀 Latest Update (February 2026)

- **Batch Deletion Optimization**: Fixed multiple permission dialogs when deleting files - now shows ONE dialog for any number of files on Android 11+ (Task 1 ✅)
- **Hidden Files Support**: Corrected hidden files filtering - files starting with `.` now properly respect the "Show Hidden Files" setting (Task 3 ✅)
- **Binary Files Support**: Added support for binary files (ZIP, RAR, APK, ISO, EXE, etc.) with programmatic thumbnails and context menu - visible only in "All Files" mode (Task 6 ✅)
- **Keyboard & Mouse Support**: Full keyboard navigation and mouse support across all screens - perfect for ChromeOS and desktop mode (Task 8 ✅)
- **Enhanced Reliability**: Fixed file visibility after copy/move operations and resolved empty folder scanning issues.
- **Better Compatibility**: Improved handling of scoped storage on Android 10-14.

## Windows Version 🖥️

Looking for a desktop solution? Check out **FastMediaSorter LITE** - a lightweight Windows Forms application for quickly sorting, viewing, and managing image and video files:

🔗 **[FastMediaSorter LITE for Windows](https://github.com/SerZhyAle/FastMediaSorter_Lite)**

Features include:

- Fast navigation through large folders of images and videos
- Slideshow and random file viewing modes
- Recent files and folders tracking
- File operations: move, copy, rename, and delete
- Image panel for quick visual navigation
- Customizable keyboard shortcuts for efficient workflow
- Multi-language support (English/Russian)
- Supports Windows 7/10/11 with .NET Framework 4.8

## Table of Contents

- [Download](#download-)
- [Key Features](#key-features)
- [Screenshots](#screenshots-)
- [Usage Scenarios](#usage-scenarios-)
- [Documentation](#documentation--документация-)
- [Build Instructions](#build-instructions)
- [Tech Stack](#technology-stack)

## Product Flavors 🎯

FastMediaSorter v2 is available in **4 different flavors** to suit different needs and reduce app size:

| Flavor | Description | Features |
|--------|-------------|----------|
| **Standard** | Full-featured version with all capabilities | Videos, Audio, Images, Documents, EPUB, Cloud Storage, Translation |
| **Lite** | Lightweight version for basic needs | Videos, Images only (no audio, cloud, documents) |
| **Photos** | Optimized for photo management | Images only - perfect for photo viewing and editing |
| **Legacy** | Extended local media support | Videos, Audio, Images (network/cloud support removed) |

### Which Flavor Should I Download?

- **Standard** ⭐ **(Recommended)**: Full experience with all features - cloud storage, document viewing, translations, audio support
- **Lite**: Smaller app size, focused on photos and videos
- **Photos**: Best for users who only work with images
- **Legacy**: No cloud/network features but full local media support

## Download 📥

**Compiled APK files are NOT stored in this GitHub repository.** All builds are available on **Google Drive**:

🔗 **[Download All Builds from Google Drive](https://drive.google.com/drive/folders/1_U47It406WWQKaXkGGzNVPcKE4OPV0Jp?usp=sharing)**

| Flavor | File Name | Description |
|--------|-----------|-------------|
| **Standard** | `FastMediaSorter_standard_release.zip` | Full features (Cloud, OCR, EPUB, Translation) |
| **Lite** | `FastMediaSorter_lite_release.zip` | Basic (Videos and Images only, no cloud/audio) |
| **Photos** | `FastMediaSorter_photos_release.zip` | Images only (with cloud support) |
| **Legacy** | `FastMediaSorter_legacy_release.zip` | Full local media (Android 6.0+ compatibility) |

> **Note**: All builds are automatically uploaded to Google Drive after successful compilation.
>
> 🔐 **ZIP Password: `1`** (APK files are packaged as password-protected ZIP archives to bypass Google Drive restrictions)

## Screenshots 📱

<!-- Place screenshots here. Example structure: -->
| Main Screen | File Actions | Settings |
|:-----------:|:------------:|:--------:|
| <img src="docs/images/Screenshot_20251109_000251.png" width="200"> | <img src="docs/images/Screenshot_20251109_000314.png" width="200"> | <img src="docs/images/Screenshot_20251109_000323.png" width="200"> |
| **Player View** | | |
| <img src="docs/images/Screenshot_20251114_184930.png" width="200"> | | |

## Key Features

- 🗂️ **Unified Interface:** View and manage files from all sources in one window.
- ⚡ **Fast Sorting:** Copy or move files to pre-configured destination folders with one click.
- ⭐ **Favorites System:** Mark important files as favorites and access them quickly from a dedicated tab that aggregates favorites across all sources.
- 🔒 **PIN Protection:** Secure individual resources with access PIN codes to prevent unauthorized browsing and editing.
- ⚙️ **Per-Resource Configuration:** Customize slideshow interval, scan depth (subdirectories), and thumbnail generation for each folder individually.
- 🖥️ **Network and Cloud Support:** Work with files on your network drives (SMB with automatic network scanning), SFTP servers, FTP, and in cloud storage (Google Drive, Dropbox, OneDrive).
- 🖼️ **Flexible Viewing:** Display files as a customizable grid or detailed list with pagination support for large collections (1000+ files).
- ▶️ **Built-in Player:** Playback of video and audio, viewing images and GIFs without leaving the app. Supports slideshow and full-screen zooming.
- 🎵 **Lyrics Support:** View song lyrics for the currently playing track. Automatically searches by metadata (Artist/Title) using `api.lyrics.ovh`, with fallback to filename parsing.
- 🎶 **Slideshow Background Music:** Play background music during image slideshows. Select any audio resource as your music source, with random track playback, volume control, and track name display. Tap the track name to skip to a different random track. Works seamlessly with network and cloud files.
- ✏️ **Image Editing:** Rotate, flip, apply filters (grayscale, sepia, negative), adjust brightness/contrast/saturation - for both local and network files.
- 🗂️ **Binary Files Support:** View and manage binary files (ZIP, RAR, APK, ISO, EXE, DLL, etc.) with generated thumbnails showing file extensions. Context menu with Share/Open With/Copy/Move/Rename/Delete. Available only in "All Files" mode.
- ⌨️ **Keyboard & Mouse Support:** Full keyboard navigation (arrow keys, shortcuts like Ctrl+A/C/X, F2, F5, Delete, Backspace) and mouse support (right-click context menu, hover effects, focus indicators) for ChromeOS and desktop mode.
- 🔍 **Sorting and Filtering:** Order files by name, date, size, and duration. Apply filters for quick search. Support for hidden files (starting with `.`) with dedicated toggle.
- ↩️ **Undo & Trash:** Ability to undo the last action (copy, move, delete) with soft-delete to `.trash/` folder. Includes "Empty Trash" functionality for resources.
- 🎨 **Modern Interface:** Support for light and dark themes, intuitive controls, Material Design 3.
- 💾 **Smart Caching:** Two-stage video metadata loading (1MB initial, 5MB extended) and configurable thumbnail cache (2GB default, up to 16GB).
- 📄 **Document Viewer:** Built-in viewer for Text files (.txt, .md, .log, .json, .xml) and PDF documents with zoom, pan, and gesture navigation.
- � **EPUB E-Book Reader:** Native EPUB reader with chapter navigation, table of contents, font size control, in-book search, and dark/light theme support. Works with local and network files.
- �📥 **Download & Open:** Download network files (SMB/SFTP/FTP) to local storage and open them in external apps with progress tracking.
- 🌐 **Auto-Translation:** Instantly translate text from images, PDFs, and text files using a **Hybrid OCR System** (Google ML Kit + Tesseract) for superior accuracy in both Latin and Cyrillic scripts. Supports both standard and **lens-style overlay mode** for in-place translations.
- 📱 **Widget Support:** Quick access to your favorite folders directly from your home screen with two widget types: **Resource Shortcut** (opens any folder instantly) and **Continue Reading** (launches slideshow mode immediately).
- 👆 **Advanced Gestures:** Smart zoom controls (2x/3x/4x) for images and intuitive touch zones for file navigation.

## Supported Media Formats 🎞️

FastMediaSorter v2 supports a wide range of formats:

- **Images:** JPG, JPEG, PNG, GIF, BMP, WEBP, HEIC, HEIF, AVIF
- **Video:** MP4, MKV, AVI, MOV, WMV, FLV, WEBM, M4V, 3GP, MPG, MPEG, TS, M2TS, VOB, OGV, DIVX, MTS
- **Audio:** MP3, FLAC, AAC, OGG, M4A, WMA, OPUS, AMR, ALAC, CAF, MKA, OGA, AC3, MID, MIDI
- **Documents:** TXT, MD, LOG, JSON, XML, PDF, **EPUB**
- **Binary Files** (All Files mode): ZIP, RAR, 7z, TAR, GZ, ISO, DMG, IMG, APK, EXE, DLL, SO, and 60+ other formats

## Usage Scenarios 💡

Here are a few ways FastMediaSorter v2 can help you:

### 1. 📸 Organizing Camera Photos

Connect your phone or open a local camera folder. Set up a "Best Photos" destination folder. Open the viewer, quickly swipe through thousands of photos, and tap the destination button to instantly copy the best shots.

### 2. 🏠 Network Backup (NAS)

Add your home NAS via SMB. Browse your local media files. Select multiple files or a range, and "Move" them to your NAS for safe keeping, freeing up space on your device.

### 3. ☁️ Cloud Management

Connect your Google Drive, Dropbox, or OneDrive account. Browse your cloud files without downloading them all. Delete unwanted files or organize them into folders directly in the cloud.

### 4. 📺 Slideshow & Presentation

Open a folder with family photos or presentation slides. Hit "Play" to start a slideshow. Use the per-resource settings to adjust the slide duration to your liking.

### 5. ⭐ Managing Favorites

Mark important files with the star button while browsing. Later, tap the "Favorites" tab in the main menu to instantly access all your favorite files from all sources in one place - perfect for creating a curated collection of your best media.

### 6. 🎶 Slideshow with Background Music

Add your music collection as a resource. In Settings → Audio, enable "Slideshow Background Music" and select your music resource. Now when you start a slideshow of your photos, your favorite tracks will play in the background. Tap the track name to skip to a different random song, creating the perfect ambiance for your photo presentations.

### 7. 🖼️ Digital Photo Frame

Give a second life to an old tablet! Even a slow device can become a beautiful photo frame. Connect it to your home PC (SMB) or cloud storage, start a slideshow with background music, and enjoy your memories without copying a single file to the device.

### 8. 🍿 Home Cinema & VR

Watch your favorite series stored on your PC or cloud directly on your phone or VR headset. No need to wait for copying or worry about free space. Just press play, and the next episode will start automatically.

### 9. 🧹 Download Organizer

Downloads folder cluttered? Open it in the source panel, set up destination buttons for "Documents", "Images", and "Installers". Quickly scan through files, preview them, and sort them into the right places with a single tap. You can even sort files directly on your network computer using your phone as a remote control.

## Documentation / Документація / Документация 📚

**🌐 Official Website:** [https://serzhyale.github.io/FastMediaSorter_mob_v2/](https://serzhyale.github.io/FastMediaSorter_mob_v2/)

Detailed guides are available in multiple languages:

**🇺🇸 English:**

- [How-To Guides](docs/HOW_TO.md)
- [Quick Start](docs/QUICK_START.md)
- [FAQ](docs/FAQ.md)
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Program Limitations](docs/LIMITATIONS.md)
- [Complete Feature List](docs/FEATURES.md)

**🇷🇺 Русский:**

- [Руководства](docs/HOW_TO_RU.md)
- [Быстрый Старт](docs/QUICK_START_RU.md)
- [FAQ](docs/FAQ_RU.md)
- [Устранение неполадок](docs/TROUBLESHOOTING_RU.md)
- [Ограничения программы](docs/LIMITATIONS_RU.md)

**🇺🇦 Українська:**

- [Посібники](docs/HOW_TO_UK.md)
- [Швидкий Старт](docs/QUICK_START_UK.md)
- [FAQ](docs/FAQ_UK.md)
- [Вирішення проблем](docs/TROUBLESHOOTING_UK.md)

## Build Instructions

### Requirements

- Android Studio Hedgehog (2023.1.1) or newer

- JDK 17+
- Android SDK 34
- Minimum Android version: 9.0 (API 28)

### Build

1. Clone the repository:

    ```bash
    git clone https://github.com/yourusername/FastMediaSorter_mob_v2.git
    cd FastMediaSorter_mob_v2
    ```

2. Open the project in Android Studio.
3. Wait for Gradle synchronization to complete.
4. Run the app on an emulator or physical device.

### Built APKs 📦

After every successful build, the generated APK file is automatically copied to the `DOWNLOADS` folder in the project root with a timestamp. You can find all your build history there.

## First Steps (Quick Usage Guide) 🚀

1. **Adding a Folder (Resource):**
    - On the main screen, press the button with the "Plus" (+) icon to add a new resource.
    - Select the resource type (e.g., "Local Folder").
    - Use scanning or add the folder manually. After adding, it will appear in the list on the main screen.

2. **Viewing Files:**
    - Double-tap (or long press) on the added resource in the list.
    - The browse screen will open, where you will see all media files from this folder as a list or grid.
    - Use the buttons on the top panel for sorting, filtering, or switching view.

3. **Playback and Sorting:**
    - Tap on any file to open it in the full-screen player.
    - Use swipes left/right or touch zones for navigation between files.
    - For operations (copy, move), use the corresponding touch zones or buttons on the control panel.

4. **Configuring Destination Folders (Destinations):**
    - In settings, on the "Destinations" tab, you can specify up to 30 folders that will be used for quick sorting.
    - Alternatively, enable "Is Destination" in any resource's edit screen to add it to the quick sort list.
    - After that, buttons for quick copying or moving files to these folders will appear on the player screen.

## Technology Stack

- **Language**: Kotlin
- **Architecture**: Clean Architecture, MVVM
- **UI**: Android View System (XML), Material Design 3
- **Asynchrony**: Kotlin Coroutines & Flow
- **DI**: Hilt (Dagger)
- **Database**: Room (version 6 with cloud provider support)
- **Navigation**: AndroidX Navigation Component
- **Media**: ExoPlayer (Media3 1.2.1)
- **Image Loading**: Glide 4.15.1 with custom NetworkFileModelLoader
- **Network Protocols**:
  - SMB: SMBJ 0.12.1 with BouncyCastle 1.78.1
  - SFTP: SSHJ 0.37.0 with EdDSA 0.3.0
  - FTP: Apache Commons Net 3.10.0
- **Cloud**: Google Drive API, OneDrive (MSAL), Dropbox API with OAuth 2.0
- **OCR & Translation**:
  - Google ML Kit (Text Recognition v2, Translation) for Latin scripts
  - Tesseract4Android (Tesseract 5.3.x) for high-accuracy Cyrillic OCR
- **Search & Lyrics**: api.lyrics.ovh (JSON API)

## Project Status

✅ **Production Ready** - Core functionality fully implemented and tested:

- ✅ Local file operations (copy, move, delete, undo)
- ✅ Network protocols (SMB, SFTP, FTP)
- ✅ Cloud storage integration (Google Drive, OneDrive, Dropbox with OAuth authentication)
- ✅ Image editing (rotation, flip, filters, adjustments)
- ✅ Pagination for large file collections (1000+ files)
- ✅ Keyboard and mouse navigation across all screens
- ✅ Smart caching with two-stage metadata loading
- ✅ Soft-delete with trash folder support
- ✅ Favorites system with cross-resource aggregation
- ✅ EPUB e-book reader with chapter navigation
- ✅ Document viewer (PDF, Text files)
- ✅ Auto-translation with hybrid OCR (ML Kit + Tesseract)
- ✅ Subfolder navigation in Browse activity with breadcrumb display
- ✅ Wear OS companion app (browse and play local media directly from your smartwatch)

## Build Version

Version format: `Y.YM.MDDH.Hmm` (e.g., `2.60.1102.207` for 2026/01/10 20:07)

See [dev/CHANGELOG.md](dev/CHANGELOG.md) for detailed release notes.

---

## Contributing 🤝

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

## Contact 📧

- **Developer**: <sza@ukr.net>
- **Website**: [https://serzhyale.github.io/FastMediaSorter_mob_v2/](https://serzhyale.github.io/FastMediaSorter_mob_v2/)
- **GitHub Issues**: [https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues)

## License 📄

Distributed under the MIT License. See `LICENSE` for more information.

*This file was generated based on project documentation.*

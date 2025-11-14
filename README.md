# FastMediaSorter v2 🚀

## About the Project

FastMediaSorter v2 is a powerful Android application for quick and convenient sorting of media files (images, videos, GIFs, audio). It is designed as a single center for managing files from various sources: local device folders, network drives (SMB, SFTP), and cloud storage.

The key idea of v2 is to combine viewing, playback, and organization of files in one intuitive interface, eliminating the shortcomings and limitations of the previous version.

## Key Features

*   🗂️ **Unified Interface:** View and manage files from all sources in one window.
*   ⚡ **Fast Sorting:** Copy or move files to pre-configured destination folders with one click.
*   🖥️ **Network and Cloud Support:** Work with files on your network drives (SMB), SFTP servers, and in cloud storage (Google Drive, Dropbox, etc.).
*   🖼️ **Flexible Viewing:** Display files as a customizable grid or detailed list.
*   ▶️ **Built-in Player:** Playback of video and audio, viewing images and GIFs without leaving the app. Supports slideshow.
*   🔍 **Sorting and Filtering:** Order files by name, date, size, and duration. Apply filters for quick search.
*   ↩️ **Undo Operations:** Ability to undo the last action (copy, move, delete).
*   🎨 **Modern Interface:** Support for light and dark themes, intuitive controls.

## Build Instructions

### Requirements
*   Android Studio Hedgehog (2023.1.1) or newer
*   JDK 17+
*   Android SDK 34
*   Minimum Android version: 9.0 (API 28)

### Build
1.  Clone the repository:
    ```bash
    git clone https://github.com/yourusername/FastMediaSorter_mob_v2.git
    cd FastMediaSorter_mob_v2
    ```
2.  Open the project in Android Studio.
3.  Wait for Gradle synchronization to complete.
4.  Run the app on an emulator or physical device.

## Quick Usage Guide

1.  **Adding a Folder (Resource):**
    *   On the main screen, press the button with the "Plus" (+) icon to add a new resource.
    *   Select the resource type (e.g., "Local Folder").
    *   Use scanning or add the folder manually. After adding, it will appear in the list on the main screen.

2.  **Viewing Files:**
    *   Double-tap (or long press) on the added resource in the list.
    *   The browse screen will open, where you will see all media files from this folder as a list or grid.
    *   Use the buttons on the top panel for sorting, filtering, or switching view.

3.  **Playback and Sorting:**
    *   Tap on any file to open it in the full-screen player.
    *   Use swipes left/right or touch zones for navigation between files.
    *   For operations (copy, move), use the corresponding touch zones or buttons on the control panel.

4.  **Configuring Destination Folders (Destinations):**
    *   In settings, on the "Destinations" tab, you can specify up to 10 folders that will be used for quick sorting.
    *   After that, buttons for quick copying or moving files to these folders will appear on the player screen.

## Technology Stack

-   **Language**: Kotlin
-   **Architecture**: Clean Architecture, MVVM
-   **UI**: Android View System (XML), Material Design
-   **Asynchrony**: Kotlin Coroutines & Flow
-   **DI**: Hilt
-   **Database**: Room
-   **Navigation**: AndroidX Navigation Component
-   **Media**: ExoPlayer (Media3)
-   **Network**: SMBJ (for SMB), SSHJ (for SFTP)

## Project Status

The project is in active development. Core functionality for working with local files is implemented. Work is underway on integrating network protocols and expanding user settings.

---
*This file was generated based on project documentation.*
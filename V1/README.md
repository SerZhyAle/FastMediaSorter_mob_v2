# FastMediaSorter Mobile

A lightweight and private Android application for viewing, sorting, and managing images and videos from your device's local storage and SMB (Samba/Windows) network shares. Perfect for creating a digital photo frame, organizing media, or simply browsing your collection with intuitive controls.

## Recent Updates

### Version 1.2.2 (2025-10-28)
- **Small Buttons Setting**: Added "Use small buttons" option to display all 10 sort buttons in a single row with smaller text size and compact square buttons (half height for better space utilization)
- **UI Improvements**: Enhanced button layout and text sizing options for better user experience

### Version 1.2.1 (2025-10-28)
- **SMB Permissions Fix**: Resolved missing action buttons for SMB folders in Sort mode
- **Slideshow Diagnostics**: Added comprehensive logging for troubleshooting GIF/video display issues
- **Build Stability**: Fixed compilation errors and improved error handling

### Version 1.2.0 (2025-10-22)
- **API 35 Support**: Updated to Android 15 with latest security and performance optimizations
- **Global Crash Handler**: Comprehensive error handling with detailed crash reports and logging
- **Enhanced Diagnostics**: Improved error visibility and troubleshooting capabilities

### Version 1.1.0 (2025-10-20)
- **Write Permissions System**: Automatic detection and validation of SMB folder permissions
- **Video Playback Control**: New "Play video till end" option for complete video playback
- **Improved Slideshow**: Fixed image flashing during transitions and enhanced empty folder handling
- **Bug Fixes**: Resolved slideshow interval persistence issues

## Key Features

-   **Dual Media Access**: Seamlessly browse media from both local device folders (Camera, Screenshots, etc.) and remote SMB network shares.
-   **Smart Network Setup**:
    -   Adaptive subnet detection - automatically suggests IP templates based on your current network.
    -   DNS resolution with NetBIOS fallback for server names.
    -   Auto-discovery - scan and add all accessible SMB shares from a server with one tap.
    -   Connection testing with comprehensive diagnostics.
    -   Write permission detection - automatically tests and displays write access status for folders.
-   **Powerful Slideshow**:
    -   Supports images (including GIF animations), videos, and mixed media types.
    -   Configurable interval (1-300 seconds) with persistent settings.
    -   Image and video preloading for smooth, instant transitions.
    -   Two control modes: invisible touch zones or a visible button panel.
    -   ABC (alphabetical) and Random (shuffle) order modes.
    -   Media validation to skip corrupted files before playback attempts.
    -   Smart empty folder handling with warning messages instead of errors.
-   **Advanced File Sorting**:
    -   Organize media by copying, moving, or deleting files.
    -   Full support for sorting between SMB shares and local Android folders (Download, Pictures, etc.).
    -   Cross-platform operations: copy/move files from SMB to local storage and vice versa.
    -   Rename files with filename validation.
    -   Configure up to 10 custom sort destinations.
    -   Write permission validation prevents adding read-only destinations.
    -   Safe by design: uses system permissions for delete/move operations on Android 11+.
-   **Video Settings**:
    -   Enable or disable video playback in slideshows.
    -   Set a maximum file size for videos to prevent playback of large files.
    -   "Play video till end" option - videos can either play completely or follow slideshow interval.
    -   Detailed error diagnostics for troubleshooting video playback issues.
-   **Customizable Settings**:
    -   Control which operations are allowed (copy, move, delete, rename).
    -   Prevent device from sleeping during slideshow and sorting.
    -   Optional confirmation dialogs for destructive operations.
    -   **Use small buttons**: Display all 10 sort buttons in a single row with smaller text size and compact square buttons (half height for better space utilization).
-   **Privacy-Focused**:
    -   No internet access required (only local network for SMB).
    -   No data collection, analytics, or tracking. All settings are stored locally on your device.
    -   Global crash handler with detailed error reporting for troubleshooting.
-   **Modern UI**:
    -   Clean, intuitive interface built with Material Design 3.
    -   Optimized for both phones and tablets in portrait and landscape orientations.

## Getting Started

### 1. Accessing Local Folders

1.  Navigate to the **Settings** tab.
2.  Tap **Grant Media Access** and allow the required permission.
3.  On the main screen, switch to the **Local Folders** tab.
4.  Select a folder (e.g., Camera, Screenshots) or tap **+ Add Custom Folder** to add your own.

### 2. Accessing a Network (SMB) Share

1.  On the main screen, switch to the **Network** tab.
2.  Enter the server address and folder path (e.g., `192.168.1.100\Photos` or `MYSERVER\Photos`).
    -   The app automatically detects your current subnet and suggests the appropriate IP template.
    -   You can use either IP addresses or server names (DNS resolution with NetBIOS fallback).
3.  Provide a username and password, or leave them blank for guest access.
4.  Optionally, enter a name for the connection and tap the **Save** icon (💾).
5.  **Auto-discovery**: Tap the **🔍** button to automatically scan and add all accessible shares from the server.
6.  Tap **Test** to verify the connection before saving.
7.  Tap **Slideshow** or **Sort** to begin.

## Usage Guide

### Slideshow Controls

The slideshow offers two control modes, which can be toggled in **Settings**.

**Mode 1: Invisible Touch Zones (Default)**

The screen is divided into invisible zones for a distraction-free experience.

![Touch Control Zones](assets/touch_zones.png)

**Mode 2: Visible Button Panel**

Enable this mode in **Settings → Slideshow Settings → Show Controls**. A minimalist panel with buttons for Back, Previous, Play/Pause, Next, and Rotate will appear.

### Sorting Screen

-   **Navigate**: Swipe left or right to browse through media.
-   **Copy/Move/Delete/Rename**: Use the buttons at the bottom to manage files. These operations can be enabled or disabled in **Settings**.
-   **Refresh**: Tap the refresh button at the top to re-read the folder contents.
-   **Destinations**: Configure up to 10 sort destinations in the **Settings → Sort to..** tab.

## Settings

### Sort to.. Settings

-   **Allow to Copy**: Enable copying files to destination folders.
-   **Allow to Move**: Enable moving files to destination folders.
-   **Allow to Delete**: Enable deleting files with optional confirmation.
-   **Allow renaming**: Enable renaming files (validates filename and prevents conflicts).

### Slideshow Settings

-   **Show Controls**: Toggle between invisible touch zones and visible button panel.

### Video Settings

-   **Enable Video**: Allow video playback in slideshows.
-   **Max Video Size**: Set maximum file size for videos (in MB).
-   **Play video till end**: Choose whether videos play completely or follow slideshow interval timing.
-   **Show detailed video error information**: Display comprehensive error diagnostics when video playback fails.
-   **Supported Formats**: MP4, MKV, MOV, WEBM, 3GP (AVI format not supported for SMB streaming due to library compatibility issues). Images: JPG, JPEG, PNG, GIF, BMP, WEBP.

### General Settings

-   **Not allow device to sleep**: Keep screen on during slideshow and sorting (enabled by default).

## Build from Source

To build the application from the source code, follow these steps:

1.  Clone the repository.
2.  Ensure you have Android SDK with API 35 installed.
3.  To sign a release build, create a `keystore.properties` file in the root directory with your signing key details.
4.  Run the appropriate Gradle task:

    -   **Debug APK**:
        ```bash
        ./gradlew assembleDebug
        ```
    -   **Release App Bundle (for Google Play)**:
        ```bash
        ./gradlew bundleRelease
        ```

## Troubleshooting

### Slideshow Issues

If videos or GIFs are not displaying in slideshow mode:

1. **Check Logs**: The app includes detailed logging for media loading diagnostics. Check the device logs using Android Studio's Logcat with filter `tag:SlideshowActivity`.
2. **Video Playback**: Ensure videos are in supported formats (MP4, MKV, MOV, WEBM, 3GP). AVI is not supported for SMB streaming.
3. **GIF Support**: Animated GIFs are supported as image files and should display with animation.
4. **File Size**: Check that video files don't exceed the maximum size limit set in Video Settings.
5. **Network Issues**: For SMB shares, ensure stable network connection and proper permissions.
6. **Crash Logs**: If the app crashes, detailed error information is saved to the app's external files directory and displayed in crash dialogs.

### SMB Connection Issues

- **Write Permissions**: The app automatically detects write permissions for SMB folders. If sort buttons are missing, the folder may be read-only. Note: SMB folders default to write-enabled for better compatibility.
- **Connection Testing**: Use the Test button when adding SMB connections to verify access.
- **Auto-discovery**: Use the 🔍 button to automatically find available shares on a server.

### Performance Issues

- **Media Validation**: The app performs fast header validation (<100ms) to skip corrupted files.
- **Preloading**: Images and videos are preloaded for smooth transitions.
- **Memory**: Large video files may cause performance issues - adjust the max video size setting.

### Getting Help

- **Crash Reports**: Crash dialogs include full stack traces and device information.
- **Logs**: Enable detailed logging in development builds for troubleshooting.
- **Error Codes**: Video playback errors include ExoPlayer error codes for precise diagnosis.

-   **Language**: 100% Kotlin
-   **Architecture**: MVVM with a Repository pattern, using LiveData and Kotlin Coroutines.
-   **UI**: Material Design 3, ViewPager2, RecyclerView, and ConstraintLayout.
-   **Database**: Room for storing SMB connection profiles.
-   **Networking**: jCIFS-ng for SMBv2/SMBv3 client support.
-   **Media**: ExoPlayer for video playback with custom SMB data source.
-   **Image Loading**: Glide for efficient image loading and caching.
-   **Validation**: Fast media header validation (<100ms) to detect corrupted files before loading.
-   **Error Handling**: Global crash handler with detailed error dialogs and logging for troubleshooting.
-   **Minimum SDK**: API 28 (Android 9.0)
-   **Target SDK**: API 35 (Android 15)

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Contact

sza@ukr.net - 2025

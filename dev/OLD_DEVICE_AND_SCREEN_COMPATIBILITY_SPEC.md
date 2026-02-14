# Compatibility with Old Devices and Non-Standard Screens

This document outlines potential issues when running "FastMediaSorter" on devices with older Android versions and non-standard screen sizes, based on the current project configuration.

## 1. Operating System Compatibility (Android Versions)

### Current Configuration
- **Minimum SDK (Standard):** API 28 (Android 9.0 Pie)
- **Minimum SDK (Legacy Flavor):** API 23 (Android 6.0 Marshmallow)
- **Target SDK:** API 35 (Android 15)

### Potential Issues on Older Versions (API 23 - 28)

1.  **Permission Model Differences**
    -   **Issue:** Android's permission model has changed significantly. Android 6.0 introduced runtime permissions. Android 10 changed file access (Scoped Storage), and Android 11 forced it. Android 13 split media permissions.
    -   **Risk:** Code handling file access might fail on devices running Android 10 (API 29) or lower if strict "legacy" storage flags aren't handled correctly in the manifest or code. The app uses `requestLegacyExternalStorage` but this is ignored on Android 11+. Ensure logic cleanly separates API levels.
    
2.  **Vector Drawables**
    -   **Issue:** While `vectorDrawables.useSupportLibrary = true` is enabled (good!), complex vectors can act differently or cause performance hits on older API levels (pre-24) due to how the support library rasterizes them.
    -   **Recommendation:** Test icons and complex UI elements on an API 23 emulator.

3.  **TLS/SSL Security**
    -   **Issue:** Older devices (Android 6/7) might have outdated root certificates or older TLS versions.
    -   **Risk:** Network requests (Cloud, Updates) might fail with SSL handshake errors.
    -   **Mitigation:** The app uses `ProviderInstaller` or similar via Google Play Services to ensure up-to-date security providers, but this is a common point of failure on unmaintained older devices.

4.  **Background Execution Constraints**
    -   **Issue:** Android 8.0 (Oreo) introduced strict background execution limits. Android 12 further restricted "phantom processes".
    -   **Risk:** If the app performs long-running sorting tasks in the background, they might be killed aggressively on Android 8+ devices if not using Foreground Services correctly.

## 2. Screen Size and Density Compatibility

### Current Configuration
-   **Layouts:** `layout` (default), `layout-land`, `layout-sw480dp`.
-   **Values:** `values-sw320dp`, `values-sw480dp`, `values-sw600dp` (dimensions only).
-   **ConstraintLayout:** Used extensively (Version 2.1.4).

### Potential Issues

1.  **Tablet Support Weakness**
    -   **Issue:** There are no `layout-sw600dp` (7-inch tablets) or `layout-sw720dp` (10-inch tablets) directories. Only `values` (dimensions) scale.
    -   **Risk:** The UI basically stretches phone layouts to fill tablet screens. This often results in:
        -   Excessively long lines of text (hard to read).
        -   Buttons being too wide or too far apart.
        -   Wasted screen real estate (whitespace).
    -   **Recommendation:** Create `layout-sw600dp` XMLs for key screens (like Main Menu or Grid View) to use a multi-column layout.

2.  **Non-Standard Aspect Ratios**
    -   **Issue:** Modern phones are often 20:9 or 21:9. Older phones are 16:9. Some foldables are nearly 1:1.
    -   **Risk:**
        -   **Tall Screens:** UI elements anchored to the bottom might leave a huge gap in the middle.
        -   **Short/Wide Screens:** Dialogs or bottom sheets might not fit vertically if the keyboard opens.
    -   **Mitigation:** Ensure all input screens (like "Rename") are wrapped in a `ScrollView`.

3.  **Density (DPI) Issues**
    -   **Issue:** Low-end devices might use `ldpi` or `mdpi`.
    -   **Risk:** If only `xhdpi` or `xxhdpi` assets are provided (to save space), Android scales them down. This usually works but can cause aliasing/blurriness on text inside images or small icons.

4.  **"Small" Screens (sw320dp)**
    -   **Issue:** Older devices (e.g., 4-inch screens) have very little width (320dp).
    -   **Risk:** Text truncation in Toolbars, overlapping buttons in "rows".
    -   **Recommendation:** Change `layout_width="wrap_content"` to `0dp` (match constraints) with proper `app:layout_constraintHorizontal_weight` in `ConstraintLayout` to adapt gracefully.

## 3. Hardware Performance on Legacy Devices

1.  **Memory (RAM)**
    -   **Issue:** The app requests `android:largeHeap="true"`.
    -   **Risk:** On devices with 2GB or 3GB RAM (common for Android 6-8), the system might be aggressive in killing the app if it consumes too much, especially during image processing.
    -   **Recommendation:** Ensure the `Lite` flavor is actually used for these devices, or implement dynamic memory management in `ImageLoadingManager`.

2.  **Rendering Speed**
    -   **Issue:** Heavy use of shadows (`elevation`), semi-transparent overlays, or complex clipping (CardView corner radius) is expensive on old GPUs.
    -   **Risk:** UI stutter (jank) during scrolling.

## Summary of Recommendations

1.  **Testing:** Specifically test on an **Android 6.0 (API 23)** emulator and an **Android 10 (API 29)** emulator to catch permission transition issues.
2.  **Tablets:** Add a specific layout for `sw600dp` for the main file browser to utilize the extra width (e.g., `GridLayoutManager` with more columns).
3.  **Scrolling:** Verify that all screens with input fields have a `ScrollView` root to handle short screens + keyboard.

## 4. Graceful Degradation Strategy (Proposed Changes)

To maximize device support, the application should adopt a **"feature detection and fallback"** strategy. If a specific feature (like modern storage access or heavy PDF processing) is not supported by the device (due to OS version or hardware limits), that specific feature should be disabled or significantly simplified, while the core file management functionality remains intact.

### 4.1. Storage and File Access
**Goal:** Support Android 6.0 (API 23) to Android 15 (API 35) without crashing on permission logic.

*   **Logic:**
    *   **Android 11+ (API 30+):** Use `MANAGE_EXTERNAL_STORAGE` (All Files Access).
        *   *Fallback:* If granted, full access.
    *   **Android 10 (API 29):** This is the "problem child".
        *   *Strategy:* Attempt to use `requestLegacyExternalStorage="true"` in Manifest.
        *   *Fallback:* If legacy access fails, fallback to **Storage Access Framework (SAF)** for specific folders only (User selects "Downloads" folder manually).
    *   **Android 6.0 - 9.0 (API 23-28):** Use standard `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`.
*   **Implementation:**
    ```kotlin
    fun checkStoragePermissions(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
    // If not granted, route user to appropriate settings page based on OS version
    ```

### 4.2. Image & Media Loading (Memory Optimization)
**Goal:** Prevent `OutOfMemoryError` on older devices (2GB-3GB RAM) when loading high-res images.

*   **Logic:**
    1.  Check `ActivityManager.isLowRamDevice()`.
    2.  Check total system memory.
*   **Strategy:**
    *   **High End:** Load full-resolution thumbnails, enable high-quality bitmap config (`ARGB_8888`), enable transition animations.
    *   **Low End (< 3GB RAM):**
        *   Disable complex transition animations (`dontAnimate()` in Glide).
        *   Force lower quality bitmap config (`RGB_565` - saves 50% memory per pixel).
        *   Reduce thumbnail resolution (override size).
        *   **Disable Document Previews:** Do not attempt to render PDF/EPUB covers; show generic file icons instead to save large amounts of native heap.

### 4.3. Heavy Features (OCR & PDF Extraction)
**Goal:** Prevent crashes during background scanning on weak CPUs/Low RAM.

*   **Feature:** OCR (Text Recognition) and PDF Page Rendering.
*   **Condition:**
    *   If `Build.VERSION.SDK_INT < 26` (Android 8.0) OR `isLowRamDevice()`:
        *   **Action:** **Disable Auto-Scanning.**
        *   **UI:** Hide "Search text inside images" option in Settings, or show it as grayed out with a note "Requires newer device".
        *   **Manual Override:** Allow user to try manually on a single file, but show a warning "This may be slow".
*   **Fallback:**
    *   Instead of extracting full PDF text, only extract file metadata (Name, Size, Date).

### 4.4. UI Components & Theming
**Goal:** Ensure the app looks "Native" on modern devices but "Clean" on older ones without crashing.

*   **Material 3 Dynamic Colors (Monet):**
    *   *Condition:* `Build.VERSION.SDK_INT >= 31` (Android 12).
    *   *Action:* Use `DynamicColors.applyToActivityIfAvailable()`.
    *   *Fallback (Older Devices):* Use a fixed, high-contrast Brand Blue/Dark theme. Do not attempt to read system colors that don't exist.
*   **Ripple Effects:**
    *   *Condition:* Android 5.0+ supports ripples.
    *   *Optimization:* On Low RAM devices, replace heavy "unbounded" ripples with simple state list drawables (color change on press) to reduce GPU overdraw.

### 4.5. Cloud Services (Google Drive, OneDrive)
**Goal:** Handle devices without Google Play Services (e.g., Huawei, Custom ROMs, old unmodified Android).

*   **Issue:** Google Sign-In requires Play Services.
*   **Logic:**
    *   Check `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable()`.
*   **Strategy:**
    *   **Success:** Show Google Drive option.
    *   **Failure:** **Hide Google Drive option entirely.**
    *   **Alternative:** Keep Dropbox/OneDrive/FTP/SMB enabled (as they don't strictly depend on Play Services in the same way or use standard OAuth via Browser).

## 5. Action Plan for Developers

### Phase 1: Safety Nets (Immediate)
1.  **Modify `ImageLoadingManager.kt`:**
    *   Add `isLowRamDevice` check.
    *   If true -> Set Glide to `RGB_565` and `dontAnimate`.
2.  **Modify `SettingsActivity.kt`:**
    *   Hide "OCR / Text Analysis" settings on devices with < 4GB RAM or < Android 8.0.
3.  **Modify `MainActivity.kt` (Cloud):**
    *   Wrap Google Drive initialization in a `try-catch` block checking for Play Services. If missing, remove the button from the UI.

### Phase 2: Layout Adaptations
1.  **Create `layout-w600dp`:**
    *   Copy `activity_browse.xml`.
    *   Change `RecyclerView` layout manager from `LinearLayoutManager` (1 column) to `GridLayoutManager` (3 columns).
    *   This instantly makes tablets look like desktop apps.
2.  **Review Inputs:**
    *   Check `AddResourceActivity` and `RenameDialog`. Ensure the root view is a `ScrollView` so the keyboard doesn't hide the "OK" button on small 16:9 screens.

### Phase 3: Build Configuration
1.  **Split APKs (Optional but Recommended):**
    *   If the app size is growing (due to Native Libraries for OCR), configure "App Bundle" so older devices don't download 64-bit libraries they can't use, and newer devices don't download 32-bit ones. *Already handled by Play Store, but ensure `splits` are configured if distributing APKs manually.*

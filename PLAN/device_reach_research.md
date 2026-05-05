# Google Play Device Reach Optimization Research

This document outlines strategies to maximize the number of supported devices for Android applications on the Google Play Store.

## 1. Manifest Configurations (`AndroidManifest.xml`)

### 1.1 Explicit Hardware Features (`<uses-feature>`)
- **Concept:** By default, if an app requests certain permissions (e.g., `CAMERA`, `BLUETOOTH`, `ACCESS_FINE_LOCATION`), Google Play assumes the app *requires* the associated hardware and filters out devices lacking it (like Android TV, certain tablets, or wearables).
- **Action:** Explicitly declare these features with `android:required="false"` if the app can still function without them.
  ```xml
  <!-- Examples of features implied by permissions -->
  <uses-feature android:name="android.hardware.camera" android:required="false" />
  <uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
  <uses-feature android:name="android.hardware.bluetooth" android:required="false" />
  <uses-feature android:name="android.hardware.location" android:required="false" />
  <uses-feature android:name="android.hardware.location.gps" android:required="false" />
  <uses-feature android:name="android.hardware.telephony" android:required="false" />
  <uses-feature android:name="android.hardware.wifi" android:required="false" />
  ```

### 1.2 `minSdkVersion`
- **Concept:** This is the primary filter. Lowering the `minSdkVersion` directly increases the pool of compatible older devices.
- **Action:** Evaluate if the app can gracefully degrade features on older Android versions. Use conditional checks (`Build.VERSION.SDK_INT`) instead of raising the minimum SDK unnecessarily.

### 1.3 Screen Sizes and Formats (`<supports-screens>`)
- **Concept:** Restricting screen sizes limits reach, particularly for tablets, foldables, and Chromebooks.
- **Action:** Ensure the app does not restrict screen support.
  ```xml
  <supports-screens
      android:smallScreens="true"
      android:normalScreens="true"
      android:largeScreens="true"
      android:xlargeScreens="true"
      android:anyDensity="true" />
  ```

## 2. Architecture and ABI Support
- **Concept:** Devices run on different CPU architectures.
- **Action:** Ensure the build configuration (`build.gradle`) includes libraries for all common architectures, or use Android App Bundles (AAB) to let Google Play serve optimized APKs.
  - `arm64-v8a` (most modern devices)
  - `armeabi-v7a` (older/low-end devices)
  - `x86` / `x86_64` (ChromeOS devices and emulators)

## 3. OpenGL ES and Graphics
- **Concept:** Restricting the app to a high OpenGL ES version excludes older devices.
- **Action:** Only require high OpenGL ES versions (`<uses-feature android:glEsVersion="..." />`) if absolutely necessary for rendering, and provide fallbacks if possible.

## 4. Google Play Console Actions
- **Device Catalog Analysis:** 
  - Navigate to **Release > Reach and devices > Device catalog**.
  - Filter by "Excluded" or "Unsupported" to see exactly which devices are restricted and **why**.
  - Look for "Manifest restrictions" or "Manual exclusions" that can be safely removed.
- **Manual Exclusions:** Check if any devices were manually excluded in the past due to known crashes. If those bugs are fixed, remove the manual exclusion.

## 5. Form Factor Expansion
- **Concept:** Android runs on more than just phones.
- **Action:** Consider adding support for:
  - **Android TV / Google TV:** Requires specific manifest flags (`android.software.leanback` set to false if not a TV-only app, handling D-pad navigation).
  - **ChromeOS:** Ensure proper handling of mouse/keyboard input and freeform window resizing.
  - **Automotive / Wear OS:** Though FastMediaSorter has a Wear module, ensure the main app isn't unintentionally blocking other form factors.

## Recommended Action Plan for FastMediaSorter v2
To maximize device reach without breaking core functionality:
1. **Audit Permissions:** Review all `<uses-permission>` tags in `app_v2/src/main/AndroidManifest.xml`.
2. **Add False Requirements:** For each permission that implies a hardware feature (like Bluetooth, Camera, Location, Wi-Fi), add a corresponding `<uses-feature android:name="..." android:required="false" />`.
3. **Verify ABI:** Check `build.gradle` to ensure broad ABI support is not artificially constrained.
4. **Console Check:** Review the Play Console's Device Catalog for any specific "Excluded" devices to identify hidden restrictions.

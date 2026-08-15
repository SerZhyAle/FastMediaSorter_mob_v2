# Phase 03 - Play flavor manifests

**Goal:** Declare the screencapture permissions + components in the `standard` and `photos` flavor manifests so the moved services/activity are registered and the overlay/FGS permissions are present. Crucially: **no accessibility service** is declared in Play flavors - that omission is the Play-policy safety boundary.

**Depends on:** Phase 02.

---

## Steps

### 3.1 Add screencapture entries to standard manifest

Edit `app_v2/src/standard/AndroidManifest.xml`. Add (mirror the Play-safe subset of `src/noLegal/AndroidManifest.xml`, excluding the a11y service and `REQUEST_INSTALL_PACKAGES`):

- `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />`
- `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />`
- `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />`
- `<activity android:name="com.sza.fastmediasorter.screencapture.ScreenCaptureConsentActivity"` exported=false, excludeFromRecents, noHistory, taskAffinity="", theme `@style/Theme.FastMediaSorter.Transparent` />
- `<service android:name="com.sza.fastmediasorter.screencapture.OverlayHostService"` exported=false, foregroundServiceType="specialUse"> with `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="persistent edge overlay strip host for screenshot gesture trigger" />`
- `<service android:name="com.sza.fastmediasorter.screencapture.ScreenCaptureService"` exported=false, foregroundServiceType="mediaProjection" />

Confirm the base `FOREGROUND_SERVICE` permission is already in `src/main/AndroidManifest.xml` (audio FGS uses it); if not, add it here too.

Do NOT add `ScreenshotAccessibilityService` or its config XML.

**Verification:** `grep -c "screencapture" app_v2/src/standard/AndroidManifest.xml` >= 3 (activity + 2 services); `grep -c "AccessibilityService" app_v2/src/standard/AndroidManifest.xml` == 0.

### 3.2 Add the same entries to photos manifest

Apply the identical block to `app_v2/src/photos/AndroidManifest.xml`.

**Verification:** same predicates as 3.1 for the photos manifest.

### 3.3 Build standard + photos; assert merged manifest

Build both Play flavors and inspect the merged manifest for the absence of the accessibility service.

**Verification:**
- `.\a.ps1 dq` (standard) and `:app_v2:assemblePhotosDebug` exit 0.
- In `app_v2/build/intermediates/merged_manifests/standardDebug/AndroidManifest.xml`: `OverlayHostService` + `ScreenCaptureService` present, `ScreenshotAccessibilityService` ABSENT, `BIND_ACCESSIBILITY_SERVICE` ABSENT.

---

## Phase Done Criteria

- [ ] standard + photos manifests declare the 3 perms + consent activity + 2 services.
- [ ] Neither Play manifest declares the accessibility service.
- [ ] `assembleStandardDebug` + `assemblePhotosDebug` green; merged standard manifest has no a11y service.

# Specification: X.5 — HEIF/HEIC Support

**Status:** Implemented
**Date:** 2026-03-28
**Tier:** 3 — Moderate (4–8h, medium risk)
**Roadmap entry:** Test Glide support; add fallback decoder if needed

---

## 1. Problem Statement

HEIC and HEIF are the default camera formats on modern iPhones and on many Android devices (API 28+). The app already lists `heic` and `heif` in `MediaExtensions.IMAGE` and in AndroidManifest intent-filter MIME types, which means it advertises support and will receive "Open with" intents for these files — but actual decode behavior is inconsistent and partially broken:

- `SmbOperationsUseCase.detectMediaType()` explicitly **throws** `IllegalArgumentException` for `heic`, `heif`, and `avif`, blocking network thumbnail generation for these formats entirely.
- There is no registered HEIC/HEIF Glide decoder in `GlideAppModule`, so all decoding falls through to the platform's `BitmapFactory`, which has no HEIC support on API 26–27 (Android 8.0–8.1, `minSdk`).
- Error-handling code in `ImageLoadingManager` and `BrowseActivity` specifically catches HEIC/HEIF decode errors, confirming they already surface in production.
- The Settings screen strings (`setting_support_images_desc`) advertise HEIC as a supported format.

The result is that users with network shares full of iPhone photos receive silent failures for a format the app claims to support.

---

## 2. Goals

1. **Correct the false rejection** in `SmbOperationsUseCase` — HEIC/HEIF should be treated as `MediaType.IMAGE`, not thrown as an error.
2. **Reliable decode on API 28+** — confirm Glide 4.16.0 + platform `ImageDecoder` path works for both local and network (SMB/SFTP/FTP) HEIC files without a custom decoder.
3. **Graceful degradation on API 26–27** — show a clear "format not supported on this device" placeholder instead of a broken image or silent spinner. API 26–27 have no native HEIC decoder and no viable pure-Java fallback exists.
4. **Consistent AVIF handling** — AVIF is in the same situation. Treat it identically: supported on API 31+, placeholder on older devices. Align `SmbOperationsUseCase` accordingly.
5. **Thumbnail generation on network resources** — network HEIC thumbnails should work on API 28+; on API 26–27 show the placeholder.

---

## 3. Current Architecture (Relevant Parts)

| Component | Location | Current HEIC Behavior |
|-----------|----------|-----------------------|
| `MediaExtensions.IMAGE` | `domain/model/MediaExtensions.kt:6` | Includes `heic`, `heif`, `avif` — correct |
| `MediaTypeUtils.IMAGE_EXTENSIONS` | `data/common/MediaTypeUtils.kt:9` | Includes `heic`, `heif`, `avif` — correct |
| `SmbOperationsUseCase.detectMediaType` | `domain/usecase/SmbOperationsUseCase.kt:298-300` | **Throws** `IllegalArgumentException` — bug |
| `GlideAppModule` | `di/GlideAppModule.kt` | No HEIC-specific decoder registered |
| `NetworkFileModelLoader` | `data/network/glide/NetworkFileModelLoader.kt:65` | Skips HEIC from video-frame decoder (correct comment) |
| `ImageLoadingManager` | `ui/player/ImageLoadingManager.kt:1152` | Catches HEIC decode errors but shows generic error |
| `BrowseActivity` | `ui/browse/BrowseActivity.kt:1622` | Same — catches but shows generic error |
| AndroidManifest | `src/main/AndroidManifest.xml:281-282` | MIME types `image/heic`, `image/heif` registered |

---

## 4. Platform Support Matrix

| Android API | HEIC decode | AVIF decode | Mechanism |
|-------------|:-----------:|:-----------:|-----------|
| 26–27 (minSdk) | No | No | `BitmapFactory` has no HEIC/AVIF codec |
| 28–29 | Yes | No | `ImageDecoder` API; Glide uses it via platform path |
| 30 | Yes | No | Same |
| 31+ | Yes | Yes | AVIF codec added |

**Glide 4.16.0 behavior:** On API 28+, Glide's default `StreamBitmapDecoder` calls `BitmapFactory.decodeStream()`. On API 28+ the platform routes this through `ImageDecoder` internally, so HEIC _should_ decode without a custom Glide component — but only for the standard image pipeline (local files and byte streams). Thumbnails for network files go through `NetworkFileModelLoader` → `InputStream` → same `BitmapFactory` path, so the same API-level rule applies.

There is no production-ready, maintained pure-Java/Kotlin HEIC decoder library for Android as of 2026. Native options (LibHeif via JNI) are out of scope for this tier.

---

## 5. Proposed Solution

### 5.1 Fix `SmbOperationsUseCase.detectMediaType` (1h)

Remove the incorrect HEIC/HEIF/AVIF exclusion block. These formats are already covered by the `else -> MediaType.IMAGE` fallback. Adding explicit entries makes the intent clear:

```kotlin
// Before (wrong — throws for valid image formats):
if (extension in setOf("avif", "heif", "heic")) {
    throw IllegalArgumentException("Unsupported file format: $extension")
}

// After:
"jpg", "jpeg", "png", "bmp", "webp" -> MediaType.IMAGE
"heic", "heif" -> MediaType.IMAGE   // API 28+ decodes natively; 26-27 shows placeholder
"avif" -> MediaType.IMAGE            // API 31+ decodes natively; older shows placeholder
"gif" -> MediaType.GIF
// ... rest unchanged
```

### 5.2 Add API-level decode guard in Glide error handling (2h)

Create a utility `HeifSupportUtils.kt` in `core/util/`:

```kotlin
object HeifSupportUtils {
    /** HEIC/HEIF native decode is available from API 28 */
    fun isHeicSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /** AVIF native decode is available from API 31 */
    fun isAvifSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun isSupported(extension: String): Boolean = when (extension.lowercase()) {
        "heic", "heif" -> isHeicSupported()
        "avif" -> isAvifSupported()
        else -> true
    }
}
```

### 5.3 Pre-flight check before Glide load (1h)

In `ImageLoadingManager` (and the browse thumbnail path in `BrowseActivity`), before calling Glide, check `HeifSupportUtils.isSupported(extension)`. If `false`, skip the Glide call and directly show a "format not supported" placeholder drawable + optional Snackbar/Toast message.

This avoids the current path where Glide starts a network download, fails to decode, and the user sees a broken-image icon with no explanation.

Pseudo-flow:
```
loadImage(file):
    if !HeifSupportUtils.isSupported(file.extension):
        showUnsupportedFormatPlaceholder(file.extension)
        return
    // ... existing Glide load
```

**Placeholder behavior:**
- Show the existing `R.drawable.ic_broken_image` (or a new `ic_format_unsupported`) with a subtitle label like "HEIC not supported below Android 9".
- Log with Timber.w (no crash, no Sentry noise).

### 5.4 Update HEIC error-catch blocks (30 min)

The existing catches in `ImageLoadingManager:1152` and `BrowseActivity:1622` that check for `"HEIC"` / `"HEIF"` in error messages should be extended to also call `HeifSupportUtils.isSupported()` to surface a device-specific message rather than a generic "load failed".

### 5.5 Verify `NetworkFileModelLoader` skip logic (30 min)

Confirm that line 65 of `NetworkFileModelLoader` (which skips HEIF from the video decoder) does **not** also skip the image decoder path. HEIC files should still reach `NetworkFileModelLoader → InputStream → BitmapFactory` for the image decode attempt. If a `handles()` filter is blocking HEIC from the image path, remove it.

### 5.6 Manual test matrix (see §7)

---

## 6. Implementation Steps

| # | Task | File(s) | Est. |
|---|------|---------|------|
| 1 | Remove HEIC/AVIF exclusion from `SmbOperationsUseCase.detectMediaType` | `SmbOperationsUseCase.kt:298-300` | 15 min |
| 2 | Create `HeifSupportUtils.kt` | `core/util/HeifSupportUtils.kt` (new) | 30 min |
| 3 | Add pre-flight check in `ImageLoadingManager.loadImage` | `ImageLoadingManager.kt` | 45 min |
| 4 | Add pre-flight check in browse thumbnail path | `BrowseActivity.kt` or browse adapter | 45 min |
| 5 | Update existing HEIC error-catch blocks to use specific message | `ImageLoadingManager.kt:1152`, `BrowseActivity.kt:1622` | 20 min |
| 6 | Verify `NetworkFileModelLoader.handles()` doesn't block HEIC image path | `NetworkFileModelLoader.kt` | 20 min |
| 7 | Manual test on API 26 emulator + API 28+ device (see §7) | — | 2–3h |
| 8 | Update `CHANGELOG.md` | `dev/CHANGELOG.md` | 5 min |

**Total estimate: 5–6h** (plus 2–3h for emulator/device testing)

---

## 7. Testing Matrix

Test the following scenarios manually. Use an Android 8.1 emulator (API 27) and a physical device running API 28+.

| Scenario | Source | API 26–27 expected | API 28+ expected |
|----------|--------|--------------------|-----------------|
| Browse: HEIC thumbnail | Local storage | Placeholder + "not supported" label | Thumbnail rendered |
| Browse: HEIC thumbnail | SMB share | Placeholder + "not supported" label | Thumbnail rendered |
| Player: open HEIC full-size | Local storage | Placeholder + Snackbar message | Image displayed |
| Player: open HEIC full-size | SMB share | Placeholder + Snackbar message | Image displayed |
| "Open with" intent: HEIC from Files app | — | Placeholder + message | Image displayed |
| Browse: AVIF thumbnail | Local storage | Placeholder + "not supported" | Thumbnail on API 31+; placeholder on 28–30 |
| Slideshow: HEIC files in folder | SMB share | Files skipped with notification | Files played |
| File info dialog: HEIC file | Any | Shows "HEIF" label correctly | Shows "HEIF" label correctly |
| No crash on HEIC when API < 28 | Any | No ANR, no FC | N/A |

---

## 8. Non-Goals

- LibHeif / native JNI HEIC decoder for API 26–27 (out of scope for Tier 3; no maintained library exists)
- HEIC capture / writing (`androidx.heifwriter`) — the app is a viewer/sorter, not a camera
- AVIF full support on API 28–30 — same constraint as HEIC on 26–27
- Transcoding HEIC → JPEG on the fly
- Slideshow auto-skip logic changes (this spec only adds a placeholder; slideshow skip is a separate concern)

---

## 9. Acceptance Criteria

- [x] `SmbOperationsUseCase.detectMediaType` no longer throws for `heic`, `heif`, `avif`
- [x] On API 28+: HEIC images from local storage and SMB/SFTP/FTP display correctly in both Browse and Player
- [x] On API 26–27: HEIC images show a clear "format not supported on this device" placeholder instead of a broken-image icon or spinner
- [x] No unhandled exceptions or ANRs triggered by HEIC files on any supported API level
- [x] AVIF files follow the same placeholder/supported logic as HEIC (supported API 31+)
- [x] `HeifSupportUtils` has unit tests covering the three format/API-level combinations
- [x] `CHANGELOG.md` updated

# Phase 02 — Intent extra `EXTRA_DETECTED_STEREO_MODE`

**Ticket:** S0026 / F02
**Goal:** carry the detected stereo mode from `BrowseEventHandler` into the player intent. When VR is the target, `VrPlayerActivity` reads the extra and primes `PlayerStereoModeCoordinator` with the correct mode **before** the route decision runs.

**Files touched:**
1. `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` — declare key + add to `createIntent` signature.
2. `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt` — fill the extra when launching VR.

---

## Step 1 — Declare the extra key

In `PlayerActivity.kt`, find the `companion object` block (search for `fun createIntent(`, the companion is just above ~line 879).

Add a `const val` near other extras:

```kotlin
        // S0026: detected stereo mode hint. Browse fills this when launching VR; VrPlayerActivity
        // primes PlayerStereoModeCoordinator with this value before applying user-settings, so the
        // route decision sees the actual file format instead of the default MONO.
        const val EXTRA_DETECTED_STEREO_MODE = "extra_detected_stereo_mode"
```

The value is the `StereoMode.name` string (e.g., `"VR180_FISHEYE_SBS"`, `"MONO"`, `"AUTO"`). Decoded via `StereoMode.valueOf(name)` on the consumer side, with safe fallback.

### Verification

- `Grep -n EXTRA_DETECTED_STEREO_MODE app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` returns the new const.

## Step 2 — Add parameter to `createIntent`

Inside the same companion object, extend `fun createIntent(...)` to accept `detectedStereoMode: StereoMode? = null` and emit the extra when non-null.

Patch the signature **and** body. Around line 879:

```kotlin
        fun createIntent(
            context: Context,
            resourceId: Long,
            initialIndex: Int = 0,
            skipAvailabilityCheck: Boolean = false,
            initialFilePath: String? = null,
            isPlaying: Boolean? = null,
            isSlideshowEnabled: Boolean = false,
            shuffleOnStart: Boolean = false,
            detectedStereoMode: StereoMode? = null,  // S0026
        ): Intent {
```

Inside `apply { ... }` after existing `putExtra` calls, add:

```kotlin
                detectedStereoMode?.let {
                    putExtra(EXTRA_DETECTED_STEREO_MODE, it.name)
                }
```

Required new import (top of file, group with other domain imports):

```kotlin
import com.sza.fastmediasorter.domain.model.StereoMode
```

If `StereoMode` is already imported, skip the import line.

### Verification

- `Grep -n "detectedStereoMode" app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` returns the new parameter and the `putExtra` line.
- Standard + VR debug builds both PASS (no other call sites are affected because the new parameter is optional).

## Step 3 — Browse fills the extra when launching VR

In `BrowseEventHandler.kt` `handleEvent` → `BrowseEvent.NavigateToPlayer` branch (around lines 73-83), the existing call:

```kotlin
                    val playerIntent = if (file != null && shouldLaunchStandardPlayer(file)) {
                        createStandardPlayerIntent(resourceId, event.fileIndex, event.filePath)
                    } else {
                        PlayerActivity.createIntent(
                            activity,
                            resourceId,
                            event.fileIndex,
                            skipAvailabilityCheck,
                            event.filePath
                        )
                    }
```

Replace the `else` arm to forward the detected mode. Compute the detected mode using the same stereo detector that's already a field of `BrowseEventHandler` (`private val stereoDetector = StereoDetector()`).

```kotlin
                    val playerIntent = if (file != null && shouldLaunchStandardPlayer(file)) {
                        createStandardPlayerIntent(resourceId, event.fileIndex, event.filePath)
                    } else {
                        // S0026: pass detected stereo mode so VrPlayerActivity can prime the
                        // coordinator before settings apply (otherwise inner route decision sees MONO).
                        val detectedForVr = file?.let { detectStereoForLaunch(it) }
                        PlayerActivity.createIntent(
                            activity,
                            resourceId,
                            event.fileIndex,
                            skipAvailabilityCheck,
                            event.filePath,
                            detectedStereoMode = detectedForVr,
                        )
                    }
```

Add a private helper at the bottom of the class (just before `private fun showAddedAsDestinationSnackbar`):

```kotlin
    /**
     * S0026: same detection priority as [shouldLaunchStandardPlayer], but returns the actual
     * StereoMode for forwarding through intent-extras (not a boolean). Filename match wins over
     * dimension match; UNKNOWN if nothing matches.
     */
    private fun detectStereoForLaunch(file: MediaFile): StereoMode {
        if (file.type != MediaType.VIDEO) return StereoMode.UNKNOWN
        val byFilename = stereoDetector.detectFromFilename(file.path)
        if (byFilename != StereoMode.UNKNOWN) return byFilename
        if (file.width != null && file.height != null) {
            return stereoDetector.detectFromDimensions(file.width, file.height)
        }
        return StereoMode.UNKNOWN
    }
```

`StereoMode` is already imported (line 15); `MediaType` already too (line 14); `MediaFile` already too (line 13). No new imports needed.

### Verification

- `Grep -n "detectStereoForLaunch" app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt` returns both the call site and the helper.
- Standard debug build PASS.
- VR debug build PASS.

---

## Acceptance for F02

- Both files modified per steps above.
- Standard + VR debug builds pass.
- Existing `PlayerActivity.createIntent` callers (standard player path) compile unchanged because the new parameter has a `null` default.
- The extra is filled with `MONO` semantics when filename does not match (`detectStereoForLaunch` returns `UNKNOWN`); coordinator on the consumer side handles `UNKNOWN` as "no hint, fall back to existing logic" (see F03).
- Dev changelog: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt;app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt" "S0026/F02" "Browse forwards detected stereo mode to VrPlayerActivity via intent-extra (EXTRA_DETECTED_STEREO_MODE)"`.

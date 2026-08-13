# Phase 01 — Browse honors `vrAutoImmersive` (extracted helper)

**Ticket:** S0026 / F01
**Goal:** when the user has disabled "auto-enter immersive on stereo content" (`settings.vrAutoImmersive == false`), `BrowseEventHandler` must launch the standard `PlayerActivity` even for stereo/spherical files. No `VrPlayerActivity` flicker. The decision moves into a pure helper class so it can be unit-tested in isolation.

**Files touched:**

1. **NEW** `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRoutingDecision.kt`
2. `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt`

---

## Step 1 — Create `BrowseRoutingDecision` helper

Create new file `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRoutingDecision.kt`:

```kotlin
package com.sza.fastmediasorter.ui.browse.managers

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode

/**
 * S0026: pure routing decision for BrowseEventHandler.
 *
 * Mirrors the subset of [com.sza.fastmediasorter.vr.helpers.VrRouteDecisionHelper] rules that
 * Browse can honor BEFORE launching VrPlayerActivity, so stereo files with auto-immersive
 * disabled never go through the "VR window flicker" path. The inner helper remains the
 * source of truth once VR has been entered (e.g. via deep-link or 3DVR button).
 */
internal object BrowseRoutingDecision {

    enum class Route { STANDARD_PLAYER, VR_PLAYER }

    /**
     * Decide whether the file should open in the standard player or the VR player.
     *
     * @param file media file (only path/type/dimensions are read).
     * @param effectiveStereoMode stereo mode after Browse-side detection + user-format overrides.
     * @param settings current AppSettings; only [AppSettings.disable3dVr] and [AppSettings.vrAutoImmersive] are used.
     */
    fun decide(
        file: MediaFile,
        effectiveStereoMode: StereoMode,
        settings: AppSettings,
    ): Route {
        // Non-video media (images, audio, docs) never enter VrPlayerActivity from Browse —
        // VR flavor lacks an explicit "enter immersive" step for them today.
        if (file.type != MediaType.VIDEO) return Route.STANDARD_PLAYER

        if (settings.disable3dVr) return Route.STANDARD_PLAYER

        val isImmersiveContent =
            effectiveStereoMode.isStereoscopic() || effectiveStereoMode.isSpherical()
        if (!isImmersiveContent) return Route.STANDARD_PLAYER

        // Mirror of VrRouteDecisionHelper "auto-immersive-disabled" branch: stereo content
        // stays on the standard panel when the user disabled the toggle.
        if (!settings.vrAutoImmersive) return Route.STANDARD_PLAYER

        return Route.VR_PLAYER
    }
}
```

### Verification

- `Read` the new file; the function is `internal object` so non-VR-flavor module sources can use it freely.
- Build phase: `pwsh -Command ".\gradlew.bat :app_v2:assembleStandardDebug"` PASS.

## Step 2 — Use the helper from `BrowseEventHandler`

In `BrowseEventHandler.kt` `shouldLaunchStandardPlayer` (lines 177-235), replace the final `shouldUseStandard` block.

**Before** (lines 224-234):

```kotlin
        val shouldUseStandard = !effectiveMode.isStereoscopic() && !effectiveMode.isSpherical()
        Timber.i(
            "BrowseEventHandler: route file=%s type=%s detected=%s effective=%s autoDetect=%b -> standard=%b",
            file.path,
            file.type,
            detectedMode,
            effectiveMode,
            settings.vrAutoDetectFormat,
            shouldUseStandard,
        )
        return shouldUseStandard
```

**After**:

```kotlin
        val route = BrowseRoutingDecision.decide(file, effectiveMode, settings)
        val shouldUseStandard = route == BrowseRoutingDecision.Route.STANDARD_PLAYER
        Timber.i(
            "BrowseEventHandler: route file=%s type=%s detected=%s effective=%s autoDetect=%b autoImmersive=%b -> standard=%b",
            file.path,
            file.type,
            detectedMode,
            effectiveMode,
            settings.vrAutoDetectFormat,
            settings.vrAutoImmersive,
            shouldUseStandard,
        )
        return shouldUseStandard
```

`BrowseRoutingDecision` is in the same package — no import needed.

The earlier guards (lines 178-200: non-video, disable3dVr) are now duplicated by the helper. Keep them in `BrowseEventHandler` for early-return + their existing log lines (they have separate log markers used in audit). The helper's own internal guards are just defensive — single-source-of-truth is achieved at the **stereo-route** decision.

### Verification (Step 2)

- `Grep -n "BrowseRoutingDecision" app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt` returns the call site.
- The log line includes `autoImmersive=%b` field.
- `Grep -n "shouldUseStandard = !effectiveMode" app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt` returns 0 hits (old expression removed).
- Build: `:app_v2:assembleStandardDebug` PASS, `:app_v2:assembleVrDebug` PASS.

---

## Acceptance for F01

- New file `BrowseRoutingDecision.kt` exists.
- `BrowseEventHandler.shouldLaunchStandardPlayer` calls `BrowseRoutingDecision.decide`.
- Standard + VR debug builds PASS.
- Dev changelog: `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseRoutingDecision.kt;app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseEventHandler.kt" "S0026/F01" "Extract BrowseRoutingDecision; honor vrAutoImmersive setting (no VR flicker on stereo files when toggle is off)"`.

# Phase 03 — `resolveLaunchStereoMode` consumes the intent-extra

**Ticket:** S0026 / F03
**Goal:** when an intent-extra `EXTRA_DETECTED_STEREO_MODE` is present and not `MONO`/`UNKNOWN`, `VrPlayerActivity.resolveLaunchStereoMode` honors it as the seed mode. This closes B2: the coordinator's MONO default no longer short-circuits the route decision into `plain-2d-video`.

**File touched:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`

---

## Step 1 — Read the extra into a field

In `VrPlayerActivity.kt`, find the existing intent-extra reads (around `onCreate ENTRY` log and intent parsing). Add a private property and parse it once on `onCreate`. Use a stable companion-private constant.

Locate the imports and add (if missing):

```kotlin
import com.sza.fastmediasorter.ui.player.PlayerActivity
```

If already present, skip.

Add a property at the class-property block (around line 100-120, near other private properties):

```kotlin
    // S0026: stereo mode hint from BrowseEventHandler. When present and resolvable, it primes
    // resolveLaunchStereoMode so the inner route decision sees the actual file format.
    // null when the activity was launched without the hint (deep-link, share intent, etc.).
    private var initialDetectedStereoModeHint: StereoMode? = null
```

Inside `onCreate`, near where other intent extras are read (search `intent.getStringExtra("initialFilePath")` or similar marker), add:

```kotlin
        initialDetectedStereoModeHint = intent.getStringExtra(PlayerActivity.EXTRA_DETECTED_STEREO_MODE)
            ?.let { name ->
                runCatching { StereoMode.valueOf(name) }.getOrNull()
            }
        Timber.i(
            "VrPlayerActivity: initial detected stereo hint = %s",
            initialDetectedStereoModeHint,
        )
```

`StereoMode` is already imported at the top of `VrPlayerActivity.kt` (used at line 8 `import com.sza.fastmediasorter.domain.model.StereoMode`).

If the exact intent-parsing block is hard to locate, place the read as the **first line** inside `onCreate` after `super.onCreate(savedInstanceState)` and before any coordinator/viewModel initialisation — the extra is read-only and lifecycle-safe.

### Verification

- `Grep -n "EXTRA_DETECTED_STEREO_MODE" app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` returns the read site.
- `Grep -n "initialDetectedStereoModeHint" app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` returns the property declaration AND the read site (2 hits min).
- VR debug build PASS.

## Step 2 — Honor the hint inside `resolveLaunchStereoMode`

Patch `resolveLaunchStereoMode` (lines 1529-1560). The current early-return drops the hint:

```kotlin
        if (requestedStereoMode != StereoMode.AUTO && requestedStereoMode != StereoMode.UNKNOWN) {
            return requestedStereoMode
        }
```

Replace the early-return block with:

```kotlin
        // S0026: when Browse provided a detected mode hint, honor it over the coordinator's
        // current value if the coordinator is still at the MONO/UNKNOWN default. The hint
        // is dropped only after the file's first proper apply-settings cycle in the coordinator.
        val hintToUse = initialDetectedStereoModeHint
        initialDetectedStereoModeHint = null  // single-use; subsequent files re-detect normally

        if (hintToUse != null && hintToUse != StereoMode.UNKNOWN && hintToUse != StereoMode.MONO &&
            (requestedStereoMode == StereoMode.MONO ||
                requestedStereoMode == StereoMode.AUTO ||
                requestedStereoMode == StereoMode.UNKNOWN)
        ) {
            Timber.i(
                "VrPlayerActivity: resolveLaunchStereoMode using browse hint = %s (was requested=%s)",
                hintToUse,
                requestedStereoMode,
            )
            return hintToUse
        }

        if (requestedStereoMode != StereoMode.AUTO && requestedStereoMode != StereoMode.UNKNOWN) {
            return requestedStereoMode
        }
```

Rationale (inline, kept terse):

- Single-use: the hint is consumed on the **first** route resolution. Subsequent file changes within the same `VrPlayerActivity` instance re-detect normally via the existing `stereoDetector.detectFromFilename` block below.
- The hint only applies when the coordinator's current value is the default (`MONO` / `AUTO` / `UNKNOWN`). If the user has explicitly forced a non-default mode via the panel dialog before XR-init completes, the explicit user choice still wins.
- `MONO` and `UNKNOWN` hints are ignored — they don't add information.

### Verification

- `Grep -n "S0026: when Browse provided" app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` finds the new block exactly once.
- Run unit-tests: `pwsh -Command ".\gradlew.bat :app_v2:testVrDebugUnitTest --tests *VrRouteDecisionHelperTest"` — existing tests must still PASS (helper unchanged).
- VR debug build PASS.

---

## Acceptance for F03

- The intent-extra read site exists in `onCreate`.
- The early-return in `resolveLaunchStereoMode` is replaced with the hint-aware block.
- `:app_v2:assembleVrDebug` PASS.
- `:app_v2:testVrDebugUnitTest` PASS (Phase 04 will add the new tests; existing tests unaffected).
- Dev changelog: `.\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" "S0026/F03" "VrPlayerActivity consumes EXTRA_DETECTED_STEREO_MODE to prime route decision"`.

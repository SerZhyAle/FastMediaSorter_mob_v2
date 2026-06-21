package com.sza.fastmediasorter.ui.settings.search

import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.screencapture.MenuScreenshotLauncher
import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.ui.settings.SettingsSearchIndex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-row capability filter for the settings-search index. Some rows live in an always-available
 * section (so [SettingsSearchAvailability], which gates only media sections, cannot drop them) yet
 * are hidden at runtime when an optional capability is absent - the owning fragment sets their
 * parent card or the row itself GONE. Indexing them anyway yields dead search results on flavors
 * lacking the capability: search returns the row, tapping it lands on a screen where it is GONE.
 *
 * Two complementary checks, each mirroring the matching runtime gate so search visibility cannot
 * drift from UI visibility:
 *  - Container membership (S0599): a row is suppressed when its ancestor chain contains a gating
 *    container whose multibound DI set is empty. No `BuildConfig` is read; the empty/non-empty set
 *    is the only signal. Self-maintaining - a row added later inside a gated card is covered too.
 *  - Per-row capability (S0600): a row in an always-available section that its fragment hides
 *    individually when a typed/compiled capability is absent. Keyed by the row's search key
 *    (= android:id resource-entry name), read through the same src/main-safe sources of truth
 *    ([MediaCapabilities], [CapabilityAvailability]) the fragments use.
 *
 * Default-player capability keys stay in `SettingsSearchRegistry.isCapabilityAvailable()` where
 * S0602 placed them; the camera-OCR device axis (`DeviceCapabilities.isOcrSupported`) is the
 * device-feature axis owned by `SettingsSearchDeviceFeatureGate` (S0601). This gate covers only
 * the compile/flavor axis of the camera-OCR rows.
 */
@Singleton
class SettingsSearchCapabilityGate @Inject constructor(
    private val screenGestureControllers: Set<@JvmSuppressWildcards ScreenGestureOverlayController>,
    private val menuScreenshotLaunchers: Set<@JvmSuppressWildcards MenuScreenshotLauncher>,
    private val mediaCapabilities: MediaCapabilities,
    private val capabilityAvailability: CapabilityAvailability
) {

    // Gating-container view-id -> "is the capability present". Mirrors the runtime gates:
    //  - OperationsGesturesManager.setup: groupScreenGestures GONE when the controller set is empty.
    //  - OperationsCaptureManager.setupScreenshotAction: groupMenuScreenshot GONE when launchers empty.
    private val capabilityByContainer: Map<Int, () -> Boolean> = mapOf(
        R.id.groupScreenGestures to { screenGestureControllers.isNotEmpty() },
        R.id.groupMenuScreenshot to { menuScreenshotLaunchers.isNotEmpty() }
    )

    /** False when a gated container is absent OR the row's own capability is absent. */
    fun isAvailable(entry: SettingsSearchIndex): Boolean =
        isContainerCapabilityPresent(entry.ancestorIds) && isKeyCapabilityAvailable(entry.key)

    private fun isContainerCapabilityPresent(ancestorIds: List<Int>): Boolean =
        capabilityByContainer.none { (containerId, capabilityPresent) ->
            containerId in ancestorIds && !capabilityPresent()
        }

    // Each branch mirrors the runtime predicate hiding that row in its fragment (see S0600 §2).
    private fun isKeyCapabilityAvailable(key: String): Boolean = when (key) {
        // Mic recording - OperationsCaptureManager.
        "rowMicRecordingEnabled",
        "rowMicRecordingAskFilename",
        "btnSelectMicRecordingDest" -> mediaCapabilities.supportsMicRecording
        // Background audio - PlaybackSettingsFragment (whole card hidden, header included).
        "rowEnablePersistentAudioPlayback",
        "rowShowNowPlayingPanel",
        "headerBackgroundAudio" -> capabilityAvailability.isPersistentAudioPlaybackAvailable()
        // Cloud source - GeneralSettingsViewSetupHelper (isCloudGroupSupported aliases supportsCloud).
        "rowSourceCloud" -> mediaCapabilities.supportsCloud
        // OCR/translation - OtherMediaSettingsFragment + OperationsSettingsFragment, compile axis.
        // The OCR spinners and TextView language pickers (S0603) share this gate: their parent
        // layouts are GONE on flavors without the translation capability.
        "rowEnableTranslation",
        "rowEnableOcr",
        "rowTranslationLensStyle",
        "rowCameraOcrTranslationEnabled",
        "rowCameraOcrOnly",
        "spinnerTranslationSourceLanguage",
        "spinnerTranslationTargetLanguage",
        "spinnerOcrEngineType",
        "spinnerPaddleOcrModel",
        "spinnerOcrFontSize",
        "spinnerOcrFontFamily" -> capabilityAvailability.isTranslationAvailable()
        // Downloadable extensions - GeneralSettingsFragment.
        "btnDownloadableExtensions" -> capabilityAvailability.isExtensionsScreenAvailable()
        else -> true
    }
}

package com.sza.fastmediasorter.ui.settings.search

import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.screencapture.MenuScreenshotLauncher
import com.sza.fastmediasorter.core.screencapture.ScreenGestureOverlayController
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
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
    private val capabilityAvailability: CapabilityAvailability,
    private val launcherModeContract: LauncherModeContract
) {

    // Gating-container view-id -> "is the capability present". Mirrors the runtime gate:
    //  - OperationsGesturesManager.setup: groupScreenGestures GONE when the controller set is empty.
    // S1035: the card now holds only the master toggle + the "Configure gestures" launcher
    // (btnOpenEdgeGestureConfig); the per-zone/direction detail rows moved to
    // EdgeGestureConfigDialogFragment, whose layout is not in the search catalog, so search now
    // surfaces the entry point only (owner §6.6). Both are covered by this container gate.
    // S1052: the S0559 screenshot-test button relocated out of this card into the General-tab debug
    // section; it is gated by its own per-row branch below, not this container.
    private val capabilityByContainer: Map<Int, () -> Boolean> = mapOf(
        R.id.groupScreenGestures to { screenGestureControllers.isNotEmpty() }
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
        "rowOcrFontSize",
        "rowOcrFontFamily" -> capabilityAvailability.isTranslationAvailable()
        // Downloadable extensions - GeneralSettingsFragment.
        "btnDownloadableExtensions" -> capabilityAvailability.isExtensionsScreenAvailable()
        // S1051: btnOpenAccessibilitySettings now lives in the always-available System-apps group
        // (relocated out of the edge-gesture dialog, S1035). The owning fragment hides it unless the
        // silent-capture capability is present, so mirror that per-row here to avoid a dead search hit.
        "btnOpenAccessibilitySettings" -> screenGestureControllers.firstOrNull()?.isFallbackCaptureAvailable() == true
        // S1052: menu-screenshot test relocated into the General-tab debug section (out of the gesture
        // card, so it loses that container gate). Build-type gate (BuildConfig.DEBUG) + flavor axis
        // (launcher bound = standard + noLegal); mirrors GeneralSettingsFragment.setupScreenshotTestButton
        // so search never surfaces it in a release build.
        "btnTakeScreenshotNow" -> BuildConfig.DEBUG && menuScreenshotLaunchers.isNotEmpty()
        // S1088: the launcher enable toggle + the dialog-entry row moved into the always-available General
        // tab; both are GONE when the launcher capability is absent, so mirror that to avoid a dead search
        // hit. The former composition/density rows now live in LauncherSettingsDialogFragment, whose layout
        // is not in the search catalog (SettingsSearchLayoutCatalog), so they no longer need a branch here.
        "rowLauncherModeEnabled",
        "rowLauncherSettings" -> launcherModeContract.isAvailableInBuild
        // S1170: "add a widget to the launcher desktop" is GONE without the launcher surface, and the
        // owning fragment additionally hides it until launcher mode is actually on. Only the build axis
        // is mirrored here - the runtime one is a live user toggle, and this gate answers per build.
        "buttonAddLauncherWidget" -> launcherModeContract.isAvailableInBuild
        else -> true
    }
}

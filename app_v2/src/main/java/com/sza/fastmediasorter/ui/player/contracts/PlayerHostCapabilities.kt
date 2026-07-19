package com.sza.fastmediasorter.ui.player.contracts

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import kotlinx.coroutines.flow.StateFlow

/**
 * The single seam between [PlayerActivity] (portrait/landscape) and [StandalonePlayerActivity].
 *
 * Why: spec_standalone-vs-inapp-player-parity §5.2 collapses the three player surfaces onto
 * one shared pipeline. The shared dialog, ViewModel and coordinators consume only this
 * capability-contract - never branch on "is this standalone?". Capability flags are the only
 * allowed fork point.
 *
 * How to apply: both activities implement this interface directly. Standalone reports every
 * list-aware flag as false; per-file flags stay true on both surfaces.
 */
interface PlayerHostCapabilities {

    // ── List / session capability flags ──────────────────────────────────────

    /** False for standalone: there is no list to navigate. */
    val supportsListNavigation: Boolean

    /** False for standalone: slideshow auto-advance needs a list. */
    val supportsSlideshow: Boolean

    /** False for standalone (ADR-2): external intents are short-lived, no foreground audio service. */
    val supportsPersistentAudio: Boolean

    /** False for standalone until X.2 (Cast from external intents is out of scope). */
    val supportsCast: Boolean

    /** True for both surfaces: destructive actions always get an undo window. */
    val supportsDeleteUndo: Boolean

    /** False for standalone: the command panel only carries list context. */
    val supportsCommandPanelFolding: Boolean

    /**
     * True when the host can page through neighbor files of the current file's folder.
     * Distinct from [supportsListNavigation], which reflects a resource-backed playlist:
     * standalone has no resource list but can still enumerate the opened file's folder.
     * Default false keeps standalone single-file until folder paging is wired.
     */
    val supportsFolderPaging: Boolean get() = false

    /**
     * True when the host may render single-file type-specific command-panel actions
     * (rotate, crop, compress, print, OCR, translate, lens, lyrics, cast..). Read by the
     * availability layer instead of branching on host identity; both surfaces support them
     * for a single file, so default true. Per-type/flavor gating stays in the availability layer.
     */
    val supportsTypeSpecificActions: Boolean get() = true

    // ── Shared per-file state ─────────────────────────────────────────────────

    val currentMediaFile: StateFlow<MediaFile?>
    val currentMediaType: StateFlow<MediaType?>

    // ── Stereo mode ───────────────────────────────────────────────────────────

    /** Effective stereo mode driving rendering (auto-detected + user override + forced format). */
    val stereoMode: StateFlow<StereoMode>

    /** Last stereo mode reported by the auto-detector; used to seed dialog default. */
    val detectedStereoMode: StateFlow<StereoMode>

    /**
     * True when the user's 3D/VR master toggle is enabled (Settings -> Media -> 3D/VR, backed by
     * [com.sza.fastmediasorter.domain.model.AppSettings.disable3dVr]). The playback control dialog
     * hides the "3D" stereo section when this is false even on VR builds: manual stereo override is
     * meaningless while 3D/VR classification is globally disabled (all content plays as 2D).
     * Default true so a host that does not gate on the master toggle keeps the prior behavior; the
     * section is still ultimately gated by the build's VR-media-controls capability. (S0763)
     */
    val is3dVrEnabled: Boolean get() = true

    /** Defaults to a seekable local file for hosts that do not play streams. */
    val activeSourceIsStream: Boolean get() = false

    /** Defaults to a seekable local file for hosts that do not play streams. */
    val activeSourceIsLive: Boolean get() = false

    /** Defaults to a seekable local file whose colour controls are supported. */
    val supportsColorAdjustmentForActiveSource: Boolean get() = true

    /** Set the user-selected stereo mode for the current file. */
    fun setStereoMode(mode: StereoMode)

    /** Persist the chosen mode for the current file path if the remember-format setting is on. */
    fun rememberStereoModeForCurrentFile(mode: StereoMode)

    // ── Video player operations ───────────────────────────────────────────────

    /**
     * Wraps all video/colour/track operations the dialog needs.
     * Null when no video player is active (e.g. image or document open).
     */
    val videoPlayerHandle: VideoPlayerHandle?

    // ── Audio service ─────────────────────────────────────────────────────────

    /**
     * True when audio is playing via the background [AudioPlaybackService], not ExoPlayer directly.
     * Always false for standalone (no audio service; see ADR-2).
     */
    val isAudioServiceActive: Boolean

    // ── Host callbacks ────────────────────────────────────────────────────────

    /** Show a short feedback message (Toast / Snackbar) to the user. */
    fun showMessage(message: String)

    /**
     * Called by the shared coordinators after a successful delete.
     * In-app: advance to the next file. Standalone: finish() the activity.
     */
    fun requestFinishAfterDelete()
}

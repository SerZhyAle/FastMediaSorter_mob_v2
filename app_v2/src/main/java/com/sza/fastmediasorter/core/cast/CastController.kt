package com.sza.fastmediasorter.core.cast

import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.domain.model.MediaFile
import kotlinx.coroutines.flow.StateFlow

/**
 * Flavor-agnostic seam over Chromecast media output (strategic S0403).
 *
 * `src/main` depends only on this interface - never on the Google Cast SDK. The real
 * GMS-backed implementation lives in the `castEnabled` source set; flavors with no Google Cast SDK
 * on their classpath (e.g. the FOSS flavor) mount a no-op from `castDisabled`. AGP mounts exactly
 * one binding per flavor, mirroring the `XrDetectionFacade`/`NoOpXrDetectionFacade` seam.
 *
 * Surface is derived from the existing call sites (player overflow "Cast to Chromecast" path).
 */
interface CastController {

    /**
     * True when the Cast SDK is available on this device (Google Play Services present and
     * initialized). Drives command-panel Cast-button visibility.
     */
    val isCastAvailable: Boolean

    /** True while a Cast session is active. */
    val isCasting: Boolean

    /** Emits whether Cast is currently available; observed to refresh command-panel availability. */
    val castAvailableState: StateFlow<Boolean>

    /** Initialize the controller once Play Services / permissions are ready. Safe to call repeatedly. */
    fun init()

    /** Tear down: unregister listeners, stop the proxy, cancel in-flight downloads. */
    fun release()

    /** Open the Cast device picker. */
    fun showCastDialog(activity: FragmentActivity)

    /** Cast [file] to the active session; a no-op when not currently casting. */
    fun sendCurrentMedia(file: MediaFile)
}

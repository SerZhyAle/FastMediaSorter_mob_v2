package com.sza.fastmediasorter.core.cast

import com.sza.fastmediasorter.ui.player.helpers.NetworkFileManager
import kotlinx.coroutines.CoroutineScope

/**
 * Builds a [CastController] from the runtime arguments the player provides per Activity
 * (strategic S0403). Singleton-graph collaborators (application context, media capabilities) are
 * injected into the per-flavor factory implementation; the values that are Activity-scoped and
 * therefore cannot come from the Hilt graph - the lifecycle scope, the per-Activity
 * [NetworkFileManager], and the casting-state callback - are passed here.
 *
 * The `castEnabled` source set binds a factory that constructs the GMS-backed controller; the
 * `castDisabled` source set binds one that returns the inert no-op.
 */
interface CastControllerFactory {

    /**
     * @param lifecycleScope the host Activity's lifecycle-bound scope (for download coroutines).
     * @param networkFileManager the Activity's network/cloud file fetcher (manually built per
     *   Activity, not Hilt-injectable) used to materialize remote files before casting.
     * @param onCastStateChanged invoked on session start/stop with the casting flag and the target
     *   device name (null when not casting). Drives the ViewModel cast state + "Casting to .." UI.
     */
    fun create(
        lifecycleScope: CoroutineScope,
        networkFileManager: NetworkFileManager,
        onCastStateChanged: (isCasting: Boolean, deviceName: String?) -> Unit
    ): CastController
}

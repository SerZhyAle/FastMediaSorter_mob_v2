package com.sza.fastmediasorter.core.cast

import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.domain.model.MediaFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Inert [CastController] for flavors built without the Google Cast SDK (strategic S0403).
 *
 * Mounted via the `castDisabled` source set. Cast is never available, never casting; every method
 * is a no-op. Cast UI entry points stay hidden because [isCastAvailable] / [castAvailableState]
 * report false (the command panel gates the Cast button on those).
 */
class NoOpCastController : CastController {

    override val isCastAvailable: Boolean = false

    override val isCasting: Boolean = false

    override val castAvailableState: StateFlow<Boolean> = MutableStateFlow(false)

    override fun init() = Unit

    override fun release() = Unit

    override fun showCastDialog(activity: FragmentActivity) = Unit

    override fun sendCurrentMedia(file: MediaFile, stereoCrop: CastStereoCrop?) = Unit
}

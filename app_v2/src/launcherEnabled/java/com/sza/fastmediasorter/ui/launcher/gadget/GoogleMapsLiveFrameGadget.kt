package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.repository.MapResult
import com.sza.fastmediasorter.domain.usecase.map.GetLauncherMapUseCase
import com.sza.fastmediasorter.ui.launcher.widget.GoogleMapsLiveFrameView
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import timber.log.Timber
import javax.inject.Inject

/**
 * S2241: Interactive Google Maps live frame desktop gadget.
 *
 * Hosts [GoogleMapsLiveFrameView] inside a [LauncherGadgetView] container to manage its
 * web view lifecycle (pause on detach/stop, resume on attach/start) and touch interaction.
 */
class GoogleMapsLiveFrameGadget @Inject constructor(
    private val getMap: Lazy<GetLauncherMapUseCase>,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_GOOGLE_MAPS_LIVE
    override val defaultSpanW: Int = SPAN_DEFAULT
    override val defaultSpanH: Int = SPAN_DEFAULT
    override val minSpanW: Int = SPAN_MIN
    override val minSpanH: Int = SPAN_MIN
    override val labelRes: Int = R.string.launcher_gadget_google_maps_live
    override val iconRes: Int = R.drawable.ic_map
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        GoogleMapsLiveFrameGadgetView(container.context, getMap.get())

    private companion object {
        private const val SPAN_DEFAULT = 2
        private const val SPAN_MIN = 1
    }
}

private class GoogleMapsLiveFrameGadgetView(
    context: Context,
    private val getMap: GetLauncherMapUseCase,
) : LauncherGadgetView(context) {

    private val liveFrameView = GoogleMapsLiveFrameView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    init {
        addView(liveFrameView)
    }

    override suspend fun CoroutineScope.onActive() {
        liveFrameView.onResume()

        runCatching {
            val result = getMap()
            val snapshot = when (result) {
                is MapResult.Fresh -> result.snapshot
                is MapResult.Stale -> result.snapshot
                is MapResult.PermissionMissing -> result.snapshot
                MapResult.Unavailable -> null
            }
            snapshot?.let {
                liveFrameView.updateLocation(it.point.latitude, it.point.longitude)
            }
        }.onFailure { Timber.w(it, "Failed to retrieve map location for live frame") }

        try {
            awaitCancellation()
        } finally {
            liveFrameView.onPause()
        }
    }
}

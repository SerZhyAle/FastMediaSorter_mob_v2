package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.launcher.widget.GoogleKeepLiveFrameView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import javax.inject.Inject

/**
 * S2285: Interactive Google Keep live frame desktop gadget.
 *
 * Hosts [GoogleKeepLiveFrameView] inside a [LauncherGadgetView] container to manage its
 * web view lifecycle (pause on detach/stop, resume on attach/start) and touch interaction.
 */
class GoogleKeepLiveFrameGadget @Inject constructor() : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_GOOGLE_KEEP_LIVE
    override val defaultSpanW: Int = SPAN_DEFAULT
    override val defaultSpanH: Int = SPAN_DEFAULT
    override val minSpanW: Int = SPAN_MIN
    override val minSpanH: Int = SPAN_MIN
    override val labelRes: Int = R.string.launcher_gadget_google_keep_live
    override val iconRes: Int = R.drawable.ic_create_text_file
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        GoogleKeepLiveFrameGadgetView(container.context)

    private companion object {
        private const val SPAN_DEFAULT = 2
        private const val SPAN_MIN = 1
    }
}

private class GoogleKeepLiveFrameGadgetView(
    context: Context,
) : LauncherGadgetView(context) {

    private val liveFrameView = GoogleKeepLiveFrameView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    init {
        addView(liveFrameView)
    }

    override suspend fun CoroutineScope.onActive() {
        liveFrameView.onResume()
        try {
            awaitCancellation()
        } finally {
            liveFrameView.onPause()
        }
    }
}

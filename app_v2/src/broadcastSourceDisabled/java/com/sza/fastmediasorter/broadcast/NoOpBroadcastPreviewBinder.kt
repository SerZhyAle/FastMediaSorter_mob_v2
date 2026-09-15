package com.sza.fastmediasorter.broadcast

import android.view.ViewGroup
import javax.inject.Inject
import javax.inject.Singleton

/** Builds without a broadcast source have no camera stream to show. */
@Singleton
class NoOpBroadcastPreviewBinder @Inject constructor() : BroadcastPreviewBinder {
    override fun attach(container: ViewGroup) = Unit

    override fun detach() = Unit
}

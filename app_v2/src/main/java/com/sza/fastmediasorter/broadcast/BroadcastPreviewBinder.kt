package com.sza.fastmediasorter.broadcast

import android.view.ViewGroup

/**
 * Puts the live camera picture of a running video broadcast into a screen.
 *
 * The screen hands over a plain container because the renderer type belongs to the streaming library,
 * which only broadcast-source builds link; a library view named in a `src/main` layout or class breaks
 * every other flavor.
 */
interface BroadcastPreviewBinder {
    fun attach(container: ViewGroup)
    fun detach()
}

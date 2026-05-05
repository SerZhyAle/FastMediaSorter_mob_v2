package com.sza.fastmediasorter.vr.ui

/**
 * Callback registered by HUD element owners and invoked by [VrHudInputDispatcher]
 * when a "trigger" event lands on the corresponding element id.
 *
 * Always invoked on the main looper — the dispatcher hops there before calling.
 * Implementations may touch UI / ViewModel state directly.
 */
fun interface VrHudInteractionCallback {
    fun onClick(elementId: Int)
}

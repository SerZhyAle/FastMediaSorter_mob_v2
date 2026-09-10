package com.sza.fastmediasorter.service

/**
 * S2881: pushes session state onto the surfaces that show it - the home-screen widget today.
 *
 * The port keeps the dependency direction honest: the session owner takes no `Context` (step 01.1)
 * and the widget provider must not name the owner, so the state crosses this one-method seam and
 * the Android side lives in the implementation the DI module binds.
 */
fun interface WatchListenStateRenderer {

    /** Called on every state change, in the application scope, for the life of the process. */
    fun render(state: WearListenState)
}

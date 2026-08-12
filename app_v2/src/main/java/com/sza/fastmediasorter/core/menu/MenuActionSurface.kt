package com.sza.fastmediasorter.core.menu

/**
 * S1424: which surface is asking for an item list.
 *
 * The two surfaces describe the same resource or channel; they differ in the state they own. That is
 * why composition takes the surface as a parameter instead of a flag decided at each call site - a
 * further cell kind joins by answering this enum rather than by changing the long-press handler
 * (strategic 5.3).
 */
enum class MenuActionSurface {

    /** Owns an ordered, visible list of resources and a window to place a second one beside. */
    MAIN_WINDOW,

    /** One launcher desktop cell: an identifier and nothing else - no list, no neighbouring window. */
    LAUNCHER_DESKTOP,
}

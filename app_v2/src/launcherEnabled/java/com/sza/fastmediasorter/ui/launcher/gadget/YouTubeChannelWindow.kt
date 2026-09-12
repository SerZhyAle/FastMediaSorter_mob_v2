package com.sza.fastmediasorter.ui.launcher.gadget

/**
 * S2032: the footprint of the YouTube channel window cell.
 *
 * Its own file rather than a companion inside the gadget, for the reason [StreamWindow] gives: the
 * placement write reads these before any view exists, and a constant read out of the callee is a
 * constant nobody finds.
 */
internal object YouTubeChannelWindow {

    /** Strategic §3.4: the owner set the cell at no less than three grid positions wide.. */
    const val SPAN_W = 3

    /** .. and two tall. */
    const val SPAN_H = 2
}

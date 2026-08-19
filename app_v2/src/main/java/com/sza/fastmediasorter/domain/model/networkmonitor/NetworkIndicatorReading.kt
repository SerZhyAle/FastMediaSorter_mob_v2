package com.sza.fastmediasorter.domain.model.networkmonitor

import com.sza.fastmediasorter.domain.usecase.networkmonitor.CgnatVerdict

/**
 * S1440: one network indicator's current reading, in a form both surfaces can draw.
 *
 * Deliberately free of Android types and resource ids: the same reading is rendered once into a
 * home-screen widget through RemoteViews and once into a launcher gadget through ordinary Views.
 *
 * "Not asked yet" and "asked, waiting" are first-class states rather than an empty string, because
 * strategic 4.3 splits the eight indicators into event-driven, tap-driven and visible-only classes -
 * a tap-driven cell has to be able to say which of the two it is in, and strategic 3.4 forbids the
 * home widget from ever fetching on a timer, which is only enforceable if "awaiting a tap" is
 * something the renderer can draw.
 */
sealed interface NetworkIndicatorReading {

    /**
     * A reading ready to show. [levelBars] is 0..4 for the signal indicator and null for the rest.
     *
     * [verdict] is carried unresolved, only for the external address: strategic 4.6 forbids claiming
     * CGNAT without proof, and the three-way wording is the formatter's job - collapsing it to text
     * here would put the same decision in two places and let the two surfaces word it differently.
     */
    data class Value(
        val primary: String,
        val caption: String?,
        val levelBars: Int?,
        val verdict: CgnatVerdict? = null
    ) : NetworkIndicatorReading

    /** The indicator needs a tap and has not been asked yet. */
    data object Awaiting : NetworkIndicatorReading

    /** A tap is in flight. */
    data object Loading : NetworkIndicatorReading

    /** The platform, a permission or a missing receiver denies the reading; [availability] says which. */
    data class Unavailable(val availability: SectionAvailability) : NetworkIndicatorReading

    /** The read was attempted and did not produce an answer. */
    data class Failed(val reason: String) : NetworkIndicatorReading
}

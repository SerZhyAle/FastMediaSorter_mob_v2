package com.sza.fastmediasorter.core.networkmonitor

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkIndicatorReading
import com.sza.fastmediasorter.domain.usecase.networkmonitor.CgnatVerdict
import com.sza.fastmediasorter.widget.networkmonitor.NetworkMonitorIndicator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * S1440: one indicator reading rendered into the three things both surfaces draw.
 *
 * The home widget draws through RemoteViews and the launcher gadget through ordinary Views, so the
 * wording lives here rather than in either renderer - two copies would word the same reading
 * differently the first time one of them changed.
 */
class NetworkIndicatorFormatter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun format(
        indicator: NetworkMonitorIndicator,
        reading: NetworkIndicatorReading
    ): FormattedIndicator = when (reading) {
        is NetworkIndicatorReading.Value -> formatValue(indicator, reading)
        NetworkIndicatorReading.Awaiting -> placeholder(indicator, R.string.widget_network_monitor_tap_to_read)
        NetworkIndicatorReading.Loading -> placeholder(indicator, R.string.loading)
        is NetworkIndicatorReading.Unavailable ->
            placeholder(indicator, R.string.widget_network_monitor_unavailable)
        // Failed carries a raw failure reason, which COMMUNICATION_POLICY section 6 forbids as the
        // primary message. The cell says what the user can act on - there is no reading - and the
        // reason stays in the log the read path already writes.
        is NetworkIndicatorReading.Failed ->
            placeholder(indicator, R.string.widget_network_monitor_unavailable)
    }

    private fun formatValue(
        indicator: NetworkMonitorIndicator,
        reading: NetworkIndicatorReading.Value
    ): FormattedIndicator = FormattedIndicator(
        primary = reading.primary,
        caption = captionFor(indicator, reading),
        iconRes = iconFor(indicator, reading.levelBars),
        levelBars = reading.levelBars
    )

    /**
     * Strategic 4.6: an echo endpoint answering does not prove carrier-grade NAT, so the three
     * verdicts get three distinct captions and [CgnatVerdict.Unknown] never borrows the CGNAT one.
     */
    private fun captionFor(
        indicator: NetworkMonitorIndicator,
        reading: NetworkIndicatorReading.Value
    ): String? {
        val verdictRes = if (indicator == NetworkMonitorIndicator.EXTERNAL_ADDRESS) {
            verdictCaptionRes(reading.verdict)
        } else {
            null
        }
        return verdictRes?.let { context.getString(it) } ?: reading.caption
    }

    @StringRes
    private fun verdictCaptionRes(verdict: CgnatVerdict?): Int? = when (verdict) {
        CgnatVerdict.Direct -> R.string.widget_network_monitor_direct_connection
        CgnatVerdict.LikelyCgnat -> R.string.widget_network_monitor_cgnat_likely
        CgnatVerdict.Unknown -> R.string.widget_network_monitor_behind_nat_unknown_depth
        null -> null
    }

    /**
     * Only the signal indicator has bars. Any other indicator, and any bar count outside the five
     * drawables, falls back to the indicator's own icon rather than picking a neighbouring bar.
     */
    @DrawableRes
    private fun iconFor(indicator: NetworkMonitorIndicator, levelBars: Int?): Int {
        val bars = levelBars.takeIf { indicator == NetworkMonitorIndicator.SIGNAL_LEVEL }
        return bars?.let { SIGNAL_BAR_ICONS.getOrNull(it) } ?: indicator.iconRes
    }

    private fun placeholder(
        indicator: NetworkMonitorIndicator,
        @StringRes textRes: Int
    ): FormattedIndicator = FormattedIndicator(
        primary = context.getString(textRes),
        caption = null,
        iconRes = indicator.iconRes,
        levelBars = null
    )

    private companion object {

        private val SIGNAL_BAR_ICONS = listOf(
            R.drawable.ic_signal_cellular_bar_0,
            R.drawable.ic_signal_cellular_bar_1,
            R.drawable.ic_signal_cellular_bar_2,
            R.drawable.ic_signal_cellular_bar_3,
            R.drawable.ic_signal_cellular_bar_4
        )
    }
}

/**
 * S1440: what a renderer needs and nothing more.
 *
 * [levelBars] is carried alongside [iconRes] because the launcher gadget draws its own bar meter
 * rather than a drawable, while the home widget can only set an image resource on a RemoteViews.
 */
data class FormattedIndicator(
    val primary: String,
    val caption: String?,
    @DrawableRes val iconRes: Int,
    val levelBars: Int?
)

package com.sza.fastmediasorter.core.networkmonitor

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkIndicatorReading
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import com.sza.fastmediasorter.domain.usecase.networkmonitor.CgnatVerdict
import com.sza.fastmediasorter.widget.networkmonitor.NetworkMonitorIndicator
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1440 step 02.5: the CGNAT wording is the one claim in this ticket that can be wrong while
 * everything still compiles, so it is asserted first and by name.
 *
 * Robolectric-free by design: the mocked Context answers every getString with the key's own name,
 * so an assertion names the string key rather than a translation that a locale pass may reword.
 */
class NetworkIndicatorFormatterTest {

    private val context = mockk<Context>(relaxed = true)
    private val formatter = NetworkIndicatorFormatter(context)

    init {
        every { context.getString(any()) } answers { KEY_NAMES[firstArg<Int>()] ?: UNMAPPED }
    }

    @Test
    fun `unknown verdict never borrows the cgnat wording`() {
        val caption = captionOf(NetworkMonitorIndicator.EXTERNAL_ADDRESS, CgnatVerdict.Unknown)

        assertEquals("widget_network_monitor_behind_nat_unknown_depth", caption)
        assertNotEquals("widget_network_monitor_cgnat_likely", caption)
    }

    @Test
    fun `likely cgnat renders the carrier nat caption`() {
        assertEquals(
            "widget_network_monitor_cgnat_likely",
            captionOf(NetworkMonitorIndicator.EXTERNAL_ADDRESS, CgnatVerdict.LikelyCgnat)
        )
    }

    @Test
    fun `direct verdict renders the direct connection caption`() {
        assertEquals(
            "widget_network_monitor_direct_connection",
            captionOf(NetworkMonitorIndicator.EXTERNAL_ADDRESS, CgnatVerdict.Direct)
        )
    }

    /**
     * The tap-to-read text is the cell's primary line, not its caption: a 1x1 widget draws the
     * primary and nothing else, so putting it in the caption would show an empty cell.
     */
    @Test
    fun `awaiting external address asks for a tap and shows no reading`() {
        val formatted = formatter.format(
            NetworkMonitorIndicator.EXTERNAL_ADDRESS,
            NetworkIndicatorReading.Awaiting
        )

        assertEquals("widget_network_monitor_tap_to_read", formatted.primary)
        assertEquals(null, formatted.caption)
        assertEquals(null, formatted.levelBars)
    }

    @Test
    fun `each signal bar count selects its own drawable and null falls back to the indicator icon`() {
        val icons = (0..MAX_BARS).map { bars ->
            formatter.format(
                NetworkMonitorIndicator.SIGNAL_LEVEL,
                NetworkIndicatorReading.Value(primary = "-60 dBm", caption = null, levelBars = bars)
            ).iconRes
        }

        assertEquals(icons.size, icons.distinct().size)
        assertEquals(
            NetworkMonitorIndicator.SIGNAL_LEVEL.iconRes,
            formatter.format(
                NetworkMonitorIndicator.SIGNAL_LEVEL,
                NetworkIndicatorReading.Value(primary = "-60 dBm", caption = null, levelBars = null)
            ).iconRes
        )
    }

    /**
     * Iterating `entries` rather than listing the eight constants is the point: a ninth indicator
     * added later fails here, at build time, instead of drawing an empty cell on someone's home
     * screen.
     */
    @Test
    fun `every indicator formats an unavailable reading`() {
        val unavailable = NetworkIndicatorReading.Unavailable(SectionAvailability.NoHardware)

        NetworkMonitorIndicator.entries.forEach { indicator ->
            val formatted = formatter.format(indicator, unavailable)

            assertEquals("widget_network_monitor_unavailable", formatted.primary)
            assertTrue(
                "indicator ${indicator.key} lost its icon",
                formatted.iconRes == indicator.iconRes
            )
        }
    }

    private fun captionOf(indicator: NetworkMonitorIndicator, verdict: CgnatVerdict): String? =
        formatter.format(
            indicator,
            NetworkIndicatorReading.Value(
                primary = "203.0.113.7",
                caption = null,
                levelBars = null,
                verdict = verdict
            )
        ).caption

    private companion object {

        private const val MAX_BARS = 4
        private const val UNMAPPED = "unmapped_string_key"

        private val KEY_NAMES = mapOf(
            R.string.widget_network_monitor_behind_nat_unknown_depth to
                "widget_network_monitor_behind_nat_unknown_depth",
            R.string.widget_network_monitor_cgnat_likely to "widget_network_monitor_cgnat_likely",
            R.string.widget_network_monitor_direct_connection to "widget_network_monitor_direct_connection",
            R.string.widget_network_monitor_tap_to_read to "widget_network_monitor_tap_to_read",
            R.string.widget_network_monitor_unavailable to "widget_network_monitor_unavailable",
            R.string.loading to "loading"
        )
    }
}

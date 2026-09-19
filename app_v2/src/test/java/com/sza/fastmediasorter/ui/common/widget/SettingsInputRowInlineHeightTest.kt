package com.sza.fastmediasorter.ui.common.widget

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import com.sza.fastmediasorter.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S3229: the inline port row inflated to the full leftover height of a `fillViewport` ScrollView,
 * pushing the start button off the broadcast control screen. The screen is measured here at a
 * portrait phone geometry, because landscape content already overflows the viewport and so never
 * reproduced it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsInputRowInlineHeightTest {

    private val widthPx = 1080
    private val heightPx = 2340

    private fun inflateBroadcastControl(): View {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_FastMediaSorter)
        val root = LayoutInflater.from(context).inflate(R.layout.activity_broadcast_control, null)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, widthPx, heightPx)
        return root
    }

    @Test
    fun `inline port row stays as compact as its sibling rows`() {
        val root = inflateBroadcastControl()
        val portRow = root.findViewById<LinearLayout>(R.id.rowPort)
        val bitRateRow = root.findViewById<View>(R.id.rowBitRate)

        assertTrue("port row must be laid out", portRow.height > 0)
        assertTrue(
            "port row ${portRow.height}px must stay within three sibling rows (${bitRateRow.height}px each)",
            portRow.height <= bitRateRow.height * 3 + portRow.paddingTop + portRow.paddingBottom,
        )
    }

    @Test
    fun `inline tail spacer takes width only`() {
        val root = inflateBroadcastControl()
        val portRow = root.findViewById<LinearLayout>(R.id.rowPort)
        val spacer = portRow.findViewById<View>(R.id.sir_inlineTailSpacer)

        assertEquals("tail spacer must stay flat", 0, spacer.height)
        assertTrue("tail spacer must still absorb the row's trailing slack", spacer.width > 0)
    }

    /**
     * S3235: the inline port row is a [LabelColumnRow], so its caption shares the column its dropdown
     * siblings already share instead of starting at an offset of its own.
     */
    @Test
    fun `inline port row answers the label column contract`() {
        val root = inflateBroadcastControl()
        val portRow = root.findViewById<SettingsInputRow>(R.id.rowPort)
        val titleLine = portRow.findViewById<View>(R.id.sir_titleLine)

        assertTrue("an inline row must report a label width", portRow.measureLabelNaturalWidth() > 0)
        assertTrue("an inline row must report a trailing width", portRow.measureTrailingNaturalWidth() > 0)

        portRow.applyLabelColumnWidth(COLUMN_PROBE_PX)
        assertEquals(COLUMN_PROBE_PX, (titleLine.layoutParams as LinearLayout.LayoutParams).width)

        portRow.applyLabelColumnWidth(0)
        assertEquals(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            (titleLine.layoutParams as LinearLayout.LayoutParams).width,
        )
    }

    @Test
    fun `port row caption shares one column with its dropdown siblings`() {
        val root = inflateBroadcastControl()
        val portLabel = root.findViewById<View>(R.id.rowPort).findViewById<View>(R.id.sir_titleLine)
        val bitRateLabel = root.findViewById<View>(R.id.rowBitRate).findViewById<View>(R.id.sdr_titleCluster)

        assertTrue("label column must be laid out", bitRateLabel.width > 0)
        assertEquals(
            "port caption column ${portLabel.width}px must match the dropdown column ${bitRateLabel.width}px",
            bitRateLabel.width,
            portLabel.width,
        )
    }

    @Test
    fun `start broadcast button keeps a real height on the portrait screen`() {
        val root = inflateBroadcastControl()
        val startButton = root.findViewById<View>(R.id.btnStartBroadcast)

        assertTrue("start button must be laid out", startButton.height > 0)
        assertTrue(
            "start button bottom ${startButton.bottom}px must fit the scrollable content",
            startButton.bottom <= (startButton.parent as View).height,
        )
    }

    private companion object {
        const val COLUMN_PROBE_PX = 321
    }
}

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
}

package com.sza.fastmediasorter.ui.networkmonitor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sza.fastmediasorter.core.panel.AppLaunchPanelRouteIntents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S2236: verifies intent construction for Network Monitor launcher origin vs direct callers. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@Suppress("FunctionNaming")
class NetworkMonitorLaunchIntentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `direct intent does not set launcher origin`() {
        val intent = NetworkMonitorActivity.createIntent(context, NetworkMonitorSection.Bluetooth)
        assertFalse(intent.hasNetworkMonitorLauncherOrigin())
        assertEquals(NetworkMonitorSection.Bluetooth, intent.readNetworkMonitorSection())
    }

    @Test
    fun `panel route intent sets launcher origin and preserves requested section`() {
        val intent = AppLaunchPanelRouteIntents.networkMonitor(context, NetworkMonitorSection.Bluetooth)
        assertTrue(intent.hasNetworkMonitorLauncherOrigin())
        assertEquals(NetworkMonitorSection.Bluetooth, intent.readNetworkMonitorSection())
    }
}

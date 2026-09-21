package com.sza.fastmediasorter.ui.launcher.dimclock

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.devicestatus.NetworkTransport
import com.sza.fastmediasorter.domain.model.network.HotspotState
import com.sza.fastmediasorter.domain.network.HotspotStateSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S3366 phase 02: the dim radio-chip mapping - what is present is shown, what is absent or unknown
 * is not, and each transport names its own icon, exactly as the tray renders the same state.
 */
@Suppress("FunctionNaming") // backtick test names, project convention
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectivityDimStatusSourceTest {

    private lateinit var application: Application
    private lateinit var source: ConnectivityDimStatusSource

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        source = ConnectivityDimStatusSource(application, UnusedHotspotStateSource)
    }

    @Test
    fun `bluetooth on and wifi transport render bluetooth and wifi chips`() {
        val chips = source.chips(bluetoothOn = true, hotspotEnabled = false, transport = NetworkTransport.WIFI)

        assertEquals(
            listOf(ConnectivityDimStatusSource.ID_BLUETOOTH, ConnectivityDimStatusSource.ID_NETWORK_WIFI),
            chips.map { it.id },
        )
        assertEquals(R.drawable.ic_wifi, chips[1].iconResId)
        assertEquals(application.getString(R.string.launcher_tray_network_wifi), chips[1].contentDescription)
    }

    @Test
    fun `an unknown bluetooth state renders no bluetooth chip`() {
        val chips = source.chips(bluetoothOn = null, hotspotEnabled = false, transport = NetworkTransport.WIFI)

        assertTrue(chips.none { it.id == ConnectivityDimStatusSource.ID_BLUETOOTH })
    }

    @Test
    fun `bluetooth off renders no bluetooth chip`() {
        val chips = source.chips(bluetoothOn = false, hotspotEnabled = false, transport = NetworkTransport.WIFI)

        assertTrue(chips.none { it.id == ConnectivityDimStatusSource.ID_BLUETOOTH })
    }

    @Test
    fun `cellular and ethernet transports land on the wireless routing id`() {
        val cellular = source.chips(false, false, NetworkTransport.CELLULAR)
        val ethernet = source.chips(false, false, NetworkTransport.ETHERNET)

        assertEquals(ConnectivityDimStatusSource.ID_NETWORK_OTHER, cellular.single().id)
        assertEquals(R.drawable.ic_signal_cellular, cellular.single().iconResId)
        assertEquals(ConnectivityDimStatusSource.ID_NETWORK_OTHER, ethernet.single().id)
        assertEquals(R.drawable.ic_ethernet, ethernet.single().iconResId)
    }

    @Test
    fun `no transport renders no network chip`() {
        val chips = source.chips(false, false, NetworkTransport.NONE)

        assertTrue(chips.isEmpty())
    }

    @Test
    fun `an enabled hotspot appends the tethering chip last`() {
        val chips = source.chips(true, true, NetworkTransport.WIFI)

        assertEquals(ConnectivityDimStatusSource.ID_TETHERING, chips.last().id)
        assertEquals(R.drawable.ic_wifi_tethering, chips.last().iconResId)
    }

    private object UnusedHotspotStateSource : HotspotStateSource {
        override fun state(): Flow<HotspotState> = flowOf(HotspotState.UNKNOWN)
    }
}

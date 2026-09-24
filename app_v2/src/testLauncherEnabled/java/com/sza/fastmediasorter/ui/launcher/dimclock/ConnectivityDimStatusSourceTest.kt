package com.sza.fastmediasorter.ui.launcher.dimclock

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.networkmonitor.TrafficRateReading
import com.sza.fastmediasorter.domain.model.devicestatus.NetworkTransport
import com.sza.fastmediasorter.domain.model.network.HotspotState
import com.sza.fastmediasorter.domain.network.HotspotStateSource
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTrayComposition
import com.sza.fastmediasorter.ui.launcher.tray.LauncherTraySimState
import io.mockk.mockk
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
 * S3366 / S3475: the dim status row maps the tray's inputs to the tray's indicators - same order, same
 * switches, same glyph level, badges and roaming mark - and what is absent or unknown is not drawn.
 */
@Suppress("FunctionNaming") // backtick test names, project convention
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConnectivityDimStatusSourceTest {

    private lateinit var application: Application
    private lateinit var source: ConnectivityDimStatusSource

    private val allOn = LauncherTrayComposition(
        clock = true,
        bluetooth = true,
        tethering = true,
        sim1 = true,
        sim2 = true,
        speed = true,
        network = true,
        battery = true,
    )

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        source = ConnectivityDimStatusSource(application, UnusedHotspotStateSource, mockk<SettingsRepository>())
    }

    private fun wifi(generation: Int? = null) = DimNetworkReading(NetworkTransport.WIFI, generation)

    @Test
    fun `every indicator appears in the tray order`() {
        val inputs = DimConnectivityInputs(
            composition = allOn,
            bluetoothOn = true,
            sims = mapOf(0 to LauncherTraySimState(signalLevel = 3), 1 to LauncherTraySimState(signalLevel = 2)),
            speed = TrafficRateReading(takenAtMillis = 0L, rxBytesPerSecond = 2048.0, txBytesPerSecond = 10.0),
            hotspotEnabled = true,
        )

        val chips = source.chips(inputs, wifi())

        assertEquals(
            listOf(
                ConnectivityDimStatusSource.ID_BLUETOOTH,
                ConnectivityDimStatusSource.ID_SIM1,
                ConnectivityDimStatusSource.ID_SIM2,
                ConnectivityDimStatusSource.ID_SPEED_RX,
                ConnectivityDimStatusSource.ID_SPEED_TX,
                ConnectivityDimStatusSource.ID_TETHERING,
                ConnectivityDimStatusSource.ID_NETWORK_WIFI,
            ),
            chips.map { it.id },
        )
    }

    @Test
    fun `a sim slot carries its signal level, data type badge and roaming mark`() {
        val inputs = DimConnectivityInputs(
            composition = allOn,
            sims = mapOf(0 to LauncherTraySimState(signalLevel = 4, roaming = true, dataNetworkType = 13)),
        )

        val sim = source.chips(inputs, wifi()).single { it.id == ConnectivityDimStatusSource.ID_SIM1 }

        assertEquals(R.drawable.launcher_tray_signal_level, sim.iconResId)
        assertEquals(4, sim.iconLevel)
        assertEquals("4G", sim.badgeText)
        assertEquals(R.drawable.launcher_tray_corner_marker, sim.cornerMarkResId)
    }

    @Test
    fun `an unreported sim slot is absent`() {
        val chips = source.chips(DimConnectivityInputs(composition = allOn), wifi())

        val simIds = setOf(ConnectivityDimStatusSource.ID_SIM1, ConnectivityDimStatusSource.ID_SIM2)
        assertTrue(chips.none { it.id in simIds })
    }

    @Test
    fun `a switched off tray indicator is absent from the dim row`() {
        val inputs = DimConnectivityInputs(
            composition = allOn.copy(sim1 = false, network = false),
            sims = mapOf(0 to LauncherTraySimState(signalLevel = 3)),
        )

        val chips = source.chips(inputs, wifi())

        assertTrue(chips.isEmpty())
    }

    @Test
    fun `an unknown or off bluetooth state renders no bluetooth chip`() {
        val unknown = source.chips(DimConnectivityInputs(allOn, bluetoothOn = null), wifi())
        val off = source.chips(DimConnectivityInputs(allOn, bluetoothOn = false), wifi())

        assertTrue(unknown.none { it.id == ConnectivityDimStatusSource.ID_BLUETOOTH })
        assertTrue(off.none { it.id == ConnectivityDimStatusSource.ID_BLUETOOTH })
    }

    @Test
    fun `connected bluetooth devices show their count and highlight the glyph`() {
        val chips = source.chips(DimConnectivityInputs(allOn, bluetoothOn = true, bluetoothConnected = 2), wifi())

        val bluetooth = chips.first()
        assertEquals("2", bluetooth.badgeText)
        assertTrue(bluetooth.highlighted)
    }

    @Test
    fun `wifi carries its generation badge and other transports land on the wireless id`() {
        val wifi = source.chips(DimConnectivityInputs(allOn), wifi(generation = 6)).single()
        val cellular = source.chips(DimConnectivityInputs(allOn), DimNetworkReading(NetworkTransport.CELLULAR)).single()

        assertEquals("6", wifi.badgeText)
        assertEquals(R.drawable.ic_wifi, wifi.iconResId)
        assertEquals(ConnectivityDimStatusSource.ID_NETWORK_OTHER, cellular.id)
        assertEquals(R.drawable.ic_signal_cellular, cellular.iconResId)
    }

    @Test
    fun `no transport draws the tray's network off glyph`() {
        val chip = source.chips(DimConnectivityInputs(allOn), DimNetworkReading(NetworkTransport.NONE)).single()

        assertEquals(R.drawable.ic_network_off, chip.iconResId)
        assertEquals(
            application.getString(
                R.string.launcher_tray_indicator_action,
                application.getString(R.string.launcher_tray_network_none),
            ),
            chip.contentDescription,
        )
    }

    @Test
    fun `speed renders as text cells`() {
        val inputs = DimConnectivityInputs(
            composition = allOn,
            speed = TrafficRateReading(takenAtMillis = 0L, rxBytesPerSecond = 0.0, txBytesPerSecond = 0.0),
        )

        val rx = source.chips(inputs, wifi()).single { it.id == ConnectivityDimStatusSource.ID_SPEED_RX }

        assertTrue(rx.text!!.startsWith("↓ "))
    }

    private object UnusedHotspotStateSource : HotspotStateSource {
        override fun state(): Flow<HotspotState> = flowOf(HotspotState.UNKNOWN)
    }
}

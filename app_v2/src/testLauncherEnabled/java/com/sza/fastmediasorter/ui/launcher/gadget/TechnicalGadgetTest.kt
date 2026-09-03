package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.domain.model.devicestatus.DeviceStatusProvider
import com.sza.fastmediasorter.domain.model.devicestatus.MetricValue
import com.sza.fastmediasorter.domain.model.devicestatus.NetworkStatus
import com.sza.fastmediasorter.domain.model.devicestatus.NetworkTransport
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.ui.networkmonitor.NetworkMonitorSection
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** S2238: verifies TechnicalGadget creation and tap handling. */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@Suppress("FunctionNaming")
class TechnicalGadgetTest {

    private val context: Context =
        ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_FastMediaSorter)
    private val host: LauncherGadgetHost = mockk(relaxed = true)

    private val dummyProvider = object : DeviceStatusProvider<Any> {
        override val refreshIntervalMs: Long = 5000L
        override suspend fun read(): Any = NetworkStatus(NetworkTransport.WIFI, MetricValue.Unknown, true)
    }

    @Test
    fun `network gadget tap opens network monitor summary`() {
        val gadget = TechnicalGadget(
            key = LauncherGadgetRegistry.KEY_NETWORK,
            labelRes = R.string.launcher_gadget_network,
            iconRes = R.drawable.ic_wifi,
            provider = dummyProvider,
        )
        val container = FrameLayout(context)
        val view = gadget.createView(container, host, null)

        view.findViewById<View>(R.id.gadgetTechnicalBody).performClick()

        verify {
            host.run(
                LauncherCellCommand.FeatureSection(
                    routeKey = InternalRouteCatalog.KEY_NETWORK_MONITOR,
                    sectionKey = NetworkMonitorSection.Summary.key,
                )
            )
        }
    }

    @Test
    fun `battery gadget tap opens system info`() {
        val gadget = TechnicalGadget(
            key = LauncherGadgetRegistry.KEY_BATTERY,
            labelRes = R.string.launcher_gadget_battery,
            iconRes = R.drawable.ic_battery,
            provider = dummyProvider,
        )
        val container = FrameLayout(context)
        val view = gadget.createView(container, host, null)

        view.findViewById<View>(R.id.gadgetTechnicalBody).performClick()

        verify {
            host.run(
                LauncherCellCommand.FeatureSection(
                    routeKey = InternalRouteCatalog.KEY_SYSTEM_INFO,
                    sectionKey = "",
                )
            )
        }
    }
}

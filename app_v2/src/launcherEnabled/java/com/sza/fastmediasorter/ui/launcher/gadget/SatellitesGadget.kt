package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.networkmonitor.GnssStatusDataSource
import com.sza.fastmediasorter.databinding.GadgetLauncherSatellitesBinding
import com.sza.fastmediasorter.domain.model.networkmonitor.GnssSnapshot
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

/**
 * S1560: how many satellites the receiver sees and how many of them the current fix was computed from.
 *
 * Reads the same GNSS source the Network Monitor does. That source is cold and foreground-only by
 * contract - it registers its callback while a collector is active and unregisters on close - which is
 * exactly the lifetime [LauncherGadgetView] gives a tile, so no extra ownership is introduced here.
 */
class SatellitesGadget @Inject constructor(
    private val gnss: Lazy<GnssStatusDataSource>,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_SATELLITES
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 1
    override val minSpanW: Int = 1
    override val minSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_satellites
    override val iconRes: Int = R.drawable.ic_satellites
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        SatellitesGadgetView(container.context, gnss.get())
}

private class SatellitesGadgetView(
    context: Context,
    private val gnss: GnssStatusDataSource,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherSatellitesBinding.inflate(LayoutInflater.from(context), this)

    /**
     * The source reports its own reason for having nothing, so a refused grant and a missing receiver
     * stay distinguishable on the tile instead of collapsing into one blank state.
     */
    override suspend fun CoroutineScope.onActive() {
        gnss.observe().collect { section ->
            val snapshot = section.data
            if (snapshot == null) {
                showMessage(messageFor(section.availability))
            } else {
                showCounts(snapshot)
            }
        }
    }

    /** Zero used-in-fix is the owner's marker for "no GPS", so it is shown as a number, not as a message. */
    private fun showCounts(snapshot: GnssSnapshot) {
        val value = context.getString(
            R.string.launcher_gadget_satellites_value,
            snapshot.usedInFixCount,
            snapshot.visibleCount,
        )
        binding.gadgetSatellitesValue.text = value
        binding.gadgetSatellitesMessage.isVisible = false
        contentDescription = context.getString(
            R.string.launcher_gadget_satellites_description,
            snapshot.usedInFixCount,
            snapshot.visibleCount,
        )
    }

    private fun showMessage(@StringRes messageRes: Int) {
        val message = context.getString(messageRes)
        binding.gadgetSatellitesMessage.text = message
        binding.gadgetSatellitesMessage.isVisible = true
        contentDescription = message
    }

    @StringRes
    private fun messageFor(availability: SectionAvailability): Int = when (availability) {
        is SectionAvailability.NoPermission -> R.string.launcher_gadget_sensor_no_permission
        else -> R.string.launcher_gadget_sensor_no_fix
    }
}

package com.sza.fastmediasorter.ui.launcher.gadget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.format.QuantityFormatter
import com.sza.fastmediasorter.databinding.GadgetLauncherAltitudeBinding
import com.sza.fastmediasorter.domain.model.Quantity
import com.sza.fastmediasorter.domain.model.UnitSystem
import com.sza.fastmediasorter.domain.model.sensors.SensorCapability
import com.sza.fastmediasorter.domain.repository.SensorAvailabilityRepository
import com.sza.fastmediasorter.domain.unit.UnitSystemProvider
import com.sza.fastmediasorter.domain.usecase.sensors.ObserveMotionUseCase
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * S1560: altitude above sea level as a single reading, alongside the altitude-over-distance chart that
 * already existed. Reads the same [ObserveMotionUseCase] as the speed tile - one motion reading feeds both.
 *
 * S2795: metres are no longer fixed - the unit follows the unit-system setting through [QuantityFormatter].
 */
class AltitudeGadget @Inject constructor(
    private val availability: SensorAvailabilityRepository,
    private val observeMotion: Lazy<ObserveMotionUseCase>,
    private val quantityFormatter: Lazy<QuantityFormatter>,
    private val unitSystemProvider: Lazy<UnitSystemProvider>,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_ALTITUDE
    override val defaultSpanW: Int = 1
    override val defaultSpanH: Int = 1
    override val minSpanW: Int = 1
    override val minSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_altitude
    override val iconRes: Int = R.drawable.ic_altitude
    override val requiresResourceParam: Boolean = false

    override fun isAvailable(): Boolean = availability.isAvailable(SensorCapability.LOCATION)

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        AltitudeGadgetView(
            container.context,
            observeMotion.get(),
            quantityFormatter.get(),
            unitSystemProvider.get(),
        )
}

private class AltitudeGadgetView(
    context: Context,
    private val observeMotion: ObserveMotionUseCase,
    private val quantityFormatter: QuantityFormatter,
    private val unitSystemProvider: UnitSystemProvider,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherAltitudeBinding.inflate(LayoutInflater.from(context), this)

    /**
     * A refused grant is a state, not an error: the tile stays on the desktop saying why it is idle.
     * The location flow is not started without the grant because `MotionReadingSource` requires its
     * caller to have checked one.
     */
    override suspend fun CoroutineScope.onActive() {
        if (!hasLocationPermission()) {
            showMessage(R.string.launcher_gadget_sensor_no_permission)
            return
        }
        showMessage(R.string.launcher_gadget_altitude_unknown)
        // S2795: the setting rides the same stream as the reading, so flipping the unit system redraws
        // the tile at once instead of waiting for the next position fix.
        combine(observeMotion(), unitSystemProvider.current) { reading, system ->
            reading.altitudeMeters to system
        }.collect { (altitudeMeters, system) ->
            if (altitudeMeters == null) {
                showMessage(R.string.launcher_gadget_altitude_unknown)
            } else {
                showAltitude(altitudeMeters, system)
            }
        }
    }

    private fun showAltitude(altitudeMeters: Double, system: UnitSystem) {
        val quantity = Quantity.Altitude(altitudeMeters)
        binding.gadgetAltitudeValue.text = quantityFormatter.format(quantity, system)
        binding.gadgetAltitudeMessage.isVisible = false
        contentDescription = context.getString(
            R.string.launcher_gadget_altitude_description,
            quantityFormatter.contentDescription(quantity, system),
        )
    }

    /** Keeps whatever value is already on screen - a momentary loss of fix must not blank a readable tile. */
    private fun showMessage(@StringRes messageRes: Int) {
        val message = context.getString(messageRes)
        binding.gadgetAltitudeMessage.text = message
        binding.gadgetAltitudeMessage.isVisible = true
        contentDescription = context.getString(
            R.string.launcher_gadget_altitude_description,
            binding.gadgetAltitudeValue.text.ifEmpty { message },
        )
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}

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
import com.sza.fastmediasorter.databinding.GadgetLauncherSpeedBinding
import com.sza.fastmediasorter.domain.model.Quantity
import com.sza.fastmediasorter.domain.model.UnitSystem
import com.sza.fastmediasorter.domain.model.sensors.SensorCapability
import com.sza.fastmediasorter.domain.repository.SensorAvailabilityRepository
import com.sza.fastmediasorter.domain.unit.UnitSystemProvider
import com.sza.fastmediasorter.domain.usecase.sensors.ObserveMotionUseCase
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import timber.log.Timber
import javax.inject.Inject

/**
 * S1179: current speed on the desktop. Its strategic §3.1.2 fixed the tile to kilometres per hour;
 * S2795 supersedes that decision - the unit now follows the unit-system setting, and the tile owns no
 * unit of its own because [QuantityFormatter] picks it.
 */
class SpeedGadget @Inject constructor(
    private val availability: SensorAvailabilityRepository,
    private val observeMotion: Lazy<ObserveMotionUseCase>,
    private val quantityFormatter: Lazy<QuantityFormatter>,
    private val unitSystemProvider: Lazy<UnitSystemProvider>,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_SPEED
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 1
    override val minSpanW: Int = 1
    override val minSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_speed
    override val iconRes: Int = R.drawable.ic_speed
    override val requiresResourceParam: Boolean = false

    override fun isAvailable(): Boolean = availability.isAvailable(SensorCapability.LOCATION)

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        SpeedGadgetView(
            container.context,
            observeMotion.get(),
            quantityFormatter.get(),
            unitSystemProvider.get(),
        )
}

private class SpeedGadgetView(
    context: Context,
    private val observeMotion: ObserveMotionUseCase,
    private val quantityFormatter: QuantityFormatter,
    private val unitSystemProvider: UnitSystemProvider,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherSpeedBinding.inflate(LayoutInflater.from(context), this)

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
        showMessage(R.string.launcher_gadget_sensor_no_fix)
        // S2795: the setting is folded into the same stream as the reading, so flipping the unit system
        // redraws the tile at once instead of waiting for the next position fix, which may be minutes away.
        combine(observeMotion(), unitSystemProvider.current) { reading, system ->
            reading.speedKmh to system
        }.collect { (speedKmh, system) ->
            if (speedKmh == null) {
                showMessage(R.string.launcher_gadget_sensor_no_fix)
            } else {
                showSpeed(speedKmh, system)
            }
        }
    }

    private fun showSpeed(speedKmh: Float, system: UnitSystem) {
        // MotionReading carries km/h because S1179 converted at the source; the format seam takes the
        // platform's own metres per second, so the reading is put back on that scale rather than the
        // gadget teaching the seam a second input unit.
        val quantity = Quantity.Speed(speedKmh * METRES_PER_SECOND_PER_KMH)
        binding.gadgetSpeedValue.text = quantityFormatter.format(quantity, system)
        binding.gadgetSpeedMessage.isVisible = false
        contentDescription = context.getString(
            R.string.launcher_gadget_speed_description,
            quantityFormatter.contentDescription(quantity, system),
        )
    }

    /** Keeps whatever value is already on screen - a momentary loss of fix must not blank a readable tile. */
    private fun showMessage(@StringRes messageRes: Int) {
        val message = context.getString(messageRes)
        binding.gadgetSpeedMessage.text = message
        binding.gadgetSpeedMessage.isVisible = true
        contentDescription = context.getString(
            R.string.launcher_gadget_speed_description,
            binding.gadgetSpeedValue.text.ifEmpty { message },
        )
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val METRES_PER_SECOND_PER_KMH = 1.0 / 3.6
    }
}

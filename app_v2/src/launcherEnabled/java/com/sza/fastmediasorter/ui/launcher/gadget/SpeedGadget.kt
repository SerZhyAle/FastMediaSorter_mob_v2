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
import com.sza.fastmediasorter.databinding.GadgetLauncherSpeedBinding
import com.sza.fastmediasorter.domain.model.sensors.SensorCapability
import com.sza.fastmediasorter.domain.repository.SensorAvailabilityRepository
import com.sza.fastmediasorter.domain.usecase.sensors.ObserveMotionUseCase
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * S1179: current speed on the desktop, in kilometres per hour (strategic §3.1.2).
 */
class SpeedGadget @Inject constructor(
    private val availability: SensorAvailabilityRepository,
    private val observeMotion: Lazy<ObserveMotionUseCase>,
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
        SpeedGadgetView(container.context, observeMotion.get())
}

private class SpeedGadgetView(
    context: Context,
    private val observeMotion: ObserveMotionUseCase,
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
        observeMotion().collect { reading ->
            val speedKmh = reading.speedKmh
            if (speedKmh == null) {
                showMessage(R.string.launcher_gadget_sensor_no_fix)
            } else {
                showSpeed(speedKmh)
            }
        }
    }

    private fun showSpeed(speedKmh: Float) {
        val value = context.getString(R.string.launcher_gadget_speed_value, speedKmh.roundToInt())
        binding.gadgetSpeedValue.text = value
        binding.gadgetSpeedMessage.isVisible = false
        contentDescription = context.getString(R.string.launcher_gadget_speed_description, value)
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
}

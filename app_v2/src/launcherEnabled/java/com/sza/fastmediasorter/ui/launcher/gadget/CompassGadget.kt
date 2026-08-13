package com.sza.fastmediasorter.ui.launcher.gadget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherCompassBinding
import com.sza.fastmediasorter.domain.model.sensors.CompassReading
import com.sza.fastmediasorter.domain.model.sensors.SensorAccuracy
import com.sza.fastmediasorter.domain.model.sensors.SensorCapability
import com.sza.fastmediasorter.domain.repository.SensorAvailabilityRepository
import com.sza.fastmediasorter.domain.usecase.sensors.ObserveCompassUseCase
import com.sza.fastmediasorter.domain.usecase.sensors.ObserveMotionUseCase
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * S1179: heading and altitude on one desktop tile, as the owner asked for them (strategic §3.1.1).
 */
class CompassGadget @Inject constructor(
    private val availability: SensorAvailabilityRepository,
    private val observeCompass: Lazy<ObserveCompassUseCase>,
    private val observeMotion: Lazy<ObserveMotionUseCase>,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_COMPASS
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 1
    override val minSpanW: Int = 1
    override val minSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_compass
    override val iconRes: Int = R.drawable.ic_compass
    override val requiresResourceParam: Boolean = false

    override fun isAvailable(): Boolean = availability.isAvailable(SensorCapability.COMPASS)

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        CompassGadgetView(container.context, observeCompass.get(), observeMotion.get())
}

private class CompassGadgetView(
    context: Context,
    private val observeCompass: ObserveCompassUseCase,
    private val observeMotion: ObserveMotionUseCase,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherCompassBinding.inflate(LayoutInflater.from(context), this)

    /**
     * The heading needs no permission and the altitude does, so the two are collected separately and
     * a refused grant costs only the altitude line. `MotionReadingSource` requires its caller to have
     * checked the grant, which is why the location flow is not even started without one.
     */
    override suspend fun CoroutineScope.onActive() {
        val granted = hasLocationPermission()
        val altitudes: Flow<Double?> = if (granted) {
            observeMotion().map { it.altitudeMeters }.onStart { emit(null) }
        } else {
            flowOf(null)
        }
        observeCompass()
            .combine(altitudes) { compass, altitude -> compass to altitude }
            .collect { (compass, altitude) -> render(compass, altitude, granted) }
    }

    private fun render(reading: CompassReading, altitudeMeters: Double?, locationGranted: Boolean) {
        val cardinal = context.getString(cardinalRes(reading.azimuthDegrees))
        val heading = context.getString(
            R.string.launcher_gadget_compass_heading,
            reading.azimuthDegrees.roundToInt(),
            cardinal,
        )
        val altitude = when {
            !locationGranted -> context.getString(R.string.launcher_gadget_sensor_no_permission)
            altitudeMeters == null -> context.getString(R.string.launcher_gadget_sensor_no_fix)
            else -> context.getString(R.string.launcher_gadget_compass_altitude, altitudeMeters.roundToInt())
        }

        binding.gadgetCompassHeading.text = heading
        binding.gadgetCompassAltitude.text = altitude
        // Negative: the needle turns against the device so it keeps pointing at north.
        binding.gadgetCompassNeedle.rotation = -reading.azimuthDegrees
        binding.gadgetCompassCalibrate.isVisible = reading.accuracy == SensorAccuracy.UNRELIABLE
        contentDescription =
            context.getString(R.string.launcher_gadget_compass_description, heading, altitude)
    }

    private fun cardinalRes(azimuthDegrees: Float): Int {
        val normalised = (azimuthDegrees % FULL_TURN + FULL_TURN + HALF_SECTOR) / SECTOR
        return CARDINALS[normalised.toInt() % CARDINALS.size]
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val FULL_TURN = 360f
        const val SECTOR = 45f
        const val HALF_SECTOR = 22.5f

        val CARDINALS = intArrayOf(
            R.string.launcher_gadget_compass_cardinal_n,
            R.string.launcher_gadget_compass_cardinal_ne,
            R.string.launcher_gadget_compass_cardinal_e,
            R.string.launcher_gadget_compass_cardinal_se,
            R.string.launcher_gadget_compass_cardinal_s,
            R.string.launcher_gadget_compass_cardinal_sw,
            R.string.launcher_gadget_compass_cardinal_w,
            R.string.launcher_gadget_compass_cardinal_nw,
        )
    }
}

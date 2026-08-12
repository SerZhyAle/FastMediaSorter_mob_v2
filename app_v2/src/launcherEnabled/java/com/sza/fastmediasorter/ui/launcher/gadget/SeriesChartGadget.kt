package com.sza.fastmediasorter.ui.launcher.gadget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.GadgetLauncherSeriesChartBinding
import com.sza.fastmediasorter.domain.model.sensors.MotionReading
import com.sza.fastmediasorter.domain.model.sensors.SensorCapability
import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesId
import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesPoint
import com.sza.fastmediasorter.domain.repository.SensorAvailabilityRepository
import com.sza.fastmediasorter.domain.usecase.sensors.ObserveMotionUseCase
import com.sza.fastmediasorter.domain.usecase.sensors.ObserveSensorSeriesUseCase
import com.sza.fastmediasorter.domain.usecase.sensors.RecordSensorSeriesPointUseCase
import com.sza.fastmediasorter.domain.usecase.sensors.ResetSensorSeriesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * S1179: one chart tile over one persistent series - instantiated twice, for speed and for
 * altitude/distance, so a third chart is a registration rather than a new view (strategic §5.3).
 */
class SeriesChartGadget(
    override val key: String,
    override val labelRes: Int,
    internal val seriesId: SensorSeriesId,
    internal val secondaryShown: Boolean,
    private val availability: SensorAvailabilityRepository,
    internal val observeSeries: ObserveSensorSeriesUseCase,
    internal val observeMotion: ObserveMotionUseCase,
    internal val recordPoint: RecordSensorSeriesPointUseCase,
    internal val resetSeries: ResetSensorSeriesUseCase,
) : LauncherGadget {

    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 1
    override val minSpanW: Int = 2
    override val minSpanH: Int = 1
    override val iconRes: Int = R.drawable.ic_series_chart
    override val requiresResourceParam: Boolean = false

    /**
     * Held by whichever view is currently feeding this series. Two desktop cells may show the same
     * chart, and both would otherwise record: the running distance would gain both deltas for one
     * movement and two decimations could interleave. The gadget is a singleton per series, so this
     * is the one place that can arbitrate. Recorded as a Phase 02 handoff obligation.
     */
    private val recording = AtomicBoolean(false)

    override fun isAvailable(): Boolean = availability.isAvailable(SensorCapability.LOCATION)

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        SeriesChartGadgetView(container.context, this)

    internal fun claimRecording(): Boolean = recording.compareAndSet(false, true)

    internal fun releaseRecording() = recording.set(false)
}

/**
 * Recording runs in `onActive` and nowhere else - no service, no `WorkManager`, no
 * application-scoped collector - so a series grows only while its chart is on screen, and a gap in
 * the line is a period the desktop was not visible. That reads like a bug and the obvious "fix",
 * moving collection into a service, is exactly what would drag a background-location declaration
 * into the app (strategic §6 item 5 and the §2 non-goals).
 */
private class SeriesChartGadgetView(
    context: Context,
    private val gadget: SeriesChartGadget,
) : LauncherGadgetView(context) {

    private val binding = GadgetLauncherSeriesChartBinding.inflate(LayoutInflater.from(context), this)

    /** Non-null only while the view is active, which is the only time a reset may run. */
    private var activeScope: CoroutineScope? = null

    init {
        binding.gadgetChartSeries.showSecondary = gadget.secondaryShown
        binding.gadgetChartReset.setOnClickListener {
            Timber.d("S1179: chart reset tapped for %s", gadget.seriesId)
            activeScope?.launch { gadget.resetSeries(gadget.seriesId) }
        }
    }

    override suspend fun CoroutineScope.onActive() {
        activeScope = this
        try {
            Timber.d("S1179: chart tile active for %s, granted=%b", gadget.seriesId, hasLocationPermission())
            launch { gadget.observeSeries(gadget.seriesId).collect { render(it) } }
            if (hasLocationPermission() && gadget.claimRecording()) {
                try {
                    gadget.observeMotion().collect { recordSample(it) }
                } finally {
                    gadget.releaseRecording()
                }
            }
        } finally {
            activeScope = null
        }
    }

    private suspend fun recordSample(reading: MotionReading) {
        val primary: Double
        val secondaryDelta: Double?
        when (gadget.seriesId) {
            SensorSeriesId.SPEED -> {
                primary = reading.speedKmh?.toDouble() ?: return
                secondaryDelta = null
            }

            SensorSeriesId.ALTITUDE_DISTANCE -> {
                primary = reading.altitudeMeters ?: return
                secondaryDelta = reading.distanceDeltaMeters
            }
        }
        gadget.recordPoint(gadget.seriesId, primary, secondaryDelta, reading.takenAtMillis)
    }

    private fun render(points: List<SensorSeriesPoint>) {
        binding.gadgetChartSeries.setPoints(points)
        val hasData = binding.gadgetChartSeries.hasData
        binding.gadgetChartSeries.isVisible = hasData
        binding.gadgetChartEmpty.isVisible = !hasData
        if (!hasData) {
            binding.gadgetChartValue.text = ""
            binding.gadgetChartSecondary.isVisible = false
            contentDescription = context.getString(R.string.launcher_gadget_chart_empty)
            return
        }

        val newest = points.last()
        val value = formatValue(newest.primaryValue)
        val elapsed = DateUtils.formatElapsedTime(
            (newest.takenAtMillis - points.first().takenAtMillis) / MILLIS_PER_SECOND,
        )
        val distance = newest.secondaryValue?.let { formatDistance(it) }

        binding.gadgetChartValue.text = value
        binding.gadgetChartSecondary.isVisible = gadget.secondaryShown && distance != null
        binding.gadgetChartSecondary.text = distance.orEmpty()
        contentDescription = if (gadget.secondaryShown) {
            context.getString(
                R.string.launcher_gadget_altitude_chart_description,
                value,
                elapsed,
                distance.orEmpty(),
            )
        } else {
            context.getString(R.string.launcher_gadget_speed_chart_description, value, elapsed)
        }
    }

    private fun formatValue(value: Double): String = when (gadget.seriesId) {
        SensorSeriesId.SPEED ->
            context.getString(R.string.launcher_gadget_speed_value, value.roundToInt())

        SensorSeriesId.ALTITUDE_DISTANCE ->
            context.getString(R.string.launcher_gadget_chart_altitude_current, value.roundToInt())
    }

    private fun formatDistance(meters: Double): String = context.getString(
        R.string.launcher_gadget_chart_distance,
        String.format(Locale.getDefault(), DISTANCE_FORMAT, meters / METERS_PER_KILOMETRE),
    )

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
        const val METERS_PER_KILOMETRE = 1000.0
        const val DISTANCE_FORMAT = "%.1f"
    }
}

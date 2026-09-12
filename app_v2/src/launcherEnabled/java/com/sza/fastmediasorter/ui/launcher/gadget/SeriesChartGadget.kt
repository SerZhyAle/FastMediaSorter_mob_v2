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
import com.sza.fastmediasorter.domain.model.Quantity
import com.sza.fastmediasorter.domain.model.UnitSystem
import com.sza.fastmediasorter.domain.model.sensors.MotionReading
import com.sza.fastmediasorter.domain.model.sensors.SensorCapability
import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesId
import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesPoint
import com.sza.fastmediasorter.ui.launcher.gadget.di.SeriesChartDependencies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * S1179: one chart tile over one persistent series - instantiated twice, for speed and for
 * altitude/distance, so a third chart is a registration rather than a new view (strategic §5.3).
 */
class SeriesChartGadget(
    override val key: String,
    override val labelRes: Int,
    internal val seriesId: SensorSeriesId,
    internal val secondaryShown: Boolean,
    // S2795: the collaborators arrive as the holder rather than one parameter each. Unfolding them
    // again would put this constructor past detekt's constructorThreshold the moment the format seam
    // joined, and the holder exists precisely to keep the tile's own four fields the visible ones.
    internal val dependencies: SeriesChartDependencies,
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

    override fun isAvailable(): Boolean =
        dependencies.availability.isAvailable(SensorCapability.LOCATION)

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
            activeScope?.launch { gadget.dependencies.resetSeries(gadget.seriesId) }
        }
    }

    override suspend fun CoroutineScope.onActive() {
        activeScope = this
        try {
            launch {
                // S2795: the setting is folded into the series stream, so flipping the unit system
                // relabels the newest reading at once instead of waiting for the next recorded point.
                combine(
                    gadget.dependencies.observeSeries(gadget.seriesId),
                    gadget.dependencies.unitSystemProvider.current,
                ) { points, system -> points to system }
                    .collect { (points, system) -> render(points, system) }
            }
            if (hasLocationPermission() && gadget.claimRecording()) {
                try {
                    gadget.dependencies.observeMotion().collect { recordSample(it) }
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
        gadget.dependencies.recordPoint(
            gadget.seriesId,
            primary,
            secondaryDelta,
            reading.takenAtMillis,
        )
    }

    private fun render(points: List<SensorSeriesPoint>, system: UnitSystem) {
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
        val value = formatValue(newest.primaryValue, system)
        val elapsed = DateUtils.formatElapsedTime(
            (newest.takenAtMillis - points.first().takenAtMillis) / MILLIS_PER_SECOND,
        )
        val distance = newest.secondaryValue?.let { formatDistance(it, system) }

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

    /**
     * S2795: the speed line carries no unit of its own - the seam picks one. The series stores km/h
     * because S1179 converted at the source, so the value is put back on the platform's metres per
     * second rather than teaching the seam a second input scale (the speed tile does the same).
     */
    private fun formatValue(value: Double, system: UnitSystem): String = when (gadget.seriesId) {
        SensorSeriesId.SPEED -> gadget.dependencies.quantityFormatter.format(
            Quantity.Speed(value * METRES_PER_SECOND_PER_KMH),
            system,
        )

        SensorSeriesId.ALTITUDE_DISTANCE -> context.getString(
            R.string.launcher_gadget_chart_altitude_now,
            gadget.dependencies.quantityFormatter.format(Quantity.Altitude(value), system),
        )
    }

    private fun formatDistance(meters: Double, system: UnitSystem): String =
        gadget.dependencies.quantityFormatter.format(Quantity.Distance(meters), system)

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
        const val METRES_PER_SECOND_PER_KMH = 1.0 / 3.6
    }
}

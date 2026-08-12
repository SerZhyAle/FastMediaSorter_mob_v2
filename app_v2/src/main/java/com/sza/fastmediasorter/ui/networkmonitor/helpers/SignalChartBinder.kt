package com.sza.fastmediasorter.ui.networkmonitor.helpers

import android.content.Context
import android.text.format.Formatter
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.networkmonitor.MonitorSection
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import com.sza.fastmediasorter.domain.model.networkmonitor.SignalSample
import com.sza.fastmediasorter.domain.model.networkmonitor.SignalSeries
import com.sza.fastmediasorter.domain.model.networkmonitor.SignalSummary
import com.sza.fastmediasorter.domain.model.networkmonitor.SignalTrend
import com.sza.fastmediasorter.ui.common.chart.SensorSeriesChartView
import java.util.Locale

/**
 * S1433: which quantity a chart draws, and therefore how its summary line reads.
 *
 * A [SignalSeries] carries plain numbers with no unit attached, because the Monitor's charts measure
 * different things - a radio in dBm, a satellite in dB-Hz, the interfaces in bytes per second. The unit is
 * named by the section that owns the chart and applied here, so one formatter serves all of them and a
 * section cannot draw a byte rate labelled in dBm.
 */
enum class ChartValueUnit { DBM, DB_HZ, BYTES_PER_SECOND }

/**
 * S1433: binds one accumulated [SignalSeries] to the shared chart and to the text line beneath it.
 *
 * One binder for every charted section. Strategic §3.2 requires the same "current / min / max / trend" line
 * under each of them, so a per-section copy would differ only by accident; only [unit] varies. The chart
 * itself is `SensorSeriesChartView` from `ui/common/chart/` - S1446 ruled that a second chart class would
 * diverge from the first at its first change.
 */
class SignalChartBinder(
    private val chart: SensorSeriesChartView,
    private val summaryView: TextView,
    private val emptyView: TextView,
    private val unit: ChartValueUnit = ChartValueUnit.DBM,
) {

    init {
        // A full-screen section has the room for the labelled axis a launcher gadget tile deliberately hides.
        chart.showValueAxis = true
    }

    /** Draws [section], or states in words why there is nothing to draw. */
    fun render(section: MonitorSection<SignalSeries>) {
        val points = section.data?.toChartPoints().orEmpty()
        val summary = section.data?.summary()
        if (section.availability !is SectionAvailability.Available || summary == null ||
            points.size < MIN_CHART_POINTS
        ) {
            showEmpty(section.availability)
            return
        }
        chart.setPoints(points)
        val text = summaryView.context.formatSignalSummary(summary, unit)
        summaryView.text = text
        // Strategic §3.2 owes every chart a text equivalent; the same line serves the screen reader.
        chart.contentDescription = text
        setChartVisible(true)
    }

    private fun showEmpty(availability: SectionAvailability) {
        setChartVisible(false)
        emptyView.setText(availability.toChartEmptyRes())
    }

    private fun setChartVisible(visible: Boolean) {
        chart.isVisible = visible
        summaryView.isVisible = visible
        emptyView.isVisible = !visible
    }

    private companion object {

        /** One point is a dot, not a line - the shared view refuses to draw below this too. */
        const val MIN_CHART_POINTS = 2
    }
}

/**
 * S1433: folds one reading into the window a section keeps for its chart.
 *
 * The window is dropped whenever the source stops being available, rather than frozen: a chart that keeps
 * showing the last two minutes after the permission was revoked or the radio went off would be reporting a
 * signal nobody is measuring.
 */
internal fun MonitorSection<SignalSeries>.appendSample(
    reading: MonitorSection<SignalSample>,
): MonitorSection<SignalSeries> {
    val sample = reading.data ?: return MonitorSection.absent(reading.availability)
    return MonitorSection.available((data ?: SignalSeries()).append(sample))
}

/** The empty window a section starts from, before its first reading arrives. */
internal fun emptySignalWindow(): MonitorSection<SignalSeries> =
    MonitorSection.absent(SectionAvailability.NoNetwork)

/**
 * S1433: the "current / min / max / trend" line strategic §3.2 requires under every chart.
 *
 * Formatted here rather than in the chart view, which holds no strings at all: the sources of the first
 * release carry different units and only the section that owns one knows which it is.
 */
internal fun Context.formatSignalSummary(summary: SignalSummary, unit: ChartValueUnit): String = getString(
    R.string.network_monitor_chart_summary,
    formatChartValue(summary.last, unit),
    formatChartValue(summary.min, unit),
    formatChartValue(summary.max, unit),
    getString(summary.trend.toLabelRes()),
)

/**
 * One value in the unit its section named.
 *
 * The two decibel scales are drawn whole - the radios and the receiver both report integers, and a decimal
 * would suggest a precision that is not there. A byte rate goes through the platform's own short-size
 * formatter instead, so it steps from B/s to kB/s to MB/s the way the rest of the system writes sizes rather
 * than filling the line with digits at a busy moment.
 */
internal fun Context.formatChartValue(value: Double, unit: ChartValueUnit): String = when (unit) {
    ChartValueUnit.DBM -> getString(R.string.network_monitor_value_dbm, formatWhole(value))
    ChartValueUnit.DB_HZ -> getString(R.string.network_monitor_value_db_hz, formatWhole(value))
    ChartValueUnit.BYTES_PER_SECOND -> getString(
        R.string.network_monitor_value_per_second,
        Formatter.formatShortFileSize(this, value.toLong().coerceAtLeast(0L)),
    )
}

private fun formatWhole(value: Double): String = String.format(Locale.getDefault(), WHOLE_NUMBER_FORMAT, value)

@StringRes
private fun SignalTrend?.toLabelRes(): Int = when (this) {
    SignalTrend.RISING -> R.string.network_monitor_trend_rising
    SignalTrend.FALLING -> R.string.network_monitor_trend_falling
    SignalTrend.FLAT -> R.string.network_monitor_trend_flat
    null -> R.string.network_monitor_trend_unknown
}

/**
 * S1433: why a section has nothing to show, in words.
 *
 * Strategic §11 criterion 2 requires the honest reason rather than a blank field, and the same four phrases
 * already label the Summary tiles - a section inventing its own wording would tell the user two different
 * things about one fact.
 */
@StringRes
internal fun SectionAvailability.toReasonRes(): Int = when (this) {
    SectionAvailability.Available -> R.string.network_monitor_status_available
    SectionAvailability.NoHardware -> R.string.network_monitor_status_no_hardware
    is SectionAvailability.NoPermission -> R.string.network_monitor_status_no_permission
    SectionAvailability.NoNetwork -> R.string.network_monitor_status_no_network
}

/**
 * Why a chart is empty.
 *
 * [SectionAvailability.Available] maps to "collecting" rather than to a reason, because an available source
 * with too few points has not failed at anything - it has been open for less than two seconds.
 */
@StringRes
private fun SectionAvailability.toChartEmptyRes(): Int =
    if (this is SectionAvailability.Available) {
        R.string.network_monitor_chart_waiting
    } else {
        toReasonRes()
    }

private const val WHOLE_NUMBER_FORMAT = "%.0f"

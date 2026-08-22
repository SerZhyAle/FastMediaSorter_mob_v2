package com.sza.fastmediasorter.ui.networkmonitor.helpers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import timber.log.Timber

/**
 * S1853: lets the user start a chart's observation window over without disturbing what feeds it.
 *
 * A reset is delivered as an event merged into the sample stream rather than as a re-subscription. The
 * Bluetooth sampler opens a real GATT connection when its flow is collected and releases it when collection
 * ends, so re-subscribing would tear the radio link down and rebuild it - seconds of dead chart where the
 * user asked for an instant restart. Merging costs the source nothing.
 *
 * The key exists for the Mobile subscreen, which draws one chart per SIM from a single flow: a reset names
 * the chart it belongs to, so restarting one SIM's window leaves the other's history alone.
 *
 * One manager per ViewModel, held for the screen's lifetime; it owns no work and nothing to release.
 */
class ChartWindowResetManager {

    private val _resets = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** The reset requests, keyed by chart. */
    val resets: Flow<String> = _resets.asSharedFlow()

    /** Asks the window of [chartKey] to start over. */
    fun reset(chartKey: String) {
        Timber.d("S1853: chart reset requested, key=%s", chartKey)
        _resets.tryEmit(chartKey)
    }

    companion object {

        /** The key a section with a single chart uses, so it does not have to invent one. */
        const val SINGLE_CHART = "single"

        /** The key of the chart drawn for SIM [slotIndex]. */
        fun simChart(slotIndex: Int): String = "sim-$slotIndex"
    }
}

/** S1853: one thing a charted section folds into its window - a reading, or a request to start over. */
sealed interface ChartWindowEvent<out T> {

    /** A new reading from the section's own source. */
    data class Sample<T>(val reading: T) : ChartWindowEvent<T>

    /** The user tapped the chart named by [chartKey]. */
    data class Reset(val chartKey: String) : ChartWindowEvent<Nothing>
}

/**
 * Merges [resets] into this stream of readings.
 *
 * The fold that consumes the result decides what a reset means for its own window shape - an empty series
 * here, an empty per-SIM map there - which is why this returns events rather than folding them itself.
 */
fun <T> Flow<T>.withChartResets(resets: Flow<String>): Flow<ChartWindowEvent<T>> = merge(
    map { reading -> ChartWindowEvent.Sample(reading) },
    resets.map { chartKey -> ChartWindowEvent.Reset(chartKey) },
)

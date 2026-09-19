package com.sza.fastmediasorter.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures the browse list while it is being driven, not while it renders.
 *
 * S3322: the navigation benchmarks open Browse and stop there, so the only automated instrument that
 * sees main-thread cost never touches the list - the exact class of stall S3072, S3282 and S3319 each
 * traced to bind-time decode checks and per-item RecyclerView updates during selection.
 *
 * Each journey opens the screen inside `setupBlock`, so the measured window holds the interaction
 * alone and does not re-measure the screen open `NavigationBenchmarks.browseReadiness` already covers.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BrowseInteractionBenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun browseListFling() {
        benchmarkRule.measureRepeated(
            packageName = BenchmarkAppSetup.TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = BenchmarkAppSetup.DEFAULT_ITERATIONS,
            setupBlock = { prepareBrowseList() }
        ) {
            with(BenchmarkJourneys) { flingBrowseList(FLING_PASSES) }
        }
    }

    @Test
    fun browseSelectAll() {
        benchmarkRule.measureRepeated(
            packageName = BenchmarkAppSetup.TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = BenchmarkAppSetup.DEFAULT_ITERATIONS,
            setupBlock = { prepareBrowseList() }
        ) {
            with(BenchmarkJourneys) { selectAllThenDeselect() }
        }
    }

    @Test
    fun browseDragSelect() {
        benchmarkRule.measureRepeated(
            packageName = BenchmarkAppSetup.TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = BenchmarkAppSetup.DEFAULT_ITERATIONS,
            setupBlock = { prepareBrowseList() }
        ) {
            with(BenchmarkJourneys) { dragSelectBrowseList() }
        }
    }

    private companion object {
        const val FLING_PASSES = 3

        fun MacrobenchmarkScope.prepareBrowseList() {
            pressHome()
            with(BenchmarkAppSetup) { prepareAppState() }
            with(BenchmarkJourneys) {
                openBrowseJourney()
                requireBrowseListRows(MIN_INTERACTION_ROWS)
            }
        }
    }
}

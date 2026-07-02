package com.sza.fastmediasorter.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
@OptIn(ExperimentalMetricApi::class)
class NavigationBenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun browseReadiness() {
        benchmarkRule.measureRepeated(
            packageName = BenchmarkAppSetup.TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                TraceSectionMetric("FMS_BROWSE_READY"),
            ),
            iterations = BenchmarkAppSetup.DEFAULT_ITERATIONS,
            setupBlock = {
                pressHome()
                with(BenchmarkAppSetup) { prepareAppState() }
            }
        ) {
            with(BenchmarkJourneys) { openBrowseJourney() }
        }
    }

    @Test
    fun playerReadiness() {
        benchmarkRule.measureRepeated(
            packageName = BenchmarkAppSetup.TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                TraceSectionMetric("FMS_PLAYER_READY"),
            ),
            iterations = BenchmarkAppSetup.DEFAULT_ITERATIONS,
            setupBlock = {
                pressHome()
                with(BenchmarkAppSetup) { prepareAppState() }
            }
        ) {
            with(BenchmarkJourneys) { openPlayerJourney() }
        }
    }

    @Test
    fun playerBackNavigation() {
        benchmarkRule.measureRepeated(
            packageName = BenchmarkAppSetup.TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                TraceSectionMetric("FMS_PLAYER_BACK_NAVIGATION"),
            ),
            iterations = BenchmarkAppSetup.DEFAULT_ITERATIONS,
            setupBlock = {
                pressHome()
                with(BenchmarkAppSetup) { prepareAppState() }
                with(BenchmarkJourneys) { openPlayerJourney() }
            }
        ) {
            with(BenchmarkJourneys) { returnToBrowseFromPlayer() }
        }
    }
}

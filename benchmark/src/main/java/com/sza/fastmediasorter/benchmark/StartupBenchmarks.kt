package com.sza.fastmediasorter.benchmark

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupBenchmarks {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = BenchmarkAppSetup.TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = BenchmarkAppSetup.DEFAULT_ITERATIONS,
            startupMode = StartupMode.COLD,
            setupBlock = {
                pressHome()
                with(BenchmarkAppSetup) { prepareAppState() }
            }
        ) {
            startActivityAndWait()
        }
    }
}

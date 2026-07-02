package com.sza.fastmediasorter.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun collect() {
        baselineProfileRule.collect(BenchmarkAppSetup.TARGET_PACKAGE) {
            pressHome()
            with(BenchmarkAppSetup) { prepareAppState() }
            startActivityAndWait()
            with(BenchmarkJourneys) {
                openBrowseJourney()
                openPlayerJourney()
                returnToBrowseFromPlayer()
            }
        }
    }
}

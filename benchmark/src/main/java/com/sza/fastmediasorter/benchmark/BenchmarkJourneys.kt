package com.sza.fastmediasorter.benchmark

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

object BenchmarkJourneys {
    private const val ACTION_OPEN_JOURNEY = "com.sza.fastmediasorter.perf.action.OPEN_JOURNEY"
    private const val ROUTE_ACTIVITY = "com.sza.fastmediasorter.perf.BenchmarkRouteActivity"
    private const val EXTRA_JOURNEY = "benchmark_journey"
    private const val JOURNEY_BROWSE = "browse"
    private const val JOURNEY_PLAYER = "player"
    private const val BROWSE_LIST_RES = "rvMediaFiles"
    private const val PLAYER_CONTAINER_RES = "mediaContentArea"
    private const val UI_TIMEOUT_MS = 10_000L

    fun MacrobenchmarkScope.openBrowseJourney() {
        startJourney(JOURNEY_BROWSE)
        waitForBrowseScreen()
    }

    fun MacrobenchmarkScope.openPlayerJourney() {
        startJourney(JOURNEY_PLAYER)
        waitForPlayerScreen()
    }

    fun MacrobenchmarkScope.returnToBrowseFromPlayer() {
        device.pressBack()
        waitForBrowseScreen()
    }

    fun MacrobenchmarkScope.waitForBrowseScreen() {
        waitForView(BROWSE_LIST_RES)
    }

    fun MacrobenchmarkScope.waitForPlayerScreen() {
        waitForView(PLAYER_CONTAINER_RES)
    }

    private fun MacrobenchmarkScope.startJourney(journey: String) {
        val intent = Intent(ACTION_OPEN_JOURNEY).apply {
            setClassName(BenchmarkAppSetup.TARGET_PACKAGE, ROUTE_ACTIVITY)
            putExtra(EXTRA_JOURNEY, journey)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivityAndWait(intent)
    }

    private fun MacrobenchmarkScope.waitForView(resId: String) {
        check(device.wait(Until.hasObject(By.res(BenchmarkAppSetup.TARGET_PACKAGE, resId)), UI_TIMEOUT_MS)) {
            "Timed out waiting for benchmark view '$resId'"
        }
    }
}

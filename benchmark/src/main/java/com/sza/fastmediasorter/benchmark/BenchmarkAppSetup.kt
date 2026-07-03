package com.sza.fastmediasorter.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope

object BenchmarkAppSetup {
    const val TARGET_PACKAGE = "com.sza.fastmediasorter"
    const val DEFAULT_ITERATIONS = 5

    private const val ACTION_PREPARE_APP = "com.sza.fastmediasorter.perf.action.PREPARE_APP"

    fun MacrobenchmarkScope.prepareAppState() {
        device.executeShellCommand(
            "am broadcast -W -a $ACTION_PREPARE_APP -p $TARGET_PACKAGE"
        )
        device.waitForIdle()
    }
}

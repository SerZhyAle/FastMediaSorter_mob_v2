package com.sza.fastmediasorter.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope

object BenchmarkAppSetup {
    const val TARGET_PACKAGE = "com.sza.fastmediasorter"
    const val DEFAULT_ITERATIONS = 5

    private const val ACTION_PREPARE_APP = "com.sza.fastmediasorter.perf.action.PREPARE_APP"

    // A fresh install holds no media grant, and MediaStore answers an ungranted caller with zero
    // rows and no error - the resource provisioning then succeeds while finding nothing, and the
    // journey times out on an empty browse list with nothing in the log to say why.
    private val MEDIA_PERMISSIONS = listOf(
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.READ_EXTERNAL_STORAGE"
    )

    fun MacrobenchmarkScope.prepareAppState() {
        grantMediaPermissions()
        // The macrobenchmark force-stops the target between iterations, and a broadcast is not
        // delivered to a stopped package without this flag - the setup receiver simply never runs,
        // leaving the route activity with no resource to open.
        device.executeShellCommand(
            "am broadcast -W --include-stopped-packages -a $ACTION_PREPARE_APP -p $TARGET_PACKAGE"
        )
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.grantMediaPermissions() {
        MEDIA_PERMISSIONS.forEach { permission ->
            device.executeShellCommand("pm grant $TARGET_PACKAGE $permission")
        }
    }
}

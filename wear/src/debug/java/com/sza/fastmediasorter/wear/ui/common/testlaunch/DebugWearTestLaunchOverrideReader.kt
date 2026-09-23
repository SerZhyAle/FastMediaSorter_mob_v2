package com.sza.fastmediasorter.wear.ui.common.testlaunch

import android.content.Intent
import android.os.BadParcelableException
import com.sza.fastmediasorter.wear.domain.model.WearGeometryMode
import timber.log.Timber
import javax.inject.Inject

/** String extra: `ORIGINAL` or `STORE`, any case. */
const val EXTRA_TEST_GEOMETRY = "fms_test_geometry"

/** Int extra: the shorter screen edge in dp, e.g. 192 or 227 for the two Play review emulators. */
const val EXTRA_TEST_SCREEN_DP = "fms_test_screen_dp"

/** Below this nothing on the watch lays out at all; above it no watch exists. */
private val SCREEN_DP_RANGE = 120..400

/**
 * S3201: the debug build's reader of one-launch test parameters.
 *
 * Sent by `scripts/devtest/adb.ps1 launch -GeometryMode <mode> -ScreenDp <dp>`.
 */
class DebugWearTestLaunchOverrideReader @Inject constructor() : WearTestLaunchOverrideReader {

    override fun read(intent: Intent): WearTestLaunchOverride? = try {
        val screenDp = intent.takeIf { it.hasExtra(EXTRA_TEST_SCREEN_DP) }?.getIntExtra(EXTRA_TEST_SCREEN_DP, 0)
        val override = parseWearTestLaunchOverride(intent.getStringExtra(EXTRA_TEST_GEOMETRY), screenDp)
        override
    } catch (e: BadParcelableException) {
        // The Activity is exported; a malformed bundle from another app is a plain launch, not a crash.
        Timber.w(e, "Unreadable test launch extras - treating as a plain launch")
        null
    }
}

/** The parsing half, free of [Intent] so a JVM test can reach it. Invalid values are dropped, not guessed. */
internal fun parseWearTestLaunchOverride(geometry: String?, screenDp: Int?): WearTestLaunchOverride? {
    val mode = geometry?.let { raw ->
        WearGeometryMode.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
    if (geometry != null && mode == null) {
        Timber.w("Ignoring unknown test geometry '%s'", geometry)
    }
    val dp = screenDp?.takeIf { it in SCREEN_DP_RANGE }
    if (screenDp != null && dp == null) {
        Timber.w("Ignoring test screen size %d dp outside %s", screenDp, SCREEN_DP_RANGE)
    }
    return if (mode == null && dp == null) {
        null
    } else {
        WearTestLaunchOverride(mode, dp)
    }
}

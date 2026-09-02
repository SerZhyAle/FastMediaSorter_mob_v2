package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import com.sza.fastmediasorter.widget.CameraQuickCaptureWidgetProvider
import com.sza.fastmediasorter.widget.LauncherWidgetToken
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S2217: the launcher reset clears whatever the deleted cells pointed at, one target at a time, so
 * the only thing standing between it and a same-named widget on the system home screen is the
 * launcher-token range check. This pins the pair the strategic risk table carries: a system id is
 * left alone, a launcher-minted token is cleared.
 */
@Suppress("FunctionNaming") // backtick test names, project convention
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfigurableWidgetCatalogTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun `clearInstance leaves a system widget id alone and clears a launcher token`() {
        val prefs = context.getSharedPreferences(CameraQuickCaptureWidgetProvider.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(CameraQuickCaptureWidgetProvider.keyTargetId(SYSTEM_ID), FIRST_VALUE)
            .putInt(CameraQuickCaptureWidgetProvider.keyTargetId(LAUNCHER_TOKEN), SECOND_VALUE)
            .apply()

        ConfigurableWidgetCatalog.clearInstance(context, LauncherGadgetRegistry.KEY_CAMERA_QUICK_CAPTURE, SYSTEM_ID)

        assertEquals(
            "a system home-screen widget must survive the launcher reset",
            FIRST_VALUE,
            prefs.getInt(CameraQuickCaptureWidgetProvider.keyTargetId(SYSTEM_ID), MISSING),
        )

        ConfigurableWidgetCatalog.clearInstance(
            context,
            LauncherGadgetRegistry.KEY_CAMERA_QUICK_CAPTURE,
            LAUNCHER_TOKEN
        )

        assertEquals(
            "a launcher cell's own instance must be gone after the reset",
            MISSING,
            prefs.getInt(CameraQuickCaptureWidgetProvider.keyTargetId(LAUNCHER_TOKEN), MISSING),
        )
    }

    private companion object {
        /** A plausible platform appWidgetId: positive, minted by AppWidgetManager. */
        const val SYSTEM_ID = 25

        /** Inside the reserved negative range the launcher mints its own tokens in. */
        const val LAUNCHER_TOKEN = LauncherWidgetToken.MAX_TOKEN - 1

        const val FIRST_VALUE = 1
        const val SECOND_VALUE = 2
        const val MISSING = -1
    }
}

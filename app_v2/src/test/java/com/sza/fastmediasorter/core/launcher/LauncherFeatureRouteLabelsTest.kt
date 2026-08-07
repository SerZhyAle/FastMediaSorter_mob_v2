package com.sza.fastmediasorter.core.launcher

import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Keeps two distinct capture flows distinguishable in the launcher feature picker. */
@Suppress("FunctionNaming") // backtick test names, project convention
class LauncherFeatureRouteLabelsTest {

    @Test
    fun `quick capture and camera launch use distinct labels`() {
        val quickCapture = requireNotNull(InternalRouteCatalog.byKey(InternalRouteCatalog.KEY_QUICK_CAMERA))
        val cameraLaunch = requireNotNull(InternalRouteCatalog.byKey(InternalRouteCatalog.KEY_CAMERA_LAUNCH))

        assertNotEquals(quickCapture.labelRes, cameraLaunch.labelRes)
    }
}

package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.os.Looper
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * S2733: the desktop stops a folded section's gadgets by dropping their cells from the render plan,
 * so the only thing that actually ends their work is [LauncherGadgetView] cancelling on detach. The
 * geometry half of that chain is pinned by LauncherSectionCollapseTest and LauncherGridGeometryTest;
 * this pins the last link, which until now rested on KDoc alone - moving a gadget's work out of
 * onActive would keep every other test green while invisible gadgets kept updating.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs a pin.
class LauncherGadgetViewLifecycleTest {

    /** Reports whether its onActive body is currently running, which is what detach must end. */
    private class ProbeGadgetView(context: Context) : LauncherGadgetView(context) {
        var running = false
            private set

        override suspend fun CoroutineScope.onActive() {
            running = true
            try {
                awaitCancellation()
            } finally {
                running = false
            }
        }
    }

    @Test
    fun `gadget work stops when the view leaves the window and restarts when it returns`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val container = FrameLayout(activity)
        activity.setContentView(container)
        val gadget = ProbeGadgetView(activity)

        container.addView(gadget)
        idleMainLooper()
        assertTrue("onActive did not start when the gadget was attached", gadget.running)

        // What a fold does: LauncherCellViewBinder.bind removes every child and re-adds only the
        // cells the render plan kept, so a folded section's gadget is detached and never returns.
        container.removeAllViews()
        idleMainLooper()
        assertFalse("onActive kept running after the gadget left the window", gadget.running)

        container.addView(gadget)
        idleMainLooper()
        assertTrue("onActive did not restart when the section was expanded again", gadget.running)
    }

    private fun idleMainLooper() = shadowOf(Looper.getMainLooper()).idle()
}

private const val ROBOLECTRIC_SDK = 34

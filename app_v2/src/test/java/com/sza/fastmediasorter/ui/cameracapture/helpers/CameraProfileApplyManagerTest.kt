package com.sza.fastmediasorter.ui.cameracapture.helpers

import android.hardware.camera2.CameraMetadata
import android.util.Range
import com.sza.fastmediasorter.ui.cameracapture.model.CameraRuntimeCapabilities
import com.sza.fastmediasorter.ui.cameracapture.model.PhotoProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S1262: the profile state machine, driven through a recording fake of its session primitives.
 *
 * The invariant under test is exclusivity: whatever a profile turned on is turned off before the
 * next one is applied, so the camera never holds two half-applied recipes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraProfileApplyManagerTest {

    private class RecordingActions : CameraProfileApplyManager.Actions {
        val calls = mutableListOf<String>()
        override fun setNightMode(enabled: Boolean) { calls += "night=$enabled" }
        override fun setBokeh(enabled: Boolean) { calls += "bokeh=$enabled" }
        override fun setSport(enabled: Boolean) { calls += "sport=$enabled" }
        override fun setMacro(enabled: Boolean) { calls += "macro=$enabled" }
        override fun switchToFrontLens() { calls += "frontLens" }
        override fun switchToMainBackLens() { calls += "backLens" }
    }

    private val actions = RecordingActions()
    private val manager = CameraProfileApplyManager(actions)

    private fun fullyCapable() = CameraRuntimeCapabilities(
        availableLensFacings = listOf(
            CameraMetadata.LENS_FACING_BACK,
            CameraMetadata.LENS_FACING_FRONT,
        ),
        supportsNightMode = true,
        supportsBokehExtension = true,
        supportsMacro = true,
        supportsManualSensor = true,
        isoRange = Range(50, 3200),
        shutterRangeNs = Range(1_000_000L, 100_000_000L),
    )

    @Test
    fun `starts on normal`() {
        assertEquals(PhotoProfile.NORMAL, manager.activeProfile)
        assertTrue(actions.calls.isEmpty())
    }

    @Test
    fun `applying portrait after night clears night first`() {
        manager.apply(PhotoProfile.NIGHT)
        actions.calls.clear()

        manager.apply(PhotoProfile.PORTRAIT)

        // The clear sweep runs before the new recipe, so night=false precedes bokeh=true.
        assertTrue(actions.calls.indexOf("night=false") < actions.calls.indexOf("bokeh=true"))
        assertEquals(PhotoProfile.PORTRAIT, manager.activeProfile)
    }

    @Test
    fun `re-selecting the active profile resets to normal`() {
        manager.apply(PhotoProfile.SPORT)
        assertEquals(PhotoProfile.SPORT, manager.activeProfile)
        actions.calls.clear()

        manager.apply(PhotoProfile.SPORT)

        assertEquals(PhotoProfile.NORMAL, manager.activeProfile)
        assertTrue("sport=false" in actions.calls)
    }

    @Test
    fun `macro applies its primitive exactly once, with no toggle bounce`() {
        manager.apply(PhotoProfile.MACRO)

        assertEquals(1, actions.calls.count { it == "macro=true" })
        assertEquals(PhotoProfile.MACRO, manager.activeProfile)
    }

    @Test
    fun `leaving macro does not fight its own lens restore`() {
        manager.apply(PhotoProfile.MACRO)
        actions.calls.clear()

        manager.resetToNormal("test")

        assertTrue("macro=false" in actions.calls)
        // S1189: the macro primitive restores the pre-macro lens itself.
        assertTrue("backLens" !in actions.calls)
    }

    @Test
    fun `leaving selfie returns the lens`() {
        manager.apply(PhotoProfile.SELFIE)
        assertTrue("frontLens" in actions.calls)
        actions.calls.clear()

        manager.resetToNormal("test")

        assertTrue("backLens" in actions.calls)
    }

    @Test
    fun `reconcile drops a profile the bound lens cannot honour`() {
        manager.apply(PhotoProfile.PORTRAIT)

        manager.reconcile(fullyCapable().copy(supportsBokehExtension = false))

        assertEquals(PhotoProfile.NORMAL, manager.activeProfile)
    }

    @Test
    fun `reconcile drops the profile without replaying its clear sweep`() {
        manager.apply(PhotoProfile.SELFIE)
        actions.calls.clear()

        manager.reconcile(fullyCapable().copy(availableLensFacings = emptyList()))

        // The rebind that changed availability already reset the session; replaying the sweep here
        // would rebind a second time from inside the callback reporting the first one.
        assertTrue(actions.calls.isEmpty())
    }

    @Test
    fun `releasing without clearing leaves the session untouched`() {
        manager.apply(PhotoProfile.NIGHT)
        actions.calls.clear()

        manager.releaseWithoutClearing("manual lens switch")

        assertEquals(PhotoProfile.NORMAL, manager.activeProfile)
        assertTrue(actions.calls.isEmpty())
    }

    @Test
    fun `reconcile keeps a profile the bound lens still honours`() {
        manager.apply(PhotoProfile.NIGHT)

        manager.reconcile(fullyCapable())

        assertEquals(PhotoProfile.NIGHT, manager.activeProfile)
    }

    @Test
    fun `reset from normal is a no-op`() {
        manager.resetToNormal("test")

        assertTrue(actions.calls.isEmpty())
    }
}

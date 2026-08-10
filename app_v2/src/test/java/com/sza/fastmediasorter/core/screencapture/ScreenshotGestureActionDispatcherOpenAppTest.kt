package com.sza.fastmediasorter.core.screencapture

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.ScreenshotGestureAction
import com.sza.fastmediasorter.domain.model.ScreenshotGestureDirection
import com.sza.fastmediasorter.domain.model.ScreenshotGestureSettings
import com.sza.fastmediasorter.domain.model.ScreenshotGestureZone
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.Lazy
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1036 Phase 01: the three outcomes of the OPEN_APP branch. The branch changes behaviour with no
 * screen of its own, so a regression here is invisible until a user reports a dead gesture.
 *
 * Intents are mocked rather than constructed, which keeps the class off Robolectric - the dispatcher
 * only forwards the intent the PackageManager hands it, so identity is the whole assertion.
 */
class ScreenshotGestureActionDispatcherOpenAppTest {

    private val ownIntent = mockk<Intent>(relaxed = true)
    private val chosenIntent = mockk<Intent>(relaxed = true)
    private val packageManager = mockk<PackageManager>()
    private val context = mockk<Context>(relaxed = true)

    init {
        every { context.packageName } returns OWN_PACKAGE
        every { context.packageManager } returns packageManager
        every { packageManager.getLaunchIntentForPackage(OWN_PACKAGE) } returns ownIntent
        every { packageManager.getLaunchIntentForPackage(CHOSEN_PACKAGE) } returns chosenIntent
        every { packageManager.getLaunchIntentForPackage(REMOVED_PACKAGE) } returns null
    }

    @Test
    fun `blank payload opens the app itself`() = runTest {
        val handled = openApp(payload = "")

        assertTrue(handled)
        assertEquals(ownIntent, startedIntent())
    }

    @Test
    fun `chosen package with a launch intent is started`() = runTest {
        val handled = openApp(payload = CHOSEN_PACKAGE)

        assertTrue(handled)
        assertEquals(chosenIntent, startedIntent())
    }

    @Test
    fun `removed package falls back to opening the app itself`() = runTest {
        val handled = openApp(payload = REMOVED_PACKAGE)

        assertTrue(handled)
        assertEquals(ownIntent, startedIntent())
    }

    private suspend fun openApp(payload: String): Boolean = dispatcherWith(payload).handlePreCaptureAction(
        context,
        ScreenshotGestureAction.OPEN_APP,
        ScreenshotGestureZone.RIGHT_BOTTOM,
        ScreenshotGestureDirection.UP,
    )

    private fun startedIntent(): Intent {
        val started = slot<Intent>()
        verify { context.startActivity(capture(started)) }
        return started.captured
    }

    private fun dispatcherWith(payload: String): ScreenshotGestureActionDispatcher {
        val settings = AppSettings(
            screenshotGesture = ScreenshotGestureSettings(payloadRightBottomUp = payload),
        )
        val repository = mockk<SettingsRepository>()
        every { repository.getSettings() } returns flowOf(settings)
        return ScreenshotGestureActionDispatcher(
            settingsRepository = Lazy { repository },
            capabilityAvailability = mockk(relaxed = true),
            deviceActionHandler = mockk(relaxed = true),
            mediaActionHandler = mockk(relaxed = true),
            launchActionHandler = mockk(relaxed = true),
            accessibilityActions = emptySet(),
        )
    }

    private companion object {
        const val OWN_PACKAGE = "com.sza.fastmediasorter"
        const val CHOSEN_PACKAGE = "com.example.messenger"
        const val REMOVED_PACKAGE = "com.example.removed"
    }
}

package com.sza.fastmediasorter.ui.main.helpers

import android.app.Activity
import android.content.Intent
import com.sza.fastmediasorter.core.launcher.LauncherStartWindowManager
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MainStartWindowRedirectManagerTest {
    private val activity = mockk<Activity>(relaxed = true)
    private val contract = mockk<LauncherModeContract>()
    private val startWindowManager = mockk<LauncherStartWindowManager>()
    private val launcherIntent = mockk<Intent>()

    private lateinit var manager: MainStartWindowRedirectManager

    @Before
    fun setUp() {
        every { launcherIntent.action } returns Intent.ACTION_MAIN
        every { launcherIntent.data } returns null
        every { launcherIntent.extras } returns null
        every { startWindowManager.isEnabled() } returns true
        manager = MainStartWindowRedirectManager(contract, startWindowManager) { false }
    }

    @Test
    fun `enabled ordinary cold launch opens the app-only desktop`() {
        val startWindowIntent = mockk<Intent>()
        every { contract.startWindowIntent(activity) } returns startWindowIntent

        assertTrue(manager.redirectIfRequested(activity, launcherIntent, null, false))

        verify { activity.startActivity(startWindowIntent) }
        verify { activity.finish() }
    }

    @Test
    fun `disabled setting keeps an ordinary launch on the main window`() {
        every { startWindowManager.isEnabled() } returns false

        assertFalse(manager.redirectIfRequested(activity, launcherIntent, null, false))

        verify(exactly = 0) { contract.startWindowIntent(any()) }
        verify(exactly = 0) { activity.startActivity(any()) }
    }

    @Test
    fun `explicit destination bypasses the desktop redirect`() {
        every { launcherIntent.action } returns Intent.ACTION_VIEW

        assertFalse(manager.redirectIfRequested(activity, launcherIntent, null, false))

        verify(exactly = 0) { contract.startWindowIntent(any()) }
        verify(exactly = 0) { activity.startActivity(any()) }
    }
}

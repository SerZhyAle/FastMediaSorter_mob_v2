package com.sza.fastmediasorter.core.launcher

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LauncherStartWindowManagerTest {

    private val context = mockk<Context>()
    private val contract = mockk<LauncherModeContract>()
    private val preferences = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>()

    private lateinit var manager: LauncherStartWindowManager

    @Before
    fun setUp() {
        every { context.getSharedPreferences(any(), any()) } returns preferences
        every { preferences.edit() } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } just Runs
        manager = LauncherStartWindowManager(context, contract)
    }

    @Test
    fun `enabled defaults true when the launcher surface is available`() {
        every { contract.isAvailableInBuild } returns true
        every { preferences.getBoolean(LauncherStartWindowManager.KEY_START_WINDOW_ENABLED, true) } returns true

        assertTrue(manager.isEnabled())
    }

    @Test
    fun `unavailable build neither reads nor writes the preference`() {
        every { contract.isAvailableInBuild } returns false

        assertFalse(manager.isEnabled())
        manager.setEnabled(true)

        verify(exactly = 0) { preferences.getBoolean(any(), any()) }
        verify(exactly = 0) { preferences.edit() }
    }

    @Test
    fun `setting enabled persists the selected value`() {
        every { contract.isAvailableInBuild } returns true

        manager.setEnabled(false)

        verify { editor.putBoolean(LauncherStartWindowManager.KEY_START_WINDOW_ENABLED, false) }
        verify { editor.apply() }
    }
}

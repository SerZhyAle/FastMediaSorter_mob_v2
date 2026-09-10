package com.sza.fastmediasorter.core.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2811: the default-on flag and its inertness on builds without the desktop surface are value
 * contracts - a grep can see the literal, only a run can see which branch answers.
 */
class LauncherStartWindowManagerTest {

    private class FakeContract(override val isAvailableInBuild: Boolean) : LauncherModeContract {
        override fun homeComponent(context: Context): ComponentName? = null
        override fun openAllApps(context: Context): Boolean = false
        override fun startWindowIntent(context: Context): Intent? = null
    }

    private class FakePreferences : SharedPreferences by mockk(relaxed = true) {
        val values = mutableMapOf<String, Boolean>()

        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] ?: defValue

        override fun edit(): SharedPreferences.Editor {
            val editor = mockk<SharedPreferences.Editor>(relaxed = true)
            every { editor.putBoolean(any(), any()) } answers {
                values[firstArg()] = secondArg()
                editor
            }
            return editor
        }
    }

    private fun manager(available: Boolean, prefs: SharedPreferences): LauncherStartWindowManager {
        val context = mockk<Context>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return LauncherStartWindowManager(context, FakeContract(available))
    }

    @Test
    fun `unwritten key reads as enabled`() {
        assertTrue(manager(available = true, prefs = FakePreferences()).isEnabled())
    }

    @Test
    fun `written false reads as disabled`() {
        val prefs = FakePreferences()
        val manager = manager(available = true, prefs = prefs)

        manager.setEnabled(false)

        assertFalse(manager.isEnabled())
    }

    @Test
    fun `build without the desktop surface reports disabled and stores nothing`() {
        val prefs = FakePreferences()
        val manager = manager(available = false, prefs = prefs)

        manager.setEnabled(true)

        assertFalse(manager.isEnabled())
        assertTrue(prefs.values.isEmpty())
    }
}

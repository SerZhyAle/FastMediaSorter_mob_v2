package com.sza.fastmediasorter.ui.launcher.signal

import com.sza.fastmediasorter.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * S1465 phase 01: the signal model can name either of its two icon sources, and a foreign signal draws last.
 */
@Suppress("FunctionNaming") // backtick test names, project convention
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SignalIconSourceTest {

    @Test
    fun `a signal built with a resource icon reports the resource source`() {
        val signal = signal(LauncherSignalIcon.Resource(R.drawable.ic_music_note))

        val icon = signal.icon as LauncherSignalIcon.Resource

        assertEquals(R.drawable.ic_music_note, icon.res)
    }

    @Test
    fun `a signal built with a package icon reports the application source`() {
        val signal = signal(
            LauncherSignalIcon.Application(packageName = ABSENT_PACKAGE, fallbackRes = R.drawable.ic_apps),
        )

        val icon = signal.icon as LauncherSignalIcon.Application

        assertEquals(ABSENT_PACKAGE, icon.packageName)
    }

    /**
     * The strip's order is this enum's declaration order, so the owner's decision of 2026-08-17 - foreign
     * notifications never push this app's own signals out of a full row - is exactly this assertion.
     */
    @Test
    fun `the foreign kind sorts after every other kind`() {
        val foreign = LauncherSignalKind.FOREIGN_NOTIFICATION

        val others = LauncherSignalKind.entries.filter { it != foreign }

        assertTrue(others.isNotEmpty())
        assertTrue(others.all { it.ordinal < foreign.ordinal })
    }

    @Test
    fun `an icon whose package is not installed resolves to the fallback instead of throwing`() {
        val resolved = LauncherSignalIconBinder.resolve(
            RuntimeEnvironment.getApplication(),
            LauncherSignalIcon.Application(packageName = ABSENT_PACKAGE, fallbackRes = R.drawable.ic_apps),
        )

        assertEquals(LauncherSignalIconBinder.Resolved.FromResource(R.drawable.ic_apps), resolved)
    }

    @Test
    fun `a resource icon resolves to that resource`() {
        val resolved = LauncherSignalIconBinder.resolve(
            RuntimeEnvironment.getApplication(),
            LauncherSignalIcon.Resource(R.drawable.ic_copy),
        )

        assertEquals(LauncherSignalIconBinder.Resolved.FromResource(R.drawable.ic_copy), resolved)
    }

    private fun signal(icon: LauncherSignalIcon) = LauncherSignal(
        id = "test",
        kind = LauncherSignalKind.FOREIGN_NOTIFICATION,
        icon = icon,
        label = "test",
    )

    private companion object {
        const val ABSENT_PACKAGE = "com.example.definitely.not.installed"
    }
}

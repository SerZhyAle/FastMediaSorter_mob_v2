package com.sza.fastmediasorter.ui.launcher.dimclock

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.sza.fastmediasorter.core.panel.OsShortcutCatalog
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimStatusChip
import com.sza.fastmediasorter.ui.launcher.signal.source.ForeignNotificationSignalSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * S3366 phase 01: the dim chip adapters - the real-icon resolver and the tap router - keep the
 * strip's rules on the dim screen without a rendered view.
 */
@Suppress("FunctionNaming") // backtick test names, project convention
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherDimChipAdaptersTest {

    // Robolectric bootstraps a fresh application per test, so this is per-instance, never a companion
    // constant - a class-load-time initializer would bind the first test's app and stale it for the rest.
    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `an icon for an absent package resolves to null instead of throwing`() = runTest {
        val loader = LauncherDimChipIconLoader(application)

        val drawable = loader.load(ABSENT_PACKAGE)

        assertNull(drawable)
    }

    @Test
    fun `the battery box tap starts the system battery usage screen`() {
        val router = LauncherDimChipActionRouter(application)

        router.openBatteryUsage()

        assertEquals(
            Intent.ACTION_POWER_USAGE_SUMMARY,
            shadowOf(application).nextStartedActivity.action,
        )
    }

    @Test
    fun `a notification chip for the application itself starts its launch intent`() {
        val router = LauncherDimChipActionRouter(application)
        val ownId = ForeignNotificationSignalSource.SIGNAL_ID_PREFIX + application.packageName

        router.openChip(ownNotificationChip(ownId))

        assertEquals(
            application.packageName,
            shadowOf(application).nextStartedActivity.`package`,
        )
    }

    @Test
    fun `a notification chip whose package is gone starts nothing and does not crash`() {
        val router = LauncherDimChipActionRouter(application)
        val shadow = shadowOf(application)
        val before = shadow.nextStartedActivity

        router.openChip(ownNotificationChip(ForeignNotificationSignalSource.SIGNAL_ID_PREFIX + ABSENT_PACKAGE))

        assertEquals(before, shadow.nextStartedActivity)
    }

    @Test
    fun `a status chip with an unknown id starts nothing`() {
        val router = LauncherDimChipActionRouter(application)
        val shadow = shadowOf(application)
        val before = shadow.nextStartedActivity

        router.openChip(DimStatusChip(id = "dim-status:unknown", isNotification = false))

        assertEquals(before, shadow.nextStartedActivity)
    }

    @Test
    fun `the catalog battery target really is the battery usage screen`() {
        val intent = OsShortcutCatalog.byKey(OsShortcutCatalog.KEY_BATTERY)?.intent(application)

        assertEquals(Intent.ACTION_POWER_USAGE_SUMMARY, intent?.action)
    }

    private fun ownNotificationChip(id: String) = DimStatusChip(
        id = id,
        isNotification = true,
        contentDescription = "test",
    )

    private companion object {
        const val ABSENT_PACKAGE = "com.example.definitely.not.installed"
        const val INSTALLED_PACKAGE = "com.example.installed.for.test"
    }
}

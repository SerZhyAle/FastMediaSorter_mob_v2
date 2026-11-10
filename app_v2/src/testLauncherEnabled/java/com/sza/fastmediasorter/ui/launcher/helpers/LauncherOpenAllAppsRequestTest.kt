package com.sza.fastmediasorter.ui.launcher.helpers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.sza.fastmediasorter.domain.launcher.LauncherModeContract
import com.sza.fastmediasorter.ui.launcher.LauncherHomeActivity
import com.sza.fastmediasorter.ui.launcher.LauncherStartWindowActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherOpenAllAppsRequestTest {

    @Test
    fun `shortcut action is consumed once`() {
        val intent = Intent(LauncherModeContract.ACTION_OPEN_ALL_APPS)

        assertTrue(LauncherOpenAllAppsRequest.consume(intent))
        assertFalse(LauncherOpenAllAppsRequest.consume(intent))
    }

    @Test
    fun `disabled home component falls back to the app window`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val home = ComponentName(context, LauncherHomeActivity::class.java)
        val packageManager = context.packageManager
        val previous = packageManager.getComponentEnabledSetting(home)
        try {
            packageManager.setComponentEnabledSetting(
                home,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            val intent = LauncherOpenAllAppsRequest.intent(context)
            assertEquals(LauncherStartWindowActivity::class.java.name, intent.component?.className)
            assertTrue(LauncherOpenAllAppsRequest.consume(intent))
            assertFalse(LauncherOpenAllAppsRequest.consume(intent))
        } finally {
            packageManager.setComponentEnabledSetting(home, previous, PackageManager.DONT_KILL_APP)
        }
    }
}

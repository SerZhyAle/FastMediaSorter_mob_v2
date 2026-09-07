package com.sza.fastmediasorter.core.apps

import android.content.Intent
import com.sza.fastmediasorter.domain.usecase.launcher.SyncInstalledAppShortcutUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstalledAppsChangeReceiverTest {

    @Test
    fun `package add is an install shortcut transition`() {
        assertEquals(
            SyncInstalledAppShortcutUseCase.Change.INSTALLED,
            InstalledAppsChangeReceiver.packageChange(Intent.ACTION_PACKAGE_ADDED, isReplacing = false),
        )
    }

    @Test
    fun `package removal is a removal shortcut transition`() {
        assertEquals(
            SyncInstalledAppShortcutUseCase.Change.REMOVED,
            InstalledAppsChangeReceiver.packageChange(Intent.ACTION_PACKAGE_REMOVED, isReplacing = false),
        )
    }

    @Test
    fun `package replacement does not alter shortcuts`() {
        assertNull(InstalledAppsChangeReceiver.packageChange(Intent.ACTION_PACKAGE_ADDED, isReplacing = true))
        assertNull(InstalledAppsChangeReceiver.packageChange(Intent.ACTION_PACKAGE_REMOVED, isReplacing = true))
        assertNull(InstalledAppsChangeReceiver.packageChange(Intent.ACTION_PACKAGE_REPLACED, isReplacing = false))
    }
}

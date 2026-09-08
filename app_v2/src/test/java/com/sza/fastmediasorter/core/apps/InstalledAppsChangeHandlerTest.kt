package com.sza.fastmediasorter.core.apps

import android.content.Intent
import com.sza.fastmediasorter.domain.usecase.launcher.SyncInstalledAppShortcutUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstalledAppsChangeHandlerTest {

    @Test
    fun `package add is an install shortcut transition`() {
        assertEquals(
            SyncInstalledAppShortcutUseCase.Change.INSTALLED,
            InstalledAppsChangeHandler.packageChange(Intent.ACTION_PACKAGE_ADDED, isReplacing = false),
        )
    }

    @Test
    fun `package removal is a removal shortcut transition`() {
        assertEquals(
            SyncInstalledAppShortcutUseCase.Change.REMOVED,
            InstalledAppsChangeHandler.packageChange(Intent.ACTION_PACKAGE_REMOVED, isReplacing = false),
        )
    }

    /** S2739: the only removal broadcast a manifest receiver actually gets on minSdk 26. */
    @Test
    fun `full package removal is a removal shortcut transition`() {
        assertEquals(
            SyncInstalledAppShortcutUseCase.Change.REMOVED,
            InstalledAppsChangeHandler.packageChange(
                Intent.ACTION_PACKAGE_FULLY_REMOVED,
                isReplacing = false,
            ),
        )
    }

    @Test
    fun `package replacement does not alter shortcuts`() {
        assertNull(InstalledAppsChangeHandler.packageChange(Intent.ACTION_PACKAGE_ADDED, isReplacing = true))
        assertNull(InstalledAppsChangeHandler.packageChange(Intent.ACTION_PACKAGE_REMOVED, isReplacing = true))
        assertNull(InstalledAppsChangeHandler.packageChange(Intent.ACTION_PACKAGE_REPLACED, isReplacing = false))
    }
}

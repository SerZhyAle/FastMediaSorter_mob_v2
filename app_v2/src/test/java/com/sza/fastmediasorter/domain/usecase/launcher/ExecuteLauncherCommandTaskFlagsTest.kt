package com.sza.fastmediasorter.domain.usecase.launcher

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.launcher.LauncherResourceMode
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * S2215: verifies task flag selection in [ExecuteLauncherCommandUseCase].
 * Internal targets get both FLAG_ACTIVITY_NEW_TASK and FLAG_ACTIVITY_MULTIPLE_TASK to prevent
 * joining MainActivity's task affinity, while external targets receive only FLAG_ACTIVITY_NEW_TASK.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExecuteLauncherCommandTaskFlagsTest {

    private val context get() = RuntimeEnvironment.getApplication()

    private val useCase get() = ExecuteLauncherCommandUseCase(
        context = context,
        resolveRouteAvailability = mockk(relaxed = true),
        streamSourceRepository = mockk(relaxed = true),
        journal = mockk(relaxed = true),
        appShortcutDataSource = mockk(relaxed = true),
        toggleRadioTarget = mockk(relaxed = true),
    )

    @Test
    fun `internal target receives both NEW_TASK and MULTIPLE_TASK flags`() = runTest {
        val command = LauncherCellCommand.Resource(resourceId = 1L, mode = LauncherResourceMode.BROWSE)
        val started = useCase.launch(command)

        assertTrue("launch should return true for internal resource", started)
        val nextIntent = shadowOf(context).nextStartedActivity
        val flags = nextIntent.flags
        assertEquals(context.packageName, nextIntent.component?.packageName)
        assertTrue(
            "internal target must have FLAG_ACTIVITY_NEW_TASK",
            (flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0,
        )
        assertTrue(
            "internal target must have FLAG_ACTIVITY_MULTIPLE_TASK",
            (flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK) != 0,
        )
    }

    @Test
    fun `external target receives NEW_TASK but not MULTIPLE_TASK`() = runTest {
        val foreignPackage = "com.example.externalapp"
        val queryIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(foreignPackage)
        }
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = foreignPackage
                name = "com.example.externalapp.MainActivity"
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(queryIntent, resolveInfo)

        val command = LauncherCellCommand.App(packageName = foreignPackage)
        val started = useCase.launch(command)

        assertTrue("launch should return true for valid external app", started)
        val nextIntent = shadowOf(context).nextStartedActivity
        val flags = nextIntent.flags
        assertEquals(foreignPackage, nextIntent.component?.packageName)
        assertTrue(
            "external target must have FLAG_ACTIVITY_NEW_TASK",
            (flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0,
        )
        assertFalse(
            "external target must NOT have FLAG_ACTIVITY_MULTIPLE_TASK",
            (flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK) != 0,
        )
    }
}

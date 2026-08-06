package com.sza.fastmediasorter.domain.usecase

import android.Manifest
import android.app.Activity
import android.os.Build
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.domain.model.PermissionEntry
import com.sza.fastmediasorter.domain.model.PermissionGroup
import com.sza.fastmediasorter.domain.model.PermissionStatus
import com.sza.fastmediasorter.domain.repository.PermissionRequestMarkerRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Covers the status matrix of [CheckPermissionStatusUseCase]. The interesting claim is that an
 * ungranted permission with no request marker is NOT_YET_REQUESTED whatever the platform answers
 * about the rationale, because before the first request that answer carries no information.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CheckPermissionStatusUseCaseTest {

    private val app = RuntimeEnvironment.getApplication()
    private val marker = FakeRequestMarkerRepository()
    private val useCase = CheckPermissionStatusUseCase(app, marker)

    /**
     * Robolectric ships no shadow for Environment.isExternalStorageManager(), and the real one indexes
     * an empty external-dirs array, so the special-permission readings are stubbed to keep the test
     * about the use case's branching rather than about the emulated storage layout.
     */
    @Before
    fun stubSpecialPermissionReadings() {
        mockkObject(PermissionHelper)
        every { PermissionHelper.hasAllFilesAccessPermission(any()) } returns false
        every { PermissionHelper.hasManageMediaPermission(any()) } returns false
    }

    @After
    fun releaseStubs() {
        unmockkObject(PermissionHelper)
    }

    @Test
    fun `granted permission reports GRANTED`() {
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        val entry = entryOf(Manifest.permission.CAMERA)

        assertEquals(PermissionStatus.GRANTED, useCase(activityWithRationale(false), entry))
    }

    @Test
    fun `ungranted permission without marker reports NOT_YET_REQUESTED whatever the rationale says`() {
        val entry = entryOf(Manifest.permission.RECORD_AUDIO)

        assertEquals(PermissionStatus.NOT_YET_REQUESTED, useCase(activityWithRationale(false), entry))
        assertEquals(PermissionStatus.NOT_YET_REQUESTED, useCase(activityWithRationale(true), entry))
    }

    @Test
    fun `ungranted permission with marker and rationale reports DENIED`() {
        val entry = entryOf(Manifest.permission.RECORD_AUDIO)
        marker.markRequested(entry.id)

        assertEquals(PermissionStatus.DENIED, useCase(activityWithRationale(true), entry))
    }

    @Test
    fun `ungranted permission with marker and no rationale reports PERMANENTLY_DENIED`() {
        val entry = entryOf(Manifest.permission.RECORD_AUDIO)
        marker.markRequested(entry.id)

        assertEquals(PermissionStatus.PERMANENTLY_DENIED, useCase(activityWithRationale(false), entry))
    }

    @Test
    fun `special permissions never report PERMANENTLY_DENIED`() {
        val specials = listOf(
            Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_MEDIA,
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        )
        specials.forEach { manifestName ->
            val entry = entryOf(manifestName)
            marker.markRequested(entry.id)

            assertNotEquals(
                "special permission $manifestName must not resolve through the rationale reading",
                PermissionStatus.PERMANENTLY_DENIED,
                useCase(activityWithRationale(false), entry),
            )
        }
    }

    @Test
    fun `entry outside its SDK window reports NOT_APPLICABLE`() {
        val entry = entryOf(Manifest.permission.RECORD_AUDIO).copy(minSdk = Build.VERSION.SDK_INT + 1)

        assertEquals(PermissionStatus.NOT_APPLICABLE, useCase(activityWithRationale(false), entry))
    }

    private fun entryOf(manifestName: String) = PermissionEntry(
        id = manifestName.substringAfterLast('.').lowercase(),
        manifestName = manifestName,
        titleRes = 0,
        descriptionRes = 0,
        group = PermissionGroup.STORAGE,
        optional = false,
    )

    private fun activityWithRationale(showRationale: Boolean): Activity {
        val activity = mockk<Activity>(relaxed = true)
        every { activity.shouldShowRequestPermissionRationale(any()) } returns showRationale
        return activity
    }
}

private class FakeRequestMarkerRepository : PermissionRequestMarkerRepository {

    private val requested = mutableSetOf<String>()

    override fun wasRequested(permissionId: String): Boolean = permissionId in requested

    override fun markRequested(permissionId: String) {
        requested += permissionId
    }
}

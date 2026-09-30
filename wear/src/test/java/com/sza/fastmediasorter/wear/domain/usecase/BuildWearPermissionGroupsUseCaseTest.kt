package com.sza.fastmediasorter.wear.domain.usecase

import android.Manifest
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.permission.WearPermissionGroup
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildWearPermissionGroupsUseCaseTest {

    private val recordingBuild: WearRestrictedCapabilities = mockk { every { offersVoiceRecording } returns true }

    private val useCase = BuildWearPermissionGroupsUseCase(BuildWearOnboardingStepsUseCase(recordingBuild))

    private val sideloadManifest = setOf(
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.VIBRATE,
    )

    @Test
    fun `the store build shows no rows, so the settings entry disappears with them`() {
        val rows = useCase(setOf(Manifest.permission.VIBRATE, Manifest.permission.WAKE_LOCK), API_33)

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `the sideload build shows the three groups the owner named, in catalog order`() {
        val rows = useCase(sideloadManifest, API_33)

        assertEquals(WearPermissionGroup.entries, rows.map { it.group })
    }

    @Test
    fun `heart rate and activity recognition are one sensors row`() {
        val sensors = useCase(sideloadManifest, API_33).single { it.group == WearPermissionGroup.SENSORS }

        assertEquals(
            listOf(Manifest.permission.BODY_SENSORS, Manifest.permission.ACTIVITY_RECOGNITION),
            sensors.permissions
        )
    }

    @Test
    fun `a group whose permissions this build does not declare is dropped`() {
        val rows = useCase(sideloadManifest - Manifest.permission.RECORD_AUDIO, API_33)

        assertTrue(rows.none { it.group == WearPermissionGroup.MICROPHONE })
    }

    private companion object {
        const val API_33 = 33
    }
}

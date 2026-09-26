package com.sza.fastmediasorter.wear.domain.usecase

import android.Manifest
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import com.sza.fastmediasorter.wear.domain.onboarding.WearOnboardingPermissionStep
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildWearOnboardingStepsUseCaseTest {

    private val useCase = BuildWearOnboardingStepsUseCase(capabilities(recording = true))

    private val storeUseCase = BuildWearOnboardingStepsUseCase(capabilities(recording = false))

    private val sideloadManifest = setOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.BODY_SENSORS,
        "android.permission.health.READ_HEART_RATE",
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.NEARBY_WIFI_DEVICES,
        Manifest.permission.VIBRATE,
    )

    @Test
    fun `a build that declares nothing sensitive walks no steps`() {
        assertTrue(useCase(setOf(Manifest.permission.VIBRATE, Manifest.permission.WAKE_LOCK), API_33).isEmpty())
    }

    @Test
    fun `the sideload build on API 33 asks every group, in catalog order`() {
        val steps = useCase(sideloadManifest, API_33)

        assertEquals(WearOnboardingPermissionStep.entries, steps.map { it.step })
        assertEquals(
            listOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_IMAGES,
            ),
            steps.first().permissions
        )
    }

    @Test
    fun `API 30 asks legacy storage and drops the groups the platform does not know yet`() {
        val steps = useCase(sideloadManifest, API_30)

        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), steps.first().permissions)
        assertTrue(steps.none { it.step == WearOnboardingPermissionStep.NOTIFICATIONS })
        assertTrue(steps.none { it.step == WearOnboardingPermissionStep.NEARBY_DEVICES })
    }

    @Test
    fun `API 36 asks the granular heart-rate permission only`() {
        val heartRate = useCase(sideloadManifest, API_36).single { it.step == WearOnboardingPermissionStep.HEART_RATE }

        assertEquals(listOf("android.permission.health.READ_HEART_RATE"), heartRate.permissions)
    }

    @Test
    fun `a recording build walks the notifications step`() {
        val steps = useCase(setOf(Manifest.permission.POST_NOTIFICATIONS), API_33)

        assertEquals(listOf(WearOnboardingPermissionStep.NOTIFICATIONS), steps.map { it.step })
    }

    @Test
    fun `the store build declares notifications for its stopwatch and still walks nothing`() {
        val storeManifest = setOf(
            Manifest.permission.VIBRATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.POST_NOTIFICATIONS,
        )

        assertTrue(storeUseCase(storeManifest, API_33).isEmpty())
    }

    @Test
    fun `a build without recording keeps every other declared group`() {
        val steps = storeUseCase(sideloadManifest, API_33)

        assertEquals(
            WearOnboardingPermissionStep.entries - WearOnboardingPermissionStep.NOTIFICATIONS,
            steps.map { it.step }
        )
    }

    private fun capabilities(recording: Boolean): WearRestrictedCapabilities =
        mockk { every { offersVoiceRecording } returns recording }

    private companion object {
        const val API_30 = 30
        const val API_33 = 33
        const val API_36 = 36
    }
}

package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.model.DetectionConfidence
import com.sza.fastmediasorter.data.model.DeviceProfile
import com.sza.fastmediasorter.data.model.DeviceProfileSource
import com.sza.fastmediasorter.data.model.DeviceProfileType
import com.sza.fastmediasorter.domain.repository.DeviceProfileRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ResetSettingsToProfileDefaultsUseCaseTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val profileRepository = mockk<DeviceProfileRepository>()
    private val applyProfilePresetUseCase = mockk<ApplyProfilePresetUseCase>()

    private fun useCase() = ResetSettingsToProfileDefaultsUseCase(
        settingsRepository,
        profileRepository,
        applyProfilePresetUseCase,
    )

    private fun storedProfile(type: DeviceProfileType) {
        every { profileRepository.getCurrentProfile() } returns flowOf(
            DeviceProfile(
                type = type,
                source = DeviceProfileSource.MANUAL_SELECTION,
                confidence = DetectionConfidence.HIGH,
                presetVersion = 1,
                appliedAtInstallTime = true,
                lastModified = 0L,
            )
        )
        coEvery { applyProfilePresetUseCase.applySettingsOnly(any()) } returns Result.success(true)
    }

    @Test
    fun `a stored profile other than the smartphone gets its own preset back`() = runTest {
        storedProfile(DeviceProfileType.CAR_HEAD_UNIT)

        useCase()()

        coVerify(exactly = 1) { settingsRepository.resetToDefaults() }
        coVerify(exactly = 1) { applyProfilePresetUseCase.applySettingsOnly(DeviceProfileType.CAR_HEAD_UNIT) }
    }

    /**
     * An install that stored no profile is not distinguishable here: the repository substitutes
     * PERSONAL_SMARTPHONE rather than emitting nothing, and that substitution is what the rest of the
     * app already treats as the current profile.
     */
    @Test
    fun `the substituted smartphone profile is applied like any other`() = runTest {
        storedProfile(DeviceProfileType.PERSONAL_SMARTPHONE)

        useCase()()

        coVerify(exactly = 1) { applyProfilePresetUseCase.applySettingsOnly(DeviceProfileType.PERSONAL_SMARTPHONE) }
    }

    @Test
    fun `the other profile stops after the factory half`() = runTest {
        storedProfile(DeviceProfileType.OTHER)

        useCase()()

        coVerify(exactly = 1) { settingsRepository.resetToDefaults() }
        coVerify(exactly = 0) { applyProfilePresetUseCase.applySettingsOnly(any()) }
    }
}

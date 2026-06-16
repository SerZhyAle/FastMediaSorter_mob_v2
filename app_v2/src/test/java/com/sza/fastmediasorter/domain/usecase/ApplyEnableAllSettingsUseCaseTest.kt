package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.testutil.testMediaCapabilities
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyEnableAllSettingsUseCaseTest {

    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    private fun caps(
        video: Boolean = true,
        audio: Boolean = true,
        images: Boolean = true,
        documents: Boolean = true,
        epub: Boolean = true,
        cloud: Boolean = true,
        defaultPlayer: Boolean = true,
    ) = testMediaCapabilities(
        supportsVideo = video,
        supportsAudio = audio,
        supportsImages = images,
        supportsDocuments = documents,
        supportsEpub = epub,
        supportsCloud = cloud,
        supportsDefaultPlayer = defaultPlayer,
    )

    private fun run(start: AppSettings, capabilities: MediaCapabilities): AppSettings {
        every { settingsRepository.getSettings() } returns flowOf(start)
        val captured = slot<AppSettings>()
        coEvery { settingsRepository.updateSettings(capture(captured)) } returns Unit
        runTest { ApplyEnableAllSettingsUseCase(settingsRepository, capabilities)() }
        coVerify { settingsRepository.updateSettings(any()) }
        return captured.captured
    }

    @Test
    fun `all capabilities force every whitelisted flag on`() {
        val result = run(AppSettings(), caps())
        assertTrue(result.allFiles)
        assertTrue(result.supportAudio)
        assertTrue(result.supportVideos)
        assertTrue(result.supportText)
        assertTrue(result.supportPdf)
        assertTrue(result.supportOfficeDocuments)
        assertTrue(result.supportEpub)
        assertTrue(result.enablePersistentAudioPlayback)
        assertTrue(result.acceptSharedFiles)
        assertTrue(result.isPrimaryMediaPlayer)
    }

    @Test
    fun `no audio capability leaves audio flags untouched`() {
        val start = AppSettings(supportAudio = false, enablePersistentAudioPlayback = false)
        val result = run(start, caps(audio = false))
        assertFalse(result.supportAudio)
        assertFalse(result.enablePersistentAudioPlayback)
        assertTrue(result.allFiles)
        assertTrue(result.isPrimaryMediaPlayer)
    }

    @Test
    fun `no documents capability leaves document flags untouched`() {
        val start = AppSettings(
            supportText = false,
            supportPdf = false,
            supportEpub = false,
            supportOfficeDocuments = false,
        )
        val result = run(start, caps(documents = false, epub = false))
        assertFalse(result.supportText)
        assertFalse(result.supportPdf)
        assertFalse(result.supportEpub)
        assertFalse(result.supportOfficeDocuments)
        assertTrue(result.allFiles)
    }
}

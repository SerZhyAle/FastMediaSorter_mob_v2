package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyWearSettingsUseCaseTest {

    @Test
    fun `invoke persists every payload field`() = runTest {
        val repository = FakeWearPreferencesRepository()
        val useCase = ApplyWearSettingsUseCase(repository)
        val payload = WearSettingsPayload(
            audioEnabled = false,
            videoEnabled = true,
            imagesEnabled = false,
            slideshowEnabled = true,
            slideshowIntervalSeconds = 17,
            slideshowWaitForFinish = true,
            downloadAlbumArt = true
        )

        useCase(payload)

        assertEquals(false, repository.audioEnabled)
        assertEquals(true, repository.videoEnabled)
        assertEquals(false, repository.imagesEnabled)
        assertEquals(true, repository.slideshowEnabled)
        assertEquals(17, repository.slideshowIntervalSecondsValue)
        assertEquals(true, repository.slideshowWaitForFinishValue)
        assertEquals(true, repository.downloadAlbumArtValue)
    }
}

private class FakeWearPreferencesRepository : WearPreferencesRepository {
    var audioEnabled = true
    var videoEnabled = true
    var imagesEnabled = true
    var slideshowEnabled = false
    var slideshowIntervalSecondsValue = 5
    var slideshowWaitForFinishValue = false
    var downloadAlbumArtValue = false
    var shuffleEnabledValue = false

    override val isAudioEnabled: Flow<Boolean> = MutableStateFlow(audioEnabled)
    override val isVideoEnabled: Flow<Boolean> = MutableStateFlow(videoEnabled)
    override val isImagesEnabled: Flow<Boolean> = MutableStateFlow(imagesEnabled)
    override val isSlideshowEnabled: Flow<Boolean> = MutableStateFlow(slideshowEnabled)
    override val slideshowIntervalSeconds: Flow<Int> = MutableStateFlow(slideshowIntervalSecondsValue)
    override val slideshowWaitForFinish: Flow<Boolean> = MutableStateFlow(slideshowWaitForFinishValue)
    override val downloadAlbumArt: Flow<Boolean> = MutableStateFlow(downloadAlbumArtValue)
    override val isShuffleEnabled: Flow<Boolean> = MutableStateFlow(shuffleEnabledValue)

    override suspend fun setAudioEnabled(enabled: Boolean) {
        audioEnabled = enabled
    }

    override suspend fun setVideoEnabled(enabled: Boolean) {
        videoEnabled = enabled
    }

    override suspend fun setImagesEnabled(enabled: Boolean) {
        imagesEnabled = enabled
    }

    override suspend fun setSlideshowEnabled(enabled: Boolean) {
        slideshowEnabled = enabled
    }

    override suspend fun setSlideshowIntervalSeconds(seconds: Int) {
        slideshowIntervalSecondsValue = seconds
    }

    override suspend fun setSlideshowWaitForFinish(wait: Boolean) {
        slideshowWaitForFinishValue = wait
    }

    override suspend fun setDownloadAlbumArt(enabled: Boolean) {
        downloadAlbumArtValue = enabled
    }

    override suspend fun setShuffleEnabled(enabled: Boolean) {
        shuffleEnabledValue = enabled
    }
}
package com.sza.fastmediasorter.wear.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.wear.domain.model.LastUsedResource
import com.sza.fastmediasorter.wear.domain.model.VideoScaleMode
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendPolicy
import com.sza.fastmediasorter.wear.domain.model.WearBackgroundMode
import com.sza.fastmediasorter.wear.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyWearSettingsUseCaseTest {

    private val context: Context = mockk(relaxed = true)

    @Test
    fun `invoke persists every payload field`() = runTest {
        val repository = FakeWearPreferencesRepository()
        val useCase = ApplyWearSettingsUseCase(context, repository)
        val payload = WearSettingsPayload(
            audioEnabled = false,
            videoEnabled = true,
            imagesEnabled = false,
            slideshowEnabled = true,
            slideshowIntervalSeconds = 17,
            downloadAlbumArt = true
        )

        useCase(payload)

        assertEquals(false, repository.audioEnabled)
        assertEquals(true, repository.videoEnabled)
        assertEquals(false, repository.imagesEnabled)
        assertEquals(true, repository.slideshowEnabled)
        assertEquals(17, repository.slideshowIntervalSecondsValue)
        assertEquals(true, repository.downloadAlbumArtValue)
    }

    @Test
    fun `a payload omitting the new fields leaves the watch values alone`() = runTest {
        val repository = FakeWearPreferencesRepository().apply {
            viewModeValue = WearViewMode.GRID_2
            keepScreenAwakeValue = true
        }
        val useCase = ApplyWearSettingsUseCase(context, repository)

        // An older phone serialises no key for either field, which Gson reads back as null.
        useCase(payloadWithoutNewFields())

        assertEquals(WearViewMode.GRID_2, repository.viewModeValue)
        assertEquals(true, repository.keepScreenAwakeValue)
    }

    @Test
    fun `a payload carrying the new fields applies them`() = runTest {
        val repository = FakeWearPreferencesRepository()
        val useCase = ApplyWearSettingsUseCase(context, repository)

        useCase(
            payloadWithoutNewFields().copy(
                viewMode = WearViewMode.GRID_3.name,
                keepScreenAwakeOutsidePlayers = true
            )
        )

        assertEquals(WearViewMode.GRID_3, repository.viewModeValue)
        assertEquals(true, repository.keepScreenAwakeValue)
    }

    @Test
    fun `a payload omitting the file-list view leaves the watch value alone`() = runTest {
        val repository = FakeWearPreferencesRepository().apply {
            fileListViewModeValue = WearViewMode.GRID_3
        }
        val useCase = ApplyWearSettingsUseCase(context, repository)

        // A phone that predates S1730 serialises no key at all, which Gson reads back as null.
        useCase(payloadWithoutNewFields())

        assertEquals(WearViewMode.GRID_3, repository.fileListViewModeValue)
    }

    @Test
    fun `a payload carrying the file-list view applies it`() = runTest {
        val repository = FakeWearPreferencesRepository()
        val useCase = ApplyWearSettingsUseCase(context, repository)

        useCase(payloadWithoutNewFields().copy(fileListViewMode = WearViewMode.GRID_2.name))

        assertEquals(WearViewMode.GRID_2, repository.fileListViewModeValue)
    }

    @Test
    fun `the two view settings are written independently`() = runTest {
        val repository = FakeWearPreferencesRepository()
        val useCase = ApplyWearSettingsUseCase(context, repository)

        useCase(
            payloadWithoutNewFields().copy(
                viewMode = WearViewMode.GRID_2.name,
                fileListViewMode = WearViewMode.GRID_3.name
            )
        )

        assertEquals(WearViewMode.GRID_2, repository.viewModeValue)
        assertEquals(WearViewMode.GRID_3, repository.fileListViewModeValue)
    }

    @Test
    fun `supported appLanguage is resolved and persisted`() = runTest {
        val repository = FakeWearPreferencesRepository()
        val useCase = ApplyWearSettingsUseCase(context, repository)

        useCase(payloadWithoutNewFields().copy(appLanguage = "ru-RU"))

        assertEquals("ru", repository.appLanguageValue)
    }

    @Test
    fun `unsupported appLanguage is ignored and leaves watch value untouched`() = runTest {
        val repository = FakeWearPreferencesRepository().apply {
            appLanguageValue = "uk"
        }
        val useCase = ApplyWearSettingsUseCase(context, repository)

        useCase(payloadWithoutNewFields().copy(appLanguage = "de"))

        assertEquals("uk", repository.appLanguageValue)
    }

    @Test
    fun `null appLanguage in payload leaves watch language untouched`() = runTest {
        val repository = FakeWearPreferencesRepository().apply {
            appLanguageValue = "uk"
        }
        val useCase = ApplyWearSettingsUseCase(context, repository)

        useCase(payloadWithoutNewFields().copy(appLanguage = null))

        assertEquals("uk", repository.appLanguageValue)
    }

    @Test
    fun `a payload carrying the background mode applies it`() = runTest {
        val repository = FakeWearPreferencesRepository()
        val useCase = ApplyWearSettingsUseCase(context, repository)

        useCase(payloadWithoutNewFields().copy(backgroundMode = WearBackgroundMode.IMAGE.name))

        assertEquals(WearBackgroundMode.IMAGE, repository.backgroundModeValue)
    }

    @Test
    fun `a payload omitting the background mode leaves the watch value alone`() = runTest {
        val repository = FakeWearPreferencesRepository().apply {
            backgroundModeValue = WearBackgroundMode.IMAGE
        }
        val useCase = ApplyWearSettingsUseCase(context, repository)

        useCase(payloadWithoutNewFields())

        assertEquals(WearBackgroundMode.IMAGE, repository.backgroundModeValue)
    }

    @Test
    fun `an unknown background mode name falls back to the branded animation`() {
        assertEquals(
            WearBackgroundMode.BRANDED_ANIMATION,
            WearBackgroundMode.fromNameOrDefault("nonsense")
        )
    }

    private fun payloadWithoutNewFields() = WearSettingsPayload(
        audioEnabled = true,
        videoEnabled = true,
        imagesEnabled = true,
        slideshowEnabled = false,
        slideshowIntervalSeconds = 5,
        downloadAlbumArt = false
    )
}

private class FakeWearPreferencesRepository : WearPreferencesRepository {
    var audioEnabled = true
    var videoEnabled = true
    var imagesEnabled = true
    var slideshowEnabled = false
    var slideshowIntervalSecondsValue = 5
    var downloadAlbumArtValue = false
    var shuffleEnabledValue = false
    var viewModeValue = WearViewMode.LIST
    var backgroundModeValue = WearBackgroundMode.BRANDED_ANIMATION
    var keepScreenAwakeValue = false
    var fileListViewModeValue = WearViewMode.LIST
    var videoScaleModeValue = VideoScaleMode.FIT
    var lastUsedResourcesValue: List<LastUsedResource> = emptyList()
    var streamsSectionEnabledValue = true
    var calculatorHistoryValue: List<String> = emptyList()
    var calculatorMemoryValue: String? = null
    var autoRotationEnabledValue = false
    var appLanguageValue: String? = null
    var gameStateValue: String? = null
    var voiceNoteSendPolicyValue = VoiceNoteSendPolicy.AUTOMATIC
    var notificationPermissionAskedValue = false

    override val isAudioEnabled: Flow<Boolean> = MutableStateFlow(audioEnabled)
    override val isVideoEnabled: Flow<Boolean> = MutableStateFlow(videoEnabled)
    override val isImagesEnabled: Flow<Boolean> = MutableStateFlow(imagesEnabled)
    override val isSlideshowEnabled: Flow<Boolean> = MutableStateFlow(slideshowEnabled)
    override val slideshowIntervalSeconds: Flow<Int> = MutableStateFlow(slideshowIntervalSecondsValue)
    override val downloadAlbumArt: Flow<Boolean> = MutableStateFlow(downloadAlbumArtValue)
    override val isShuffleEnabled: Flow<Boolean> = MutableStateFlow(shuffleEnabledValue)
    override val viewMode: Flow<WearViewMode> = MutableStateFlow(viewModeValue)
    override val backgroundMode: Flow<WearBackgroundMode> = MutableStateFlow(backgroundModeValue)
    override val fileListViewMode: Flow<WearViewMode> = MutableStateFlow(fileListViewModeValue)
    override val videoScaleMode: Flow<VideoScaleMode> = MutableStateFlow(videoScaleModeValue)
    override val keepScreenAwakeOutsidePlayers: Flow<Boolean> = MutableStateFlow(keepScreenAwakeValue)
    override val lastUsedResources: Flow<List<LastUsedResource>> = MutableStateFlow(lastUsedResourcesValue)
    override val streamsSectionEnabled: Flow<Boolean> = MutableStateFlow(streamsSectionEnabledValue)
    override val calculatorHistory: Flow<List<String>> = MutableStateFlow(calculatorHistoryValue)
    override val calculatorMemory: Flow<String?> = MutableStateFlow(calculatorMemoryValue)
    override val isAutoRotationEnabled: Flow<Boolean> = MutableStateFlow(autoRotationEnabledValue)
    override val appLanguage: Flow<String?> = MutableStateFlow(appLanguageValue)
    override val gameState: Flow<String?> = MutableStateFlow(gameStateValue)
    override val voiceNoteSendPolicy: Flow<VoiceNoteSendPolicy> = MutableStateFlow(voiceNoteSendPolicyValue)
    override val notificationPermissionAsked: Flow<Boolean> =
        MutableStateFlow(notificationPermissionAskedValue)

    override suspend fun setNotificationPermissionAsked(asked: Boolean) {
        notificationPermissionAskedValue = asked
    }

    override suspend fun setGameState(value: String?) {
        gameStateValue = value
    }

    override suspend fun setVoiceNoteSendPolicy(policy: VoiceNoteSendPolicy) {
        voiceNoteSendPolicyValue = policy
    }

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

    override suspend fun setBackgroundMode(mode: WearBackgroundMode) {
        backgroundModeValue = mode
    }

    override suspend fun setDownloadAlbumArt(enabled: Boolean) {
        downloadAlbumArtValue = enabled
    }

    override suspend fun setShuffleEnabled(enabled: Boolean) {
        shuffleEnabledValue = enabled
    }

    override suspend fun setViewMode(mode: WearViewMode) {
        viewModeValue = mode
    }

    override suspend fun setFileListViewMode(mode: WearViewMode) {
        fileListViewModeValue = mode
    }

    override suspend fun setVideoScaleMode(mode: VideoScaleMode) {
        videoScaleModeValue = mode
    }

    override suspend fun setKeepScreenAwakeOutsidePlayers(enabled: Boolean) {
        keepScreenAwakeValue = enabled
    }

    override suspend fun setLastUsedResource(id: String, name: String) {
        lastUsedResourcesValue = listOf(LastUsedResource(id, name)) +
            lastUsedResourcesValue.filterNot { it.id == id }
    }

    override suspend fun clearLastUsedResource() {
        lastUsedResourcesValue = emptyList()
    }

    override suspend fun setStreamsSectionEnabled(enabled: Boolean) {
        streamsSectionEnabledValue = enabled
    }

    override suspend fun setCalculatorHistory(entries: List<String>) {
        calculatorHistoryValue = entries
    }

    override suspend fun setCalculatorMemory(value: String?) {
        calculatorMemoryValue = value
    }

    override suspend fun setAutoRotationEnabled(enabled: Boolean) {
        autoRotationEnabledValue = enabled
    }

    override suspend fun setAppLanguage(languageCode: String?) {
        appLanguageValue = languageCode
    }
}

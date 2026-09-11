package com.sza.fastmediasorter.ui.settings

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.serialization.InstantTypeAdapter
import com.sza.fastmediasorter.data.repository.wear.SharedPreferencesWearSettingsMirrorStore
import com.sza.fastmediasorter.domain.model.UnitSystem
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.domain.repository.WearFileTransferRepository
import com.sza.fastmediasorter.domain.usecase.EnsureWatchResourceUseCase
import com.sza.fastmediasorter.domain.usecase.GetPairedWatchStatusUseCase
import com.sza.fastmediasorter.domain.usecase.ImportWatchSourcesUseCase
import com.sza.fastmediasorter.domain.usecase.ObserveUnitSystemUseCase
import com.sza.fastmediasorter.domain.usecase.PushWearSettingsUseCase
import com.sza.fastmediasorter.domain.usecase.PushWearStreamPinsUseCase
import com.sza.fastmediasorter.domain.usecase.SendPlaybackCommandUseCase
import com.sza.fastmediasorter.domain.usecase.SendResourcesToWatchUseCase
import com.sza.fastmediasorter.domain.usecase.SendWearBackgroundImageUseCase
import com.sza.fastmediasorter.domain.usecase.StartWatchListeningUseCase
import com.sza.fastmediasorter.domain.usecase.StopWatchListeningUseCase
import com.sza.fastmediasorter.domain.usecase.SyncWithWatchUseCase
import com.sza.fastmediasorter.service.WatchListenSessionManager
import com.sza.fastmediasorter.service.WearListenState
import com.sza.fastmediasorter.service.WearSyncEvents
import com.sza.fastmediasorter.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Unit coverage for [WearSyncViewModel] - specifically S2916 settings push timeout,
 * completion via merge report, and failure handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WearSyncViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = RuntimeEnvironment.getApplication()

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Instant::class.java, InstantTypeAdapter())
        .create()

    private lateinit var wearSettingsMirrorStore: SharedPreferencesWearSettingsMirrorStore
    private lateinit var pushWearSettingsUseCase: PushWearSettingsUseCase
    private lateinit var outbound: WearOutboundUseCases
    private lateinit var importWatchSourcesUseCase: ImportWatchSourcesUseCase
    private lateinit var getPairedWatchStatusUseCase: GetPairedWatchStatusUseCase
    private lateinit var ensureWatchResourceUseCase: EnsureWatchResourceUseCase
    private lateinit var sendWearBackgroundImageUseCase: SendWearBackgroundImageUseCase
    private lateinit var wearFileTransferRepository: WearFileTransferRepository
    private lateinit var observeUnitSystemUseCase: ObserveUnitSystemUseCase
    private lateinit var watchListenSessionManager: WatchListenSessionManager
    private lateinit var applicationScope: TestScope

    private val testPayload = WearSettingsPayload(
        audioEnabled = true,
        videoEnabled = false,
        imagesEnabled = true,
        slideshowEnabled = true,
        slideshowIntervalSeconds = 5,
        downloadAlbumArt = true,
        appLanguage = "en"
    )

    @Before
    fun setup() {
        wearSettingsMirrorStore = SharedPreferencesWearSettingsMirrorStore(context, gson)
        pushWearSettingsUseCase = mockk()
        val sendResourcesToWatchUseCase = mockk<SendResourcesToWatchUseCase>(relaxed = true)
        val sendPlaybackCommandUseCase = mockk<SendPlaybackCommandUseCase>(relaxed = true)
        val pushWearStreamPinsUseCase = mockk<PushWearStreamPinsUseCase>(relaxed = true)
        val syncWithWatchUseCase = mockk<SyncWithWatchUseCase>(relaxed = true)
        val startWatchListeningUseCase = mockk<StartWatchListeningUseCase>(relaxed = true)
        val stopWatchListeningUseCase = mockk<StopWatchListeningUseCase>(relaxed = true)

        outbound = WearOutboundUseCases(
            sendResources = sendResourcesToWatchUseCase,
            pushSettings = pushWearSettingsUseCase,
            sendPlaybackCommand = sendPlaybackCommandUseCase,
            pushStreamPins = pushWearStreamPinsUseCase,
            syncEverything = syncWithWatchUseCase,
            startListening = startWatchListeningUseCase,
            stopListening = stopWatchListeningUseCase
        )

        importWatchSourcesUseCase = mockk(relaxed = true)
        getPairedWatchStatusUseCase = mockk(relaxed = true)
        ensureWatchResourceUseCase = mockk(relaxed = true)
        sendWearBackgroundImageUseCase = mockk(relaxed = true)
        wearFileTransferRepository = mockk(relaxed = true)

        observeUnitSystemUseCase = mockk()
        every { observeUnitSystemUseCase() } returns flowOf(UnitSystem.METRIC)

        watchListenSessionManager = mockk(relaxed = true)
        every { watchListenSessionManager.listenState } returns MutableStateFlow(WearListenState.Idle())

        applicationScope = TestScope(mainDispatcherRule.testDispatcher)
    }

    private fun createViewModel(): WearSyncViewModel {
        return WearSyncViewModel(
            context = context,
            outbound = outbound,
            importWatchSourcesUseCase = importWatchSourcesUseCase,
            getPairedWatchStatusUseCase = getPairedWatchStatusUseCase,
            ensureWatchResourceUseCase = ensureWatchResourceUseCase,
            sendWearBackgroundImageUseCase = sendWearBackgroundImageUseCase,
            wearFileTransferRepository = wearFileTransferRepository,
            wearSettingsMirrorStore = wearSettingsMirrorStore,
            observeUnitSystemUseCase = observeUnitSystemUseCase,
            watchListenSessionManager = watchListenSessionManager,
            applicationScope = applicationScope
        )
    }

    @Test
    fun `pushSettings emits Timeout and resets to Idle when watch does not report merge within timeout`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { pushWearSettingsUseCase(any()) } returns Result.success(Unit)

            val viewModel = createViewModel()
            runCurrent()

            val events = mutableListOf<SettingsPushEvent>()
            val collectJob = launch {
                viewModel.settingsPushEvent.toList(events)
            }

            viewModel.pushSettings(testPayload)
            runCurrent()

            assertEquals(WearSyncUiState.Sending, viewModel.uiState.value)
            assertEquals(0, events.size)

            // Advance time past 15 000 ms ACK_TIMEOUT_MS
            advanceTimeBy(15_001L)
            runCurrent()

            assertEquals(WearSyncUiState.Idle, viewModel.uiState.value)
            assertEquals(1, events.size)
            val event = events.first()
            assertTrue(event is SettingsPushEvent.Timeout)
            assertEquals(
                context.getString(R.string.wear_sync_settings_no_ack),
                (event as SettingsPushEvent.Timeout).message
            )

            collectJob.cancel()
        }

    @Test
    fun `pushSettings transitions to SettingsPushed when merge report arrives before timeout`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { pushWearSettingsUseCase(any()) } returns Result.success(Unit)

            val viewModel = createViewModel()
            runCurrent()

            val events = mutableListOf<SettingsPushEvent>()
            val collectJob = launch {
                viewModel.settingsPushEvent.toList(events)
            }

            viewModel.pushSettings(testPayload)
            runCurrent()

            assertEquals(WearSyncUiState.Sending, viewModel.uiState.value)

            // Simulate watch reporting merge report before timeout by updating prefs synchronously
            context.getSharedPreferences("wear_sync_prefs", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_sync_timestamp", 123456789L)
                .putString("watch_app_version_name", "2.60.9111.428-NoLegal-DEBUG")
                .commit()

            WearSyncEvents.emitWatchSettingsMerged(testPayload.copy(slideshowIntervalSeconds = 5))
            runCurrent()

            assertEquals(WearSyncUiState.SettingsPushed, viewModel.uiState.value)
            assertEquals(123456789L, viewModel.lastSyncedAt.value)
            assertEquals("2.60.9111.428-NoLegal-DEBUG", viewModel.watchAppVersion.value)

            // Advancing past timeout should not emit Timeout since it was cancelled
            advanceTimeBy(20_000L)
            runCurrent()

            assertEquals(WearSyncUiState.SettingsPushed, viewModel.uiState.value)
            assertEquals(0, events.size)

            collectJob.cancel()
        }

    @Test
    fun `pushSettings emits Failed and resets to Idle on local send failure`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { pushWearSettingsUseCase(any()) } returns Result.failure(IllegalStateException("No watch"))

            val viewModel = createViewModel()
            runCurrent()

            val events = mutableListOf<SettingsPushEvent>()
            val collectJob = launch {
                viewModel.settingsPushEvent.toList(events)
            }

            viewModel.pushSettings(testPayload)
            runCurrent()

            assertEquals(WearSyncUiState.Idle, viewModel.uiState.value)
            assertEquals(1, events.size)
            val event = events.first()
            assertTrue(event is SettingsPushEvent.Failed)
            assertEquals(
                context.getString(R.string.wear_push_settings_failed),
                (event as SettingsPushEvent.Failed).message
            )

            collectJob.cancel()
        }

    @Test
    fun `reset cancels pending timeout and sets Idle state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { pushWearSettingsUseCase(any()) } returns Result.success(Unit)

            val viewModel = createViewModel()
            runCurrent()

            val events = mutableListOf<SettingsPushEvent>()
            val collectJob = launch {
                viewModel.settingsPushEvent.toList(events)
            }

            viewModel.pushSettings(testPayload)
            runCurrent()

            assertEquals(WearSyncUiState.Sending, viewModel.uiState.value)

            viewModel.reset()
            runCurrent()

            assertEquals(WearSyncUiState.Idle, viewModel.uiState.value)

            // Advancing past timeout should not emit Timeout event
            advanceTimeBy(20_000L)
            runCurrent()

            assertEquals(0, events.size)

            collectJob.cancel()
        }
}

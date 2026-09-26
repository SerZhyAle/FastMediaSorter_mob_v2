package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.launcher.LauncherSettings
import com.sza.fastmediasorter.domain.repository.ClockDialStyle
import com.sza.fastmediasorter.domain.repository.ClockDialStyleSource
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * S3557: the clock-style payload keys are a wire contract with the watch module, which shares no code
 * with the phone - these tests pin the keys, the path, the dedup and the companion gate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PushWearClockStyleUseCaseTest {

    private val wearRepository = mockk<WearableDataLayerRepository>()
    private val dialSource = mockk<ClockDialStyleSource>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val gson = Gson()

    private val paths = mutableListOf<String>()
    private val envelopes = mutableListOf<WearEventEnvelope>()

    private val settings = AppSettings(
        enableWearCompanion = true,
        launcher = LauncherSettings(
            animationPalette = AppSettings.ANIMATION_PALETTE_DYNAMIC,
            wallpaperIntensity = 0.5f,
            wallpaperAnimationSpeed = 1.5f,
            wallpaperParticleDensity = 0.25f,
        ),
    )
    private val style = ClockDialStyle(secondsVisible = false, dialColor = 0xFF112233.toInt(), typefaceName = "serif")

    @Before
    fun setUp() {
        every { settingsRepository.getSettings() } returns flowOf(settings)
        coEvery { wearRepository.putEnvelopeDataItem(capture(paths), capture(envelopes)) } just Runs
    }

    private fun useCase() = PushWearClockStyleUseCase(wearRepository, gson, dialSource, settingsRepository)

    @Test
    fun `a style change puts one data item at the clock style path with the pinned keys`() = runTest {
        every { dialSource.observe() } returns flowOf(style)

        val job = useCase().observeAndPush(this)
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(WearDataLayerPaths.CLOCK_STYLE), paths)
        assertEquals(WearDataLayerPaths.EVENT_CLOCK_STYLE, envelopes.single().eventType)
        val json = JsonParser.parseString(String(envelopes.single().data, Charsets.UTF_8)).asJsonObject
        assertEquals(
            WIRE_KEYS,
            json.keySet(),
        )
        assertEquals(false, json["secondsVisible"].asBoolean)
        assertEquals(0xFF112233.toInt(), json["dialColor"].asInt)
        assertEquals("serif", json["dialTypeface"].asString)
        assertEquals(AppSettings.ANIMATION_PALETTE_DYNAMIC, json["animationPalette"].asString)
        assertEquals(0.5f, json["wallpaperIntensity"].asFloat)
        assertEquals(1.5f, json["wallpaperAnimationSpeed"].asFloat)
        assertEquals(0.25f, json["wallpaperParticleDensity"].asFloat)
    }

    @Test
    fun `an identical style emitted again does not put again`() = runTest {
        every { dialSource.observe() } returns flow {
            emit(style)
            delay(SETTLE_MS)
            emit(style.copy())
        }

        val job = useCase().observeAndPush(this)
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, envelopes.size)
    }

    @Test
    fun `a disabled companion puts nothing`() = runTest {
        every { settingsRepository.getSettings() } returns flowOf(settings.copy(enableWearCompanion = false))
        every { dialSource.observe() } returns flowOf(style)

        val job = useCase().observeAndPush(this)
        advanceUntilIdle()
        job.cancel()

        coVerify(exactly = 0) { wearRepository.putEnvelopeDataItem(any(), any()) }
    }

    private companion object {
        // Mirrored by the watch module; renaming one here without the watch breaks the face silently.
        val WIRE_KEYS = setOf(
            "secondsVisible",
            "dialColor",
            "dialTypeface",
            "animationPalette",
            "wallpaperIntensity",
            "wallpaperAnimationSpeed",
            "wallpaperParticleDensity",
            "sentAt",
        )

        // Past the publisher's debounce, so the second emission is judged by dedup and not swallowed.
        const val SETTLE_MS = 5_000L
    }
}

package com.sza.fastmediasorter.domain.usecase

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.WearEventEnvelope
import com.sza.fastmediasorter.domain.model.WearFaceSlot
import com.sza.fastmediasorter.domain.model.WearFaceSlotAssignment
import com.sza.fastmediasorter.domain.model.WearFaceSlotOption
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.repository.WearFaceSlotsRepository
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
 * S3558: the face-slots payload keys, the path, the event name and the option ids are a wire contract
 * with the watch module, which shares no code with the phone - these tests pin them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PushWearFaceSlotsUseCaseTest {

    private val wearRepository = mockk<WearableDataLayerRepository>()
    private val slotsRepository = mockk<WearFaceSlotsRepository>()
    private val settingsRepository = mockk<SettingsRepository>()
    private val gson = Gson()

    private val paths = mutableListOf<String>()
    private val envelopes = mutableListOf<WearEventEnvelope>()

    private val settings = AppSettings(enableWearCompanion = true)
    private val assignment = WearFaceSlotAssignment(
        mapOf(
            WearFaceSlot.OUTER_LEFT to WearFaceSlotOption.DEST_CALCULATOR,
            WearFaceSlot.INNER_LEFT to WearFaceSlotOption.SYS_BATTERY,
            WearFaceSlot.INNER_RIGHT to WearFaceSlotOption.DATA_NOW_PLAYING,
            WearFaceSlot.OUTER_RIGHT to WearFaceSlotOption.NONE,
        )
    )

    @Before
    fun setUp() {
        every { settingsRepository.getSettings() } returns flowOf(settings)
        coEvery { wearRepository.putEnvelopeDataItem(capture(paths), capture(envelopes)) } just Runs
    }

    private fun useCase() = PushWearFaceSlotsUseCase(wearRepository, gson, slotsRepository, settingsRepository)

    @Test
    fun `an assignment puts one data item at the face slots path carrying the four ids`() = runTest {
        every { slotsRepository.observe() } returns flowOf(assignment)

        val job = useCase().observeAndPush(this)
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("/fms/wear/face_slots"), paths)
        assertEquals(WearDataLayerPaths.FACE_SLOTS, paths.single())
        assertEquals("FACE_SLOTS", envelopes.single().eventType)
        val json = JsonParser.parseString(String(envelopes.single().data, Charsets.UTF_8)).asJsonObject
        assertEquals(WIRE_KEYS, json.keySet())
        assertEquals("dest:CALCULATOR", json["slot1"].asString)
        assertEquals("sys:BATTERY", json["slot2"].asString)
        assertEquals("data:NOW_PLAYING", json["slot3"].asString)
        assertEquals("none", json["slot4"].asString)
    }

    @Test
    fun `an unchanged choice publishes once`() = runTest {
        every { slotsRepository.observe() } returns flow {
            emit(assignment)
            delay(SETTLE_MS)
            emit(assignment.copy())
        }

        val job = useCase().observeAndPush(this)
        advanceUntilIdle()
        job.cancel()

        assertEquals(1, envelopes.size)
    }

    @Test
    fun `a disabled companion puts nothing`() = runTest {
        every { settingsRepository.getSettings() } returns flowOf(settings.copy(enableWearCompanion = false))
        every { slotsRepository.observe() } returns flowOf(assignment)

        val job = useCase().observeAndPush(this)
        advanceUntilIdle()
        job.cancel()

        coVerify(exactly = 0) { wearRepository.putEnvelopeDataItem(any(), any()) }
    }

    @Test
    fun `the defaults and the option ids match the watch contract`() {
        assertEquals(
            listOf("data:FAVOURITES_COUNT", "data:LAST_RESOURCE", "data:NOW_PLAYING", "none"),
            WearFaceSlot.entries.map { WearFaceSlotAssignment.DEFAULT.optionFor(it).wireId },
        )
        assertEquals(CONTRACT_IDS, WearFaceSlotOption.entries.map { it.wireId }.toSet())
    }

    private companion object {
        // Mirrored by the watch module; renaming one here without the watch breaks the face silently.
        val WIRE_KEYS = setOf("slot1", "slot2", "slot3", "slot4", "sentAt")

        val CONTRACT_IDS = setOf(
            "dest:RESOURCES", "dest:PHONE", "dest:LOCAL", "dest:STREAMS", "dest:APPS", "dest:FAVOURITES",
            "dest:PHONE_CAMERA", "dest:HOME", "dest:CALCULATOR", "dest:NETWORK_MONITOR", "dest:GAME",
            "dest:VOICE_RECORDER", "dest:SYSTEM_INFO", "dest:WATER_FLASHLIGHT", "dest:MOTION_MONITOR",
            "dest:BODY_SENSOR", "dest:BLOOD_PRESSURE", "dest:BROADCAST", "dest:STOPWATCH", "dest:TOURIST",
            "dest:CLIPBOARD", "dest:SOS", "data:FAVOURITES_COUNT", "data:LAST_RESOURCE", "data:NOW_PLAYING",
            "sys:BATTERY", "sys:DATE", "sys:NEXT_ALARM", "sys:ALARMS", "sys:TIMER", "none",
        )

        // Past the publisher's debounce, so the second emission is judged by dedup and not swallowed.
        const val SETTLE_MS = 5_000L
    }
}

package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorParser
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorSerializer
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.testing.InMemoryRoomRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportStreamBroadcastUseCaseTest {

    @get:Rule
    val dbRule = InMemoryRoomRule { RuntimeEnvironment.getApplication() }

    private val dao get() = dbRule.db.streamSourceDao()
    private val repo get() = StreamSourceRepository(
        dbRule.db,
        dao,
        dbRule.db.streamQualityMemoryDao(),
        dbRule.db.streamUserStateDao(),
        CoroutineScope(Dispatchers.Unconfined),
    )
    private val stats = object : StatsSink {
        override fun record(event: StatsEvent) = Unit
        override suspend fun flushNow() = Unit
    }
    private val parser = BroadcastDescriptorParser()
    private val serializer = BroadcastDescriptorSerializer()
    private val addStreamUseCase get() = AddStreamSourceUseCase(repo, StreamMediaKindClassifier(), stats)
    private val importUseCase get() = ImportStreamBroadcastUseCase(parser, addStreamUseCase, repo)

    @Test
    fun importFromValidCompressedPayload_addsOrdinaryStreamSourceEntity() = runTest {
        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "http://192.168.1.50:8768/live-audio.aac",
            title = "My Phone Broadcast",
            mode = "AUDIO_ONLY"
        )
        val compressed = serializer.serializeCompressed(dto)

        val result = importUseCase(compressed)
        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Success, result)

        val entity: StreamSourceEntity? = dao.getByUrl("http://192.168.1.50:8768/live-audio.aac")
        assertNotNull(entity)
        assertEquals("My Phone Broadcast", entity?.title)
        assertEquals("AUDIO", entity?.mediaKind)
        assertEquals("MANUAL", entity?.sourceOrigin)
    }

    @Test
    fun importDuplicateUrl_returnsDuplicateResult() = runTest {
        val dto = BroadcastDescriptorDto(
            schemaVersion = 1,
            url = "http://192.168.1.50:8768/live-audio.aac",
            title = "My Phone Broadcast",
            mode = "AUDIO_ONLY"
        )
        val rawJson = serializer.serialize(dto)

        val result1 = importUseCase(rawJson)
        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Success, result1)

        val result2 = importUseCase(rawJson)
        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Duplicate, result2)
    }

    @Test
    fun importInvalidPayload_returnsInvalidDescriptor() = runTest {
        val result = importUseCase("NOT_A_VALID_DESCRIPTOR")
        assertEquals(ImportStreamBroadcastUseCase.ImportResult.InvalidDescriptor, result)
    }

    @Test
    fun importNewerSchemaVersion_isDistinguishedFromGarbage() = runTest {
        val dto = BroadcastDescriptorDto(
            schemaVersion = BroadcastDescriptorParser.SUPPORTED_SCHEMA_VERSION + 1,
            url = "http://192.168.1.50:8768/live-audio.aac",
            title = "Future Broadcast",
            mode = "AUDIO_ONLY"
        )

        val result = importUseCase(serializer.serialize(dto))

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.UnsupportedVersion, result)
    }

    // S2813: the four ways a scanned descriptor can meet the catalog. They differ only in stored
    // state, which is exactly what a device walk cannot tell apart.

    @Test
    fun importFromUnknownDevice_recordsTheDeviceOnTheNewRow() = runTest {
        val result = importUseCase(serializer.serialize(watchDto(FIRST_URL)))

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Success, result)
        assertEquals(WATCH_ID, dao.getByUrl(FIRST_URL)?.sourceDeviceId)
    }

    @Test
    fun importFromKnownDeviceOnANewAddress_refreshesTheRowInsteadOfAddingOne() = runTest {
        importUseCase(serializer.serialize(watchDto(FIRST_URL)))
        val before = dao.getByUrl(FIRST_URL)
        repo.pinToTop(requireNotNull(before).id)

        val result = importUseCase(serializer.serialize(watchDto(SECOND_URL)))

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Updated, result)
        assertEquals("the watch must still own exactly one row", 1, dao.observeAll().first().size)
        val after = dao.getByUrl(SECOND_URL)
        assertEquals(before.id, after?.id)
        assertEquals("the pin must survive the address change", true, after?.pinned)
    }

    @Test
    fun importFromKnownDeviceOnTheSameAddress_isADuplicate() = runTest {
        importUseCase(serializer.serialize(watchDto(FIRST_URL)))

        val result = importUseCase(serializer.serialize(watchDto(FIRST_URL)))

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Duplicate, result)
    }

    @Test
    fun importWithoutASourceId_behavesExactlyAsBefore() = runTest {
        val first = importUseCase(serializer.serialize(watchDto(FIRST_URL, sourceId = null)))
        val second = importUseCase(serializer.serialize(watchDto(SECOND_URL, sourceId = null)))

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Success, first)
        assertEquals("a nameless source still adds a second row", 2, dao.observeAll().first().size)
        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Success, second)
    }

    private fun watchDto(url: String, sourceId: String? = WATCH_ID) = BroadcastDescriptorDto(
        schemaVersion = 1,
        url = url,
        title = "Galaxy Watch",
        mode = "AUDIO_ONLY",
        sourceId = sourceId
    )

    private companion object {
        const val WATCH_ID = "6f1a8f0e-0f4e-4a2b-9d1c-2b7f1a8f0e00"
        const val FIRST_URL = "http://192.168.1.77:41000/live-audio.aac"
        const val SECOND_URL = "http://192.168.1.77:52311/live-audio.aac"
    }
}

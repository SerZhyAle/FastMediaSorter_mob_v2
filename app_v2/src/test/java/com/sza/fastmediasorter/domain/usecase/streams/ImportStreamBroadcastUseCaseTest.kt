package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.data.broadcast.BroadcastAuthDto
import com.sza.fastmediasorter.data.broadcast.BroadcastCredentialsDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorParser
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorSerializer
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointCodec
import com.sza.fastmediasorter.data.broadcast.BroadcastEndpointDto
import com.sza.fastmediasorter.data.local.db.StreamSourceEntity
import com.sza.fastmediasorter.data.repository.StreamSourceRepository
import com.sza.fastmediasorter.domain.repository.BroadcastPasswordStore
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
import org.junit.Assert.assertNull
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
    private val passwords = FakeBroadcastPasswordStore()
    private val importUseCase get() = ImportStreamBroadcastUseCase(parser, addStreamUseCase, repo, passwords)

    private class FakeBroadcastPasswordStore : BroadcastPasswordStore {
        val pairs = mutableMapOf<String, BroadcastPasswordStore.StoredPair>()
        override suspend fun get(streamId: String) = pairs[streamId]
        override suspend fun put(streamId: String, user: String, password: String?): Boolean {
            pairs[streamId] = BroadcastPasswordStore.StoredPair(user, password)
            return true
        }
        override suspend fun remove(streamId: String) {
            pairs.remove(streamId)
        }
    }

    private fun protectedDescriptor() = BroadcastDescriptorDto(
        schemaVersion = 1,
        url = "http://192.168.1.50:8768/live-audio.aac",
        title = "Protected",
        mode = "AUDIO_ONLY",
        auth = BroadcastAuthDto(user = "listener"),
    )

    @Test
    fun importProtectedDescriptor_keepsTypedPasswordForTheNewRow() = runTest {
        val result = importUseCase(serializer.serialize(protectedDescriptor()), password = "s3cret")

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Success, result)
        val id = requireNotNull(dao.getByUrl("http://192.168.1.50:8768/live-audio.aac")).id
        assertEquals(BroadcastPasswordStore.StoredPair("listener", "s3cret"), passwords.pairs[id])
    }

    @Test
    fun importProtectedDescriptorAgain_keepsThePasswordAlreadyTyped() = runTest {
        importUseCase(serializer.serialize(protectedDescriptor()), password = "s3cret")
        importUseCase(serializer.serialize(protectedDescriptor()))

        val id = requireNotNull(dao.getByUrl("http://192.168.1.50:8768/live-audio.aac")).id
        assertEquals("s3cret", passwords.pairs[id]?.password)
    }

    @Test
    fun importProtectedDescriptorWithoutPassword_recordsTheUserOnly() = runTest {
        importUseCase(serializer.serialize(protectedDescriptor()))

        val id = requireNotNull(dao.getByUrl("http://192.168.1.50:8768/live-audio.aac")).id
        assertEquals(BroadcastPasswordStore.StoredPair("listener", null), passwords.pairs[id])
    }

    @Test
    fun importOwnProtectedDescriptor_keepsTheAccountPairFromTheBox() = runTest {
        val own = protectedDescriptor().copy(credentials = BroadcastCredentialsDto("account", "own-secret"))

        importUseCase(serializer.serialize(own))

        val id = requireNotNull(dao.getByUrl("http://192.168.1.50:8768/live-audio.aac")).id
        assertEquals(BroadcastPasswordStore.StoredPair("account", "own-secret"), passwords.pairs[id])
    }

    @Test
    fun importUnprotectedDescriptor_storesNoPair() = runTest {
        importUseCase(serializer.serialize(protectedDescriptor().copy(auth = null)), password = "ignored")

        assertEquals(emptyMap<String, BroadcastPasswordStore.StoredPair>(), passwords.pairs)
    }

    @Test
    fun needsPassword_asksOnlyForAProtectedDescriptorWithNoPairOnThisDevice() = runTest {
        val protected = serializer.serialize(protectedDescriptor())
        val own = serializer.serialize(
            protectedDescriptor().copy(credentials = BroadcastCredentialsDto("account", "own-secret")),
        )

        assertEquals(true, importUseCase.needsPassword(protected))
        assertEquals(false, importUseCase.needsPassword(own))
        assertEquals(false, importUseCase.needsPassword(serializer.serialize(protectedDescriptor().copy(auth = null))))
        importUseCase(protected, password = "s3cret")
        assertEquals(false, importUseCase.needsPassword(protected))
    }

    @Test
    fun replacePassword_keepsTheRecordedUserAndTheNewPassword() = runTest {
        importUseCase(serializer.serialize(protectedDescriptor()), password = "old")
        val id = requireNotNull(dao.getByUrl("http://192.168.1.50:8768/live-audio.aac")).id

        assertEquals(true, importUseCase.replacePassword(id, "new"))
        assertEquals(BroadcastPasswordStore.StoredPair("listener", "new"), passwords.pairs[id])
        assertEquals(false, importUseCase.replacePassword("unknown-row", "new"))
    }

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

    // S2868: a row created while the watch titled itself with its model code takes the node name on
    // the next visit; a row the user renamed keeps the user's name.

    @Test
    fun importFromKnownDevice_replacesAStoredModelCodeWithTheOfferedName() = runTest {
        importUseCase(serializer.serialize(watchDto(FIRST_URL, title = "SM-L310")))

        val result = importUseCase(serializer.serialize(watchDto(SECOND_URL, title = "Galaxy Watch7")))

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Updated, result)
        assertEquals("Galaxy Watch7", dao.getByUrl(SECOND_URL)?.title)
    }

    @Test
    fun importFromKnownDeviceOnTheSameAddress_stillReplacesAModelCode() = runTest {
        importUseCase(serializer.serialize(watchDto(FIRST_URL, title = "SM-L310")))

        val result = importUseCase(serializer.serialize(watchDto(FIRST_URL, title = "Galaxy Watch7")))

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Updated, result)
        assertEquals("Galaxy Watch7", dao.getByUrl(FIRST_URL)?.title)
    }

    @Test
    fun importFromKnownDevice_neverOverwritesAUserRename() = runTest {
        importUseCase(serializer.serialize(watchDto(FIRST_URL, title = "SM-L310")))
        dao.updateTitle(requireNotNull(dao.getByUrl(FIRST_URL)).id, "Kitchen watch")

        val result = importUseCase(serializer.serialize(watchDto(FIRST_URL, title = "Galaxy Watch7")))

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Duplicate, result)
        assertEquals("Kitchen watch", dao.getByUrl(FIRST_URL)?.title)
    }

    // S4160: the descriptor's endpoint list is the channel's way in from any network.

    @Test
    fun importWithExchangeEndpoints_keepsTheWholeListInOrder() = runTest {
        val payload = serializer.serializeCompressed(watchDto(FIRST_URL, endpoints = exchangeEndpoints("a")))
        val result = importUseCase(payload)

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Success, result)
        val stored = BroadcastEndpointCodec.decode(dao.getByUrl(FIRST_URL)?.broadcastEndpoints)
        assertEquals(listOf("HTTP", "RELAY", "TUNNEL"), stored?.map { it.transport })
        assertEquals(FINGERPRINT, stored?.get(1)?.certFingerprint)
        assertEquals(FIRST_URL, stored?.get(2)?.inner)
    }

    @Test
    fun importOfTheSourcesNextBroadcast_replacesTheStoredEndpoints() = runTest {
        importUseCase(serializer.serialize(watchDto(FIRST_URL, endpoints = exchangeEndpoints("a"))))

        val result = importUseCase(serializer.serialize(watchDto(FIRST_URL, endpoints = exchangeEndpoints("b"))))

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Updated, result)
        assertEquals(1, dao.observeAll().first().size)
        val stored = BroadcastEndpointCodec.decode(dao.getByUrl(FIRST_URL)?.broadcastEndpoints)
        assertEquals(true, stored?.get(1)?.url?.contains("/v2/b/b/"))
    }

    @Test
    fun importWithoutAnEndpointList_leavesTheColumnNull() = runTest {
        importUseCase(serializer.serialize(watchDto(FIRST_URL)))

        assertNull(dao.getByUrl(FIRST_URL)?.broadcastEndpoints)
    }

    @Test
    fun importWithEndpointsAndNoSourceId_refreshesTheRowOfTheSameAddress() = runTest {
        importUseCase(serializer.serialize(watchDto(FIRST_URL, sourceId = null, endpoints = exchangeEndpoints("a"))))

        val result = importUseCase(
            serializer.serialize(watchDto(FIRST_URL, sourceId = null, endpoints = exchangeEndpoints("b")))
        )

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Updated, result)
        assertEquals(1, dao.observeAll().first().size)
    }

    // S4289: the descriptor's mode decides the row's kind on import and on every refresh.

    @Test
    fun importVideoBroadcast_storesAVideoKindEvenWhenTheUrlHasNoExtension() = runTest {
        importUseCase(serializer.serialize(watchDto(LIVE_URL, mode = "VIDEO_AUDIO")))

        assertEquals("VIDEO", dao.getByUrl(LIVE_URL)?.mediaKind)
    }

    @Test
    fun importVideoOnlyBroadcastOverRtsp_keepsTheRtspKind() = runTest {
        importUseCase(serializer.serialize(watchDto(RTSP_URL, mode = "VIDEO_ONLY")))

        assertEquals("RTSP", dao.getByUrl(RTSP_URL)?.mediaKind)
    }

    @Test
    fun importOfABroadcastThatSwitchedFromAudioToVideo_refreshesTheKindAndKeepsTheRest() = runTest {
        importUseCase(serializer.serialize(watchDto(LIVE_URL, title = "Kitchen watch", mode = "AUDIO_ONLY")))
        val before = requireNotNull(dao.getByUrl(LIVE_URL))
        repo.pinToTop(before.id)

        val video = watchDto(LIVE_URL, title = "Kitchen watch", mode = "VIDEO_AUDIO")
        val result = importUseCase(serializer.serialize(video))

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Updated, result)
        assertEquals(1, dao.observeAll().first().size)
        val after = requireNotNull(dao.getByUrl(LIVE_URL))
        assertEquals(before.id, after.id)
        assertEquals("VIDEO", after.mediaKind)
        assertEquals("Kitchen watch", after.title)
        assertEquals("the pin must survive the kind refresh", true, after.pinned)
    }

    @Test
    fun importOfABroadcastThatSwitchedFromVideoToAudio_refreshesTheKindBack() = runTest {
        importUseCase(serializer.serialize(watchDto(LIVE_URL, mode = "VIDEO_AUDIO")))

        val result = importUseCase(serializer.serialize(watchDto(LIVE_URL, mode = "AUDIO_ONLY")))

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Updated, result)
        assertEquals("AUDIO", dao.getByUrl(LIVE_URL)?.mediaKind)
    }

    @Test
    fun importOfABroadcastWhoseKindDidNotChange_isADuplicate() = runTest {
        importUseCase(serializer.serialize(watchDto(LIVE_URL, mode = "VIDEO_AUDIO")))

        val result = importUseCase(serializer.serialize(watchDto(LIVE_URL, mode = "VIDEO_AUDIO")))

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Duplicate, result)
    }

    @Test
    fun importOverwritesATypeTheUserChoseInTheEditDialog() = runTest {
        importUseCase(serializer.serialize(watchDto(LIVE_URL, mode = "AUDIO_ONLY")))
        val typed = requireNotNull(dao.getByUrl(LIVE_URL))
        repo.updateUserFields(typed.id, typed.url, typed.title, "VIDEO")

        val result = importUseCase(serializer.serialize(watchDto(LIVE_URL, mode = "AUDIO_ONLY")))

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Updated, result)
        assertEquals("AUDIO", dao.getByUrl(LIVE_URL)?.mediaKind)
    }

    @Test
    fun importOfAKnownDeviceOnANewAddressAndANewMode_movesTheRowAndRefreshesTheKind() = runTest {
        importUseCase(serializer.serialize(watchDto(FIRST_URL, mode = "AUDIO_ONLY")))
        val before = requireNotNull(dao.getByUrl(FIRST_URL))
        repo.pinToTop(before.id)

        val result = importUseCase(serializer.serialize(watchDto(LIVE_URL, mode = "VIDEO_AUDIO")))

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Updated, result)
        val after = requireNotNull(dao.getByUrl(LIVE_URL))
        assertEquals(before.id, after.id)
        assertEquals("VIDEO", after.mediaKind)
        assertEquals("the pin must survive the move", true, after.pinned)
    }

    @Test
    fun importWithAModeThisAppDoesNotKnow_keepsTheStoredKindAndClassifiesANewRowByUrl() = runTest {
        importUseCase(serializer.serialize(watchDto(LIVE_URL, mode = "FUTURE_MODE")))
        assertEquals("AUDIO", dao.getByUrl(LIVE_URL)?.mediaKind)
        importUseCase(serializer.serialize(watchDto(LIVE_URL, mode = "VIDEO_AUDIO")))

        val result = importUseCase(serializer.serialize(watchDto(LIVE_URL, mode = "FUTURE_MODE")))

        assertEquals(ImportStreamBroadcastUseCase.ImportResult.Duplicate, result)
        assertEquals("VIDEO", dao.getByUrl(LIVE_URL)?.mediaKind)
    }

    private fun exchangeEndpoints(broadcastId: String) = listOf(
        BroadcastEndpointDto(url = FIRST_URL, transport = "HTTP"),
        BroadcastEndpointDto(
            url = "https://exchange.example.org:8443/v2/b/$broadcastId/stream",
            transport = "RELAY",
            targetLatencyMs = 2000,
            certFingerprint = FINGERPRINT,
        ),
        BroadcastEndpointDto(
            url = "fmsx://exchange.example.org:8443/b/$broadcastId/http",
            transport = "TUNNEL",
            inner = FIRST_URL,
            certFingerprint = FINGERPRINT,
        ),
    )

    private fun watchDto(
        url: String,
        sourceId: String? = WATCH_ID,
        title: String = "Galaxy Watch",
        endpoints: List<BroadcastEndpointDto>? = null,
        mode: String = "AUDIO_ONLY",
    ) = BroadcastDescriptorDto(
        schemaVersion = 1,
        url = url,
        title = title,
        mode = mode,
        sourceId = sourceId,
        endpoints = endpoints,
    )

    private companion object {
        const val WATCH_ID = "6f1a8f0e-0f4e-4a2b-9d1c-2b7f1a8f0e00"
        const val FIRST_URL = "http://192.168.1.77:41000/live-audio.aac"
        const val SECOND_URL = "http://192.168.1.77:52311/live-audio.aac"
        const val LIVE_URL = "http://192.168.1.77:41000/live"
        const val RTSP_URL = "rtsp://192.168.1.77:8554/live"
        const val FINGERPRINT = "SHA256:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU"
    }
}

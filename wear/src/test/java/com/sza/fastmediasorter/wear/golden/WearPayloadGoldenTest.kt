package com.sza.fastmediasorter.wear.golden

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.wear.domain.model.WearCastAck
import com.sza.fastmediasorter.wear.domain.model.WearCastOutcome
import com.sza.fastmediasorter.wear.domain.model.WearCastState
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteDeltaItem
import com.sza.fastmediasorter.wear.domain.model.WearFavoritesDeltaPayload
import com.sza.fastmediasorter.wear.domain.model.WearSyncPayload
import com.sza.fastmediasorter.wear.domain.model.appendFavoriteDelta
import com.sza.fastmediasorter.wear.domain.model.favoriteIdentityKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3371 phase 06 step 06.4: golden proof that a Data Layer payload an older phone wrote still parses
 * on this watch, and that what this watch writes still carries the field names the other side ships.
 *
 * THE READER UNDER TEST is plain Gson - `WatchWearListenerService.handlePush` parses the sync
 * envelope with `gson.fromJson(json, WearSyncPayload::class.java)`, and `WearCastRepositoryImpl`
 * parses the cast shapes the same way - so a fixture fed to Gson here travels the production path.
 *
 * WHERE EACH SYNC VERSION COMES FROM. `WearSyncPayload.version` carries its own history in source:
 * "S2488: raised to 2 with the `endpoints` field. S2502 raised it to 3 with `lastEditedAt`. S2882
 * raised it to 4 with `deselectedIds`."
 *
 * - `payload_sync_v1.json` is a payload from a phone older than every optional field this reader now
 *   knows: no `hostKeyFingerprint` (S1555), `iconId` (S2129), `supportedMediaTypes`/`allFiles`
 *   (S2487), `endpoints` (S2488), `lastEditedAt` (S2502), `tombstones` (S2885) or `deselectedIds`
 *   (S2882). Its second source also omits `basePath` and `domain`, the pair S2887 made nullable.
 * - `payload_sync_v3.json` is the S2502 generation - endpoints and edit stamps present, exclusions
 *   not yet.
 * - `payload_sync_v4.json` is the current generation.
 *
 * THE CAST AND FAVOURITE SHAPES HAVE NO RECORDED VERSION HISTORY. Nothing in this tree states a
 * prior form of `WearCastState`, `WearCastAck` or `WearFavoritesDeltaPayload`, so their fixtures are
 * the current form plus - for the cast state - the one exercising the absence of its newest optional
 * fields, which is also exactly what the phone writes when nothing is casting (Gson omits nulls).
 * No older cast or favourite form is invented here.
 *
 * `WearBridgeAbsentKeyTest` is the lexical sibling of this file: it builds one-key-absent JSON
 * literals to pin nullability. These fixtures are whole payloads a real sender would have produced.
 */
class WearPayloadGoldenTest {

    private val gson = Gson()

    @Test
    fun `a pre-S2488 sync payload parses with every later field absent`() {
        val payload = gson.fromJson(readFixture("payload_sync_v1.json"), WearSyncPayload::class.java)

        assertEquals(1, payload.version)
        assertEquals("Pixel 6a", payload.phoneName)
        assertEquals(2, payload.sources.size)
        assertNull("S2885: an absent tombstones key must arrive null, not empty", payload.tombstones)
        assertNull("S2882: an absent deselectedIds key says nothing about exclusions", payload.deselectedIds)

        val nas = payload.sources.first()
        assertNull("S1555: an older phone pinned no host key", nas.hostKeyFingerprint)
        assertNull("S2129: an older phone sent no resource icon", nas.iconId)
        assertNull("S2487: an older phone sent no media-type filter", nas.supportedMediaTypes)
        assertNull(nas.allFiles)
        assertNull("S2488: an older phone resolved no endpoint group", nas.endpoints)
        assertNull("S2502: an older phone stamped no edit time", nas.lastEditedAt)
    }

    @Test
    fun `a pre-S2488 sync payload reads its absent fields through their documented fallbacks`() {
        val payload = gson.fromJson(readFixture("payload_sync_v1.json"), WearSyncPayload::class.java)

        assertEquals(emptyList<Any>(), payload.tombstones.orEmpty())
        assertEquals(emptyList<Any>(), payload.deselectedIds.orEmpty())

        // S2887: null means the sender wrote no key - basePath then reads as "/" and domain as "".
        val studio = payload.sources[1]
        assertNull(studio.basePath)
        assertNull(studio.domain)
        assertEquals("/", studio.basePath ?: "/")
        assertEquals("", studio.domain ?: "")

        // S2488: with no endpoint group, server and port stay authoritative.
        assertEquals("10.0.0.14", studio.server)
        assertEquals(2222, studio.port)
    }

    @Test
    fun `an S2502 sync payload parses its endpoints and edit stamps but declares no exclusions`() {
        val payload = gson.fromJson(readFixture("payload_sync_v3.json"), WearSyncPayload::class.java)

        assertEquals(3, payload.version)
        val nas = payload.sources.single()
        assertEquals(2, nas.endpoints?.size)
        assertEquals("nas.example.org", nas.endpoints?.get(1)?.host)
        assertEquals(4450, nas.endpoints?.get(1)?.port)
        assertEquals(1742900000000L, nas.lastEditedAt)
        assertEquals("src-old-09", payload.tombstones?.single()?.id)
        assertNull("S2882 had not shipped at version 3", payload.deselectedIds)
    }

    @Test
    fun `the current sync payload parses every field and survives a write-back`() {
        val json = readFixture("payload_sync_v4.json")
        val payload = gson.fromJson(json, WearSyncPayload::class.java)

        assertEquals(4, payload.version)
        assertEquals(listOf("src-desk-77"), payload.deselectedIds)
        assertNotNull(payload.sources.single().hostKeyFingerprint)

        val rewritten = gson.fromJson(gson.toJson(payload), WearSyncPayload::class.java)
        assertEquals(payload, rewritten)
    }

    @Test
    fun `what this build writes still carries every field name the v1 sender shipped`() {
        val v1 = JsonParser.parseString(readFixture("payload_sync_v1.json")).asJsonObject
        val current = gson.toJsonTree(
            gson.fromJson(readFixture("payload_sync_v4.json"), WearSyncPayload::class.java)
        ).asJsonObject

        assertKeysKept("sync envelope", v1, current)
        assertKeysKept(
            "sync source",
            v1.getAsJsonArray("sources").first().asJsonObject,
            current.getAsJsonArray("sources").first().asJsonObject,
        )
    }

    @Test
    fun `the cast shapes parse in both the running and the idle form`() {
        val casting = gson.fromJson(readFixture("payload_cast_state_v1.json"), WearCastState::class.java)
        assertTrue(casting.isCasting)
        assertEquals("Living room TV", casting.deviceName)
        assertEquals("Beach 2024", casting.displayName)

        val idle = gson.fromJson(readFixture("payload_cast_state_idle_v1.json"), WearCastState::class.java)
        assertEquals(false, idle.isCasting)
        assertNull("an idle state omits the nullable names, which is what Gson writes for null", idle.deviceName)
        assertNull(idle.displayName)

        val ack = gson.fromJson(readFixture("payload_cast_ack_v1.json"), WearCastAck::class.java)
        assertEquals("0f4a8c31-6a2e-4c19-9f7d-1b8e5c2a4d60", ack.requestId)
        assertEquals(WearCastOutcome.PICKER_NEEDED, ack.outcome)
    }

    @Test
    fun `a favourites delta parses and still feeds the queue rule that consumes it`() {
        val payload = gson.fromJson(
            readFixture("payload_favorites_delta_v1.json"),
            WearFavoritesDeltaPayload::class.java
        )

        assertEquals(2, payload.items.size)
        assertTrue(payload.items.first().isFavorite)
        assertEquals("/photos/2024/beach.jpg", payload.items.first().filePath)

        // S2435: a later change to the same identity replaces the queued one rather than stacking.
        val reversal = payload.items.first().copy(isFavorite = false, changedAt = 1757999999999L)
        val queued = appendFavoriteDelta(payload.items, reversal)

        assertEquals(2, queued.size)
        assertEquals(reversal, queued.last())
        assertEquals(
            1,
            queued.count { favoriteIdentityKey(it.sourceId, it.filePath) == identityOf(reversal) }
        )
    }

    private fun identityOf(item: WearFavoriteDeltaItem): String =
        favoriteIdentityKey(item.sourceId, item.filePath)

    /** Every key the older side wrote must still be written, or that side reads it as absent. */
    private fun assertKeysKept(what: String, older: JsonObject, current: JsonObject) {
        older.keySet().forEach { key ->
            assertTrue("$what no longer writes '$key', which a shipped older side reads", current.has(key))
        }
    }

    private fun readFixture(fileName: String): String {
        val path = "golden/wear-payload/$fileName"
        return checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) {
            "Golden fixture $path is missing from the test resources"
        }.bufferedReader().use { it.readText() }
    }
}

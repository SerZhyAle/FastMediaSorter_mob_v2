package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2885: every case here builds its JSON as a string literal with a key deliberately absent.
 *
 * A round trip through `gson.toJson(dto)` cannot express this: the current build always writes every
 * key the current class declares, so it can never produce the payload an older sender emits. That is
 * why S2507's compatibility criterion passed on a test that built its payload with the Kotlin
 * constructor - it exercised Kotlin's own default and never reached Gson's reflection path, which is
 * the thing that breaks.
 *
 * The invariant under test: a collection field on a bridge envelope is declared nullable, so Gson's
 * absent-key null is a value the type system forces every reader to handle, rather than a null under
 * a non-null type that kills the first dereference.
 */
class WearBridgeAbsentKeyTest {

    private val gson = Gson()

    @Test
    fun `sync payload without a tombstones key parses and leaves the field null`() {
        val json = """
            {
              "version": 4,
              "sentAt": 1730000000000,
              "phoneName": "Pixel 8 Pro",
              "sources": []
            }
        """.trimIndent()

        val payload = gson.fromJson(json, WearSyncPayload::class.java)

        assertNull("an absent tombstones key must survive parsing as null", payload.tombstones)
        assertNull("an absent deselectedIds key must stay null - S2882 relies on it", payload.deselectedIds)
        assertEquals(emptyList<WearNetworkSourcePayload>(), payload.sources)
    }

    @Test
    fun `sync payload without a tombstones key reads as no deletions`() {
        val json = """
            {"sentAt": 1730000000000, "phoneName": "Pixel", "sources": []}
        """.trimIndent()

        val payload = gson.fromJson(json, WearSyncPayload::class.java)

        assertEquals(emptyList<WearSourceTombstonePayload>(), payload.tombstones.orEmpty())
    }

    @Test
    fun `sources export payload without a tombstones key parses and leaves the field null`() {
        val json = """
            {"sources": [], "watchName": "Galaxy Watch"}
        """.trimIndent()

        val payload = gson.fromJson(json, WearSourcesExportPayload::class.java)

        assertNull(payload.tombstones)
        assertNull("sentAt predates no field here but must stay nullable", payload.sentAt)
    }

    @Test
    fun `receiver entry without an applicableTypes key parses and leaves the field null`() {
        val json = """
            {
              "receivers": [
                {"id": "clipboard", "title": "Clipboard"}
              ]
            }
        """.trimIndent()

        val payload = gson.fromJson(json, WearSendToReceiversPayload::class.java)
        val entry = payload.receivers.single()

        assertNull("an absent applicableTypes key must survive parsing as null", entry.applicableTypes)
        assertEquals("null must read as the any-type declaration", 0, entry.applicableTypes.orEmpty().size)
    }

    @Test
    fun `phone resource page without an items key parses and leaves the field null`() {
        val json = """
            {"requestId": "req-1", "status": "OK"}
        """.trimIndent()

        val page = gson.fromJson(json, WearPhoneResourcePage::class.java)

        assertNull("an absent items key must survive parsing as null", page.items)
        assertEquals(emptyList<WearPhoneResourceItem>(), page.items.orEmpty())
    }
}

package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S2885: every case here builds its JSON as a string literal with a key deliberately absent.
 *
 * A round trip through `gson.toJson(dto)` cannot express this: the current build always writes every
 * key the current class declares, so it can never produce the payload an older sender emits. The
 * phone's own half of the defect is the quieter one - `ImportWatchSourcesUseCase` runs inside
 * `runCatching`, so the failure surfaced as "0 added, 0 updated, 0 skipped" rather than as an error.
 *
 * The invariant under test: a collection field on a bridge envelope is declared nullable, so Gson's
 * absent-key null is a value the type system forces every reader to handle.
 */
class WearBridgeAbsentKeyTest {

    private val gson = Gson()

    @Test
    fun `sources export payload without a tombstones key parses and leaves the field null`() {
        val json = """
            {"sources": [], "watchName": "Galaxy Watch"}
        """.trimIndent()

        val payload = gson.fromJson(json, WearSourcesExportPayload::class.java)

        assertNull("an absent tombstones key must survive parsing as null", payload.tombstones)
        assertEquals(emptyList<WearSourceTombstonePayload>(), payload.tombstones.orEmpty())
    }

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

        assertNull(payload.tombstones)
        assertNull("an absent deselectedIds key must stay null - S2882 relies on it", payload.deselectedIds)
    }

    @Test
    fun `receiver entry without an applicableTypes key parses and leaves the field null`() {
        val json = """
            {"receivers": [{"id": "clipboard", "title": "Clipboard"}]}
        """.trimIndent()

        val payload = gson.fromJson(json, WearSendToReceiversPayload::class.java)

        assertNull(payload.receivers.single().applicableTypes)
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

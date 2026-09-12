package com.sza.fastmediasorter.data.identity.transfer

import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInProviderKeys
import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract of the transferable sign-in record codec (S2101).
 *
 * The decode cases matter more than the round trip: the reader runs on a device the writer never
 * saw, on a build that may be older or newer, and a throw there is a crash on the first launch after
 * a migration.
 */
class TransferableSignInRecordCodecTest {

    private val codec = TransferableSignInRecordCodec()

    @Test
    fun `round trip preserves both entry kinds`() {
        val record = TransferableSignInRecord(
            schemaVersion = TransferableSignInRecord.SCHEMA_VERSION,
            writtenAt = WRITTEN_AT,
            entries = listOf(envelopeEntry(), secretEntry())
        )

        val decoded = codec.decode(codec.encode(record))

        assertEquals(record, decoded)
    }

    @Test
    fun `malformed input decodes to null instead of throwing`() {
        assertNull(codec.decode("not json at all".toByteArray()))
        assertNull(codec.decode(ByteArray(0)))
    }

    @Test
    fun `a record from a newer schema still decodes`() {
        val record = TransferableSignInRecord(
            schemaVersion = TransferableSignInRecord.SCHEMA_VERSION + 1,
            writtenAt = WRITTEN_AT,
            entries = listOf(envelopeEntry())
        )

        val decoded = codec.decode(codec.encode(record))

        assertNotNull(decoded)
        assertEquals(TransferableSignInRecord.SCHEMA_VERSION + 1, decoded?.schemaVersion)
        assertEquals(1, decoded?.entries?.size)
    }

    @Test
    fun `an unrecognised kind is dropped and its siblings are kept`() {
        val encoded = String(codec.encode(recordWithBothKinds()), Charsets.UTF_8)
            .replace("\"${TransferableSignInRecord.Kind.SECRET.name}\"", "\"FUTURE_KIND\"")

        val decoded = codec.decode(encoded.toByteArray(Charsets.UTF_8))

        assertNotNull(decoded)
        assertEquals(1, decoded?.entries?.size)
        assertEquals(TransferableSignInProviderKeys.GOOGLE_PRIMARY, decoded?.entries?.first()?.providerKey)
    }

    @Test
    fun `withEntry replaces the entry under the same provider key`() {
        val updated = TransferableSignInRecord.empty(WRITTEN_AT)
            .withEntry(envelopeEntry())
            .withEntry(envelopeEntry().copy(payload = mapOf("email" to "second@example.com")))

        assertEquals(1, updated.entries.size)
        assertEquals("second@example.com", updated.entries.first().payload["email"])
    }

    @Test
    fun `withoutProvider removes only the named provider`() {
        val remaining = recordWithBothKinds().withoutProvider(TransferableSignInProviderKeys.DROPBOX)

        assertEquals(1, remaining.entries.size)
        assertTrue(remaining.entries.none { it.providerKey == TransferableSignInProviderKeys.DROPBOX })
    }

    private fun recordWithBothKinds() = TransferableSignInRecord(
        schemaVersion = TransferableSignInRecord.SCHEMA_VERSION,
        writtenAt = WRITTEN_AT,
        entries = listOf(envelopeEntry(), secretEntry())
    )

    private fun envelopeEntry() = TransferableSignInRecord.Entry(
        providerKey = TransferableSignInProviderKeys.GOOGLE_PRIMARY,
        kind = TransferableSignInRecord.Kind.IDENTITY_ENVELOPE,
        payload = mapOf("email" to "user@example.com", "scopes" to "drive.readonly")
    )

    private fun secretEntry() = TransferableSignInRecord.Entry(
        providerKey = TransferableSignInProviderKeys.DROPBOX,
        kind = TransferableSignInRecord.Kind.SECRET,
        payload = mapOf("email" to "user@example.com", "credentials" to "{\"refresh_token\":\"x\"}")
    )

    private companion object {
        const val WRITTEN_AT = 1_756_800_000_000L
    }
}

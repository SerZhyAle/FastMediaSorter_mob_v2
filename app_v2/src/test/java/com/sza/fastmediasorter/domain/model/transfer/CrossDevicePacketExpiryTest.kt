package com.sza.fastmediasorter.domain.model.transfer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** S4231: expiry on Drive's clock (DEVICE-EXCHANGE item CR). */
class CrossDevicePacketExpiryTest {

    private val created = 1_700_000_000_000L

    @Test
    fun `files expire after seven days of drive time`() {
        val sixDays = created + TimeUnit.DAYS.toMillis(SIX)
        val eightDays = created + TimeUnit.DAYS.toMillis(EIGHT)
        assertFalse(expired(CrossDevicePayloadKind.FILES, sixDays))
        assertTrue(expired(CrossDevicePayloadKind.FILES, eightDays))
    }

    @Test
    fun `ttl seconds replace ttl days`() {
        val request = CrossDevicePacketExpiry.REQUEST_TTL_SECONDS
        val justBefore = created + TimeUnit.SECONDS.toMillis(request) - 1
        val justAfter = created + TimeUnit.SECONDS.toMillis(request) + 1
        assertFalse(expired(CrossDevicePayloadKind.REQUEST, justBefore, request))
        assertTrue(expired(CrossDevicePayloadKind.REQUEST, justAfter, request))
    }

    @Test
    fun `the clipboard never expires`() {
        val muchLater = created + TimeUnit.DAYS.toMillis(YEAR)
        assertFalse(expired(CrossDevicePayloadKind.CLIPBOARD, muchLater))
    }

    @Test
    fun `an upload without a manifest is abandoned after seven days`() {
        assertFalse(CrossDevicePacketExpiry.isAbandonedUpload(created, created + TimeUnit.DAYS.toMillis(SIX)))
        assertTrue(CrossDevicePacketExpiry.isAbandonedUpload(created, created + TimeUnit.DAYS.toMillis(EIGHT)))
    }

    @Test
    fun `the device clock never enters the decision`() {
        // Only Drive times are inputs: the same two Drive instants give the same answer whatever the
        // writer's createdAtEpochMs said.
        val manifest = CrossDevicePacketManifest(
            packetId = "p",
            createdAtEpochMs = 0L,
            senderDeviceName = "skewed",
            targetDeviceName = null,
            payloadKind = CrossDevicePayloadKind.FILES,
            fileNames = emptyList(),
            totalSizeBytes = 0L
        )
        val entry = CrossDevicePacketEntry(manifest, "folder", created)
        assertFalse(CrossDevicePacketExpiry.isExpired(entry, created + TimeUnit.DAYS.toMillis(SIX)))
    }

    private fun expired(kind: CrossDevicePayloadKind, nowMs: Long, ttlSeconds: Long? = null): Boolean =
        CrossDevicePacketExpiry.isExpired(kind, created, nowMs, ttlSeconds, SEVEN)

    private companion object {
        const val SIX = 6L
        const val SEVEN = 7
        const val EIGHT = 8L
        const val YEAR = 365L
    }
}

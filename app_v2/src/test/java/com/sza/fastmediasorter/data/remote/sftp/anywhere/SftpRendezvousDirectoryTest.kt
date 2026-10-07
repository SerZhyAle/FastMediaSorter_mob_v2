package com.sza.fastmediasorter.data.remote.sftp.anywhere

import com.sza.fastmediasorter.data.cloud.GoogleDriveSftpRendezvousDataSource
import com.sza.fastmediasorter.data.cloud.GoogleDriveSftpRendezvousDataSource.Entry
import com.sza.fastmediasorter.data.cloud.GoogleDriveSftpRendezvousDataSource.Snapshot
import com.sza.fastmediasorter.data.network.exceptions.NetworkErrorClassifier
import com.sza.fastmediasorter.data.network.exceptions.NetworkPeerUnreachableException
import com.sza.fastmediasorter.data.repository.settings.SftpRendezvousIdentityStore
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.SftpPairingPayload
import com.sza.fastmediasorter.domain.model.SftpRendezvousDevice
import com.sza.fastmediasorter.domain.model.SftpRendezvousRequest
import com.sza.fastmediasorter.domain.model.SftpRendezvousResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

/** The consumer side of the Drive channel, contract DEVICE-EXCHANGE section 8. */
@OptIn(ExperimentalCoroutinesApi::class)
class SftpRendezvousDirectoryTest {

    private val store = mockk<GoogleDriveSftpRendezvousDataSource>(relaxed = true)
    private val ids = mockk<SftpRendezvousIdentityStore>().also { coEvery { it.deviceId() } returns OWN_DEVICE }
    private val verdicts = SftpRendezvousVerdicts()
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    private var now = START_MS
    private val directory = SftpRendezvousDirectory(
        store,
        verdicts,
        ids,
        scope,
        Dispatchers.Unconfined,
    ).also { it.nowMs = { now } }

    @After
    fun tearDown() = runBlocking { scope.coroutineContext.job.cancelAndJoin() }

    private fun published(
        writtenAtMs: Long,
        presence: String = SftpRendezvousDevice.PRESENCE_ONLINE,
        product: String = SftpRendezvousDevice.PRODUCT_FMS_ANDROID,
        requests: List<Entry<SftpRendezvousRequest>> = emptyList(),
    ) {
        val device = SftpRendezvousDevice(
            PRODUCER, "Pixel", product, null, null, emptyList(), presence, writtenAtMs, writtenAtMs, TTL_SECONDS,
        )
        val descriptor = SftpPairingPayload(listOf(NEW_ADDRESS.host), NEW_ADDRESS.port, "fms", "pw", PIN).encode()
        val resource = SftpRendezvousResource(
            "res",
            PRODUCER,
            SftpRendezvousResource.KIND_SFTP_SHARE,
            "Pixel",
            descriptor,
            writtenAtMs,
            writtenAtMs,
            TTL_SECONDS,
        )
        val snapshot = Snapshot(
            listOf(Entry("d", "fmsx-device-$PRODUCER.json", 0L, device)),
            listOf(Entry("r", "fmsx-resource-res.json", 0L, resource)),
            requests,
        )
        coEvery { store.snapshot(false) } returns Result.success(snapshot)
    }

    @Test
    fun `a published address is served by the descriptor's pin and maps back to it`() = runBlocking {
        published(START_MS)
        directory.refreshAfterFailure()
        assertEquals(listOf(NEW_ADDRESS), directory.endpointsFor(PIN))
        assertEquals(PIN, directory.fingerprintForEndpoint(NEW_ADDRESS))
        assertNull(directory.fingerprintForEndpoint(OLD_ADDRESS))
    }

    @Test
    fun `another product's records are read the same way`() = runBlocking {
        published(START_MS, product = "fms-windows")
        directory.refreshAfterFailure()
        assertEquals(listOf(NEW_ADDRESS), directory.endpointsFor(PIN))
    }

    @Test
    fun `an online producer inside its TTL nobody reaches gets the verdict, not a request`() = runBlocking {
        published(START_MS)
        directory.refreshAfterFailure()
        directory.onNoCandidateAnswered(PIN, listOf(OLD_ADDRESS, NEW_ADDRESS))
        assertTrue(verdicts.isUnreachableFromHere(OLD_ADDRESS.host, OLD_ADDRESS.port))
        val annotated = verdicts.annotate(OLD_ADDRESS.host, OLD_ADDRESS.port, SocketTimeoutException("timed out"))
        assertTrue(NetworkErrorClassifier.classify(annotated) is NetworkPeerUnreachableException)
        coVerify(exactly = 0) { store.writeRequest(any()) }
    }

    @Test
    fun `a stale producer is asked by device id once per request TTL`() = runBlocking {
        published(START_MS - TTL_SECONDS * 1_000L - 1L)
        coEvery { store.writeRequest(any()) } returns Result.success("request-id")
        directory.refreshAfterFailure()
        directory.onNoCandidateAnswered(PIN, listOf(OLD_ADDRESS))
        directory.onNoCandidateAnswered(PIN, listOf(OLD_ADDRESS))
        assertFalse(verdicts.isUnreachableFromHere(OLD_ADDRESS.host, OLD_ADDRESS.port))
        coVerify(exactly = 1) {
            store.writeRequest(
                match {
                    it.toDeviceId == PRODUCER && it.action == SftpRendezvousRequest.ACTION_ANNOUNCE &&
                        it.fromDeviceId == OWN_DEVICE
                },
            )
        }
    }

    @Test
    fun `an offline producer is asked even inside its TTL`() = runBlocking {
        published(START_MS, presence = SftpRendezvousDevice.PRESENCE_OFFLINE)
        coEvery { store.writeRequest(any()) } returns Result.success("request-id")
        directory.refreshAfterFailure()
        directory.onNoCandidateAnswered(PIN, listOf(OLD_ADDRESS))
        assertFalse(verdicts.isUnreachableFromHere(OLD_ADDRESS.host, OLD_ADDRESS.port))
        coVerify(exactly = 1) { store.writeRequest(match { it.toDeviceId == PRODUCER }) }
    }

    @Test
    fun `only this device's expired requests are deleted`() = runBlocking {
        val expired = START_MS - 2 * TTL_SECONDS * 1_000L
        val own = SftpRendezvousRequest(PRODUCER, SftpRendezvousRequest.ACTION_ANNOUNCE, OWN_DEVICE, expired, 60L)
        val foreign = own.copy(fromDeviceId = "someone-else")
        val fresh = own.copy(writtenAtMs = START_MS)
        published(
            START_MS,
            requests = listOf(
                Entry("own", "fmsx-request-a.json", 0L, own),
                Entry("foreign", "fmsx-request-b.json", 0L, foreign),
                Entry("fresh", "fmsx-request-c.json", 0L, fresh),
            ),
        )
        directory.refreshAfterFailure()
        coVerify(exactly = 1) { store.delete("own") }
        coVerify(exactly = 0) { store.delete("foreign") }
        coVerify(exactly = 0) { store.delete("fresh") }
    }

    @Test
    fun `an authentication failure is never masked by the verdict`() {
        verdicts.markUnreachableFromHere(listOf(OLD_ADDRESS))
        val auth = com.jcraft.jsch.JSchException("Auth fail")
        assertTrue(verdicts.annotate(OLD_ADDRESS.host, OLD_ADDRESS.port, auth) === auth)
    }

    private companion object {
        const val START_MS = 10_000_000L
        const val TTL_SECONDS = 1_800L
        const val PIN = "SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val PRODUCER = "producer-device"
        const val OWN_DEVICE = "own-device"
        val OLD_ADDRESS = HostPort("192.168.1.20", 2222)
        val NEW_ADDRESS = HostPort("192.168.1.31", 2222)
    }
}

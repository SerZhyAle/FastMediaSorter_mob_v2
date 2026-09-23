package com.sza.fastmediasorter.data.remote.sftp

import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.domain.model.ResourceType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

/**
 * S3415: every runtime SFTP session must carry the resource's stored host-key pin, whichever address
 * of the resource it dials, and a pin the caller supplied must survive untouched.
 */
class SftpHostKeyPinRegistryTest {

    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    private val dao = mockk<ResourceDao>()
    private val mdns = mockk<CompanionMdnsDiscovery>()
    private val resources = MutableStateFlow<List<ResourceEntity>>(emptyList())

    init {
        every { dao.getAllResources() } returns resources
        every { mdns.endpointForFingerprint(any()) } returns null
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
    }

    private fun fingerprint(seed: Int): String =
        "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(ByteArray(32) { (it + seed).toByte() })

    private fun sftpResource(id: Long, path: String, pin: String?, alternates: String? = null) = ResourceEntity(
        id = id,
        name = "Server $id",
        path = path,
        type = ResourceType.SFTP,
        hostKeyFingerprint = pin,
        altAccessPaths = alternates,
    )

    private fun info(host: String, port: Int = 22, pin: String? = null) =
        SftpClient.SftpConnectionInfo(host = host, port = port, username = "u", expectedFingerprint = pin)

    private fun registry() = SftpHostKeyPinRegistry(dao, mdns, scope)

    private fun pinned(registry: SftpHostKeyPinRegistry, request: SftpClient.SftpConnectionInfo): String? =
        runBlocking { withTimeout(TIMEOUT_MS) { registry.withPin(request) } }.expectedFingerprint

    @Test
    fun `primary address gets the stored pin`() {
        val pin = fingerprint(1)
        resources.value = listOf(sftpResource(1, "sftp://10.0.0.5:2222/Share", pin))

        assertEquals(pin, pinned(registry(), info("10.0.0.5", 2222)))
    }

    @Test
    fun `alternate address of the resource gets the same pin`() {
        val pin = fingerprint(2)
        resources.value = listOf(sftpResource(1, "sftp://10.0.0.5:22/Share", pin, "example.org:6022;bad-entry"))

        assertEquals(pin, pinned(registry(), info("example.org", 6022)))
    }

    @Test
    fun `mdns endpoint found under the fingerprint gets that pin`() {
        val pin = fingerprint(3)
        resources.value = listOf(sftpResource(1, "sftp://203.0.113.9:22/Share", pin))
        every { mdns.endpointForFingerprint(pin) } returns HostPort("192.168.1.40", 2022)

        assertEquals(pin, pinned(registry(), info("192.168.1.40", 2022)))
    }

    @Test
    fun `caller supplied pin is never replaced`() {
        val stored = fingerprint(4)
        val supplied = fingerprint(5)
        resources.value = listOf(sftpResource(1, "sftp://10.0.0.5:22/Share", stored))

        assertEquals(supplied, pinned(registry(), info("10.0.0.5", pin = supplied)))
    }

    @Test
    fun `unpinned resource and unknown host stay without a pin`() {
        resources.value = listOf(sftpResource(1, "sftp://10.0.0.5:22/Share", null))

        assertNull(pinned(registry(), info("10.0.0.5")))
        assertNull(pinned(registry(), info("10.0.0.99")))
    }

    @Test
    fun `newer resource wins when two pins disagree on one address`() {
        val older = fingerprint(6)
        val newer = fingerprint(7)
        resources.value = listOf(
            sftpResource(9, "sftp://10.0.0.5:22/B", newer),
            sftpResource(3, "sftp://10.0.0.5:22/A", older),
        )

        assertEquals(newer, pinned(registry(), info("10.0.0.5")))
    }

    @Test
    fun `a re-paired resource takes effect without a restart`() {
        val first = fingerprint(8)
        val second = fingerprint(9)
        resources.value = listOf(sftpResource(1, "sftp://10.0.0.5:22/Share", first))
        val registry = registry()
        assertEquals(first, pinned(registry, info("10.0.0.5")))

        resources.value = listOf(sftpResource(1, "sftp://10.0.0.5:22/Share", second))

        runBlocking {
            withTimeout(TIMEOUT_MS) {
                while (registry.withPin(info("10.0.0.5")).expectedFingerprint != second) {
                    kotlinx.coroutines.delay(POLL_MS)
                }
            }
        }
    }

    @Test
    fun `blocking path returns the same pin`() {
        val pin = fingerprint(10)
        resources.value = listOf(sftpResource(1, "sftp://10.0.0.5:22/Share", pin))

        assertEquals(pin, registry().withPinBlocking(info("10.0.0.5")).expectedFingerprint)
    }

    @Test
    fun `a failing resources query degrades to no pin instead of hanging`() {
        every { dao.getAllResources() } returns flow { error("db closed") }

        assertNull(pinned(registry(), info("10.0.0.5")))
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 10L
    }
}

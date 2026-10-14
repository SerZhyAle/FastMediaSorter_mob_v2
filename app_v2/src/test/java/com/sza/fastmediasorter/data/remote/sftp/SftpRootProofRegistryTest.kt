package com.sza.fastmediasorter.data.remote.sftp

import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.data.network.exceptions.NetworkPinRefusedException
import com.sza.fastmediasorter.data.remote.exchange.ExchangeResourcePinStore
import com.sza.fastmediasorter.domain.model.ExchangeBinding
import com.sza.fastmediasorter.domain.model.ResourceType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SftpRootProofRegistryTest {

    private val pins = MemoryPins()

    private fun TestScope.registry(rows: List<ResourceEntity>): SftpRootProofRegistry {
        val dao = mockk<ResourceDao>(relaxed = true)
        every { dao.getAllResources() } returns flowOf(rows)
        return SftpRootProofRegistry(dao, pins, CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))
    }

    private fun entity(
        path: String,
        resourceId: String? = RESOURCE,
        account: String? = ACCOUNT,
        alt: String? = null,
    ) = ResourceEntity(
        id = 1L,
        name = "R",
        path = path,
        type = ResourceType.SFTP,
        exchangeAccount = account,
        exchangeResourceId = resourceId,
        altAccessPaths = alt,
    )

    private fun info(host: String = HOST, port: Int = PORT) =
        SftpClient.SftpConnectionInfo(host = host, port = port, username = "fms", password = "shared")

    @Test
    fun `a root covers itself and its subtree but not a sibling that shares its prefix`() {
        assertTrue(SftpRootProofRegistry.covers("Music", "/Music"))
        assertTrue(SftpRootProofRegistry.covers("Music", "/Music/Live/a.flac"))
        assertTrue(SftpRootProofRegistry.covers("Music", "Music/a.flac"))
        assertFalse(SftpRootProofRegistry.covers("Music", "/MusicBox/a.flac"))
        assertFalse(SftpRootProofRegistry.covers("Music", "/"))
        assertTrue(SftpRootProofRegistry.covers("A/B", "/A/B/c"))
    }

    @Test
    fun `every address of a bound resource maps to its root and binding`() {
        val roots = SftpRootProofRegistry.buildRoots(
            listOf(
                entity("sftp://$HOST:$PORT/Music", alt = "10.0.0.9:2222;tunnel.example:7000"),
                entity("sftp://$HOST:$PORT/Free", resourceId = null),
                entity("sftp://other:22/Docs", account = null),
            )
        )

        val expected = SftpRootProofRegistry.Root("Music", ExchangeBinding(ACCOUNT, RESOURCE))
        assertEquals(listOf(expected), roots["$HOST:$PORT"])
        assertEquals(listOf(expected), roots["10.0.0.9:2222"])
        assertEquals(listOf(expected), roots["tunnel.example:7000"])
        assertNull(roots["other:22"])
    }

    @Test
    fun `an operation under a protected root gets the stored PIN and its root`() = runTest {
        pins.values[BINDING] = "4711"
        val registry = registry(listOf(entity("sftp://$HOST:$PORT/Music")))

        val proven = registry.withProof(info(), "/Music/Live/a.flac")

        assertEquals(SftpRootProof("Music", "4711"), proven.rootProof)
    }

    @Test
    fun `a path outside the root, or a root with no stored PIN, passes through unchanged`() = runTest {
        val registry = registry(listOf(entity("sftp://$HOST:$PORT/Music")))
        val given = info()

        assertSame(given, registry.withProof(given, "/Music/a.flac"))
        pins.values[BINDING] = "4711"
        assertSame(given, registry.withProof(given, "/MusicBox/a.flac"))
        assertSame(given, registry.withProof(given, null))
        assertSame(given, registry.withProof(given, "/"))
    }

    @Test
    fun `a proof the caller supplied is never replaced`() = runTest {
        pins.values[BINDING] = "4711"
        val registry = registry(listOf(entity("sftp://$HOST:$PORT/Music")))
        val candidate = info().copy(rootProof = SftpRootProof("Music", "just-typed"))

        assertSame(candidate, registry.withProof(candidate, "/Music"))
    }

    @Test
    fun `a refused proof marks its binding and the next accepted one clears it`() = runTest {
        pins.values[BINDING] = "4711"
        val registry = registry(listOf(entity("sftp://$HOST:$PORT/Music")))
        val proven = registry.withProof(info(), "/Music")

        registry.noteOutcome(proven, Result.failure<Unit>(NetworkPinRefusedException()))
        assertEquals(setOf(BINDING), registry.refusals.value)

        registry.noteOutcome(proven, Result.success(Unit))
        assertTrue(registry.refusals.value.isEmpty())
    }

    @Test
    fun `an ordinary failure or a session without a proof is not a refusal`() = runTest {
        pins.values[BINDING] = "4711"
        val registry = registry(listOf(entity("sftp://$HOST:$PORT/Music")))
        val proven = registry.withProof(info(), "/Music")

        registry.noteOutcome(proven, Result.failure<Unit>(IOException("reset")))
        registry.noteOutcome(info(), Result.failure<Unit>(NetworkPinRefusedException()))

        assertTrue(registry.refusals.value.isEmpty())
    }

    @Test
    fun `nothing printable carries the PIN`() {
        val proof = SftpRootProof("Music", "4711-secret")

        assertFalse(proof.toString().contains("4711"))
        assertFalse(proof.toString().contains("Music"))
        assertFalse(NetworkPinRefusedException(RuntimeException("Auth fail")).message.orEmpty().contains("4711"))
    }

    private class MemoryPins : ExchangeResourcePinStore {
        val values = mutableMapOf<ExchangeBinding, String>()

        override suspend fun pin(binding: ExchangeBinding): String? = values[binding]

        override suspend fun save(binding: ExchangeBinding, pin: String): Boolean {
            values[binding] = pin
            return true
        }

        override suspend fun forget(binding: ExchangeBinding) {
            values.remove(binding)
        }

        override suspend fun move(from: String, to: String, resourceIds: Collection<String>) = Unit
    }

    private companion object {
        const val HOST = "192.168.1.5"
        const val PORT = 2222
        const val ACCOUNT = "exchange.example.org:8443/alice"
        const val RESOURCE = "d5sTbSIZ1diWe7u7mtCesw"
        val BINDING = ExchangeBinding(ACCOUNT, RESOURCE)
    }
}

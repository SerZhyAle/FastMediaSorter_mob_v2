package com.sza.fastmediasorter.wear.data.preferences

import com.sza.fastmediasorter.wear.data.repository.InMemorySharedPreferences
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearSourceTombstonePayload
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** S3832: overlapping source and tombstone writes must all land - none may overwrite another's edit. */
class NetworkSourceRepositoryConcurrencyTest {

    private val edits = 20

    private fun repository(prefs: InMemorySharedPreferences) = NetworkSourceRepositoryImpl(
        encryptedPrefs = prefs,
        newSmbProbe = { mockk(relaxed = true) },
        ftpConnectionTest = mockk(relaxed = true),
        sftpConnectionTest = mockk(relaxed = true)
    )

    private fun source(n: Int) = NetworkSource(
        id = "src-$n",
        type = NetworkSourceType.SMB,
        name = "share-$n",
        server = "10.0.0.$n",
        username = "user",
        password = "secret"
    )

    @Test
    fun `overlapping imports keep every source`() = runBlocking {
        val repo = repository(InMemorySharedPreferences())

        (1..edits).map { n -> async(Dispatchers.Default) { repo.upsertSource(source(n)) } }.awaitAll()

        assertEquals(edits, repo.getAllSources().size)
    }

    @Test
    fun `an import overlapping deletes neither resurrects nor loses a source`() = runBlocking {
        val repo = repository(InMemorySharedPreferences())
        (1..edits).forEach { n -> repo.addSource(source(n)) }

        val deletes = (1..edits).map { n -> async(Dispatchers.Default) { repo.deleteSource("src-$n") } }
        val imports = (edits + 1..edits * 2).map { n -> async(Dispatchers.Default) { repo.upsertSource(source(n)) } }
        (deletes + imports).awaitAll()

        val ids = repo.getAllSources().map { it.id }.toSet()
        assertEquals((edits + 1..edits * 2).map { "src-$it" }.toSet(), ids)
    }

    @Test
    fun `overlapping tombstones keep every delete event`() = runBlocking {
        val repo = repository(InMemorySharedPreferences())

        (1..edits).map { n ->
            val tombstone = WearSourceTombstonePayload(id = "src-$n", deletedAt = n.toLong())
            async(Dispatchers.Default) { repo.recordTombstone(tombstone) }
        }.awaitAll()

        assertEquals(edits, repo.getTombstones().size)
    }
}

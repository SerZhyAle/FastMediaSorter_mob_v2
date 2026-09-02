package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * S2149: the empty-set case is the only thing that carries an unpin on the phone across to the watch,
 * and the survives-a-restart case is what keeps a watch out of range of the phone ranking correctly.
 */
class WearPhonePinsRepositoryTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private fun repository(): WearPhonePinsRepository {
        val context = mockk<Context>()
        every { context.filesDir } returns temporaryFolder.root
        return WearPhonePinsRepository(context, Gson())
    }

    @Test
    fun `replaceAll stores every received identity`() = runBlocking {
        val repository = repository()

        repository.replaceAll(listOf("web://host.tv/one", "web://host.tv/two"))

        assertEquals(
            setOf("web://host.tv/one", "web://host.tv/two"),
            repository.observe().value
        )
    }

    @Test
    fun `an empty set replaces the previous one rather than being ignored`() = runBlocking {
        val repository = repository()
        repository.replaceAll(listOf("web://host.tv/one"))

        repository.replaceAll(emptyList())

        assertEquals(emptySet<String>(), repository.observe().value)
    }

    @Test
    fun `the stored set survives a repository re-created over the same directory`() = runBlocking {
        repository().replaceAll(listOf("web://host.tv/one", "web://host.tv/two"))

        val reopened = repository()

        assertEquals(
            setOf("web://host.tv/one", "web://host.tv/two"),
            reopened.observe().value
        )
    }

    @Test
    fun `a watch that never received a set starts empty`() {
        assertEquals(emptySet<String>(), repository().observe().value)
    }
}

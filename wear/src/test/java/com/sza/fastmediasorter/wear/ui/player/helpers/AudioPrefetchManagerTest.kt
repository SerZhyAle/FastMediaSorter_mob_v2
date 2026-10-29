package com.sza.fastmediasorter.wear.ui.player.helpers

import android.net.Uri
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.SelectedMedia
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * S4471: what has to hold is the hand-over - a fetch is started once per file, the load that follows
 * receives exactly that fetch, and a fetch for another file is never mistaken for it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioPrefetchManagerTest {

    private val fetched = mutableListOf<String>()

    private fun selection(name: String, sourceId: String = "source") = SelectedMedia(
        file = WearMediaFile(
            id = name.hashCode().toLong(),
            name = name,
            uri = mockk<Uri>(relaxed = true),
            mimeType = "audio/mpeg",
            size = 1024L,
            dateModified = 0L
        ),
        isNetworkSource = true,
        streamUri = "sftp://host/$name",
        sourceId = sourceId
    )

    private fun manager(scope: CoroutineScope) = AudioPrefetchManager(scope) { chosen ->
        fetched.add(chosen.file.name)
        Result.success(File(chosen.file.name))
    }

    @Test
    fun `a repeated prefetch of the same file fetches once`() = runTest {
        val manager = manager(this)

        manager.prefetch(selection("b.mp3"))
        manager.prefetch(selection("b.mp3"))
        advanceUntilIdle()

        assertEquals(listOf("b.mp3"), fetched)
    }

    @Test
    fun `take hands the fetch over once`() = runTest {
        val manager = manager(this)
        manager.prefetch(selection("b.mp3"))

        val taken = manager.take(selection("b.mp3"))

        assertNotNull(taken)
        assertEquals("b.mp3", taken?.await()?.getOrNull()?.name)
        assertNull(manager.take(selection("b.mp3")))
    }

    @Test
    fun `take of another file or another source finds nothing`() = runTest {
        val manager = manager(this)
        manager.prefetch(selection("b.mp3"))

        assertNull(manager.take(selection("c.mp3")))
        assertNull(manager.take(selection("b.mp3", sourceId = "other")))
        assertNotNull(manager.take(selection("b.mp3")))
    }

    @Test
    fun `cancel forgets the held fetch`() = runTest {
        val manager = manager(this)
        manager.prefetch(selection("b.mp3"))

        manager.cancel()

        assertNull(manager.take(selection("b.mp3")))
    }
}

package com.sza.fastmediasorter.data.cloud

import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudListingPageCacheTest {

    private var now = 0L
    private val cache = CloudListingPageCache(clock = { now })
    private var loads = 0

    private val key = CloudListingPageCache.Key(
        path = "42",
        supportedTypes = setOf(MediaType.IMAGE),
        sizeFilter = null,
        scanSubdirectories = true,
        showHiddenFiles = false
    )

    private fun listing(vararg names: String): List<MediaFile> = names.map {
        MediaFile(name = it, path = "cloud://x/$it", type = MediaType.IMAGE, size = 1L, createdDate = 0L)
    }

    private suspend fun load(
        reuseMaxAgeMs: Long?,
        k: CloudListingPageCache.Key = key,
        result: List<MediaFile> = listing("a", "b")
    ) = cache.getOrLoad(k, reuseMaxAgeMs) {
        loads++
        result
    }

    @Test
    fun `later page reuses the listing the first page loaded`() = runTest {
        load(reuseMaxAgeMs = null)
        now = 1_000L
        load(reuseMaxAgeMs = 5_000L)
        assertEquals(1, loads)
    }

    @Test
    fun `first page always re-lists`() = runTest {
        load(reuseMaxAgeMs = null)
        load(reuseMaxAgeMs = null)
        assertEquals(2, loads)
    }

    @Test
    fun `expired listing is loaded again`() = runTest {
        load(reuseMaxAgeMs = null)
        now = 5_000L
        load(reuseMaxAgeMs = 5_000L)
        assertEquals(2, loads)
    }

    @Test
    fun `different key does not reuse the listing`() = runTest {
        load(reuseMaxAgeMs = null)
        load(reuseMaxAgeMs = 5_000L, k = key.copy(showHiddenFiles = true))
        assertEquals(2, loads)
    }

    @Test
    fun `empty listing is never served again`() = runTest {
        load(reuseMaxAgeMs = null, result = emptyList())
        load(reuseMaxAgeMs = 5_000L)
        assertEquals(2, loads)
    }

    @Test
    fun `reused listing is the stored one`() = runTest {
        load(reuseMaxAgeMs = null, result = listing("x", "y", "z"))
        val reused = load(reuseMaxAgeMs = 5_000L)
        assertEquals(listOf("x", "y", "z"), reused.map { it.name })
    }
}

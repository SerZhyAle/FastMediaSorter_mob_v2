package com.sza.fastmediasorter.data.paging

import androidx.paging.PagingSource
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.usecase.MediaFilePage
import com.sza.fastmediasorter.domain.usecase.MediaScanner
import com.sza.fastmediasorter.domain.usecase.MediaScannerFactory
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import com.sza.fastmediasorter.testing.MainDispatcherRule
import com.sza.fastmediasorter.testing.createMediaFile
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaFilesPagingSourceTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val scanner: MediaScanner = mockk()
    private val factory: MediaScannerFactory = mockk()
    private val resource: MediaResource = createMediaResource()
    private val sizeFilter = SizeFilter(0, Long.MAX_VALUE, 0, Long.MAX_VALUE, 0, Long.MAX_VALUE)

    private fun source(sortMode: SortMode) =
        MediaFilesPagingSource(resource, sortMode, sizeFilter, factory).also {
            every { factory.getScanner(resource.type) } returns scanner
        }

    private fun loadParams(key: Int?) =
        PagingSource.LoadParams.Refresh(key, MediaFilesPagingSource.PAGE_SIZE, false)

    @Test
    fun `NAME_ASC passes scanner order through unchanged`() = runTest {
        val files = listOf(createMediaFile(name = "b.jpg"), createMediaFile(name = "a.jpg"))
        coEvery {
            scanner.scanFolderPaged(any(), any(), any(), 0, MediaFilesPagingSource.PAGE_SIZE, any(), any(), any())
        } returns MediaFilePage(files, hasMore = false)

        val result = source(SortMode.NAME_ASC).load(loadParams(null))

        result as PagingSource.LoadResult.Page
        assertEquals(listOf("b.jpg", "a.jpg"), result.data.map { it.name })
        assertNull(result.prevKey)
        assertNull(result.nextKey)
    }

    @Test
    fun `NAME_DESC reverses the scanner page`() = runTest {
        val files = listOf(createMediaFile(name = "a.jpg"), createMediaFile(name = "b.jpg"))
        coEvery {
            scanner.scanFolderPaged(any(), any(), any(), any(), any(), any(), any(), any())
        } returns MediaFilePage(files, hasMore = false)

        val result = source(SortMode.NAME_DESC).load(loadParams(0))

        result as PagingSource.LoadResult.Page
        assertEquals(listOf("b.jpg", "a.jpg"), result.data.map { it.name })
    }

    @Test
    fun `SIZE_ASC sorts the page by size`() = runTest {
        val files = listOf(
            createMediaFile(name = "big.jpg", size = 300),
            createMediaFile(name = "small.jpg", size = 100),
            createMediaFile(name = "mid.jpg", size = 200),
        )
        coEvery {
            scanner.scanFolderPaged(any(), any(), any(), any(), any(), any(), any(), any())
        } returns MediaFilePage(files, hasMore = false)

        val result = source(SortMode.SIZE_ASC).load(loadParams(0))

        result as PagingSource.LoadResult.Page
        assertEquals(listOf("small.jpg", "mid.jpg", "big.jpg"), result.data.map { it.name })
    }

    @Test
    fun `offset is page index times page size`() = runTest {
        coEvery {
            scanner.scanFolderPaged(any(), any(), any(), any(), any(), any(), any(), any())
        } returns MediaFilePage(emptyList(), hasMore = false)

        source(SortMode.NAME_ASC).load(loadParams(2))

        coVerify {
            scanner.scanFolderPaged(
                resource.path,
                resource.supportedMediaTypes,
                sizeFilter,
                2 * MediaFilesPagingSource.PAGE_SIZE,
                MediaFilesPagingSource.PAGE_SIZE,
                resource.credentialsId,
                resource.scanSubdirectories,
                any(),
            )
        }
    }

    @Test
    fun `prevKey is null on first page and set on later pages`() = runTest {
        coEvery {
            scanner.scanFolderPaged(any(), any(), any(), any(), any(), any(), any(), any())
        } returns MediaFilePage(emptyList(), hasMore = true)

        val first = source(SortMode.NAME_ASC).load(loadParams(0)) as PagingSource.LoadResult.Page
        assertNull(first.prevKey)
        assertEquals(1, first.nextKey)

        val third = source(SortMode.NAME_ASC).load(loadParams(3)) as PagingSource.LoadResult.Page
        assertEquals(2, third.prevKey)
        assertEquals(4, third.nextKey)
    }

    @Test
    fun `nextKey is null when scanner reports no more`() = runTest {
        coEvery {
            scanner.scanFolderPaged(any(), any(), any(), any(), any(), any(), any(), any())
        } returns MediaFilePage(listOf(createMediaFile()), hasMore = false)

        val result = source(SortMode.NAME_ASC).load(loadParams(0)) as PagingSource.LoadResult.Page
        assertNull(result.nextKey)
    }

    @Test
    fun `scanner failure yields a LoadResult Error`() = runTest {
        val boom = RuntimeException("scan failed")
        coEvery {
            scanner.scanFolderPaged(any(), any(), any(), any(), any(), any(), any(), any())
        } throws boom

        val result = source(SortMode.NAME_ASC).load(loadParams(0))

        result as PagingSource.LoadResult.Error
        assertEquals(boom, result.throwable)
    }

    @Test
    fun `null key is treated as page zero`() = runTest {
        coEvery {
            scanner.scanFolderPaged(any(), any(), any(), 0, any(), any(), any(), any())
        } returns MediaFilePage(emptyList(), hasMore = false)

        val result = source(SortMode.NAME_ASC).load(loadParams(null)) as PagingSource.LoadResult.Page
        assertTrue(result.data.isEmpty())
        assertNull(result.prevKey)
    }
}

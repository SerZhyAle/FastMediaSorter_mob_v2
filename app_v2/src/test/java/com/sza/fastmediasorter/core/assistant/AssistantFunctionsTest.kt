package com.sza.fastmediasorter.core.assistant

import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.assistant.model.OpenFolderParams
import com.sza.fastmediasorter.core.assistant.model.OpenMediaParams
import com.sza.fastmediasorter.core.assistant.model.SearchMediaParams
import com.sza.fastmediasorter.core.assistant.navigation.OpenAssistantFolderUseCase
import com.sza.fastmediasorter.core.assistant.navigation.OpenAssistantMediaUseCase
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.domain.usecase.assistant.SearchAssistantMediaUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssistantFunctionsTest {

    private val resourceRepository: ResourceRepository = mockk(relaxed = true)
    private val getMediaFilesUseCase: GetMediaFilesUseCase = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private lateinit var searchUseCase: SearchAssistantMediaUseCase
    private lateinit var openMediaUseCase: OpenAssistantMediaUseCase
    private lateinit var openFolderUseCase: OpenAssistantFolderUseCase

    @Before
    fun setup() {
        searchUseCase = SearchAssistantMediaUseCase(resourceRepository, getMediaFilesUseCase)
        openMediaUseCase = OpenAssistantMediaUseCase(context)
        openFolderUseCase = OpenAssistantFolderUseCase(context)
    }

    @Test
    fun searchMedia_emptyQuery_returnsEmpty() = runTest {
        val result = searchUseCase(SearchMediaParams(query = ""))
        assertEquals(0, result.totalCount)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun searchMedia_matchingFiles_returnsFoundItems() = runTest {
        val resource = MediaResource(
            id = 1L,
            name = "PC Photos",
            path = "/storage/pc",
            type = ResourceType.SMB
        )
        val file1 = MediaFile(
            name = "vacation_beach.jpg",
            path = "/storage/pc/vacation_beach.jpg",
            type = MediaType.IMAGE,
            size = 1024L,
            createdDate = 1000L
        )
        val file2 = MediaFile(
            name = "work_report.pdf",
            path = "/storage/pc/work_report.pdf",
            type = MediaType.PDF,
            size = 2048L,
            createdDate = 2000L
        )

        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(resource)
        every { getMediaFilesUseCase(resource = resource) } returns flowOf(listOf(file1, file2))

        val result = searchUseCase(SearchMediaParams(query = "beach"))
        assertEquals(1, result.totalCount)
        assertEquals(1, result.items.size)
        assertEquals("vacation_beach.jpg", result.items[0].displayName)
        assertEquals(1L, result.items[0].resourceId)
        assertFalse(result.items[0].isFolder)
    }

    @Test
    fun openMedia_launchesPlayerActivityIntent() {
        val params = OpenMediaParams(resourceId = 42L, filePath = "/path/photo.jpg")
        val result = openMediaUseCase(params)

        assertTrue(result.success)
        verify {
            context.startActivity(any<Intent>())
        }
    }

    @Test
    fun openFolder_launchesMainActivityBrowseIntent() {
        val params = OpenFolderParams(resourceId = 42L)
        val result = openFolderUseCase(params)

        assertTrue(result.success)
        verify {
            context.startActivity(any<Intent>())
        }
    }
}

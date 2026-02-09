package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.DisplayMode
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetDestinationsUseCaseTest {

    private val resourceRepository: ResourceRepository = mockk()
    private val settingsRepository: SettingsRepository = mockk()
    private lateinit var useCase: GetDestinationsUseCase

    @Before
    fun setup() {
        useCase = GetDestinationsUseCase(resourceRepository, settingsRepository)
    }

    @Test
    fun `invoke should return only writable destinations sorted by order`() = runTest {
        // Given
        val resource1 = createResource(id = 1, isDestination = true, destinationOrder = 2, isReadOnly = false)
        val resource2 = createResource(id = 2, isDestination = true, destinationOrder = 1, isReadOnly = false)
        val resource3 = createResource(id = 3, isDestination = true, destinationOrder = 3, isReadOnly = true) // READ ONLY
        val resource4 = createResource(id = 4, isDestination = false, destinationOrder = 4, isReadOnly = false)

        val settings = AppSettings(maxRecipients = 10)

        coEvery { resourceRepository.getAllResources() } returns flowOf(listOf(resource1, resource2, resource3, resource4))
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)

        // When
        val result = useCase().first()

        // Then
        assertEquals(2, result.size)
        assertEquals(2L, result[0].id)
        assertEquals(1L, result[1].id)
    }
    
    @Test
    fun `getDestinationsExcluding should exclude read-only resources`() = runTest {
         // Given
        val resource1 = createResource(id = 1, isDestination = true, destinationOrder = 1, isReadOnly = false)
        val resource2 = createResource(id = 2, isDestination = true, destinationOrder = 2, isReadOnly = true) // READ ONLY

        val settings = AppSettings(maxRecipients = 10)

        coEvery { resourceRepository.getAllResourcesSync() } returns listOf(resource1, resource2)
        coEvery { settingsRepository.getSettings() } returns flowOf(settings)

        // When
        val result = useCase.getDestinationsExcluding(999)

        // Then
        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
    }

    private fun createResource(
        id: Long,
        isDestination: Boolean,
        destinationOrder: Int?,
        isReadOnly: Boolean
    ): MediaResource {
        return MediaResource(
            id = id,
            name = "Resource $id",
            path = "/path/$id",
            type = ResourceType.LOCAL,
            isDestination = isDestination,
            destinationOrder = destinationOrder,
            isWritable = true,
            isReadOnly = isReadOnly,
            displayMode = DisplayMode.LIST,
            sortMode = SortMode.NAME_ASC
        )
    }
}

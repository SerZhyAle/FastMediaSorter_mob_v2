package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.util.DestinationColors
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AddResourceAsDestinationUseCaseTest {

    private val getDestinations = mockk<GetDestinationsUseCase>()
    private val updateResource = mockk<UpdateResourceUseCase>()
    private lateinit var useCase: AddResourceAsDestinationUseCase

    @Before
    fun setup() {
        useCase = AddResourceAsDestinationUseCase(getDestinations, updateResource)
    }

    @Test
    fun `fails when resource is read-only`() = runTest {
        val result = useCase(createMediaResource(isReadOnly = true))
        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when local resource has no probed write access`() = runTest {
        val result = useCase(createMediaResource(isReadOnly = false, isWritable = false))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { getDestinations.getNextAvailableOrder() }
    }

    @Test
    fun `fails when resource is already a destination`() = runTest {
        val result = useCase(createMediaResource(isDestination = true, isWritable = true))
        assertTrue(result.isFailure)
    }

    @Test
    fun `fails when destinations list is full`() = runTest {
        coEvery { getDestinations.getNextAvailableOrder() } returns -1
        val result = useCase(createMediaResource(isWritable = true))
        assertTrue(result.isFailure)
    }

    @Test
    fun `assigns next order and matching color on success`() = runTest {
        coEvery { getDestinations.getNextAvailableOrder() } returns 2
        val saved = slot<MediaResource>()
        coEvery { updateResource(capture(saved)) } returns Result.success(Unit)

        // S2625: allowsWriteOperations() demands the probed isWritable for LOCAL, not only isReadOnly.
        val result = useCase(createMediaResource(name = "Dest", isWritable = true))

        assertTrue(result.isSuccess)
        assertEquals(true, saved.captured.isDestination)
        assertEquals(2, saved.captured.destinationOrder)
        assertEquals(DestinationColors.getColorForDestination(2), saved.captured.destinationColor)
    }
}

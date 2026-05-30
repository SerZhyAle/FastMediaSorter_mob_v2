package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry
import com.sza.fastmediasorter.data.local.staging.StagingDirectoryProvider
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM coverage for [CreateDrawingUseCase] input-validation guards. These branches return before any
 * Bitmap/Canvas work (buildBlankDrawingBytes), so they are pure-JVM. The success path needs Android
 * graphics + display metrics and is intentionally not covered here (partial coverage).
 */
class CreateDrawingUseCaseTest {

    private val context = mockk<Context>(relaxed = true)
    private val stagingDirectoryProvider = mockk<StagingDirectoryProvider>(relaxed = true)
    private val stagingRegistry = mockk<LocalStagingRegistry>(relaxed = true)
    private lateinit var useCase: CreateDrawingUseCase

    @Before
    fun setup() {
        useCase = CreateDrawingUseCase(context, stagingDirectoryProvider, stagingRegistry)
    }

    @Test
    fun `read-only resource is rejected`() = runTest {
        val resource = createMediaResource(isReadOnly = true)

        val result = useCase(resource, "/parent", "drawing.jpg")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("read-only") == true)
    }

    @Test
    fun `blank file name is rejected`() = runTest {
        val resource = createMediaResource(isReadOnly = false)

        val result = useCase(resource, "/parent", "   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("empty") == true)
    }

    @Test
    fun `file name with forbidden characters is rejected`() = runTest {
        val resource = createMediaResource(isReadOnly = false)

        val result = useCase(resource, "/parent", "bad/name?.jpg")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("invalid characters") == true)
    }

    @Test
    fun `overlong file name is rejected`() = runTest {
        val resource = createMediaResource(isReadOnly = false)

        val result = useCase(resource, "/parent", "a".repeat(256))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("too long") == true)
    }
}

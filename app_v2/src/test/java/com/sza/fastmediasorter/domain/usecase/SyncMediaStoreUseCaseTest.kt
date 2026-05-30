package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.testing.createMediaResource
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * JVM coverage for [SyncMediaStoreUseCase] guard/early-return branches. The actual MediaStore
 * notification (MediaStoreNotifier -> Environment) is Android-bound; tests cover the non-local,
 * virtual, invalid-directory, and empty/hidden-only directory paths that resolve before any
 * notification work and therefore stay deterministic on the JVM.
 */
class SyncMediaStoreUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private lateinit var useCase: SyncMediaStoreUseCase

    @Before
    fun setup() {
        useCase = SyncMediaStoreUseCase(context)
    }

    @Test
    fun `non-local resource is skipped with zero count`() = runTest {
        val resource = createMediaResource(type = ResourceType.SMB, path = "smb://host/share")

        val result = useCase.invoke(resource)

        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `virtual resource is skipped with zero count`() = runTest {
        val resource = createMediaResource(type = ResourceType.LOCAL, path = "virtual://camera")

        val result = useCase.invoke(resource)

        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `missing directory yields failure`() = runTest {
        val resource = createMediaResource(type = ResourceType.LOCAL, path = "/no/such/dir")

        val result = useCase.invoke(resource)

        assertTrue(result.isFailure)
    }

    @Test
    fun `empty directory syncs zero files`() = runTest {
        val dir = tempFolder.newFolder("empty")
        val resource = createMediaResource(type = ResourceType.LOCAL, path = dir.absolutePath)

        val result = useCase.invoke(resource)

        assertEquals(0, result.getOrThrow())
    }

    @Test
    fun `directory with only hidden files syncs zero files`() = runTest {
        val dir = tempFolder.newFolder("hidden")
        File(dir, ".secret").writeText("x")
        File(dir, ".cache").writeText("y")
        val resource = createMediaResource(type = ResourceType.LOCAL, path = dir.absolutePath)

        val result = useCase.invoke(resource)

        // Hidden files (dot-prefixed) are filtered out before any notification.
        assertEquals(0, result.getOrThrow())
    }
}

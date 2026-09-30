package com.sza.fastmediasorter.domain.usecase

import android.os.Environment
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.data.local.db.ResourceDao
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import kotlin.coroutines.CoroutineContext

/**
 * S3938: the export is launched from viewModelScope, so the file write must go through the injected
 * IO dispatcher rather than the caller's Main thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class ExportFavoritesUseCaseTest {

    private val favoritesDao = mockk<FavoritesDao>()
    private val resourceDao = mockk<ResourceDao>()
    private val ioDispatcher = RecordingDispatcher()
    private val useCase = ExportFavoritesUseCase(
        RuntimeEnvironment.getApplication(),
        favoritesDao,
        resourceDao,
        ioDispatcher
    )

    @Test
    fun `the export file is written through the injected io dispatcher`() = runTest {
        coEvery { favoritesDao.getFileFavoritesSync() } returns emptyList()
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).mkdirs()

        val result = useCase()

        assertTrue(result.isSuccess)
        assertTrue(ioDispatcher.dispatchCount > 0)
        assertEquals(File(result.filePath).length(), result.fileSizeBytes)
    }

    private class RecordingDispatcher : CoroutineDispatcher() {
        var dispatchCount = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCount++
            block.run()
        }
    }
}

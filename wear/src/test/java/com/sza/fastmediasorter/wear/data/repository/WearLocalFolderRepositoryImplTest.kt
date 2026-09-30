package com.sza.fastmediasorter.wear.data.repository

import android.content.ContentResolver
import android.content.Context
import com.sza.fastmediasorter.wear.domain.model.WearFolderAddress
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** S3797: a later page of a level windows the first page's listing instead of reading the folder again. */
class WearLocalFolderRepositoryImplTest {

    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private fun repository(): Pair<WearLocalFolderRepositoryImpl, File> {
        val files = temporaryFolder.newFolder("files")
        val context = mockk<Context>()
        every { context.filesDir } returns files
        every { context.cacheDir } returns temporaryFolder.newFolder("cache")
        every { context.getExternalFilesDir(null) } returns null
        return WearLocalFolderRepositoryImpl(context, mockk<ContentResolver>()) to files
    }

    @Test
    fun `the second page comes from the first page's listing`() = runBlocking {
        val (repo, folder) = repository()
        repeat(60) { File(folder, "clip-%02d.mp4".format(it)).writeText("x") }
        val address = WearFolderAddress.AppOwned(folder.absolutePath)

        val first = repo.listLevel(address, 0).getOrThrow()
        folder.listFiles()!!.forEach { it.delete() }
        val second = repo.listLevel(address, first.nextOffset!!).getOrThrow()

        assertEquals(50, first.entries.size)
        assertEquals(10, second.entries.size)
        assertEquals("clip-50.mp4", second.entries.first().name)
        assertNull(second.nextOffset)
    }

    @Test
    fun `a first page always reads the folder again`() = runBlocking {
        val (repo, folder) = repository()
        repeat(3) { File(folder, "clip-$it.mp4").writeText("x") }
        val address = WearFolderAddress.AppOwned(folder.absolutePath)

        repo.listLevel(address, 0).getOrThrow()
        File(folder, "clip-new.mp4").writeText("x")

        assertEquals(4, repo.listLevel(address, 0).getOrThrow().entries.size)
    }
}

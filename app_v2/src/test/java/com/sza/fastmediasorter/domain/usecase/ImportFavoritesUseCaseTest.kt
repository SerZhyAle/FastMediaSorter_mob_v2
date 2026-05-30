package com.sza.fastmediasorter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.gson.Gson
import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.data.local.db.ResourceDao
import com.sza.fastmediasorter.data.local.db.ResourceEntity
import com.sza.fastmediasorter.domain.model.ExportedFavorite
import com.sza.fastmediasorter.domain.model.FavoritesConflictStrategy
import com.sza.fastmediasorter.domain.model.FavoritesExportFile
import com.sza.fastmediasorter.domain.model.FavoritesImportStatus
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * JVM coverage for [ImportFavoritesUseCase]: file parse + version check, preview counts, and the
 * import conflict matrix (added / skipped / unresolved). Context.contentResolver is mocked to feed
 * the export JSON; the parsing/dedup/resolution logic is the unit under test.
 */
class ImportFavoritesUseCaseTest {

    private val context = mockk<Context>()
    private val contentResolver = mockk<ContentResolver>()
    private val favoritesDao = mockk<FavoritesDao>(relaxed = true)
    private val resourceDao = mockk<ResourceDao>()
    private val uri = mockk<Uri>(relaxed = true)
    private lateinit var useCase: ImportFavoritesUseCase

    @Before
    fun setup() {
        every { context.contentResolver } returns contentResolver
        useCase = ImportFavoritesUseCase(context, favoritesDao, resourceDao)
    }

    private fun feedJson(json: String, size: Long = json.length.toLong()) {
        val pfd = mockk<ParcelFileDescriptor>(relaxed = true)
        every { pfd.statSize } returns size
        every { pfd.close() } just Runs
        every { contentResolver.openFileDescriptor(uri, "r") } returns pfd
        every { contentResolver.openInputStream(uri) } returns ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))
    }

    private fun exportFile(vararg favs: ExportedFavorite, version: String = "1.0") = Gson().toJson(
        FavoritesExportFile(
            version = version,
            exportDate = "2026-01-01",
            appVersion = "2.0",
            deviceName = "TestPhone",
            totalCount = favs.size,
            favorites = favs.toList(),
        ),
    )

    private fun fav(uriStr: String, resourcePath: String) = ExportedFavorite(
        uri = uriStr, resourceId = 1, resourceName = "R", resourcePath = resourcePath,
        displayName = "name", mediaType = 1, size = 10, dateModified = 0, addedTimestamp = 0,
    )

    private fun resourceEntity(id: Long, path: String): ResourceEntity =
        mockk { every { this@mockk.id } returns id; every { this@mockk.path } returns path }

    @Test
    fun `preview reports added and existing counts`() = runTest {
        feedJson(exportFile(fav("u1", "/A"), fav("u2", "/A")))
        coEvery { favoritesDao.getFavoriteUrisForPaths(any()) } returns listOf("u1")

        val preview = useCase.preview(uri).getOrThrow()

        assertEquals(2, preview.totalInFile)
        assertEquals(1, preview.alreadyExisting)
        assertEquals(1, preview.willBeAdded)
        assertEquals("TestPhone", preview.sourceDevice)
    }

    @Test
    fun `preview fails on unsupported version`() = runTest {
        feedJson(exportFile(fav("u1", "/A"), version = "2.0"))

        assertTrue(useCase.preview(uri).isFailure)
    }

    @Test
    fun `import adds resolvable non-duplicate favorite`() = runTest {
        feedJson(exportFile(fav("u1", "/A")))
        coEvery { resourceDao.getAllResourcesSync() } returns listOf(resourceEntity(7, "/A"))
        coEvery { favoritesDao.getFavoriteUrisForPaths(any()) } returns emptyList()
        coEvery { favoritesDao.insert(any()) } just Runs

        val result = useCase(uri, FavoritesConflictStrategy.SKIP)

        assertTrue(result.isSuccess)
        assertEquals(1, result.imported)
        assertEquals(FavoritesImportStatus.ADDED, result.details.single().status)
    }

    @Test
    fun `import marks unresolved when resource path missing`() = runTest {
        feedJson(exportFile(fav("u1", "/missing")))
        coEvery { resourceDao.getAllResourcesSync() } returns listOf(resourceEntity(7, "/A"))
        coEvery { favoritesDao.getFavoriteUrisForPaths(any()) } returns emptyList()

        val result = useCase(uri, FavoritesConflictStrategy.SKIP)

        assertEquals(0, result.imported)
        assertEquals(1, result.unresolved)
        assertEquals(FavoritesImportStatus.UNRESOLVED, result.details.single().status)
    }

    @Test
    fun `import skips duplicate under SKIP strategy`() = runTest {
        feedJson(exportFile(fav("u1", "/A")))
        coEvery { resourceDao.getAllResourcesSync() } returns listOf(resourceEntity(7, "/A"))
        coEvery { favoritesDao.getFavoriteUrisForPaths(any()) } returns listOf("u1")

        val result = useCase(uri, FavoritesConflictStrategy.SKIP)

        assertEquals(0, result.imported)
        assertEquals(1, result.skipped)
        assertEquals(FavoritesImportStatus.SKIPPED, result.details.single().status)
    }

    @Test
    fun `import overwrites duplicate under OVERWRITE strategy`() = runTest {
        feedJson(exportFile(fav("u1", "/A")))
        coEvery { resourceDao.getAllResourcesSync() } returns listOf(resourceEntity(7, "/A"))
        coEvery { favoritesDao.getFavoriteUrisForPaths(any()) } returns listOf("u1")
        coEvery { favoritesDao.insert(any()) } just Runs

        val result = useCase(uri, FavoritesConflictStrategy.OVERWRITE)

        assertEquals(1, result.imported)
        assertEquals(0, result.skipped)
    }

    @Test
    fun `import returns failure result on read error`() = runTest {
        every { contentResolver.openFileDescriptor(uri, "r") } returns null
        every { contentResolver.openInputStream(uri) } returns null

        val result = useCase(uri, FavoritesConflictStrategy.SKIP)

        assertFalse(result.isSuccess)
    }
}

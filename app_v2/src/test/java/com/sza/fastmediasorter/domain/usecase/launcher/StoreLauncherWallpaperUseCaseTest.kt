package com.sza.fastmediasorter.domain.usecase.launcher

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class StoreLauncherWallpaperUseCaseTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val resolver = mockk<ContentResolver>()
    private val uri = mockk<Uri>()
    private lateinit var previous: File
    private lateinit var useCase: StoreLauncherWallpaperUseCase

    @Before
    fun setUp() {
        val filesDir = tempFolder.newFolder("files")
        val context = mockk<Context>()
        every { context.filesDir } returns filesDir
        every { context.contentResolver } returns resolver
        every { resolver.getType(uri) } returns null
        mockkStatic(MimeTypeMap::class)
        val mimeMap = mockk<MimeTypeMap>()
        every { MimeTypeMap.getSingleton() } returns mimeMap
        every { mimeMap.getExtensionFromMimeType(any()) } returns null

        previous = File(File(filesDir, "launcher_wallpaper").apply { mkdirs() }, "wallpaper_previous.jpg")
        previous.writeText("previous")
        useCase = StoreLauncherWallpaperUseCase(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(MimeTypeMap::class)
    }

    @Test
    fun `throwing stream keeps the previous wallpaper`() = runTest {
        every { resolver.openInputStream(uri) } returns object : InputStream() {
            override fun read(): Int = throw IOException("grant revoked")
        }

        assertEquals(LauncherWallpaperImport.Failed, useCase(uri))
        assertOnlyPreviousRemains()
    }

    @Test
    fun `unopenable uri keeps the previous wallpaper`() = runTest {
        every { resolver.openInputStream(uri) } returns null

        assertEquals(LauncherWallpaperImport.Failed, useCase(uri))
        assertOnlyPreviousRemains()
    }

    @Test
    fun `empty stream keeps the previous wallpaper`() = runTest {
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(0))

        assertEquals(LauncherWallpaperImport.Failed, useCase(uri))
        assertOnlyPreviousRemains()
    }

    @Test
    fun `successful import replaces the previous wallpaper`() = runTest {
        every { resolver.openInputStream(uri) } returns ByteArrayInputStream("new".toByteArray())

        val result = useCase(uri)

        assertTrue(result is LauncherWallpaperImport.Stored)
        val stored = File((result as LauncherWallpaperImport.Stored).absolutePath)
        assertEquals("new", stored.readText())
        assertFalse(previous.exists())
        assertEquals(listOf(stored.name), previous.parentFile!!.list()!!.toList())
    }

    private fun assertOnlyPreviousRemains() {
        assertEquals("previous", previous.readText())
        assertEquals(listOf(previous.name), previous.parentFile!!.list()!!.toList())
    }
}

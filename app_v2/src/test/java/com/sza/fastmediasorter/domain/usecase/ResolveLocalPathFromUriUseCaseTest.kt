package com.sza.fastmediasorter.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ResolveLocalPathFromUriUseCaseTest {

    private val context = mockk<Context>(relaxed = true)
    private val useCase = ResolveLocalPathFromUriUseCase(context)

    @After
    fun tearDown() {
        unmockkStatic(DocumentsContract::class)
    }

    @Test
    fun `file uri to existing file resolves to local path and parent`() = runTest {
        val tempFile = File.createTempFile("s0389_", ".txt").apply { deleteOnExit() }
        val uri = mockk<Uri>()
        every { uri.scheme } returns "file"
        every { uri.path } returns tempFile.absolutePath

        val result = useCase(uri)

        assertTrue(result is LocalPathResolution.Local)
        result as LocalPathResolution.Local
        assertEquals(tempFile.absolutePath, result.absolutePath)
        assertEquals(File(tempFile.absolutePath).parent, result.parentFolderPath)
    }

    @Test
    fun `downloads raw document uri resolves to embedded local path`() = runTest {
        val tempFile = File.createTempFile("s0414_", ".pdf").apply { deleteOnExit() }
        val resolver = mockk<ContentResolver>()
        every { resolver.query(any(), any(), any(), any(), any()) } returns null
        every { context.contentResolver } returns resolver
        mockkStatic(DocumentsContract::class)
        every { DocumentsContract.isDocumentUri(any(), any()) } returns true
        every { DocumentsContract.getDocumentId(any()) } returns "raw:${tempFile.absolutePath}"
        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"

        val result = useCase(uri)

        assertTrue(result is LocalPathResolution.Local)
        result as LocalPathResolution.Local
        assertEquals(tempFile.absolutePath, result.absolutePath)
        assertEquals(File(tempFile.absolutePath).parent, result.parentFolderPath)
    }

    @Test
    fun `content uri with no resolvable path is not local`() = runTest {
        val resolver = mockk<ContentResolver>()
        every { resolver.query(any(), any(), any(), any(), any()) } returns null
        every { context.contentResolver } returns resolver
        mockkStatic(DocumentsContract::class)
        every { DocumentsContract.isDocumentUri(any(), any()) } returns false
        val uri = mockk<Uri>()
        every { uri.scheme } returns "content"

        val result = useCase(uri)

        assertEquals(LocalPathResolution.NotLocal, result)
    }
}

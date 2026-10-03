package com.sza.fastmediasorter.data.transfer

import android.content.Context
import com.sza.fastmediasorter.data.network.exceptions.HostKeyMismatchFinder
import com.sza.fastmediasorter.data.remote.sftp.HostKeyMismatchException
import com.sza.fastmediasorter.domain.usecase.FileOperation
import com.sza.fastmediasorter.domain.usecase.FileOperationResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * S4037: a transfer that fails on a typed SFTP host-key mismatch must hand the first failure's
 * exception to the surface, because the error list is flattened to strings and carries no
 * fingerprints - without it the re-pin action could never be offered after a copy or move.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BaseFileOperationHandlerFirstThrowableTest {

    private val context: Context = mockk(relaxed = true)
    private val strategy: FileOperationStrategy = mockk(relaxed = true)

    private val handler = object : BaseFileOperationHandler(context) {
        override fun getStrategies(): List<FileOperationStrategy> = listOf(strategy)
    }

    private val mismatch = HostKeyMismatchException(expected = "SHA256:old", actual = "SHA256:new")

    private fun copyOf(vararg names: String) = FileOperation.Copy(
        sources = names.map { File("/storage/src/$it") },
        destination = File("/storage/dst"),
        overwrite = true,
    )

    @Test
    fun `copy failing on a mismatch exposes the typed pair through the first throwable`() = runBlocking {
        every { strategy.supportsProtocol(any()) } returns true
        coEvery { strategy.copyFile(any(), any(), any(), any()) } returns Result.failure(mismatch)

        val result = handler.executeCopy(copyOf("a.jpg", "b.jpg"))

        val failure = result as FileOperationResult.Failure
        assertSame(mismatch, failure.firstThrowable)
        assertEquals("SHA256:old" to "SHA256:new", HostKeyMismatchFinder.find(failure.firstThrowable))
    }

    @Test
    fun `copy with one success and one mismatch keeps the first throwable on the partial result`() = runBlocking {
        every { strategy.supportsProtocol(any()) } returns true
        coEvery {
            strategy.copyFile(match { it.endsWith("a.jpg") }, any(), any(), any())
        } returns Result.success("/storage/dst/a.jpg")
        coEvery { strategy.copyFile(match { it.endsWith("b.jpg") }, any(), any(), any()) } returns
            Result.failure(mismatch)

        val result = handler.executeCopy(copyOf("a.jpg", "b.jpg"))

        val partial = result as FileOperationResult.PartialSuccess
        assertSame(mismatch, partial.firstThrowable)
    }

    @Test
    fun `an ordinary failure leaves no typed pair behind`() = runBlocking {
        every { strategy.supportsProtocol(any()) } returns true
        coEvery { strategy.copyFile(any(), any(), any(), any()) } returns Result.failure(IllegalStateException("boom"))

        val result = handler.executeCopy(copyOf("a.jpg"))

        assertTrue(result is FileOperationResult.Failure)
        assertNull(HostKeyMismatchFinder.find((result as FileOperationResult.Failure).firstThrowable))
    }
}

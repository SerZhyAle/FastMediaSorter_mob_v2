package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler
import com.sza.fastmediasorter.data.transfer.strategy.LocalOperationStrategy
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * JVM coverage for [LocalDeleteFileOperation]'s aggregation logic. Under the unit-test runtime
 * Build.VERSION.SDK_INT is 0, so the batch-delete (Android 11+) path is skipped and
 * collectMediaStoreUris returns empty. Tests drive the cloud-vs-local split and result passthrough
 * via a mocked [CloudFileOperationHandler] using "cloud:" paths; the internally-constructed local
 * delete handler is not exercised (it needs real Android MediaStore on shared-storage paths).
 */
class LocalDeleteFileOperationTest {

    private val context = mockk<Context>(relaxed = true)
    private val cloudHandler = mockk<CloudFileOperationHandler>()
    private val localStrategy = mockk<LocalOperationStrategy>(relaxed = true)
    private lateinit var op: LocalDeleteFileOperation

    @Before
    fun setup() {
        op = LocalDeleteFileOperation(context, cloudHandler, localStrategy)
    }

    private fun cloudFile(name: String): File = object : File("cloud:/$name") {
        override fun getPath(): String = "cloud:/$name"
        override fun getName(): String = name
    }

    @Test
    fun `empty file list yields success with zero processed`() = runTest {
        val result = op.execute(FileOperation.Delete(emptyList(), softDelete = true))

        val success = result as FileOperationResult.Success
        assertEquals(0, success.processedCount)
    }

    @Test
    fun `single cloud result is returned unchanged on success`() = runTest {
        coEvery { cloudHandler.executeDelete(any()) } returns
            FileOperationResult.Success(2, FileOperation.Delete(emptyList()), listOf("a", "b"))

        val result = op.execute(
            FileOperation.Delete(listOf(cloudFile("a"), cloudFile("b")), softDelete = true)
        )

        val success = result as FileOperationResult.Success
        assertEquals(2, success.processedCount)
    }

    @Test
    fun `cloud failure is propagated`() = runTest {
        coEvery { cloudHandler.executeDelete(any()) } returns
            FileOperationResult.Failure("cloud down")

        val result = op.execute(
            FileOperation.Delete(listOf(cloudFile("a")), softDelete = true)
        )

        val failure = result as FileOperationResult.Failure
        assertEquals("cloud down", failure.error)
    }

    @Test
    fun `cloud partial success is propagated`() = runTest {
        coEvery { cloudHandler.executeDelete(any()) } returns
            FileOperationResult.PartialSuccess(1, 1, listOf("err"), listOf("a"))

        val result = op.execute(
            FileOperation.Delete(listOf(cloudFile("a"), cloudFile("b")), softDelete = true)
        )

        assertTrue(result is FileOperationResult.PartialSuccess)
        assertEquals(1, (result as FileOperationResult.PartialSuccess).processedCount)
    }
}

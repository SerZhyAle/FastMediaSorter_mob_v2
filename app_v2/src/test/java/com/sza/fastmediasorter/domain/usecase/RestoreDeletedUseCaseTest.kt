package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.data.transfer.strategy.CloudOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.FtpOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.LocalOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SftpOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.SmbOperationStrategy
import com.sza.fastmediasorter.data.transfer.trash.TrashFolderContract
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RestoreDeletedUseCaseTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val localStrategy = LocalOperationStrategy(context, mockk(relaxed = true))
    private val smbStrategy: SmbOperationStrategy = mockk(relaxed = true)
    private val sftpStrategy: SftpOperationStrategy = mockk(relaxed = true)
    private val ftpStrategy: FtpOperationStrategy = mockk(relaxed = true)
    private val cloudStrategy: CloudOperationStrategy = mockk(relaxed = true)

    private val useCase = RestoreDeletedUseCase(
        context = context,
        localStrategy = localStrategy,
        smbStrategy = smbStrategy,
        sftpStrategy = sftpStrategy,
        ftpStrategy = ftpStrategy,
        cloudStrategy = cloudStrategy,
    )

    @Test
    fun `invoke restores latest local snapshot and removes snapshot folder`() {
        runBlocking {
            val resourceRoot = temp.newFolder("resource")
            val originalFile = File(resourceRoot, "photo.jpg")
            originalFile.writeText("payload")

            val snapshotTimestamp = System.currentTimeMillis()
            val snapshotDir = File(TrashFolderContract.buildSnapshotPath(resourceRoot.absolutePath, snapshotTimestamp))
            assertTrue("snapshot directory should be created", snapshotDir.mkdirs() || snapshotDir.isDirectory)

            val trashedFile = File(snapshotDir, originalFile.name)
            assertTrue("renameTo should move the file into the snapshot folder", originalFile.renameTo(trashedFile))

            File(snapshotDir, "metadata.json").writeText(
                """
                {
                  "originalPath": "${resourceRoot.absolutePath.replace("\\", "\\\\")}",
                  "resourceId": 1,
                  "resourceType": "Local",
                  "deletedFiles": ["${originalFile.name}"],
                  "deletionTimestamp": $snapshotTimestamp,
                  "isDirectory": false
                }
                """.trimIndent()
            )

            val result = useCase(resourceRoot.absolutePath)

            assertTrue("restore should succeed but failed with: ${result.exceptionOrNull()?.message}", result.isSuccess)
            assertEquals(1, result.getOrThrow())
            assertTrue("original file should exist after restore", originalFile.exists())
            assertEquals("payload", originalFile.readText())
            assertFalse("snapshot directory should be removed after restore", snapshotDir.exists())
        }
    }
}
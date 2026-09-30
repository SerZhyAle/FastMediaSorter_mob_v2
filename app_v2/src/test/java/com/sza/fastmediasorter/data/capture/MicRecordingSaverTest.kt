package com.sza.fastmediasorter.data.capture

import android.os.Environment
import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationCategory
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.data.transfer.local.LocalSink
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SaveFallbackReason
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.testing.fakes.FakeResourceRepository
import com.sza.fastmediasorter.testing.fakes.FakeSettingsRepository
import com.sza.fastmediasorter.util.CaptureDestinationPolicy
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.OutputStream

/**
 * S3916: [MicRecordingSaver] must never lose a recording - neither on a refused upload (S0522) nor
 * when the host scope is cancelled mid-upload, where the transfer strategies swallow the cancellation
 * and the local fallback's own `withContext` is what throws it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MicRecordingSaverTest {

    private val context = RuntimeEnvironment.getApplication()
    private val noOpStatsSink = object : StatsSink {
        override fun record(event: StatsEvent) = Unit
        override suspend fun flushNow() = Unit
    }
    private val networkStateMonitor = mockk<NetworkStateMonitor> {
        every { canReach(any()) } returns true
    }
    private val saver = MicRecordingSaver(
        FakeSettingsRepository(),
        FakeResourceRepository(),
        LocalCaptureDestinationWriter(context, LocalDestinationClassifier(), FilesystemWriter()),
        networkStateMonitor,
        noOpStatsSink,
    )
    private val nas = MediaResource(name = "NAS", path = "smb://host/share", type = ResourceType.SMB)

    /** Writes every category straight to disk so the saved location can be asserted. */
    private class FilesystemWriter : LocalDestinationWriter {
        override suspend fun open(
            destination: LocalDestinationCategory,
            overwrite: Boolean,
        ): Result<LocalSink> {
            val file = when (destination) {
                is LocalDestinationCategory.NonPublic -> File(destination.absolutePath)
                is LocalDestinationCategory.PublicCollection -> File(
                    File(Environment.getExternalStorageDirectory(), destination.relativePath.trim('/')),
                    destination.displayName,
                )
            }
            file.parentFile?.mkdirs()
            return Result.success(FilesystemSink(file))
        }

        private class FilesystemSink(private val file: File) : LocalSink {
            override val outputStream: OutputStream = file.outputStream()
            override suspend fun commit(): Result<String> {
                outputStream.close()
                return Result.success(file.absolutePath)
            }
            override suspend fun abort() {
                outputStream.close()
                file.delete()
            }
        }
    }

    private fun newTempFile(): File =
        File.createTempFile("mic_test_", ".m4a").apply { writeText("audio-bytes") }

    private fun defaultFolderFile(name: String): File =
        File(CaptureDestinationPolicy.resolveMicDestination(null), name)

    @Test
    fun `refused upload falls back to the default folder`() = runTest {
        val name = "refused_${System.nanoTime()}.m4a"

        val result = saver.save(newTempFile(), name, nas) { _, _, _ -> false }

        assertTrue(result.success)
        assertEquals(SaveFallbackReason.ResourceUnavailable, result.fallbackReason)
        assertTrue("expected fallback file", defaultFolderFile(name).exists())
    }

    @Test
    fun `cancellation during upload rescues the recording locally`() = runTest {
        val temp = newTempFile()
        val name = "cancel_${System.nanoTime()}.m4a"
        lateinit var job: Job
        job = launch {
            saver.save(temp, name, nas) { _, _, _ ->
                job.cancel()
                false
            }
        }
        job.join()

        assertTrue("the save job must end cancelled", job.isCancelled)
        assertTrue("expected rescued recording", defaultFolderFile(name).exists())
        assertTrue("the caller never reaches its delete on this path", temp.exists())
    }
}

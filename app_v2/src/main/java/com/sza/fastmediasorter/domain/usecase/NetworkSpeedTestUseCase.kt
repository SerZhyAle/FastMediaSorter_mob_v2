package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.random.Random

data class SpeedTestResult(
    val readSpeedMbps: Double,
    val writeSpeedMbps: Double,
    val recommendedThreads: Int,
    val recommendedBufferSize: Int
)

class NetworkSpeedTestUseCase @Inject constructor(
    private val context: Context,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val googleDriveClient: GoogleDriveRestClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    private val resourceRepository: ResourceRepository,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    
    companion object {
        private const val TEST_FILE_SIZE_MB = 10
        private const val TEST_FILE_SIZE_BYTES = TEST_FILE_SIZE_MB * 1024L * 1024L
        private const val TEST_FILENAME_PREFIX = ".speedtest_"
    }

    sealed class SpeedTestStatus {
        data class Progress(val messageResId: Int) : SpeedTestStatus()
        data class Complete(val result: SpeedTestResult) : SpeedTestStatus()
        // No usable measurement returned (e.g. empty/truncated server response) - not a real failure
        data class MeasurementUnavailable(val reason: String) : SpeedTestStatus()
        data class Error(val message: String) : SpeedTestStatus()
    }

    suspend fun runSpeedTest(resource: MediaResource): Flow<SpeedTestStatus> = flow {
        emit(SpeedTestStatus.Progress(R.string.speed_test_preparing))
        
        try {
            val result = when (resource.type) {
                ResourceType.LOCAL -> testLocalSpeed(resource)
                ResourceType.SMB -> testSmbSpeed(resource)
                ResourceType.SFTP -> testSftpSpeed(resource)
                ResourceType.FTP -> testFtpSpeed(resource)
                ResourceType.CLOUD -> testCloudSpeed(resource)
            }
            
            emit(SpeedTestStatus.Progress(R.string.speed_test_saving))
            
            val updatedResource = resource.copy(
                readSpeedMbps = result.readSpeedMbps,
                writeSpeedMbps = result.writeSpeedMbps,
                recommendedThreads = result.recommendedThreads,
                lastSpeedTestDate = System.currentTimeMillis()
            )
            resourceRepository.updateResource(updatedResource)
            
            // Update ConnectionThrottleManager with recommended threads
            val resourceKey = when {
                resource.path.startsWith("smb://") -> resource.path.substringBefore("/", resource.path)
                resource.path.startsWith("ftp://") -> resource.path.substringBefore("/", resource.path.substringAfter("://"))
                    .let { "ftp://$it" }
                resource.path.startsWith("sftp://") -> resource.path.substringBefore("/", resource.path.substringAfter("://"))
                    .let { "sftp://$it" }
                resource.path.startsWith("cloud://") -> {
                    // Normalize cloud key to provider: cloud://google_drive
                    val providerId = resource.path.substringAfter("://").substringBefore("/")
                    "cloud://$providerId"
                }
                else -> resource.path
            }
            com.sza.fastmediasorter.data.network.ConnectionThrottleManager.setRecommendedThreads(
                resourceKey, 
                result.recommendedThreads
            )
            
            // Update ConnectionThrottleManager with recommended buffer size
            com.sza.fastmediasorter.data.network.ConnectionThrottleManager.setRecommendedBufferSize(
                resourceKey,
                result.recommendedBufferSize
            )

            // Store measured read speed so SmbConnectionManager can pick an adaptive
            // SMBJ transaction-timeout tier (FAST / MEDIUM / SLOW) for this resource.
            // Only SMB resources benefit from this; others are ignored silently by the manager.
            com.sza.fastmediasorter.data.network.ConnectionThrottleManager.setLastSpeedMbps(
                resourceKey,
                result.readSpeedMbps
            )

            emit(SpeedTestStatus.Complete(result))
            
        } catch (e: Exception) {
            if (isEmptyMeasurementError(e)) {
                // Benign: server returned no parseable measurement - not a real failure
                Timber.d("Speed test: measurement unavailable for ${resource.name} - ${e.message}")
                emit(SpeedTestStatus.MeasurementUnavailable(e.message ?: "no data"))
            } else {
                // Unexpected error - one short WARNING, no stack-trace, no ERROR channel noise
                Timber.w("Speed test failed for ${resource.name}: ${e.message}")
                emit(SpeedTestStatus.Error(e.message ?: "Unknown error"))
            }
        }
    }.flowOn(ioDispatcher)

    /**
     * Returns true for exceptions that indicate a missing/incomplete measurement
     * rather than a genuine failure: null/blank message or known FTP parser
     * 'Conversion = End of String' patterns from Apache Commons Net.
     */
    private fun isEmptyMeasurementError(e: Exception): Boolean {
        val msg = e.message ?: return true
        return msg.isBlank() ||
            msg.contains("End of String", ignoreCase = true) ||
            msg.startsWith("Conversion", ignoreCase = true)
    }

    private suspend fun testSmbSpeed(resource: MediaResource): SpeedTestResult {
        val credentialsId = resource.credentialsId ?: throw Exception("No credentials")
        val connectionInfo = smbOperationsUseCase.getConnectionInfo(credentialsId).getOrThrow()
        val testFileName = "${TEST_FILENAME_PREFIX}${UUID.randomUUID()}.tmp"
        
        // Measure Write Speed
        val writeSpeed = measureTime {
             val result = smbClient.uploadFile(connectionInfo, testFileName, ReferenceRandomInputStream(TEST_FILE_SIZE_BYTES), TEST_FILE_SIZE_BYTES)
             if (result is com.sza.fastmediasorter.data.network.model.SmbResult.Error) {
                 throw result.exception ?: Exception(result.message)
             }
        }.let { calculateSpeed(TEST_FILE_SIZE_BYTES, it) }
        
        // Measure Read Speed
        val readSpeed = measureTime {
            val result = smbClient.downloadFile(connectionInfo, testFileName, NullOutputStream(), TEST_FILE_SIZE_BYTES)
            if (result is com.sza.fastmediasorter.data.network.model.SmbResult.Error) {
                throw result.exception ?: Exception(result.message)
            }
        }.let { calculateSpeed(TEST_FILE_SIZE_BYTES, it) }
        
        // Cleanup
        smbClient.deleteFile(connectionInfo, testFileName)
        
        return SpeedTestResult(readSpeed, writeSpeed, calculateThreads(readSpeed), calculateBufferSize(readSpeed))
    }

    private suspend fun testSftpSpeed(resource: MediaResource): SpeedTestResult {
         val credentialsId = resource.credentialsId ?: throw Exception("No credentials")
         val credentials = smbOperationsUseCase.getSftpCredentials(credentialsId).getOrThrow()
         val connectionInfo = SftpClient.SftpConnectionInfo(
             host = credentials.server,
             port = credentials.port,
             username = credentials.username,
             password = credentials.password,
             privateKey = credentials.sshPrivateKey
         )
         
         // Fix SFTP path extraction
         val remotePath = when {
             resource.path.startsWith("sftp://") -> {
                 // Remove sftp://host:port part
                 val withoutProtocol = resource.path.substringAfter("://")
                 val pathPart = withoutProtocol.substringAfter("/", "")
                 if (pathPart.isNotEmpty()) "/$pathPart" else "/"
             }
             else -> "/"
         }
         
         // Ensure path ends with slash if it's a directory
         val dirPath = if (remotePath.endsWith("/")) remotePath else "$remotePath/"
         val testFilePath = "${dirPath}${TEST_FILENAME_PREFIX}${UUID.randomUUID()}.tmp"
         
         Timber.d("SFTP Speed Test Path: $testFilePath (Original: ${resource.path})")

        // Measure Write Speed
        val writeSpeed = measureTime {
            sftpClient.uploadFile(connectionInfo, testFilePath, ReferenceRandomInputStream(TEST_FILE_SIZE_BYTES), TEST_FILE_SIZE_BYTES).getOrThrow()
        }.let { calculateSpeed(TEST_FILE_SIZE_BYTES, it) }

        // Measure Read Speed
        val readSpeed = measureTime {
            sftpClient.downloadFile(connectionInfo, testFilePath, NullOutputStream(), TEST_FILE_SIZE_BYTES).getOrThrow()
        }.let { calculateSpeed(TEST_FILE_SIZE_BYTES, it) }

        // Cleanup
        sftpClient.deleteFile(connectionInfo, testFilePath)

        return SpeedTestResult(readSpeed, writeSpeed, calculateThreads(readSpeed), calculateBufferSize(readSpeed))
    }

    private suspend fun testFtpSpeed(resource: MediaResource): SpeedTestResult {
        val credentialsId = resource.credentialsId ?: throw Exception("No credentials")
        val credentials = smbOperationsUseCase.getFtpCredentials(credentialsId).getOrThrow()
        
        // Extract remote path
        val remotePath = resource.path.substringAfter("://").substringAfter("/")
        val testFilePath = if (remotePath.isNotEmpty()) "$remotePath/${TEST_FILENAME_PREFIX}${UUID.randomUUID()}.tmp" else "${TEST_FILENAME_PREFIX}${UUID.randomUUID()}.tmp"

        // Connect
        ftpClient.connect(credentials.server, credentials.port, credentials.username, credentials.password).getOrThrow()

        try {
            // Measure Write Speed
            val writeSpeed = measureTime {
                 ftpClient.uploadFile(testFilePath, ReferenceRandomInputStream(TEST_FILE_SIZE_BYTES)).getOrThrow()
            }.let { calculateSpeed(TEST_FILE_SIZE_BYTES, it) }

            // Measure Read Speed
            val readSpeed = measureTime {
                ftpClient.downloadFile(testFilePath, NullOutputStream()).getOrThrow()
            }.let { calculateSpeed(TEST_FILE_SIZE_BYTES, it) }

             // Cleanup
            ftpClient.deleteFile(testFilePath)
            
            return SpeedTestResult(readSpeed, writeSpeed, calculateThreads(readSpeed), calculateBufferSize(readSpeed))
            
        } finally {
            ftpClient.disconnect()
        }
    }
    
    private suspend fun testCloudSpeed(resource: MediaResource): SpeedTestResult {
        // Verify provider (Google Drive only for now)
        val isGoogleDrive = resource.cloudProvider == com.sza.fastmediasorter.data.cloud.CloudProvider.GOOGLE_DRIVE ||
                            resource.path.contains("google_drive", ignoreCase = true)
                            
        if (!isGoogleDrive) {
             throw IllegalArgumentException("Speed test only supported for Google Drive at this time")
        }

        // Authenticate client
        if (!googleDriveClient.isAuthenticated()) {
             // Try to restore from global storage
             // We don't need resource.credentialsId for Google Drive as it uses global singleton credentials
             if (!googleDriveClient.tryRestoreFromStorage()) {
                 throw Exception("Google Drive Client not authenticated. Please re-login in Settings.")
             }
        }
        
        val testFileName = "${TEST_FILENAME_PREFIX}${UUID.randomUUID()}.tmp"
        
        // Measure Write Speed (Upload)
        // Upload to root folder for test

        
        var uploadedFileId: String? = null
        val writeTime = measureTime {
             val result = googleDriveClient.uploadFile(
                inputStream = ReferenceRandomInputStream(TEST_FILE_SIZE_BYTES),
                fileName = testFileName,
                mimeType = "application/octet-stream",
                parentFolderId = null,
                progressCallback = null
            )
            if (result is com.sza.fastmediasorter.data.cloud.CloudResult.Success) {
                uploadedFileId = result.data.id
            } else {
                throw Exception("Upload failed: ${(result as? com.sza.fastmediasorter.data.cloud.CloudResult.Error)?.message}")
            }
        }
        val writeSpeed = calculateSpeed(TEST_FILE_SIZE_BYTES, writeTime)
        
        val fileId = uploadedFileId ?: throw Exception("Upload failed, no ID")

        // Measure Read Speed (Download)
        val readTime = measureTime {
            val result = googleDriveClient.downloadFile(
                fileId = fileId,
                outputStream = NullOutputStream(),
                progressCallback = null
            )
             if (result is com.sza.fastmediasorter.data.cloud.CloudResult.Error) {
                throw Exception("Download failed: ${result.message}")
            }
        }
         val readSpeed = calculateSpeed(TEST_FILE_SIZE_BYTES, readTime)

        // Cleanup
        googleDriveClient.deleteFile(fileId)

        return SpeedTestResult(readSpeed, writeSpeed, calculateThreads(readSpeed), calculateBufferSize(readSpeed))
    }

    private inline fun measureTime(block: () -> Unit): Long {
        val start = System.currentTimeMillis()
        block()
        return System.currentTimeMillis() - start
    }

    private fun calculateSpeed(bytes: Long, timeMs: Long): Double {
        if (timeMs == 0L) return 0.0
        val seconds = timeMs / 1000.0
        val bits = bytes * 8.0
        val mbps = (bits / seconds) / (1024 * 1024)
        return mbps
    }
    
    private fun calculateThreads(readSpeedMbps: Double): Int {
        return when {
            readSpeedMbps > 500 -> 8
            readSpeedMbps > 100 -> 4
            readSpeedMbps > 20 -> 2
            else -> 1
        }
    }
    
    private fun calculateBufferSize(readSpeedMbps: Double): Int {
        // Adaptive buffer size based on speed
        // Larger buffers reduce IOPS overhead on high-latency/high-speed connections
        return when {
            readSpeedMbps > 500 -> 4 * 1024 * 1024      // 4 MB for Gigabit+
            readSpeedMbps > 100 -> 2 * 1024 * 1024      // 2 MB for fast Wifi/LAN
            readSpeedMbps > 20 -> 512 * 1024            // 512 KB for moderate speed
            else -> 64 * 1024                           // 64 KB for slow connections (responsiveness)
        }
    }

    private suspend fun testLocalSpeed(resource: MediaResource): SpeedTestResult {
        val path = resource.path
        
        // Check if this is a SAF resource (content:// URI)
        if (path.startsWith("content://")) {
            return testSafSpeed(path)
        }
        
        // Regular file system path
        val testFile = java.io.File(path, "${TEST_FILENAME_PREFIX}${UUID.randomUUID()}.tmp")
        
        // Ensure directory exists
        val dir = java.io.File(path)
        if (!dir.exists()) {
             throw Exception("Directory does not exist: $path")
        }
        if (!dir.canWrite()) {
             throw Exception("Directory is not writable: $path")
        }

        // Measure Write Speed
        val writeSpeed = measureTime {
            java.io.FileOutputStream(testFile).use { output ->
                ReferenceRandomInputStream(TEST_FILE_SIZE_BYTES).copyTo(output)
            }
        }.let { calculateSpeed(TEST_FILE_SIZE_BYTES, it) }

        // Measure Read Speed
        val readSpeed = measureTime {
            java.io.FileInputStream(testFile).use { input ->
                input.copyTo(NullOutputStream())
            }
        }.let { calculateSpeed(TEST_FILE_SIZE_BYTES, it) }

        // Cleanup
        testFile.delete()

        return SpeedTestResult(
            readSpeed, 
            writeSpeed, 
            calculateThreads(readSpeed),
            calculateBufferSize(readSpeed)
        )
    }
    
    private suspend fun testSafSpeed(uriString: String): SpeedTestResult {
        val uri = android.net.Uri.parse(uriString)
        val docDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
            ?: throw Exception("Cannot access SAF directory: $uriString")
        
        if (!docDir.canWrite()) {
            throw Exception("Directory is not writable: $uriString")
        }
        
        val testFileName = "${TEST_FILENAME_PREFIX}${UUID.randomUUID()}.tmp"
        val testFile = docDir.createFile("application/octet-stream", testFileName)
            ?: throw Exception("Cannot create test file in SAF directory")
        
        try {
            // Measure Write Speed
            val writeSpeed = measureTime {
                context.contentResolver.openOutputStream(testFile.uri)?.use { output ->
                    ReferenceRandomInputStream(TEST_FILE_SIZE_BYTES).copyTo(output)
                } ?: throw Exception("Cannot open output stream for SAF file")
            }.let { calculateSpeed(TEST_FILE_SIZE_BYTES, it) }

            // Measure Read Speed
            val readSpeed = measureTime {
                context.contentResolver.openInputStream(testFile.uri)?.use { input ->
                    input.copyTo(NullOutputStream())
                } ?: throw Exception("Cannot open input stream for SAF file")
            }.let { calculateSpeed(TEST_FILE_SIZE_BYTES, it) }

            return SpeedTestResult(
                readSpeed, 
                writeSpeed, 
                calculateThreads(readSpeed),
                calculateBufferSize(readSpeed)
            )
        } finally {
            // Cleanup
            testFile.delete()
        }
    }
    
    // Helper classes
    // Valid for both test types
    private class ReferenceRandomInputStream(private val size: Long) : InputStream() {
        private var readBytes = 0L
        private val random = Random(System.currentTimeMillis())
        
        override fun read(): Int {
             if (readBytes >= size) return -1
             readBytes++
             return random.nextInt(256)
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (readBytes >= size) return -1
            val remaining = size - readBytes
            val toRead = remaining.coerceAtMost(len.toLong()).toInt()
            random.nextBytes(b, off, off + toRead)
            readBytes += toRead
            return toRead
        }
    }
    
    private class NullOutputStream : OutputStream() {
        override fun write(b: Int) {}
        override fun write(b: ByteArray) {}
        override fun write(b: ByteArray, off: Int, len: Int) {}
    }
}


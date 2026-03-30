package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.domain.model.FileTypeFilter
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.TimeFilter
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ScheduledExecutionResult(
    val operationId: Long,
    val filesProcessed: Int,
    val errors: List<String>
) {
    val isSuccess: Boolean get() = errors.isEmpty()
    val statusString: String get() = if (isSuccess) "OK" else "ERROR: ${errors.first()}"
}

@Singleton
class ExecuteScheduledOperationUseCase @Inject constructor(
    private val scheduledOperationRepository: ScheduledOperationRepository,
    private val resourceRepository: ResourceRepository,
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    private val fileOperationUseCase: FileOperationUseCase,
    private val appendToScheduledLogUseCase: AppendToScheduledLogUseCase
) {
    private val logDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    suspend operator fun invoke(operationId: Long): ScheduledExecutionResult {
        val operation = scheduledOperationRepository.getById(operationId)
            ?: return ScheduledExecutionResult(operationId, 0, listOf("Operation $operationId not found"))

        if (!operation.isEnabled) {
            return ScheduledExecutionResult(operationId, 0, listOf("Operation is disabled"))
        }

        val ts = logDateFormat.format(Date())
        val opName = operation.operationType.name

        // In release builds log only the fact that the scheduler fired.
        // In debug builds Timber gets everything per-file.
        if (BuildConfig.DEBUG) {
            Timber.d("ScheduledOp[$operationId] fired: $opName")
        } else {
            Timber.i("ScheduledOp[$operationId] fired: $opName")
        }

        val sourceResource = resourceRepository.getResourceById(operation.sourceResourceId)
        if (sourceResource == null) {
            val msg = "Source resource not found (id=${operation.sourceResourceId})"
            logOp(ts, opName, "—", "—", msg)
            Timber.w("ScheduledOp[$operationId] $msg")
            return ScheduledExecutionResult(operationId, 0, listOf(msg))
        }

        val targetResource: MediaResource? = if (operation.operationType != ScheduledOpType.DELETE) {
            val t = resourceRepository.getResourceById(operation.targetResourceId!!)
            if (t == null) {
                val msg = "Target resource not found (id=${operation.targetResourceId})"
                logOp(ts, opName, sourceResource.name, "—", msg)
                Timber.w("ScheduledOp[$operationId] $msg")
                return ScheduledExecutionResult(operationId, 0, listOf(msg))
            }
            t
        } else null

        val srcLabel = "${sourceResource.name} [${sourceResource.type.name}]"
        val dstLabel = targetResource?.let { "${it.name} [${it.type.name}]" } ?: "—"

        return try {
            // Step 1: Load and filter source files.
            val allFiles = getMediaFilesUseCase(
                resource = sourceResource,
                sortMode = SortMode.NAME_ASC,
                forceFullScan = true
            ).first()

            val filtered = applyFilters(allFiles, operation)

            if (filtered.isEmpty()) {
                val msg = "No files found"
                logOp(ts, opName, srcLabel, dstLabel, msg)
                Timber.d("ScheduledOp[$operationId] $msg")
                return ScheduledExecutionResult(operationId, 0, emptyList())
            }

            // Step 2: For COPY/MOVE — verify target is reachable before doing any work.
            if (targetResource != null) {
                val reachabilityError = checkTargetReachability(targetResource)
                if (reachabilityError != null) {
                    logOp(ts, opName, srcLabel, dstLabel, "ERROR: $reachabilityError")
                    Timber.w("ScheduledOp[$operationId] $reachabilityError")
                    return ScheduledExecutionResult(operationId, 0, listOf(reachabilityError))
                }
            }

            val errors = mutableListOf<String>()
            var successCount = 0

            when (operation.operationType) {
                ScheduledOpType.COPY -> {
                    val targetDir = File(targetResource!!.path)
                    filtered.forEach { file ->
                        val result = fileOperationUseCase.execute(
                            FileOperation.Copy(
                                sources = listOf(File(file.path)),
                                destination = targetDir,
                                overwrite = operation.overwrite,
                                sourceCredentialsId = sourceResource.credentialsId
                            )
                        )
                        when {
                            result is FileOperationResult.Success -> {
                                if (result.skippedCount > 0) {
                                    logOp(ts, opName, srcLabel, dstLabel, "SKIP: ${file.name}")
                                    Timber.d("ScheduledOp[$operationId] COPY SKIP ${file.name}")
                                } else {
                                    successCount++
                                    logOp(ts, opName, srcLabel, dstLabel, "OK: ${file.name}")
                                    Timber.d("ScheduledOp[$operationId] COPY OK ${file.name}")
                                }
                            }
                            result is FileOperationResult.PartialSuccess -> {
                                if (result.skippedCount > 0) {
                                    logOp(ts, opName, srcLabel, dstLabel, "SKIP: ${file.name}")
                                    Timber.d("ScheduledOp[$operationId] COPY SKIP ${file.name}")
                                } else {
                                    successCount++
                                    logOp(ts, opName, srcLabel, dstLabel, "OK: ${file.name}")
                                    Timber.d("ScheduledOp[$operationId] COPY OK ${file.name}")
                                }
                            }
                            result is FileOperationResult.Failure -> {
                                errors.add(result.error)
                                logOp(ts, opName, srcLabel, dstLabel, "ERROR: ${file.name}: ${result.error}")
                                Timber.d("ScheduledOp[$operationId] COPY ERROR ${file.name}: ${result.error}")
                            }
                            else -> {
                                val err = "Failed to copy ${file.name}"
                                errors.add(err)
                                logOp(ts, opName, srcLabel, dstLabel, "ERROR: ${file.name}: $err")
                                Timber.d("ScheduledOp[$operationId] COPY ERROR ${file.name}")
                            }
                        }
                    }
                }
                ScheduledOpType.MOVE -> {
                    val targetDir = File(targetResource!!.path)
                    filtered.forEach { file ->
                        val result = fileOperationUseCase.execute(
                            FileOperation.Copy(
                                sources = listOf(File(file.path)),
                                destination = targetDir,
                                overwrite = operation.overwrite,
                                sourceCredentialsId = sourceResource.credentialsId
                            )
                        )
                        when {
                            result is FileOperationResult.Success -> {
                                if (result.skippedCount > 0) {
                                    logOp(ts, opName, srcLabel, dstLabel, "SKIP: ${file.name}")
                                    Timber.d("ScheduledOp[$operationId] MOVE SKIP ${file.name}")
                                } else {
                                    fileOperationUseCase.execute(
                                        FileOperation.Delete(
                                            files = listOf(File(file.path)),
                                            softDelete = false
                                        )
                                    )
                                    successCount++
                                    logOp(ts, opName, srcLabel, dstLabel, "OK: ${file.name}")
                                    Timber.d("ScheduledOp[$operationId] MOVE OK ${file.name}")
                                }
                            }
                            result is FileOperationResult.PartialSuccess -> {
                                if (result.skippedCount > 0) {
                                    logOp(ts, opName, srcLabel, dstLabel, "SKIP: ${file.name}")
                                    Timber.d("ScheduledOp[$operationId] MOVE SKIP ${file.name}")
                                } else {
                                    fileOperationUseCase.execute(
                                        FileOperation.Delete(
                                            files = listOf(File(file.path)),
                                            softDelete = false
                                        )
                                    )
                                    successCount++
                                    logOp(ts, opName, srcLabel, dstLabel, "OK: ${file.name}")
                                    Timber.d("ScheduledOp[$operationId] MOVE OK ${file.name}")
                                }
                            }
                            result is FileOperationResult.Failure -> {
                                errors.add(result.error)
                                logOp(ts, opName, srcLabel, dstLabel, "ERROR: ${file.name}: ${result.error}")
                                Timber.d("ScheduledOp[$operationId] MOVE ERROR ${file.name}: ${result.error}")
                            }
                            else -> {
                                val err = "Failed to move ${file.name}"
                                errors.add(err)
                                logOp(ts, opName, srcLabel, dstLabel, "ERROR: ${file.name}: $err")
                                Timber.d("ScheduledOp[$operationId] MOVE ERROR ${file.name}")
                            }
                        }
                    }
                }
                ScheduledOpType.DELETE -> {
                    filtered.forEach { file ->
                        val result = fileOperationUseCase.execute(
                            FileOperation.Delete(
                                files = listOf(File(file.path)),
                                softDelete = false
                            )
                        )
                        when {
                            result is FileOperationResult.Success ||
                            result is FileOperationResult.PartialSuccess -> {
                                successCount++
                                logOp(ts, opName, srcLabel, dstLabel, "OK: ${file.name}")
                                Timber.d("ScheduledOp[$operationId] DELETE OK ${file.name}")
                            }
                            result is FileOperationResult.Failure -> {
                                errors.add(result.error)
                                logOp(ts, opName, srcLabel, dstLabel, "ERROR: ${file.name}: ${result.error}")
                                Timber.d("ScheduledOp[$operationId] DELETE ERROR ${file.name}: ${result.error}")
                            }
                            else -> {
                                val err = "Failed to delete ${file.name}"
                                errors.add(err)
                                logOp(ts, opName, srcLabel, dstLabel, "ERROR: ${file.name}: $err")
                                Timber.d("ScheduledOp[$operationId] DELETE ERROR ${file.name}")
                            }
                        }
                    }
                }
            }

            // Summary line
            val statusStr = if (errors.isEmpty()) "OK ($successCount files)" else "ERROR: ${errors.first()}"
            logOp(ts, opName, srcLabel, dstLabel, statusStr)
            ScheduledExecutionResult(operationId, successCount, errors)

        } catch (e: Exception) {
            Timber.e(e, "ScheduledOp[$operationId] unexpected exception")
            val msg = e.message ?: "Unknown error"
            logOp(ts, opName, srcLabel, dstLabel, "ERROR: $msg")
            ScheduledExecutionResult(operationId, 0, listOf(msg))
        }
    }

    /**
     * Returns an error string if the target is unreachable, null if OK.
     * - Local: checks that the directory exists.
     * - Remote (SMB/SFTP/FTP/CLOUD): uses resourceRepository.testConnection().
     */
    private suspend fun checkTargetReachability(target: MediaResource): String? {
        return if (target.type.isNetworkResource) {
            val result = runCatching { resourceRepository.testConnection(target) }.getOrElse { e ->
                Result.failure(e)
            }
            if (result.isSuccess) null
            else "Target '${target.name}' is unreachable: ${result.exceptionOrNull()?.message ?: "connection failed"}"
        } else {
            val dir = File(target.path)
            if (dir.exists() && dir.isDirectory) null
            else "Target directory '${target.path}' does not exist"
        }
    }

    private fun applyFilters(files: List<MediaFile>, op: ScheduledOperation): List<MediaFile> {
        val now = System.currentTimeMillis()
        return files
            .filter { matchesTypeFilter(it, op.fileTypeFilter) }
            .filter { matchesTimeFilter(it, op.timeFilter, op.lastRunAt, now) }
    }

    private fun matchesTypeFilter(file: MediaFile, filter: FileTypeFilter): Boolean = when (filter) {
        FileTypeFilter.ALL -> true
        FileTypeFilter.AUDIO -> file.type == MediaType.AUDIO
        FileTypeFilter.IMAGES -> file.type == MediaType.IMAGE || file.type == MediaType.GIF
        FileTypeFilter.VIDEO -> file.type == MediaType.VIDEO
        FileTypeFilter.DOCUMENTS -> file.type in setOf(MediaType.PDF, MediaType.EPUB, MediaType.TEXT)
    }

    private fun matchesTimeFilter(file: MediaFile, filter: TimeFilter, lastRunAt: Long?, now: Long): Boolean = when (filter) {
        TimeFilter.ALL -> true
        TimeFilter.SINCE_LAST -> lastRunAt == null || file.createdDate > lastRunAt
        TimeFilter.LAST_HOUR -> file.createdDate > now - 3_600_000L
        TimeFilter.LAST_DAY -> file.createdDate > now - 86_400_000L
    }

    /** Appends one line to the user-visible operations log. */
    private fun logOp(ts: String, opName: String, src: String, dst: String, message: String) {
        appendToScheduledLogUseCase("$ts | $opName | $src → $dst | $message")
    }
}

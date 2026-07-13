package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.domain.model.FileTypeFlags
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.TimeFilter
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.ScheduledOperationRepository
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import kotlinx.coroutines.CancellationException
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
    val errors: List<String>,
    // S0710: set when the run halted because deleting the source requires an interactive permission
    // grant (All-Files-Access / MANAGE_MEDIA) that is unreachable from a headless worker. The worker
    // surfaces a notification and the loop stops instead of re-uploading the same files every run.
    val permissionRequired: Boolean = false
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
    private val appendToScheduledLogUseCase: AppendToScheduledLogUseCase,
    private val statsSink: StatsSink,
) {
    private val logDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    }

    suspend operator fun invoke(operationId: Long): ScheduledExecutionResult {
        val operation = scheduledOperationRepository.getById(operationId)
            ?: return ScheduledExecutionResult(operationId, 0, listOf("Operation $operationId not found"))

        if (!operation.isEnabled) {
            return ScheduledExecutionResult(operationId, 0, listOf("Operation is disabled"))
        }

        val ts = logDateFormat.get()!!.format(Date())
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
            logOp(ts, opName, "-", "-", msg)
            Timber.w("ScheduledOp[$operationId] $msg")
            return ScheduledExecutionResult(operationId, 0, listOf(msg))
        }

        val targetResource: MediaResource? = if (operation.operationType != ScheduledOpType.DELETE) {
            val t = resourceRepository.getResourceById(operation.targetResourceId!!)
            if (t == null) {
                val msg = "Target resource not found (id=${operation.targetResourceId})"
                logOp(ts, opName, sourceResource.name, "-", msg)
                Timber.w("ScheduledOp[$operationId] $msg")
                return ScheduledExecutionResult(operationId, 0, listOf(msg))
            }
            t
        } else null

        val srcLabel = "${sourceResource.name} [${sourceResource.type.name}]"
        val dstLabel = targetResource?.let { "${it.name} [${it.type.name}]" } ?: "-"

        return try {
            // Step 1: Load and filter source files.
            val allFiles = getMediaFilesUseCase(
                resource = buildEffectiveResource(sourceResource, operation.fileTypeMask),
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

            // Step 2: For COPY/MOVE - verify target is reachable before doing any work.
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
            // S0710: a permission-required result is an operation-level wall, not a per-file error.
            // Once seen, stop processing the rest of the batch so the run does not keep re-uploading
            // files it can never delete in the background.
            var permissionStop = false

            when (operation.operationType) {
                ScheduledOpType.COPY -> {
                    val targetDir = File(targetResource!!.path)
                    filtered.forEach { file ->
                        if (permissionStop) return@forEach
                        val result = fileOperationUseCase.execute(
                            FileOperation.Copy(
                                sources = listOf(File(file.path)),
                                destination = targetDir,
                                overwrite = operation.overwrite,
                                sourceCredentialsId = sourceResource.credentialsId
                            )
                        )
                        handleFileResult(
                            result = result,
                            file = file,
                            opLabel = "COPY",
                            fallbackErrMessage = "Failed to copy ${file.name}",
                            operationId = operationId,
                            ts = ts,
                            opName = opName,
                            srcLabel = srcLabel,
                            dstLabel = dstLabel,
                            addError = { errors.add(it) },
                            incrementSuccess = { successCount++ },
                            setPermissionStop = { permissionStop = true }
                        )
                    }
                }
                ScheduledOpType.MOVE -> {
                    val targetDir = File(targetResource!!.path)
                    filtered.forEach { file ->
                        if (permissionStop) return@forEach
                        val result = fileOperationUseCase.execute(
                            FileOperation.Move(
                                sources = listOf(File(file.path)),
                                destination = targetDir,
                                overwrite = operation.overwrite,
                                sourceCredentialsId = sourceResource.credentialsId
                            )
                        )
                        handleFileResult(
                            result = result,
                            file = file,
                            opLabel = "MOVE",
                            fallbackErrMessage = "Failed to move ${file.name}",
                            operationId = operationId,
                            ts = ts,
                            opName = opName,
                            srcLabel = srcLabel,
                            dstLabel = dstLabel,
                            addError = { errors.add(it) },
                            incrementSuccess = { successCount++ },
                            setPermissionStop = { permissionStop = true }
                        )
                    }
                }
                ScheduledOpType.DELETE -> {
                    filtered.forEach { file ->
                        if (permissionStop) return@forEach
                        val result = fileOperationUseCase.execute(
                            FileOperation.Delete(
                                files = listOf(File(file.path)),
                                softDelete = false
                            )
                        )
                        handleFileResult(
                            result = result,
                            file = file,
                            opLabel = "DELETE",
                            fallbackErrMessage = "Failed to delete ${file.name}",
                            operationId = operationId,
                            ts = ts,
                            opName = opName,
                            srcLabel = srcLabel,
                            dstLabel = dstLabel,
                            addError = { errors.add(it) },
                            incrementSuccess = { successCount++ },
                            setPermissionStop = { permissionStop = true }
                        )
                    }
                }
            }

            // Summary line
            val statusStr = if (errors.isEmpty()) "OK ($successCount files)" else "ERROR: ${errors.first()}"
            logOp(ts, opName, srcLabel, dstLabel, statusStr)
            // Count one run once the work loop executed; config-failure early returns above and the
            // exception path below are not runs. filesProcessed carries the successfully handled count.
            statsSink.record(StatsEvent.ScheduledRun(filesProcessed = successCount.toLong()))
            ScheduledExecutionResult(operationId, successCount, errors, permissionRequired = permissionStop)

        } catch (e: CancellationException) {
            throw e
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
            try {
                val result = resourceRepository.testConnection(target)
                if (result.isSuccess) null
                else "Target '${target.name}' is unreachable: ${result.exceptionOrNull()?.message ?: "connection failed"}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Target '${target.name}' is unreachable: ${e.message ?: "connection failed"}"
            }
        } else {
            val dir = File(target.path)
            if (dir.exists() && dir.isDirectory) null
            else "Target directory '${target.path}' does not exist"
        }
    }

    /**
     * Returns the source resource with supportedMediaTypes adjusted for the file type mask.
     * When ALL_FILES flag is set, passes all MediaType values to the scanner which activates
     * the isAllFilesMode (null-extensions) path in SmbMediaScanner, CloudMediaScanner, etc.
     * Subdirectory recursion is governed by resource.scanSubdirectories as configured by the user.
     */
    private fun buildEffectiveResource(resource: MediaResource, mask: Int): MediaResource {
        return if (FileTypeFlags.isAllFiles(mask)) {
            resource.copy(
                supportedMediaTypes = MediaType.entries.toSet(),
                allFiles = true
            )
        } else {
            val types = mutableSetOf<MediaType>()
            if (FileTypeFlags.hasImages(mask))    types += setOf(MediaType.IMAGE, MediaType.GIF)
            if (FileTypeFlags.hasAudio(mask))     types += MediaType.AUDIO
            if (FileTypeFlags.hasVideo(mask))     types += MediaType.VIDEO
            if (FileTypeFlags.hasDocuments(mask)) {
                types += setOf(MediaType.PDF, MediaType.EPUB, MediaType.TEXT, MediaType.OFFICE_DOCUMENT)
            }
            resource.copy(supportedMediaTypes = types)
        }
    }

    private fun applyFilters(files: List<MediaFile>, op: ScheduledOperation): List<MediaFile> {
        val now = System.currentTimeMillis()
        return files
            // When ALL_FILES - type filtering was already applied at the scanner level via buildEffectiveResource
            .filter { if (FileTypeFlags.isAllFiles(op.fileTypeMask)) true else matchesTypeMask(it, op.fileTypeMask) }
            .filter { matchesTimeFilter(it, op.timeFilter, op.lastRunAt, now) }
    }

    private fun matchesTypeMask(file: MediaFile, mask: Int): Boolean {
        if (FileTypeFlags.hasImages(mask)    && (file.type == MediaType.IMAGE || file.type == MediaType.GIF)) return true
        if (FileTypeFlags.hasAudio(mask)     && file.type == MediaType.AUDIO)  return true
        if (FileTypeFlags.hasVideo(mask)     && file.type == MediaType.VIDEO)  return true
        if (FileTypeFlags.hasDocuments(mask) && file.type.isDocumentFile()) return true
        return false
    }

    private fun matchesTimeFilter(file: MediaFile, filter: TimeFilter, lastRunAt: Long?, now: Long): Boolean = when (filter) {
        TimeFilter.ALL -> true
        TimeFilter.SINCE_LAST -> lastRunAt == null || file.createdDate > lastRunAt
        TimeFilter.LAST_HOUR -> file.createdDate > now - 3_600_000L
        TimeFilter.LAST_DAY -> file.createdDate > now - 86_400_000L
    }

    /**
     * S0710: a [FileOperationResult.PermissionRequired] / [FileOperationResult.AuthenticationRequired]
     * cannot be resolved inside a headless worker (it needs an interactive grant). Map it to a stable
     * message so the run halts with a clear cause instead of being logged as a generic per-file failure.
     */
    private fun permissionStopError(result: FileOperationResult): String = when (result) {
        is FileOperationResult.AuthenticationRequired -> "Re-authentication required for ${result.provider}"
        else -> "Permission required to delete source in background (grant All-files access)"
    }

    private fun handleFileResult(
        result: FileOperationResult,
        file: MediaFile,
        opLabel: String,
        fallbackErrMessage: String,
        operationId: Long,
        ts: String,
        opName: String,
        srcLabel: String,
        dstLabel: String,
        addError: (String) -> Unit,
        incrementSuccess: () -> Unit,
        setPermissionStop: () -> Unit
    ) {
        when {
            result is FileOperationResult.Success ||
            result is FileOperationResult.PartialSuccess -> {
                val skippedCount = when (result) {
                    is FileOperationResult.Success -> result.skippedCount
                    is FileOperationResult.PartialSuccess -> result.skippedCount
                    else -> 0
                }
                if (skippedCount > 0) {
                    logOp(ts, opName, srcLabel, dstLabel, "SKIP: ${file.name}")
                    Timber.d("ScheduledOp[$operationId] $opLabel SKIP ${file.name}")
                } else {
                    incrementSuccess()
                    logOp(ts, opName, srcLabel, dstLabel, "OK: ${file.name}")
                    Timber.d("ScheduledOp[$operationId] $opLabel OK ${file.name}")
                }
            }
            result is FileOperationResult.PermissionRequired ||
            result is FileOperationResult.AuthenticationRequired -> {
                setPermissionStop()
                val err = permissionStopError(result)
                addError(err)
                logOp(ts, opName, srcLabel, dstLabel, "ERROR: ${file.name}: $err")
                Timber.w("ScheduledOp[$operationId] $opLabel halted - $err (${file.name})")
            }
            result is FileOperationResult.Failure -> {
                addError(result.error)
                logOp(ts, opName, srcLabel, dstLabel, "ERROR: ${file.name}: ${result.error}")
                Timber.d("ScheduledOp[$operationId] $opLabel ERROR ${file.name}: ${result.error}")
            }
            else -> {
                addError(fallbackErrMessage)
                logOp(ts, opName, srcLabel, dstLabel, "ERROR: ${file.name}: $fallbackErrMessage")
                Timber.d("ScheduledOp[$operationId] $opLabel ERROR ${file.name}")
            }
        }
    }

    /** Appends one line to the user-visible operations log. */
    private fun logOp(ts: String, opName: String, src: String, dst: String, message: String) {
        appendToScheduledLogUseCase("$ts | $opName | $src → $dst | $message")
    }
}

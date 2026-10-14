package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.util.PathUtils
import com.sza.fastmediasorter.data.network.exceptions.NetworkPinRefusedException
import com.sza.fastmediasorter.domain.model.FileTypeFlags
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ScheduledOpType
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.model.ScheduledRunOutcome
import com.sza.fastmediasorter.domain.model.ScheduledRunStatus
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.model.TimeFilter
import com.sza.fastmediasorter.domain.mutation.MutationRecorder
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
    val permissionRequired: Boolean = false,
    // Set when a network side could not be reached before any file was touched. Not an error: the
    // window stays open for the next slot, and no failure notification is sent for it.
    val skippedReason: String? = null
) {
    val isSkipped: Boolean get() = skippedReason != null
    val isSuccess: Boolean get() = errors.isEmpty() && !isSkipped
    val outcome: ScheduledRunOutcome
        get() = when {
            isSkipped -> ScheduledRunOutcome.SKIPPED
            errors.isEmpty() -> ScheduledRunOutcome.OK
            else -> ScheduledRunOutcome.ERROR
        }

    /** The line shown in the log and stored as `lastRunStatus`; the reason is the first error or the skip cause. */
    val statusString: String
        get() = ScheduledRunStatus.format(outcome, skippedReason ?: errors.firstOrNull())
}

// One collaborator per operation type plus the shared scan/log/journal seams; grouping them into a holder
// object would hide the Hilt wiring without reducing what this executor genuinely depends on.
@Suppress("LongParameterList")
@Singleton
class ExecuteScheduledOperationUseCase @Inject constructor(
    private val scheduledOperationRepository: ScheduledOperationRepository,
    private val resourceRepository: ResourceRepository,
    private val getMediaFilesUseCase: GetMediaFilesUseCase,
    private val fileOperationUseCase: FileOperationUseCase,
    private val appendToScheduledLogUseCase: AppendToScheduledLogUseCase,
    private val checkLocalFolderWritableUseCase: CheckLocalFolderWritableUseCase,
    private val refreshResourceFileCountsUseCase: RefreshResourceFileCountsUseCase,
    private val statsSink: StatsSink,
    private val mutationRecorder: MutationRecorder,
    private val archiveFilesUseCase: ArchiveFilesUseCase,
    private val cleanupOrphanedTempFilesUseCase: CleanupOrphanedTempFilesUseCase,
) {
    // One stable line for a refused protected-folder PIN: the journal, the notification body and the
    // status string all quote it, so a raw exception message never reaches the user (S4232).
    private companion object {
        const val PIN_REFUSED_MESSAGE =
            "Protected folder PIN was rejected - re-enter the PIN on the resource screen"
    }

    // S2598: Locale.US, not the interface language - this stamp goes into a persisted log line that
    // outlives a language change, and an Arabic or Bengali interface would render its digits in a script
    // the neighbouring lines of the same file do not share.
    private val logDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
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
        } else {
            null
        }

        val srcLabel = "${sourceResource.name} [${sourceResource.type.name}]"
        val dstLabel = targetResource?.let { "${it.name} [${it.type.name}]" } ?: "-"

        if (operation.operationType == ScheduledOpType.ARCHIVE) {
            val archiveScopeError = archiveScopeError(sourceResource, targetResource)
            if (archiveScopeError != null) {
                logOp(ts, opName, srcLabel, dstLabel, "ERROR: $archiveScopeError")
                Timber.w("ScheduledOp[$operationId] $archiveScopeError")
                return ScheduledExecutionResult(operationId, 0, listOf(archiveScopeError))
            }
        }

        return try {
            // Step 0: a network source that cannot be reached scans as an empty folder, so the run would
            // end "No files found" - a success that consumes the SINCE_LAST window and loses its files.
            val sourceUnreachable = unreachableReason(sourceResource, "Source")
            if (sourceUnreachable != null) {
                return skipped(operationId, sourceUnreachable, ts, opName, srcLabel, dstLabel)
            }

            // Step 1: Load and filter source files.
            val allFiles = getMediaFilesUseCase(
                resource = buildEffectiveResource(sourceResource, operation.fileTypeMask),
                sortMode = SortMode.NAME_ASC,
                forceFullScan = true
            ).first()

            val filtered = applyFilters(allFiles, operation)

            if (filtered.isEmpty()) {
                // An unmounted card or a removed folder lists as empty too; the same loss as above.
                val missing = missingLocalSourceReason(sourceResource)
                if (missing != null) {
                    return skipped(operationId, missing, ts, opName, srcLabel, dstLabel)
                }
                val msg = "No files found"
                logOp(ts, opName, srcLabel, dstLabel, msg)
                Timber.d("ScheduledOp[$operationId] $msg")
                return ScheduledExecutionResult(operationId, 0, emptyList())
            }

            // Step 2: For COPY/MOVE - verify target is reachable before doing any work.
            if (targetResource != null) {
                val targetUnreachable = unreachableReason(targetResource, "Target")
                if (targetUnreachable != null) {
                    return skipped(operationId, targetUnreachable, ts, opName, srcLabel, dstLabel)
                }
                val reachabilityError = localTargetError(targetResource)
                if (reachabilityError != null) {
                    logOp(ts, opName, srcLabel, dstLabel, "ERROR: $reachabilityError")
                    Timber.w("ScheduledOp[$operationId] $reachabilityError")
                    return ScheduledExecutionResult(operationId, 0, listOf(reachabilityError))
                }
            }

            // S4232: an interrupted earlier run can leave a *.temp_copy on the target that its window
            // never retries, and the browse-open cleanup never runs for a target only the schedule
            // touches - so the run cleans its own target first; cloud and virtual paths no-op inside
            // the use case, and a colliding temp of the very next file is replaced by the atomic layer.
            if (operation.operationType == ScheduledOpType.COPY ||
                operation.operationType == ScheduledOpType.MOVE
            ) {
                cleanupOrphanedTempFilesUseCase(requireNotNull(targetResource).path)
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
                            // S3376: bound to incrementSuccess rather than to the branch above it, because
                            // handleFileResult calls this only for a success with skippedCount == 0 - a
                            // record on a skip or a partial would claim a move the disk did not make.
                            incrementSuccess = {
                                successCount++
                                recordScheduledMutation(
                                    ScheduledOpType.MOVE,
                                    file,
                                    sourceResource,
                                    targetResource
                                )
                            },
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
                            incrementSuccess = {
                                successCount++
                                recordScheduledMutation(
                                    ScheduledOpType.DELETE,
                                    file,
                                    sourceResource,
                                    targetResource
                                )
                            },
                            setPermissionStop = { permissionStop = true }
                        )
                    }
                }
                ScheduledOpType.ARCHIVE -> {
                    successCount = runArchive(
                        files = filtered,
                        sourceName = sourceResource.name,
                        // archiveScopeError above already refused a null target.
                        targetDir = requireNotNull(targetResource).path,
                        operationId = operationId,
                        log = { logOp(ts, opName, srcLabel, dstLabel, it) },
                        addError = { errors.add(it) }
                    )
                }
            }

            // Summary line
            val statusStr = if (errors.isEmpty()) "OK ($successCount files)" else "ERROR: ${errors.first()}"
            logOp(ts, opName, srcLabel, dstLabel, statusStr)
            // Count one run once the work loop executed; config-failure early returns above and the
            // exception path below are not runs. filesProcessed carries the successfully handled count.
            statsSink.record(StatsEvent.ScheduledRun(filesProcessed = successCount.toLong()))
            if (successCount > 0) {
                refreshResourceFileCountsUseCase(
                    listOfNotNull(sourceResource.id, targetResource?.id),
                )
            }
            ScheduledExecutionResult(operationId, successCount, errors, permissionRequired = permissionStop)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkPinRefusedException) {
            // S4232: a required-but-refused PIN is a configuration error the user fixes on the resource
            // screen, not an unreachable device, so it stays ERROR with its own line - never SKIPPED -
            // and the existing failure notification fires. The stored PIN itself never reaches a log.
            val msg = PIN_REFUSED_MESSAGE
            Timber.w(e, "ScheduledOp[$operationId] protected folder PIN refused")
            logOp(ts, opName, srcLabel, dstLabel, "ERROR: $msg")
            ScheduledExecutionResult(operationId, 0, listOf(msg))
        } catch (e: Exception) {
            Timber.e(e, "ScheduledOp[$operationId] unexpected exception")
            val msg = e.message ?: "Unknown error"
            logOp(ts, opName, srcLabel, dstLabel, "ERROR: $msg")
            ScheduledExecutionResult(operationId, 0, listOf(msg))
        }
    }

    /**
     * Records and returns a skipped run: a network side was unreachable before any file was touched.
     * The status is `SKIPPED`, not `ERROR` - an offline server is a normal state - so the worker keeps
     * the SINCE_LAST marker and sends no failure notification.
     */
    private fun skipped(
        operationId: Long,
        reason: String,
        ts: String,
        opName: String,
        srcLabel: String,
        dstLabel: String
    ): ScheduledExecutionResult {
        logOp(ts, opName, srcLabel, dstLabel, "${ScheduledRunStatus.SKIPPED_PREFIX}: $reason")
        Timber.i("ScheduledOp[$operationId] skipped - $reason")
        Timber.d("S4211: skipped op=$operationId reason=$reason")
        return ScheduledExecutionResult(operationId, 0, emptyList(), skippedReason = reason)
    }

    /**
     * Returns why a network [resource] cannot be reached, null when it can or when it is not a network
     * resource. Uses `resourceRepository.testConnection()` (SMB/SFTP/FTP/CLOUD).
     */
    private suspend fun unreachableReason(resource: MediaResource, role: String): String? {
        if (!resource.type.isNetworkResource) return null
        return try {
            val result = resourceRepository.testConnection(resource)
            if (result.isSuccess) {
                null
            } else {
                val cause = result.exceptionOrNull()?.message ?: "connection failed"
                "$role '${resource.name}' is unreachable: $cause"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "$role '${resource.name}' is unreachable: ${e.message ?: "connection failed"}"
        }
    }

    /** Why a local filesystem [source] that listed empty is not actually there, or null when it exists. */
    private fun missingLocalSourceReason(source: MediaResource): String? {
        val isFilesystemPath = !source.type.isNetworkResource && !PathUtils.isContentUri(source.path)
        return if (isFilesystemPath && !File(source.path).isDirectory) {
            "Source folder '${source.path}' is not available"
        } else {
            null
        }
    }

    /**
     * Returns an error string if a local target cannot receive files, null if OK.
     * - Content tree: checks that the tree is writable.
     * - Filesystem path: checks that the directory exists.
     * A network target is judged by [unreachableReason] instead.
     */
    private suspend fun localTargetError(target: MediaResource): String? {
        return if (target.type.isNetworkResource) {
            null
        } else if (PathUtils.isContentUri(target.path)) {
            // A SAF tree Uri is not a filesystem path, so File(path).exists() is always false
            // for one. That made every ad-hoc local-folder TARGET fail pre-flight and the operation
            // finish "done" having copied nothing. Probe the tree the way the picker does - for a
            // receiver, writability is the property that actually matters.
            val writable = checkLocalFolderWritableUseCase(target.path)
            if (writable) null else "Target directory '${target.path}' is not writable"
        } else {
            val dir = File(target.path)
            if (dir.exists() && dir.isDirectory) {
                null
            } else {
                "Target directory '${target.path}' does not exist"
            }
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
            if (FileTypeFlags.hasImages(mask)) types += setOf(MediaType.IMAGE, MediaType.GIF)
            if (FileTypeFlags.hasAudio(mask)) types += MediaType.AUDIO
            if (FileTypeFlags.hasVideo(mask)) types += MediaType.VIDEO
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
            .filter { matchesTimeFilter(it, op.timeFilter, op.sinceLastMarkerAt, now) }
            .filter { op.fileConditions.matches(it, now) }
    }

    private fun matchesTypeMask(file: MediaFile, mask: Int): Boolean {
        if (FileTypeFlags.hasImages(mask) && (file.type == MediaType.IMAGE || file.type == MediaType.GIF)) return true
        if (FileTypeFlags.hasAudio(mask) && file.type == MediaType.AUDIO) return true
        if (FileTypeFlags.hasVideo(mask) && file.type == MediaType.VIDEO) return true
        if (FileTypeFlags.hasDocuments(mask) && file.type.isDocumentFile()) return true
        return false
    }

    private fun matchesTimeFilter(
        file: MediaFile,
        filter: TimeFilter,
        sinceMarker: Long?,
        now: Long
    ): Boolean = when (filter) {
        TimeFilter.ALL -> true
        TimeFilter.SINCE_LAST -> sinceMarker == null || file.createdDate > sinceMarker
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
            // A partial result for one source file means that file failed; counting it as handled
            // would let the run end "OK" and move the SINCE_LAST marker past a file never transferred.
            result is FileOperationResult.PartialSuccess && result.failedCount > 0 -> {
                val err = result.errors.firstOrNull() ?: fallbackErrMessage
                addError(err)
                logOp(ts, opName, srcLabel, dstLabel, "ERROR: ${file.name}: $err")
                Timber.d("ScheduledOp[$operationId] $opLabel ERROR ${file.name}: $err")
            }
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

    /**
     * S3376: registers one processed file in the mutation journal so an open Browse list reconciles it on
     * its next resume. The scheduler is a background actor - unlike Browse's own delete, nothing calls
     * `reloadFiles()` when a schedule fires, and a remote resource is outside the local FileObserver's
     * reach entirely, so the journal is the only channel that reaches the surface.
     *
     * COPY records nothing - it destroys no source, and an entry would tell the reconciler to drop a row
     * whose file is still on disk.
     */
    private fun recordScheduledMutation(
        operationType: ScheduledOpType,
        file: MediaFile,
        source: MediaResource,
        target: MediaResource?
    ) {
        when (operationType) {
            // ARCHIVE keeps every source and only adds one new ZIP, so like COPY there is nothing to reconcile.
            ScheduledOpType.COPY, ScheduledOpType.ARCHIVE -> return
            ScheduledOpType.DELETE -> mutationRecorder.recordDelete(source.id, file.path, source.type)
            ScheduledOpType.MOVE -> {
                if (target == null) return
                mutationRecorder.recordMove(
                    srcResourceId = source.id,
                    srcResourceType = source.type,
                    srcRawPath = file.path,
                    dstResourceId = target.id,
                    dstResourceType = target.type,
                    dstRawPath = "${target.path.trimEnd('/')}/${file.name}"
                )
            }
        }
    }

    /**
     * ARCHIVE writes the ZIP through plain `File` I/O and [ArchiveFilesUseCase] skips every remote path,
     * so a network source or a non-filesystem target would produce an empty or missing archive. Rejecting
     * the run up front gives the log a clear cause instead.
     */
    private fun archiveScopeError(source: MediaResource, target: MediaResource?): String? = when {
        source.type.isNetworkResource -> "Archive source must be a local resource"
        target == null || target.type.isNetworkResource || PathUtils.isContentUri(target.path) ->
            "Archive target must be a local folder"
        else -> null
    }

    /** Packs [files] into one new ZIP in [targetDir]; sources are kept. Returns the archived file count. */
    private suspend fun runArchive(
        files: List<MediaFile>,
        sourceName: String,
        targetDir: String,
        operationId: Long,
        log: (String) -> Unit,
        addError: (String) -> Unit
    ): Int {
        var archivedCount = 0
        archiveFilesUseCase(files.map { it.path }, buildArchiveName(sourceName), targetDir).collect { progress ->
            when (progress) {
                is ArchiveProgress.Started ->
                    Timber.d("ScheduledOp[$operationId] ARCHIVE started: ${progress.totalFiles} files")
                is ArchiveProgress.FileDone ->
                    Timber.d("ScheduledOp[$operationId] ARCHIVE OK ${progress.fileName}")
                is ArchiveProgress.FileWarning -> log("SKIP: ${progress.fileName}: ${progress.reason}")
                is ArchiveProgress.Success -> {
                    archivedCount = progress.archivedCount
                    log("OK: ${progress.archivePath}")
                }
                is ArchiveProgress.Error -> addError(progress.message)
            }
        }
        return archivedCount
    }

    /**
     * `<source>_<yyyyMMdd_HHmm>`; anything outside a filename-safe ASCII set becomes `_` so a resource name
     * with slashes or colons cannot escape the target folder or break a FAT-formatted card.
     * Locale.US for the same reason as [logDateFormat].
     */
    private fun buildArchiveName(sourceName: String): String {
        val safeName = sourceName.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_', '.')
            .ifEmpty { "scheduled_archive" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "${safeName}_$stamp"
    }

    /** Appends one line to the user-visible operations log. */
    private fun logOp(ts: String, opName: String, src: String, dst: String, message: String) {
        appendToScheduledLogUseCase("$ts | $opName | $src → $dst | $message")
    }
}

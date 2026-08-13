package com.sza.fastmediasorter.data.transfer

import com.sza.fastmediasorter.domain.transfer.TempFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1325: whole-tree copy and move between two resources of different protocols.
 *
 * Same-protocol trees keep their per-protocol implementation in [FileOperationStrategy]; this
 * manager exists for the combination that had no implementation at all - a folder crossing from
 * one protocol to another, which used to be refused outright while a single file crossed fine.
 *
 * The walk is breadth-first and streams: one directory listing is held at a time, never the whole
 * tree, so memory does not grow with the number of files. Each entry is transferred through the
 * per-file path of the strategies involved, so a tree inherits exactly the write behaviour a single
 * file already has on that pair of protocols.
 */
@Singleton
class DirectoryTreeTransferManager @Inject constructor(
    private val operationStrategies: Map<String, @JvmSuppressWildcards FileOperationStrategy>,
    private val tempFileManager: TempFileManager,
) {

    /** Copies [sourcePath] to [destinationPath]; returns the number of files written. */
    suspend fun copyTree(
        sourcePath: String,
        destinationPath: String,
        progressCallback: ((Int, Int, String) -> Unit)? = null,
    ): Result<Int> = transferTree(sourcePath, destinationPath, progressCallback, deleteSource = false)

    /**
     * Moves [sourcePath] to [destinationPath]. A source file is deleted only after its own copy
     * succeeded, and the emptied source directories are removed only when every entry made it, so
     * an interrupted move leaves a partial tree and never a hole.
     */
    suspend fun moveTree(
        sourcePath: String,
        destinationPath: String,
        progressCallback: ((Int, Int, String) -> Unit)? = null,
    ): Result<Int> = transferTree(sourcePath, destinationPath, progressCallback, deleteSource = true)

    private suspend fun transferTree(
        sourcePath: String,
        destinationPath: String,
        progressCallback: ((Int, Int, String) -> Unit)?,
        deleteSource: Boolean,
    ): Result<Int> = withContext(Dispatchers.IO) {
        val sourceStrategy = strategyFor(sourcePath)
            ?: return@withContext Result.failure(noStrategy(sourcePath))
        val destinationStrategy = strategyFor(destinationPath)
            ?: return@withContext Result.failure(noStrategy(destinationPath))

        var transferred = 0
        val failures = mutableListOf<String>()
        val pending = ArrayDeque<Pair<String, String>>()
        pending.addLast(sourcePath to destinationPath)

        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (sourceDir, destinationDir) = pending.removeFirst()

            val created = destinationStrategy.createDirectory(destinationDir)
            if (created.isFailure) {
                return@withContext Result.failure(
                    created.exceptionOrNull() ?: Exception("Failed to create $destinationDir")
                )
            }

            val entries = sourceStrategy.listEntries(sourceDir)
            if (entries.isFailure) {
                return@withContext Result.failure(
                    entries.exceptionOrNull() ?: Exception("Failed to list $sourceDir")
                )
            }

            for (entry in entries.getOrThrow()) {
                currentCoroutineContext().ensureActive()
                val target = "${destinationDir.trimEnd('/')}/${entry.name}"
                if (entry.isDirectory) {
                    pending.addLast(entry.path to target)
                } else {
                    // Total is unknown by design - the walk never pre-collects the tree. Consumers
                    // show an indeterminate indicator while total is 0 and still name the entry.
                    progressCallback?.invoke(transferred, 0, entry.name)
                    val copied = copyEntry(entry.path, target, sourceStrategy, destinationStrategy)
                    if (copied.isFailure) {
                        failures += "${entry.name}: ${copied.exceptionOrNull()?.message.orEmpty()}"
                        Timber.w("DirectoryTreeTransferManager: entry failed - ${entry.path}")
                    } else {
                        transferred++
                        if (deleteSource) {
                            sourceStrategy.deleteFile(entry.path)
                        }
                    }
                }
            }
        }

        if (failures.isNotEmpty()) {
            return@withContext Result.failure(
                Exception("Transferred $transferred entries, ${failures.size} failed: ${failures.first()}")
            )
        }
        if (deleteSource) {
            sourceStrategy.deleteDirectory(sourcePath, null)
        }
        Result.success(transferred)
    }

    /**
     * One file across the protocol boundary. A remote strategy already handles the remote-to-local
     * and local-to-remote directions itself, so only remote-to-different-remote needs the temp file
     * hop - the same shape the single-file cross-protocol copy uses.
     */
    private suspend fun copyEntry(
        sourceFilePath: String,
        destinationFilePath: String,
        sourceStrategy: FileOperationStrategy,
        destinationStrategy: FileOperationStrategy,
    ): Result<Unit> {
        val sourceIsLocal = sourceStrategy.getProtocolName() == LOCAL_PROTOCOL
        val destinationIsLocal = destinationStrategy.getProtocolName() == LOCAL_PROTOCOL

        return if (sourceIsLocal || destinationIsLocal) {
            val strategy = if (sourceIsLocal) destinationStrategy else sourceStrategy
            strategy.copyFile(sourceFilePath, destinationFilePath, overwrite = true).map { }
        } else {
            copyThroughTempFile(sourceFilePath, destinationFilePath, sourceStrategy, destinationStrategy)
        }
    }

    /** Remote to a different remote: no strategy spans both ends, so the bytes land locally first. */
    private suspend fun copyThroughTempFile(
        sourceFilePath: String,
        destinationFilePath: String,
        sourceStrategy: FileOperationStrategy,
        destinationStrategy: FileOperationStrategy,
    ): Result<Unit> {
        val tempFile = tempFileManager.createTempFileFromName(
            sourceFilePath.trimEnd('/').substringAfterLast('/')
        )
        return try {
            sourceStrategy.copyFile(sourceFilePath, tempFile.absolutePath, overwrite = true)
                .mapCatching {
                    destinationStrategy
                        .copyFile(tempFile.absolutePath, destinationFilePath, overwrite = true)
                        .getOrThrow()
                }
                .map { }
        } finally {
            tempFileManager.cleanupTempFile(tempFile)
        }
    }

    private fun strategyFor(path: String): FileOperationStrategy? =
        operationStrategies[protocolKeyOf(path)]

    private fun noStrategy(path: String): Exception =
        IllegalStateException("No FileOperationStrategy registered for $path")

    private fun protocolKeyOf(path: String): String = transferProtocolKeyFor(path)

    private companion object {
        const val LOCAL_PROTOCOL = "local"
    }
}

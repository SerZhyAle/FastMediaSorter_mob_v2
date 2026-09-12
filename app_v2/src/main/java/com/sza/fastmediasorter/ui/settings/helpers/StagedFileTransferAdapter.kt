package com.sza.fastmediasorter.ui.settings.helpers

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import com.sza.fastmediasorter.domain.port.StagedFileTransferPort
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.ExportResourcesToFileUseCase
import com.sza.fastmediasorter.util.VirtualPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1565: stages transfer bytes as a private file so the shipped `Uri`-based flows can consume them.
 *
 * Lives in the UI layer because the resource-share importer does; the contract it satisfies is
 * declared in the domain layer.
 */
@Singleton
class StagedFileTransferAdapter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository,
    private val exportResourcesToFile: ExportResourcesToFileUseCase
) : StagedFileTransferPort {

    override suspend fun exportResources(): ByteArray? = withContext(Dispatchers.IO) {
        val target = stagingFile(TransferDataKind.RESOURCES)
        val ids = resourceRepository.getAllResourcesSync()
            .filter { !VirtualPathUtils.isVirtualPath(it.path) }
            .map { it.id }
        when (val result = exportResourcesToFile(ids, Uri.fromFile(target))) {
            is ExportResourcesToFileUseCase.ExportResult.Success -> target.readBytes()
            is ExportResourcesToFileUseCase.ExportResult.Failure -> {
                Timber.e(result.error, "Resource export for a data transfer failed")
                null
            }
        }
    }

    override suspend fun stage(kind: TransferDataKind, bytes: ByteArray): Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = stagingFile(kind)
                file.writeBytes(bytes)
                Uri.fromFile(file)
            }.onFailure { Timber.e(it, "Staging ${kind.name} for a data transfer failed") }.getOrNull()
        }

    override suspend fun readFrom(source: Uri): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(source)?.use { it.readBytes() } }
            .onFailure { Timber.e(it, "Reading a transfer document failed") }
            .getOrNull()
    }

    override suspend fun writeTo(target: Uri, bytes: ByteArray): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                // "wt" truncates: without it a shorter export leaves the tail of the previous one.
                context.contentResolver.openOutputStream(target, "wt")?.use { it.write(bytes) }
                    ?: return@runCatching false
                true
            }.onFailure { Timber.e(it, "Writing a transfer document failed") }.getOrDefault(false)
        }

    private fun stagingFile(kind: TransferDataKind): File {
        val dir = File(context.cacheDir, STAGING_DIR).apply { mkdirs() }
        return File(dir, kind.driveFileName)
    }

    private companion object {
        const val STAGING_DIR = "transfer"
    }
}

package com.sza.fastmediasorter.data.transfer

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.transfer.strategy.CloudOperationStrategy
import com.sza.fastmediasorter.data.transfer.strategy.parseCloudUri
import com.sza.fastmediasorter.domain.transfer.SiblingFolder
import com.sza.fastmediasorter.utils.SafHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.FileNotFoundException
import javax.inject.Inject

/**
 * S3408: the folder a FileDO result goes into, beside a file that is not a local path.
 *
 * Null for a local path (written through `java.io.File` by the local pair), for a cloud file whose
 * provider names no containing folder, and for any other scheme.
 */
class SiblingFolderResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val strategies: Map<String, @JvmSuppressWildcards FileOperationStrategy>,
    private val cloudStrategy: CloudOperationStrategy,
) {

    /**
     * [currentFolder] is the folder the list is showing. It is used only for a document whose own
     * folder the provider cannot name - with subfolder scanning on, a listed file may sit below it.
     * A cloud file never falls back to it for the same reason: its folder comes from the provider.
     */
    suspend fun folderBeside(filePath: String, currentFolder: String?): SiblingFolder? =
        when (val key = transferProtocolKeyFor(filePath)) {
            in NETWORK_KEYS -> strategies[key]?.let { StrategySiblingFolder(filePath.substringBeforeLast('/'), it) }
            SAF_KEY -> safFolderOf(filePath, currentFolder)?.let { SafSiblingFolder(context.contentResolver, it) }
            CLOUD_KEY -> cloudFolderOf(filePath)
            else -> null
        }

    /** A cloud path names the file by id alone, so its folder is asked of the provider. */
    private suspend fun cloudFolderOf(filePath: String): SiblingFolder? {
        val info = parseCloudUri(filePath) ?: return null
        val client = cloudStrategy.authenticatedClient(info.provider)
        val parentId = client?.let { (it.getFileMetadata(info.idOrPath) as? CloudResult.Success)?.data?.parentId }
        return if (client != null && parentId != null) {
            CloudSiblingFolder(client, parentId)
        } else {
            Timber.w("fdsec: the folder of a %s file is unknown", info.provider)
            null
        }
    }

    private fun safFolderOf(filePath: String, currentFolder: String?): Uri? =
        treeParentOf(SafHelper.parseUri(filePath))
            ?: currentFolder?.takeIf { SafHelper.isContentUri(it) }?.let { asFolderDocument(SafHelper.parseUri(it)) }

    private fun treeParentOf(document: Uri): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isTreeBased(document)) return null
        val path = try {
            DocumentsContract.findDocumentPath(context.contentResolver, document)?.path
        } catch (e: FileNotFoundException) {
            Timber.w("fdsec: the document's folder is unknown to its provider (%s)", e.javaClass.simpleName)
            null
        } catch (e: UnsupportedOperationException) {
            Timber.w("fdsec: the provider cannot name a document's folder (%s)", e.javaClass.simpleName)
            null
        } catch (e: SecurityException) {
            Timber.w("fdsec: the provider refused to name a document's folder (%s)", e.javaClass.simpleName)
            null
        } catch (e: IllegalArgumentException) {
            Timber.w("fdsec: the provider did not accept the document address (%s)", e.javaClass.simpleName)
            null
        }
        // The path runs from the tree root to the document itself, so its parent is the entry before last.
        return path?.takeIf { it.size >= 2 }?.let { ids ->
            DocumentsContract.buildDocumentUriUsingTree(document, ids[ids.size - 2])
        }
    }

    /** The Browse folder is a tree URI at a resource root and a tree-based document URI below it. */
    private fun asFolderDocument(folder: Uri): Uri? {
        val segments = folder.pathSegments.size
        return when {
            !isTreeBased(folder) || segments < TREE_SEGMENTS -> null
            segments >= DOCUMENT_SEGMENTS -> folder
            else -> DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
        }
    }

    // DocumentsContract.isTreeUri is API 24 and the legacy flavor starts at 23.
    private fun isTreeBased(uri: Uri): Boolean = uri.pathSegments.firstOrNull() == TREE_SEGMENT

    private companion object {
        const val SAF_KEY = "saf"
        const val CLOUD_KEY = "cloud"
        const val TREE_SEGMENT = "tree"

        /** `tree/<treeId>`. */
        const val TREE_SEGMENTS = 2

        /** `tree/<treeId>/document/<documentId>`. */
        const val DOCUMENT_SEGMENTS = 4
        val NETWORK_KEYS = setOf("smb", "sftp", "ftp")
    }
}

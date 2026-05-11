package com.sza.fastmediasorter.ui.player.fileops

import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.usecase.DeletePathPolicy
import com.sza.fastmediasorter.domain.usecase.FileOperation
import java.io.File
import java.util.UUID

sealed class PlayerFileOperation {
    abstract val id: String
    abstract val sourcePath: String
    abstract val sourceName: String
    abstract val sourceCredentialsId: String?
    open val displayName: String get() = sourceName

    data class MoveToResource(
        override val sourcePath: String,
        override val sourceName: String,
        override val sourceCredentialsId: String?,
        val destination: MediaResource,
        override val id: String = UUID.randomUUID().toString(),
        override val displayName: String = sourceName,
    ) : PlayerFileOperation()

    data class MoveToPath(
        override val sourcePath: String,
        override val sourceName: String,
        override val sourceCredentialsId: String?,
        val destinationPath: String,
        override val id: String = UUID.randomUUID().toString(),
        override val displayName: String = sourceName,
    ) : PlayerFileOperation()

    data class Delete(
        override val sourcePath: String,
        override val sourceName: String,
        override val sourceCredentialsId: String?,
        val softDeleteAllowed: Boolean,
        override val id: String = UUID.randomUUID().toString(),
        override val displayName: String = sourceName,
    ) : PlayerFileOperation()

    data class Rename(
        override val sourcePath: String,
        override val sourceName: String,
        override val sourceCredentialsId: String?,
        val newName: String,
        override val id: String = UUID.randomUUID().toString(),
        override val displayName: String = sourceName,
    ) : PlayerFileOperation()

    companion object {
        fun moveToResource(
            currentFile: MediaFile,
            currentResource: MediaResource?,
            destination: MediaResource,
        ): MoveToResource {
            return MoveToResource(
                sourcePath = currentFile.path,
                sourceName = currentFile.name,
                sourceCredentialsId = currentResource?.credentialsId,
                destination = destination,
            )
        }

        fun moveToPath(
            currentFile: MediaFile,
            currentResource: MediaResource?,
            destinationPath: String,
        ): MoveToPath {
            return MoveToPath(
                sourcePath = currentFile.path,
                sourceName = currentFile.name,
                sourceCredentialsId = currentResource?.credentialsId,
                destinationPath = destinationPath,
            )
        }

        fun delete(currentFile: MediaFile, currentResource: MediaResource?): Delete {
            return Delete(
                sourcePath = currentFile.path,
                sourceName = currentFile.name,
                sourceCredentialsId = currentResource?.credentialsId,
                softDeleteAllowed = DeletePathPolicy.canUseSoftDelete(currentFile.path),
            )
        }

        fun rename(
            currentFile: MediaFile,
            currentResource: MediaResource?,
            newName: String,
        ): Rename {
            return Rename(
                sourcePath = currentFile.path,
                sourceName = currentFile.name,
                sourceCredentialsId = currentResource?.credentialsId,
                newName = newName,
            )
        }
    }
}

fun PlayerFileOperation.toDomainFileOperation(settings: AppSettings): FileOperation {
    return when (this) {
        is PlayerFileOperation.MoveToResource -> FileOperation.Move(
            sources = listOf(createNetworkAwareFile(sourcePath, sourceName)),
            destination = createNetworkAwareFile(destination.path, null),
            overwrite = settings.overwriteOnMove,
            sourceCredentialsId = sourceCredentialsId,
        )

        is PlayerFileOperation.MoveToPath -> FileOperation.Move(
            sources = listOf(createNetworkAwareFile(sourcePath, sourceName)),
            destination = createNetworkAwareFile(destinationPath, null),
            overwrite = settings.overwriteOnMove,
            sourceCredentialsId = sourceCredentialsId,
        )

        is PlayerFileOperation.Delete -> FileOperation.Delete(
            files = listOf(createNetworkAwareFile(sourcePath, sourceName)),
            softDelete = softDeleteAllowed,
        )

        is PlayerFileOperation.Rename -> FileOperation.Rename(
            file = createNetworkAwareFile(sourcePath, sourceName),
            newName = newName,
        )
    }
}

// Preserve protocol-backed paths verbatim so FileOperationUseCase can route by scheme.
internal fun createNetworkAwareFile(path: String, name: String?): File {
    return if (
        path.startsWith("smb://") ||
        path.startsWith("sftp://") ||
        path.startsWith("ftp://") ||
        path.startsWith("cloud://")
    ) {
        object : File(path) {
            override fun getAbsolutePath(): String = path
            override fun getPath(): String = path
            override fun getName(): String = name ?: super.getName()
        }
    } else {
        File(path)
    }
}
package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry
import com.sza.fastmediasorter.data.local.staging.StagedKind
import com.sza.fastmediasorter.data.local.staging.StagingDirectoryProvider
import com.sza.fastmediasorter.domain.files.FileNameConflictResolver
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.util.DrawingTargetPolicy
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * Creates the initial blank JPEG file for the S0191 Browse -> Draw flow.
 *
 * Unlike text notes, the drawing editor needs real image bytes immediately so PlayerActivity
 * can display the file and enter draw mode on top of it. LOCAL resources therefore get a real
 * file in the target folder up front; network/cloud resources get a staged local JPEG that will
 * later be committed to the remote parent on Save.
 */
class CreateDrawingUseCase @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val stagingDirectoryProvider: StagingDirectoryProvider,
    private val stagingRegistry: LocalStagingRegistry,
) {

    private val forbiddenChars = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    suspend operator fun invoke(
        resource: MediaResource,
        parentPath: String,
        fileName: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (resource.isReadOnly) {
            return@withContext Result.failure(Exception("Resource is read-only"))
        }

        val trimmedName = fileName.trim()
        if (trimmedName.isEmpty()) {
            return@withContext Result.failure(Exception("File name cannot be empty"))
        }

        if (trimmedName.any { it in forbiddenChars }) {
            return@withContext Result.failure(Exception("File name contains invalid characters"))
        }

        if (trimmedName.length > 255) {
            return@withContext Result.failure(Exception("File name is too long"))
        }

        val requestedName = ensureJpegExtension(trimmedName)
        val initialBytes = buildBlankDrawingBytes()

        val resolvedParentPath = DrawingTargetPolicy.resolveParentPath(resource, parentPath)
        DrawingTargetPolicy.ensureFallbackDirectoryIfNeeded(resource, resolvedParentPath)
            .onFailure { return@withContext Result.failure(it) }

        runCatching {
            when (resource.type) {
                ResourceType.LOCAL -> createLocalDrawing(
                    resource = resource,
                    parentPath = resolvedParentPath,
                    requestedName = requestedName,
                    bytes = initialBytes,
                )

                else -> createStagedDrawing(
                    resource = resource,
                    parentPath = resolvedParentPath,
                    requestedName = requestedName,
                    bytes = initialBytes,
                )
            }.absolutePath
        }
    }

    private fun createLocalDrawing(
        resource: MediaResource,
        parentPath: String,
        requestedName: String,
        bytes: ByteArray,
    ): File {
        val parentDir = File(parentPath)
        require(parentDir.exists() && parentDir.isDirectory) {
            "Target parent directory is unavailable: $parentPath"
        }

        val (finalName, _) = FileNameConflictResolver.resolveLocal(parentDir, requestedName)
        val targetFile = File(parentDir, finalName)
        targetFile.writeBytes(bytes)

        stagingRegistry.register(
            file = targetFile,
            targetResourceId = resource.id,
            targetParentPath = parentPath,
            intendedName = finalName,
            kind = StagedKind.DRAWING,
            location = LocalStagingRegistry.Location.LOCAL_DEFERRED,
        )

        // Register the new JPEG in MediaStore so it surfaces in the "all images" / "camera"
        // aggregate views, not only in the direct folder listing.
        MediaStoreNotifier.notifyFile(appContext, targetFile.absolutePath, "create-drawing")

        return targetFile
    }

    private fun createStagedDrawing(
        resource: MediaResource,
        parentPath: String,
        requestedName: String,
        bytes: ByteArray,
    ): File {
        val stagingDir = stagingDirectoryProvider.directoryFor(StagedKind.DRAWING)
        val suffix = requestedName.substringAfterLast('.', "jpg").let { ".${it.lowercase()}" }
        val stagingFile = File.createTempFile("drawing_", suffix, stagingDir)
        stagingFile.writeBytes(bytes)

        stagingRegistry.register(
            file = stagingFile,
            targetResourceId = resource.id,
            targetParentPath = parentPath,
            intendedName = requestedName,
            kind = StagedKind.DRAWING,
            location = LocalStagingRegistry.Location.NETWORK_STAGED,
        )

        return stagingFile
    }

    private fun ensureJpegExtension(name: String): String {
        return if (name.contains('.')) name else "$name.jpg"
    }

    private fun buildBlankDrawingBytes(): ByteArray {
        val metrics = appContext.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val backgroundColor = resolveBackgroundColor()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            Canvas(bitmap).drawColor(backgroundColor)
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                    "Failed to encode the blank drawing bitmap"
                }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun resolveBackgroundColor(): Int {
        val nightMask = appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMask == Configuration.UI_MODE_NIGHT_YES) Color.BLACK else Color.WHITE
    }
}
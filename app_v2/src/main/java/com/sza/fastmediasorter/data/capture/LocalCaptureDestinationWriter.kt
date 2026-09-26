package com.sza.fastmediasorter.data.capture

import android.content.Context
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.util.CaptureFileNamer
import com.sza.fastmediasorter.utils.SafHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a finished capture into an on-device folder - a plain path through the MediaStore-aware
 * writer, or a SAF tree. The final name is chosen here against the destination's own listing
 * (CAPTURE-OUTPUT rules 5 and 6), so an existing file is never replaced and the storage layer never
 * appends a suffix of its own.
 */
@Singleton
class LocalCaptureDestinationWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val destinationClassifier: LocalDestinationClassifier,
    private val destinationWriter: LocalDestinationWriter,
) {

    /** [location] is the committed path or URI; [displayName] is the name the file really got. */
    data class Saved(val location: String, val displayName: String)

    suspend fun write(tempFile: File, destinationPath: String, displayName: String): Result<String> =
        writeCapture(tempFile, destinationPath, displayName).map { it.location }

    suspend fun writeCapture(tempFile: File, destinationPath: String, displayName: String): Result<Saved> =
        withContext(Dispatchers.IO) {
            if (isSafDestination(destinationPath)) {
                writeToSafTree(tempFile, destinationPath, displayName)
            } else {
                writeToFilePath(tempFile, destinationPath, displayName)
            }
        }

    internal fun isSafDestination(destinationPath: String): Boolean =
        destinationPath.startsWith("content:/")

    internal fun normalizeSafDestination(destinationPath: String): String =
        SafHelper.normalizeContentUri(destinationPath)

    private suspend fun writeToSafTree(
        tempFile: File,
        destinationPath: String,
        displayName: String,
    ): Result<Saved> = runCatching {
        val normalizedPath = normalizeSafDestination(destinationPath)
        val root = SafHelper.getTreeRoot(context, normalizedPath)
            ?: throw IOException("Cannot open SAF tree for capture")
        val finalName = CaptureFileNamer.nextFreeName(displayName) { root.findFile(it) != null }
        val document = SafHelper.getOrCreateWritableChildFile(
            context = context,
            treeUriString = normalizedPath,
            displayName = finalName,
            overwrite = false,
        ) ?: throw IOException("Cannot create capture document in SAF tree")
        tempFile.inputStream().use { input ->
            context.contentResolver.openOutputStream(document.uri, "w")?.use { output ->
                input.copyTo(output)
            } ?: throw IOException("Cannot open SAF destination output stream")
        }
        Saved(document.uri.toString(), finalName)
    }

    private suspend fun writeToFilePath(
        tempFile: File,
        destinationPath: String,
        displayName: String,
    ): Result<Saved> {
        val finalName = CaptureFileNamer.freeNameIn(File(destinationPath), displayName)
        Timber.d("S3746: capture writer requested=%s final=%s", displayName, finalName)
        val path = File(destinationPath, finalName).absolutePath
        // overwrite stays true: the name is already free on disk, and a stale pending MediaStore row
        // under it (left by an interrupted write) must not fail the capture.
        val sink = destinationWriter.open(destinationClassifier.classify(path), overwrite = true)
            .getOrElse { return Result.failure(it) }
        return try {
            tempFile.inputStream().use { input -> input.copyTo(sink.outputStream) }
            sink.commit().map { Saved(it, finalName) }
        } catch (e: CancellationException) {
            sink.abort()
            throw e
        } catch (e: IOException) {
            sink.abort()
            Result.failure(e)
        }
    }
}

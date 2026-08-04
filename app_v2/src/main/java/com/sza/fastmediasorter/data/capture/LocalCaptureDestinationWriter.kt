package com.sza.fastmediasorter.data.capture

import android.content.Context
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.utils.SafHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCaptureDestinationWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val destinationClassifier: LocalDestinationClassifier,
    private val destinationWriter: LocalDestinationWriter,
) {

    suspend fun write(tempFile: File, destinationPath: String, displayName: String): Result<String> =
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
    ): Result<String> = runCatching {
        val normalizedPath = normalizeSafDestination(destinationPath)
        val document = SafHelper.getOrCreateWritableChildFile(
            context = context,
            treeUriString = normalizedPath,
            displayName = displayName,
            overwrite = true,
        ) ?: throw IOException("Cannot create capture document in SAF tree")
        tempFile.inputStream().use { input ->
            context.contentResolver.openOutputStream(document.uri, "w")?.use { output ->
                input.copyTo(output)
            } ?: throw IOException("Cannot open SAF destination output stream")
        }
        document.uri.toString()
    }

    private suspend fun writeToFilePath(
        tempFile: File,
        destinationPath: String,
        displayName: String,
    ): Result<String> {
        val path = File(destinationPath, displayName).absolutePath
        val sink = destinationWriter.open(destinationClassifier.classify(path), overwrite = true)
            .getOrElse { return Result.failure(it) }
        return try {
            tempFile.inputStream().use { input -> input.copyTo(sink.outputStream) }
            sink.commit()
        } catch (e: CancellationException) {
            sink.abort()
            throw e
        } catch (e: IOException) {
            sink.abort()
            Result.failure(e)
        }
    }
}

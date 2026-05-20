package com.sza.fastmediasorter.data.transfer.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.sza.fastmediasorter.data.transfer.FileExistsException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

/**
 * Production implementation of [LocalDestinationWriter].
 *
 * - **Public collections** → MediaStore insert with `IS_PENDING = 1`, returns
 *   a sink whose `commit()` flips `IS_PENDING = 0`; respects the `overwrite`
 *   flag via a pre-insert query.
 * - **Non-public destinations** → direct `FileOutputStream`. EACCES on open is
 *   surfaced as [LocalDestinationPermissionDeniedException].
 *
 * Atomicity for public collections is provided by the pending flag; external
 * observers do not see the file until the sink is committed. For non-public
 * paths atomicity is the caller's concern (see [com.sza.fastmediasorter.data.transfer.AtomicFileOperationStrategy]).
 */
class MediaStoreLocalDestinationWriter @Inject constructor(
    @ApplicationContext private val context: Context
) : LocalDestinationWriter {

    override suspend fun open(
        destination: LocalDestinationCategory,
        overwrite: Boolean
    ): Result<LocalSink> = withContext(Dispatchers.IO) {
        when (destination) {
            is LocalDestinationCategory.PublicCollection -> openMediaStoreSink(destination, overwrite)
            is LocalDestinationCategory.NonPublic -> openFileSystemSink(destination, overwrite)
        }
    }

    // --------------------------------------------------------------------- MediaStore

    private fun openMediaStoreSink(
        destination: LocalDestinationCategory.PublicCollection,
        overwrite: Boolean
    ): Result<LocalSink> {
        val collectionUri = collectionUriFor(destination.collection)

        // Step 1 - check for existing record.
        val existingUri = findExistingItem(collectionUri, destination.displayName, destination.relativePath)
        if (existingUri != null) {
            if (!overwrite) {
                Timber.w("MediaStoreLocalDestinationWriter: exists & overwrite=false displayName=${destination.displayName} relativePath=${destination.relativePath}")
                return Result.failure(
                    FileExistsException(destination.displayName, existingUri.toString(), isMove = false)
                )
            }
            val deletedRows = context.contentResolver.delete(existingUri, null, null)
            Timber.d("MediaStoreLocalDestinationWriter: deleted existing record uri=$existingUri rows=$deletedRows")
        }

        // Step 2 - insert pending record.
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, destination.displayName)
            put(MediaStore.MediaColumns.RELATIVE_PATH, destination.relativePath)
            put(MediaStore.MediaColumns.MIME_TYPE, destination.mimeType)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        var insertedUri = context.contentResolver.insert(collectionUri, values)

        // Step 3 - fallback to Files collection if media-typed insert rejected (MIME mismatch).
        if (insertedUri == null && destination.collection != LocalDestinationCategory.PublicCollection.Kind.FILES) {
            val filesUri = MediaStore.Files.getContentUri("external")
            Timber.w("MediaStoreLocalDestinationWriter: insert rejected for ${destination.collection}, retrying on Files collection")
            insertedUri = context.contentResolver.insert(filesUri, values)
        }

        if (insertedUri == null) {
            Timber.e("MediaStoreLocalDestinationWriter: insert returned null for ${destination.displayName}")
            return Result.failure(IOException("MediaStore.insert returned null for ${destination.displayName}"))
        }

        // Step 4 - open output stream.
        val outputStream = try {
            context.contentResolver.openOutputStream(insertedUri)
        } catch (e: Exception) {
            Timber.e(e, "MediaStoreLocalDestinationWriter: openOutputStream failed uri=$insertedUri")
            context.contentResolver.delete(insertedUri, null, null)
            return Result.failure(IOException("openOutputStream failed for $insertedUri", e))
        }

        if (outputStream == null) {
            context.contentResolver.delete(insertedUri, null, null)
            return Result.failure(IOException("openOutputStream returned null for $insertedUri"))
        }

        return Result.success(MediaStoreSink(context, insertedUri, outputStream))
    }

    private fun collectionUriFor(kind: LocalDestinationCategory.PublicCollection.Kind): Uri = when (kind) {
        LocalDestinationCategory.PublicCollection.Kind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        LocalDestinationCategory.PublicCollection.Kind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        LocalDestinationCategory.PublicCollection.Kind.IMAGES -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        LocalDestinationCategory.PublicCollection.Kind.DOWNLOADS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Files.getContentUri("external")
            }
        LocalDestinationCategory.PublicCollection.Kind.FILES -> MediaStore.Files.getContentUri("external")
    }

    private fun findExistingItem(collectionUri: Uri, displayName: String, relativePath: String): Uri? {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val args = arrayOf(displayName, relativePath)
        return try {
            context.contentResolver.query(collectionUri, projection, selection, args, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    Uri.withAppendedPath(collectionUri, id.toString())
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "MediaStoreLocalDestinationWriter: existence query failed; treating as not-exists")
            null
        }
    }

    // --------------------------------------------------------------------- Filesystem

    private fun openFileSystemSink(
        destination: LocalDestinationCategory.NonPublic,
        overwrite: Boolean
    ): Result<LocalSink> {
        val file = File(destination.absolutePath)
        file.parentFile?.mkdirs()

        if (file.exists()) {
            if (!overwrite) {
                return Result.failure(
                    FileExistsException(destination.displayName, destination.absolutePath, isMove = false)
                )
            }
            if (!file.delete()) {
                Timber.w("MediaStoreLocalDestinationWriter: failed to delete existing file ${destination.absolutePath}")
            }
        }

        val outputStream = try {
            FileOutputStream(file)
        } catch (e: FileNotFoundException) {
            // EACCES from scoped storage typically surfaces as FileNotFoundException.
            Timber.e(e, "MediaStoreLocalDestinationWriter: cannot open ${destination.absolutePath} (EACCES likely)")
            return Result.failure(LocalDestinationPermissionDeniedException(destination.absolutePath, e))
        } catch (e: SecurityException) {
            Timber.e(e, "MediaStoreLocalDestinationWriter: SecurityException on ${destination.absolutePath}")
            return Result.failure(LocalDestinationPermissionDeniedException(destination.absolutePath, e))
        } catch (e: IOException) {
            Timber.e(e, "MediaStoreLocalDestinationWriter: IOException on ${destination.absolutePath}")
            return Result.failure(e)
        }

        return Result.success(FileSystemSink(file, outputStream))
    }

    // --------------------------------------------------------------------- Sink impls

    private class MediaStoreSink(
        private val context: Context,
        private val itemUri: Uri,
        override val outputStream: OutputStream
    ) : LocalSink {
        override suspend fun commit(): Result<String> = withContext(Dispatchers.IO + NonCancellable) {
            try {
                runCatching { outputStream.flush() }
                runCatching { outputStream.close() }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                val updated = context.contentResolver.update(itemUri, values, null, null)
                if (updated <= 0) {
                    Timber.w("MediaStoreSink.commit: update returned $updated for $itemUri")
                }
                Timber.d("MediaStoreSink.commit: published $itemUri")
                Result.success(itemUri.toString())
            } catch (e: Exception) {
                Timber.e(e, "MediaStoreSink.commit: failed for $itemUri")
                Result.failure(e)
            }
        }

        override suspend fun abort() {
            withContext(Dispatchers.IO + NonCancellable) {
                runCatching { outputStream.close() }
                val deletedRows = runCatching { context.contentResolver.delete(itemUri, null, null) }.getOrDefault(0)
                Timber.d("MediaStoreSink.abort: deleted pending uri=$itemUri rows=$deletedRows")
            }
        }
    }

    private class FileSystemSink(
        private val file: File,
        override val outputStream: OutputStream
    ) : LocalSink {
        override suspend fun commit(): Result<String> = withContext(Dispatchers.IO + NonCancellable) {
            try {
                runCatching { outputStream.flush() }
                runCatching { outputStream.close() }
                Timber.d("FileSystemSink.commit: ${file.absolutePath}")
                Result.success(file.absolutePath)
            } catch (e: Exception) {
                Timber.e(e, "FileSystemSink.commit: failed for ${file.absolutePath}")
                Result.failure(e)
            }
        }

        override suspend fun abort() {
            withContext(Dispatchers.IO + NonCancellable) {
                runCatching { outputStream.close() }
                if (file.exists() && !file.delete()) {
                    Timber.w("FileSystemSink.abort: failed to delete ${file.absolutePath}")
                }
            }
        }
    }
}


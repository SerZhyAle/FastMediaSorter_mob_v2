package com.sza.fastmediasorter.wear.data.files

import android.content.ContentResolver
import android.content.Context
import com.sza.fastmediasorter.wear.domain.model.WEAR_FILE_TRANSFER_MAX_BYTES
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

private const val STAGED_PREFIX = "wear-staged-"

/**
 * Gives an operation a real [File] to hand to the transfer channel.
 *
 * A browsed MediaStore row carries a content URI and no path, while the sender takes a
 * `java.io.File`, so those bytes have to land somewhere first. An app-owned file is returned as it
 * stands, so the case this ticket exists for - a voice note leaving the watch - copies nothing.
 */
class WearMediaFileStager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * The file behind [file] when it is app-owned, or null when only a content URI names it.
     *
     * Public because the operation engine needs the same answer to delete or rename in place, and
     * two implementations of "which file is this really" would be free to disagree.
     */
    fun localFileOf(file: WearMediaFile): File? {
        val scheme = file.uri.scheme
        val path = file.uri.path
        return if ((scheme == null || scheme == ContentResolver.SCHEME_FILE) && path != null) {
            File(path).takeIf { it.exists() }
        } else {
            null
        }
    }

    suspend fun stage(file: WearMediaFile): File? = withContext(Dispatchers.IO) {
        val own = localFileOf(file)
        when {
            own != null -> own
            // Refused before the first byte: the channel would reject it anyway, and copying 32 MB
            // into the watch's cache to learn that is the expensive way to find out.
            file.size > WEAR_FILE_TRANSFER_MAX_BYTES -> {
                Timber.w("Not staging %s: %d bytes is over the transfer ceiling", file.name, file.size)
                null
            }
            else -> copyIntoCache(file)
        }
    }

    /** Removes a staged copy. A file that was never copied is the user's own and is left alone. */
    fun discard(staged: File, original: WearMediaFile) {
        val own = localFileOf(original)
        if (own != null && own.absolutePath == staged.absolutePath) return
        if (!staged.delete()) {
            Timber.w("Could not remove staged copy %s", staged.absolutePath)
        }
    }

    private fun copyIntoCache(file: WearMediaFile): File? {
        val target = File(context.cacheDir, "$STAGED_PREFIX${file.name}")
        return try {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                target
            }
        } catch (e: IOException) {
            // A half-written copy is worse than none: the sender would ship a truncated file under
            // the original's name and the phone would accept it.
            target.delete()
            Timber.w(e, "Could not stage %s", file.name)
            null
        }
    }
}

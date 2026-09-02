package com.sza.fastmediasorter.wear.data.files

import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
import com.sza.fastmediasorter.wear.domain.files.WearMediaStoreConsent
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Deletes and renames a MediaStore row, which has no file path to act on.
 *
 * A row browsed out of the watch's own MediaStore is addressed by a content URI, so the file-based
 * path every other origin uses does not apply: there is nothing to call `delete()` on. The resolver
 * is the only way in, and it refuses on a row this app did not write until the owner confirms.
 *
 * The operation is attempted before the confirmation is asked for, not after. A row the app wrote
 * itself - a shot from its own camera screen - belongs to it and goes through silently; asking first
 * would put a system dialog in front of the owner deleting their own photo.
 */
class WearMediaStoreFileWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val consent: WearMediaStoreConsent
) {

    /** What the resolver did, and what the caller has to do next when it did nothing. */
    sealed interface Result {
        data object Succeeded : Result

        /** Refused pending the owner's answer; [request] is the confirmation to show. */
        data class NeedsConsent(val request: IntentSender) : Result

        data object Failed : Result
    }

    fun delete(uri: Uri): Result = attempt(uri, consent::deleteRequest) {
        context.contentResolver.delete(uri, null, null) > 0
    }

    fun rename(uri: Uri, newName: String): Result = attempt(uri, consent::writeRequest) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
        }
        context.contentResolver.update(uri, values, null, null) > 0
    }

    /**
     * A refusal is not a failure: it is the one outcome that becomes a success once the owner
     * answers, so it is reported apart from the row that could not be written for any other reason.
     *
     * The three arms after it are not defensive padding. The resolver rejects an unknown URI and a
     * display name whose extension contradicts the row's MIME type with [IllegalArgumentException],
     * a colliding name with [IllegalStateException], and a volume that does not accept the column
     * with [UnsupportedOperationException] - and the new name is free text typed on a watch, so the
     * extension case is ordinary rather than exotic. Two of the three callers collect this flow
     * without a `catch`, where an escaping exception takes the app down instead of the operation.
     */
    private fun attempt(
        uri: Uri,
        requestConsent: (Collection<Uri>) -> IntentSender?,
        action: () -> Boolean
    ): Result = try {
        if (action()) Result.Succeeded else Result.Failed
    } catch (e: SecurityException) {
        Timber.d(e, "MediaStore row %s needs the owner's confirmation", uri)
        requestConsent(listOf(uri))?.let(Result::NeedsConsent) ?: Result.Failed
    } catch (e: IllegalArgumentException) {
        refused(e, uri)
    } catch (e: IllegalStateException) {
        refused(e, uri)
    } catch (e: UnsupportedOperationException) {
        refused(e, uri)
    }

    private fun refused(cause: Exception, uri: Uri): Result {
        Timber.w(cause, "MediaStore refused the write to %s", uri)
        return Result.Failed
    }
}

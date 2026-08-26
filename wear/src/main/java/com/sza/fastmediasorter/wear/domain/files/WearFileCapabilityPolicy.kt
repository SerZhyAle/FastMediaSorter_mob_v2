package com.sza.fastmediasorter.wear.domain.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.domain.model.WearFileStorageClass
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Where a paired-phone file lands on the watch, named once so the classifier and the screen that writes
 * the copy cannot drift apart on which directory means "the phone still has this".
 */
const val WEAR_PHONE_FILE_CACHE_DIR = "phone-files"

/** The four operations any file the watch itself can reach allows. */
private val LOCAL_OPERATIONS = setOf(
    WearFileOperationKind.SEND_TO_PHONE,
    WearFileOperationKind.MOVE_TO_PHONE,
    WearFileOperationKind.DELETE,
    WearFileOperationKind.RENAME
)

/**
 * The single place that answers "what may this file be asked to do".
 *
 * Keeping the answer here rather than in the action menu is what keeps the menu honest: the browse
 * list mixes app-owned files, MediaStore rows and read-only network entries, and offering an
 * operation the source cannot perform is the failure the strategic spec rates as a trust risk.
 *
 * The same lattice settles which browse categories each origin offers, so no screen re-derives that
 * on its own: a content category exists for an origin exactly when the watch can do something with
 * that origin's files. Three consequences follow. The paired phone earns a Documents category,
 * because a [WearFileStorageClass.PHONE_COPY] file carries the token that opens it on the phone.
 * The watch's own MediaStore reaches audio, video and images only, so a document there has no
 * address to reach it by. A network share allows nothing at all (S1863), so a category there would
 * list rows with no operation behind any of them.
 */
class WearFileCapabilityPolicy @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * A network listing is decided by the caller because the entry itself cannot be told apart - its
     * id is a list position and its URI a share-relative path.
     *
     * [WearFileStorageClass.APP_OWNED] covers both internal and external app storage: the internal
     * cache and files directories as well as the external sandbox.
     */
    fun classify(file: WearMediaFile, isNetworkSource: Boolean): WearFileStorageClass = when {
        isNetworkSource -> WearFileStorageClass.NETWORK
        else -> localPathOf(file.uri)?.let(::classifyLocalPath) ?: WearFileStorageClass.MEDIA_STORE
    }

    /**
     * The paired-phone copy is recognised by the one directory it is ever written to, so "the phone
     * still has this file" is decided here rather than re-derived by each screen that shows one.
     */
    private fun classifyLocalPath(path: String): WearFileStorageClass = when {
        phoneCopyRoot()?.let { path.startsWith(it) } == true -> WearFileStorageClass.PHONE_COPY
        appOwnedRoots().any { path.startsWith(it) } -> WearFileStorageClass.APP_OWNED
        else -> WearFileStorageClass.MEDIA_STORE
    }

    private fun phoneCopyRoot(): String? = canonicalPathOf(File(context.cacheDir, WEAR_PHONE_FILE_CACHE_DIR))

    /**
     * Both temporary copy directories live in the internal cache, so an external-only comparison
     * called a file the app itself wrote foreign and left the user unable to remove it.
     */
    private fun appOwnedRoots(): List<String> = listOfNotNull(
        context.cacheDir,
        context.filesDir,
        context.getExternalFilesDir(null)?.parentFile
    ).mapNotNull { canonicalPathOf(it) }

    /**
     * One expression on purpose: adding the MediaStore consent flow later moves one line rather than
     * reshaping every caller.
     */
    fun allowedOperations(storageClass: WearFileStorageClass): Set<WearFileOperationKind> =
        when (storageClass) {
            WearFileStorageClass.APP_OWNED -> LOCAL_OPERATIONS
            // Everything a watch file allows, plus the one thing only this class can be asked: the
            // phone still holds the original, so it alone can be opened there.
            WearFileStorageClass.PHONE_COPY -> LOCAL_OPERATIONS + WearFileOperationKind.OPEN_ON_PHONE
            WearFileStorageClass.MEDIA_STORE -> setOf(WearFileOperationKind.SEND_TO_PHONE)
            WearFileStorageClass.NETWORK -> emptySet()
        }

    /**
     * An unreadable URI falls back to the most restricted non-network class instead of throwing: a
     * browse list that cannot be classified must still be listable.
     */
    private fun localPathOf(uri: Uri): String? {
        val scheme = uri.scheme
        val isLocal = scheme == null || scheme == ContentResolver.SCHEME_FILE
        val rawPath = uri.path?.takeIf { isLocal } ?: return null
        return canonicalPathOf(File(rawPath))
    }

    private fun canonicalPathOf(file: File): String? = try {
        file.canonicalPath
    } catch (e: IOException) {
        Timber.w(e, "Could not canonicalise %s; treating it as MediaStore", file.path)
        null
    }
}

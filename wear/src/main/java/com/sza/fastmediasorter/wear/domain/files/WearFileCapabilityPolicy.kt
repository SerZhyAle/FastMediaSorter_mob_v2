package com.sza.fastmediasorter.wear.domain.files

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.wear.data.repository.WearSendToReceiversRepository
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
    @ApplicationContext private val context: Context,
    private val mediaStoreConsent: WearMediaStoreConsent,
    private val sendToReceivers: WearSendToReceiversRepository
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
     * One expression on purpose: the MediaStore consent flow moved exactly the one line it was
     * promised to move, and the next class needing a device-dependent answer changes one more.
     *
     * [WearFileStorageClass.MEDIA_STORE] is the only entry that depends on the device rather than on
     * the class alone. The row belongs to whoever wrote it, so writing to it needs the owner's
     * confirmation, and that confirmation exists only from API 30 - below it the operations are
     * withheld rather than offered and refused, which is the rule S2004 ADR-4 already applies to
     * every other unavailable action.
     */
    fun allowedOperations(storageClass: WearFileStorageClass): Set<WearFileOperationKind> =
        when (storageClass) {
            WearFileStorageClass.APP_OWNED -> LOCAL_OPERATIONS + sendToReceiver()
            // Everything a watch file allows, plus the one thing only this class can be asked: the
            // phone still holds the original, so it alone can be opened there.
            WearFileStorageClass.PHONE_COPY ->
                LOCAL_OPERATIONS + WearFileOperationKind.OPEN_ON_PHONE + sendToReceiver()
            WearFileStorageClass.MEDIA_STORE -> if (mediaStoreConsent.isAvailable()) {
                LOCAL_OPERATIONS + sendToReceiver()
            } else {
                // Sending is a read, so it survives the consent this branch lacks: the confirmation
                // withheld here guards writing to someone else's row, and handing the bytes to a
                // receiver changes nothing on the watch.
                setOf(WearFileOperationKind.SEND_TO_PHONE) + sendToReceiver()
            }
            WearFileStorageClass.NETWORK -> emptySet()
        }

    /**
     * The «Send to..» entry, present only while this watch actually holds receivers to offer.
     *
     * An empty list is the same offer-that-ends-in-a-refusal ADR-3 forbids: the entry would open a
     * dialog with nothing in it. The list is the phone's answer cached here, so it is empty both
     * before the first push and after the owner switched the last receiver off there.
     */
    private fun sendToReceiver(): Set<WearFileOperationKind> =
        if (sendToReceivers.observe().value.isEmpty()) {
            emptySet()
        } else {
            setOf(WearFileOperationKind.SEND_TO_RECEIVER)
        }

    /**
     * An unreadable URI falls back to [WearFileStorageClass.MEDIA_STORE] instead of throwing: a
     * browse list that cannot be classified must still be listable.
     *
     * That fallback was the most restricted non-network class until S2142; it no longer is, because
     * MediaStore now carries the write operations on API 30+. What keeps it safe is the far end
     * rather than this one - a URI that reached here without a usable file path is not a row the
     * resolver can write either, so the write is refused there and reported as a failure.
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

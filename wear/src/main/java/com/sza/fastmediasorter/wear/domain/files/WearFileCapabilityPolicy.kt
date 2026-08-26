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
 * The single place that answers "what may this file be asked to do".
 *
 * Keeping the answer here rather than in the action menu is what keeps the menu honest: the browse
 * list mixes app-owned files, MediaStore rows and read-only network entries, and offering an
 * operation the source cannot perform is the failure the strategic spec rates as a trust risk.
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
    fun classify(file: WearMediaFile, isNetworkSource: Boolean): WearFileStorageClass {
        if (isNetworkSource) return WearFileStorageClass.NETWORK
        val path = localPathOf(file.uri) ?: return WearFileStorageClass.MEDIA_STORE
        val owned = appOwnedRoots().any { path.startsWith(it) }
        return if (owned) WearFileStorageClass.APP_OWNED else WearFileStorageClass.MEDIA_STORE
    }

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
            WearFileStorageClass.APP_OWNED -> WearFileOperationKind.entries.toSet()
            WearFileStorageClass.MEDIA_STORE -> setOf(WearFileOperationKind.SEND_TO_PHONE)
            WearFileStorageClass.NETWORK -> emptySet()
        }

    /**
     * An unreadable URI falls back to the most restricted non-network class instead of throwing: a
     * browse list that cannot be classified must still be listable.
     */
    private fun localPathOf(uri: Uri): String? {
        val scheme = uri.scheme
        if (scheme != null && scheme != ContentResolver.SCHEME_FILE) return null
        val rawPath = uri.path ?: return null
        return canonicalPathOf(File(rawPath))
    }

    private fun canonicalPathOf(file: File): String? = try {
        file.canonicalPath
    } catch (e: IOException) {
        Timber.w(e, "Could not canonicalise %s; treating it as MediaStore", file.path)
        null
    }
}

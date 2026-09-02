package com.sza.fastmediasorter.wear.data.files

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.sza.fastmediasorter.wear.domain.files.WearMediaStoreConsent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The system confirmation, where the system has one.
 *
 * `createDeleteRequest` and `createWriteRequest` both arrived in API 30. Below that the platform
 * offers no way to ask for permission over a foreign MediaStore row at all - not a dialog that gets
 * declined, but no dialog - so every entry point here answers "cannot ask" rather than throwing.
 *
 * The version check is written out at each call rather than hidden behind [isAvailable], because
 * only the literal comparison is what marks the API-30 call as guarded.
 */
class WearMediaStoreConsentImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WearMediaStoreConsent {

    override fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * An empty set is refused rather than passed on: the platform builds a confirmation naming no
     * file, which the owner would be asked to answer with nothing to answer about.
     */
    override fun deleteRequest(uris: Collection<Uri>): IntentSender? {
        if (uris.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return MediaStore.createDeleteRequest(context.contentResolver, uris.toList()).intentSender
    }

    override fun writeRequest(uris: Collection<Uri>): IntentSender? {
        if (uris.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return MediaStore.createWriteRequest(context.contentResolver, uris.toList()).intentSender
    }
}

package com.sza.fastmediasorter.wear.domain.files

import android.content.IntentSender
import android.net.Uri

/**
 * Asks the system for the owner's permission to write to a MediaStore row this app did not create.
 *
 * A row the watch's own MediaStore holds - a camera shot, a downloaded picture - belongs to whoever
 * wrote it, and the app may read it but not change it. The system grants that permission only
 * through a confirmation the owner answers, so the operation cannot be attempted and then reported:
 * it has to be asked first, and the answer decides whether it runs at all.
 *
 * The seam exists because the confirmation itself does not exist everywhere. It arrived in API 30,
 * and this module's floor is 28, so on the 28-29 band there is no dialog to show and therefore no
 * way to obtain the permission. [isAvailable] is what the capability policy reads to keep those
 * operations out of the menu entirely, rather than offering work that ends in a refusal.
 */
interface WearMediaStoreConsent {

    /** Whether this device can show the owner a write confirmation at all. */
    fun isAvailable(): Boolean

    /** The confirmation to delete [uris], or null when this device cannot ask for one. */
    fun deleteRequest(uris: Collection<Uri>): IntentSender?

    /** The confirmation to modify [uris], or null when this device cannot ask for one. */
    fun writeRequest(uris: Collection<Uri>): IntentSender?
}

package com.sza.fastmediasorter.ui.main.helpers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File

/**
 * S1424: hands an exported file to the system share sheet, for every host that can export one.
 *
 * Extracted from MainEventHandler, which built the same `FileProvider` uri and the same `ACTION_SEND`
 * chooser twice and where a second host could reach neither. Strategic 4 notes that an action
 * transfers to the desktop unchanged only when it is expressed as an intent - this is what makes the
 * two export rows meet that description.
 */
object ResourceShareIntents {

    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    /**
     * The companion-config payload's own media type - not the exported-resource one.
     *
     * Must stay in sync with the CompanionConfigImportActivity SEND intent-filter in the manifest.
     */
    const val COMPANION_CONFIG_MIME = "application/vnd.fms.companion-config+json"

    /**
     * Opens the chooser for [filePath]. Returns false when it cannot be shared at all, so the caller
     * shows the message that fits what it was exporting rather than a message chosen here.
     */
    fun share(
        activity: Activity,
        filePath: String,
        mimeType: String,
        @StringRes chooserTitleRes: Int,
    ): Boolean = try {
        val uri = FileProvider.getUriForFile(
            activity,
            activity.packageName + FILE_PROVIDER_SUFFIX,
            File(filePath),
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        activity.startActivity(Intent.createChooser(intent, activity.getString(chooserTitleRes)))
        true
    } catch (e: IllegalArgumentException) {
        // FileProvider throws exactly this for a path outside its configured roots, which is a
        // packaging mistake rather than a runtime condition - worth a log, never a crash.
        Timber.e(e, "Cannot share %s - outside the file provider's roots", filePath)
        false
    } catch (e: ActivityNotFoundException) {
        // A chooser with nothing behind it: possible on a stripped device image.
        Timber.e(e, "No handler for the share chooser")
        false
    }
}

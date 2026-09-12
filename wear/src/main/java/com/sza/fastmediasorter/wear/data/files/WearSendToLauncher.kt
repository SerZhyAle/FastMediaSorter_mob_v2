package com.sza.fastmediasorter.wear.data.files

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * S2142: fires the share intent for a receiver this watch serves itself.
 *
 * In the data layer rather than in the use case for [WearMediaStoreFileWriter]'s reason: starting an
 * activity is a platform call, and keeping it here leaves the operation runner testable on a plain
 * JVM, which is the property strategic 5.1 relies on for the whole policy-and-runner pair.
 *
 * The file is handed over as a content URI, never as a `file://` one: a receiver is another
 * application, and a raw path it cannot read would fail after the chooser rather than before it.
 */
class WearSendToLauncher @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Starts [sendIntent] for [file], and reports whether it actually left.
     *
     * `false` covers both a receiver that vanished between the reachability check and the tap and a
     * provider that refused the path - the caller turns either into a failure the owner can read,
     * because a send that silently did nothing is the one outcome strategic 7 rates as losing trust.
     */
    fun launch(file: File, sendIntent: Intent): Boolean {
        val shared = contentUriFor(file) ?: return false
        val intent = Intent(sendIntent)
            .putExtra(Intent.EXTRA_STREAM, shared)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "Send to: no activity took %s on this watch", file.name) }
            .isSuccess
    }

    private fun contentUriFor(file: File) = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.onFailure { Timber.w(it, "Send to: %s is outside the shared provider's paths", file.name) }
        .getOrNull()
}

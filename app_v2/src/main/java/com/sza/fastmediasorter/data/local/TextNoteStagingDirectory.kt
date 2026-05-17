package com.sza.fastmediasorter.data.local

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves and ensures the existence of the local staging directory used for network/cloud text notes.
 *
 * Preferred location: `Downloads/FastMediaSorter/notes/`.
 * Fallback (scoped storage): `getExternalFilesDir(null)/notes/`.
 * The fallback is used when public Downloads is unavailable (unusual, but defensive).
 */
@Singleton
class TextNoteStagingDirectory @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Returns the staging directory, creating it (and parents) if absent.
     */
    fun ensureDirectory(): File {
        val preferred = try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(downloads, "FastMediaSorter/notes")
        } catch (e: Exception) {
            Timber.w(e, "TextNoteStagingDirectory: cannot access public Downloads — using scoped fallback")
            null
        }

        val dir = if (preferred != null && (preferred.exists() || preferred.mkdirs())) {
            preferred
        } else {
            File(context.getExternalFilesDir(null), "notes").also { it.mkdirs() }
        }

        Timber.d("S0189: TextNoteStagingDirectory.ensureDirectory -> ${dir.absolutePath}")
        return dir
    }
}

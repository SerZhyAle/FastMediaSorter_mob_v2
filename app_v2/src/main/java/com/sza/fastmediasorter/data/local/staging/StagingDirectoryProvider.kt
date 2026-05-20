package com.sza.fastmediasorter.data.local.staging

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0189 (Phase 09): resolves and ensures the existence of the per-kind local staging
 * directory used for network/cloud staging of new files.
 *
 * Preferred location: `Downloads/FastMediaSorter/<sub>/`, where `<sub>` is the
 * per-kind subdirectory (`notes/` for [StagedKind.TEXT_NOTE], `drawings/` for
 * [StagedKind.DRAWING]).  Fallback (scoped storage): `getExternalFilesDir(null)/<sub>/`.
 *
 * The fallback is used when public Downloads is unavailable (unusual, but defensive).
 */
@Singleton
class StagingDirectoryProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Returns the staging directory for [kind], creating it (and parents) if absent.
     */
    fun directoryFor(kind: StagedKind): File {
        val sub = subdirFor(kind)
        val preferred = try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(downloads, "FastMediaSorter/$sub")
        } catch (e: Exception) {
            Timber.w(e, "StagingDirectoryProvider: cannot access public Downloads - using scoped fallback (kind=$kind)")
            null
        }

        return if (preferred != null && (preferred.exists() || preferred.mkdirs())) {
            preferred
        } else {
            File(context.getExternalFilesDir(null), sub).also { it.mkdirs() }
        }
    }

    private fun subdirFor(kind: StagedKind): String = when (kind) {
        StagedKind.TEXT_NOTE -> "notes"
        StagedKind.DRAWING -> "drawings"
    }
}

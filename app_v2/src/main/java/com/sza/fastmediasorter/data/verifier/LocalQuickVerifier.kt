package com.sza.fastmediasorter.data.verifier

import android.content.Context
import com.sza.fastmediasorter.domain.verifier.QuickVerifier
import com.sza.fastmediasorter.utils.SafHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0242 Phase 04 / S2373 - local filesystem and SAF tree probe.
 *
 * Local filesystem files are checked via `File.exists()` (stat() syscall).
 * SAF content:// URIs are checked via [SafHelper.getDocumentFileFromUri] and `.exists()`.
 */
@Singleton
class LocalQuickVerifier @Inject constructor(
    @ApplicationContext private val context: Context
) : QuickVerifier {

    override suspend fun missingFiles(resourceId: Long, paths: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            try {
                Timber.d("S2373: LocalQuickVerifier probing %d paths for resource=%d", paths.size, resourceId)
                paths.filter { path -> !fileExists(path) }
            } catch (e: Exception) {
                Timber.w(e, "QuickVerifier(LOCAL): probe error for resource=%d, returning no-op", resourceId)
                emptyList()
            }
        }

    private fun fileExists(path: String): Boolean {
        return if (SafHelper.isContentUri(path)) {
            try {
                val uri = SafHelper.parseUri(path)
                SafHelper.getDocumentFileFromUri(context, uri)?.exists() ?: false
            } catch (e: Exception) {
                Timber.w(e, "LocalQuickVerifier: Failed to check SAF URI existence: %s", path)
                // Fail-safe: assume file exists to prevent false-positive deletion from journal
                true
            }
        } else {
            File(path).exists()
        }
    }
}


package com.sza.fastmediasorter.util

import android.content.Context
import android.content.Intent

/**
 * S0189: checks whether Google Keep (or Keep Notes) can receive ACTION_SEND text/plain intents.
 *
 * Google has shipped Keep under two package ids historically - both are probed.
 * The resolved value is cached for the lifetime of this instance.
 *
 * Can be used standalone (plain constructor) or injected via Hilt if needed in the future.
 */
class GoogleKeepAvailabilityChecker(private val context: Context) {

    companion object {
        private val KEEP_CANDIDATES = listOf(
            "com.google.android.keep",
            "com.google.android.keep.notes"
        )
        // Sentinel to distinguish "not yet resolved" from "resolved as null"
        private val UNRESOLVED: String? = " unresolved"
    }

    private var resolvedPackageCache: String? = UNRESOLVED

    fun resolveTargetPackage(): String? {
        if (resolvedPackageCache !== UNRESOLVED) return resolvedPackageCache
        val resolved = KEEP_CANDIDATES.firstOrNull { candidate ->
            val probe = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(candidate)
            }
            context.packageManager.queryIntentActivitiesCompat(probe).isNotEmpty()
        }
        resolvedPackageCache = resolved
        return resolved
    }
}

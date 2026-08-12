package com.sza.fastmediasorter.ui.launcher.search

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.sza.fastmediasorter.util.resolveActivityCompat
import timber.log.Timber
import java.net.URLEncoder
import javax.inject.Inject

/**
 * S1566: the one answer to "where does a launcher query go" - always the browser search page.
 *
 * An installed search application is deliberately never consulted (ADR-3), so a `noLegal` device without
 * Google services behaves exactly like a `standard` one instead of depending on what happens to be
 * installed. The boolean result is the whole contract: a caller that gets `false` tells the user the search
 * could not be opened rather than leaving a cell that silently does nothing.
 *
 * Shared rather than private to one gadget because a second caller is expected - `WeatherGadget` builds its
 * own search intent today, and moving it here is its own ticket.
 */
class WebSearchLaunchManager @Inject constructor() {

    /** False for every reason a search cannot start: nothing typed, nothing resolves, or the start throws. */
    fun launch(context: Context, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        val intent = Intent(Intent.ACTION_VIEW, buildSearchUrl(trimmed).toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return open(context, intent)
    }

    private fun open(context: Context, intent: Intent): Boolean {
        if (context.packageManager.resolveActivityCompat(intent) == null) {
            Timber.i("Launcher search: no activity can open the search page")
            return false
        }
        return runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "Launcher search: the browser refused to open the search page") }
            .isSuccess
    }

    companion object {

        /**
         * URLEncoder rather than `Uri.encode`: this is the only pure logic here and it is unit-tested, but
         * the module sets `isReturnDefaultValues`, under which the Android encoder returns null in a JVM
         * test and the assertion would hold for the wrong reason.
         */
        fun buildSearchUrl(query: String): String =
            SEARCH_URL_PREFIX + URLEncoder.encode(query.trim(), Charsets.UTF_8.name())

        private const val SEARCH_URL_PREFIX = "https://www.google.com/search?q="
    }
}

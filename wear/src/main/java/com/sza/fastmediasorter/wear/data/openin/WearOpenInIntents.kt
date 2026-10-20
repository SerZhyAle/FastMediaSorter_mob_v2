package com.sza.fastmediasorter.wear.data.openin

import android.app.SearchManager
import android.content.Intent
import android.net.Uri

/**
 * S4381: the intents "Open in.." builds, one per [com.sza.fastmediasorter.wear.domain.model.WearOpenInAction].
 *
 * Kept apart from the repository so each intent has one definition shared by the enumeration (which asks who
 * handles it) and the launch (which starts it): a rule that listed handlers of an intent nobody starts would drift.
 * The browsable category is added to web addresses only, because the manifest `<queries>` element declares it there.
 */
internal object WearOpenInIntents {

    private const val PLACE_QUERY_PREFIX = "geo:0,0?q="
    private const val TEXT_MIME_TYPE = "text/plain"
    private const val GEO_SCHEME_PREFIX = "geo:"
    private const val WEB_PROBE_ADDRESS = "https://example.com"

    fun viewLink(text: String): Intent {
        val trimmed = text.trim()
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed))
        if (trimmed.startsWith("http", ignoreCase = true)) {
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
        }
        return intent
    }

    fun place(text: String): Intent {
        val trimmed = text.trim()
        val uri = if (trimmed.startsWith(GEO_SCHEME_PREFIX, ignoreCase = true)) {
            Uri.parse(trimmed)
        } else {
            Uri.parse(PLACE_QUERY_PREFIX + Uri.encode(trimmed))
        }
        return Intent(Intent.ACTION_VIEW, uri)
    }

    fun sendText(text: String): Intent =
        Intent(Intent.ACTION_SEND).setType(TEXT_MIME_TYPE).putExtra(Intent.EXTRA_TEXT, text)

    fun webSearch(text: String): Intent =
        Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text)

    fun viewFile(uri: Uri, mimeType: String): Intent =
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    /** What the system starts for a web address decides the default browser (research 02). */
    fun defaultBrowserProbe(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(WEB_PROBE_ADDRESS)).addCategory(Intent.CATEGORY_BROWSABLE)

    /** What the system starts for a place decides the default maps application (research 02). */
    fun defaultMapsProbe(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(PLACE_QUERY_PREFIX))
}

package com.sza.fastmediasorter.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Main-safe read of a bundled help page with its body direction set for the reading locale (S4288).
 *
 * The bundled pages are authored LTR (en/ru/uk only), and a WebView never calls
 * shouldInterceptRequest for file:///android_asset/ URLs, so an RTL app locale needs the document
 * read and its html dir rewritten before the viewer hands it to loadDataWithBaseURL. Same layering
 * as [SafDocumentProbe] (S3790): the UI layer asks a question, this class owns the file read.
 */
object HelpPageBodyLoader {

    /**
     * Page text for [assetUrl], with the html dir forced to rtl when [mirrorBody] is set.
     * Null when the asset cannot be read - the caller falls back to the plain loadUrl path.
     */
    suspend fun load(context: Context, assetUrl: String, mirrorBody: Boolean): String? =
        withContext(Dispatchers.IO) {
            val html = try {
                context.assets.open(assetUrl.removePrefix(ASSET_PATH_PREFIX)).bufferedReader().use { it.readText() }
            } catch (_: IOException) {
                return@withContext null
            }
            if (mirrorBody) mirrored(html) else html
        }

    private fun mirrored(html: String): String {
        val tag = HTML_OPEN_TAG.find(html) ?: return html
        val attrs = tag.groupValues[1]
        val patched = if (DIR_ATTRIBUTE.containsMatchIn(attrs)) {
            DIR_ATTRIBUTE.replace(attrs, RTL_DIR_ATTRIBUTE)
        } else {
            "$attrs $RTL_DIR_ATTRIBUTE"
        }
        return html.replaceFirst(tag.value, "<html$patched>")
    }

    private const val ASSET_PATH_PREFIX = "file:///android_asset/"
    private const val RTL_DIR_ATTRIBUTE = "dir=\"rtl\""
    private val HTML_OPEN_TAG = Regex("<html\\b([^>]*)>")
    private val DIR_ATTRIBUTE = Regex("\\bdir\\s*=\\s*([\"'])[^\"']*\\1")
}

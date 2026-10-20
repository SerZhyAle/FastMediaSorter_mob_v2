package com.sza.fastmediasorter.ui.common.help

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.google.gson.JsonParseException
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.ui.common.support.DocsPageOpenManager
import timber.log.Timber
import java.io.IOException

/**
 * The one entry every F1 key and every help button calls (contract `CONTEXT-HELP` rule 3): one surface
 * id resolves to one form of help.
 *
 * A surface without a bundled page yet keeps the answer it had before the help package existed - the
 * shortcut dialog, or the portal page - so no entry point ever opens nothing.
 */
object HelpOpenManager {

    private val lock = Any()

    @Volatile
    private var resolver: HelpTargetResolver? = null

    fun open(activity: FragmentActivity, surfaceId: String, uiSurface: UiSurface?, elementAnchor: String? = null) {
        Timber.d("S4328: help open surface=%s", surfaceId)
        val language = LocaleHelper.getLanguage(activity)
        val target = resolverOrNull(activity)?.resolve(surfaceId, language, elementAnchor)
        if (target != null) {
            activity.startActivity(HelpPageActivity.intent(activity, target.assetUrl, target.siteUrl, uiSurface?.name))
        } else if (uiSurface != null) {
            InputHelpDialogFragment.show(activity.supportFragmentManager, uiSurface)
        } else {
            DocsPageOpenManager.open(activity, UiSurface.MAIN)
        }
    }

    /** True when the help map gives [surfaceId] a page that ships in the package. */
    fun hasHelp(context: Context, surfaceId: String): Boolean =
        resolverOrNull(context)?.hasPage(surfaceId) == true

    /** Opens the page of [surfaceId] and returns true; returns false and opens nothing when the surface has none. */
    fun openIfPresent(activity: FragmentActivity, surfaceId: String, uiSurface: UiSurface?): Boolean {
        if (!hasHelp(activity, surfaceId)) return false
        open(activity, surfaceId, uiSurface)
        return true
    }

    // A failed read is not cached: the next request retries instead of pinning the fallback for the process.
    private fun resolverOrNull(context: Context): HelpTargetResolver? =
        resolver ?: synchronized(lock) {
            resolver ?: loadResolver(context)?.also { resolver = it }
        }

    private fun loadResolver(context: Context): HelpTargetResolver? =
        try {
            HelpTargetResolver.fromAssets(context.applicationContext.assets)
        } catch (e: IOException) {
            Timber.w(e, "Help map: bundled map unreadable, falling back to the shortcut dialog")
            null
        } catch (e: JsonParseException) {
            Timber.w(e, "Help map: bundled map malformed, falling back to the shortcut dialog")
            null
        } catch (e: IllegalStateException) {
            Timber.w(e, "Help map: bundled map has an unexpected shape, falling back to the shortcut dialog")
            null
        }
}

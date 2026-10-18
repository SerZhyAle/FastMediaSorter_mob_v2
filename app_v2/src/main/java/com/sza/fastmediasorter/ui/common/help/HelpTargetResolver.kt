package com.sza.fastmediasorter.ui.common.help

import android.content.res.AssetManager
import com.sza.fastmediasorter.ui.common.input.InputHelpLinkResolver

/** Where a help request lands: the bundled page and its published twin on the portal. */
data class HelpTarget(
    val helpId: String,
    val anchor: String?,
    val assetUrl: String,
    val siteUrl: String,
)

/**
 * The one resolver every F1 handler and every `?` action calls (contract `CONTEXT-HELP` rule 17):
 * surface id, optional element anchor and app language in, the help page out.
 *
 * @param availablePages asset paths relative to the help root that exist in the package, such as
 *   `ru/main.html`; a page missing in the app language falls back to [HelpMap.fallbackLocale], never to
 *   a missing page (rule 13).
 */
class HelpTargetResolver(
    private val map: HelpMap,
    private val availablePages: Set<String>,
) {

    /** The help of [surfaceId] in [language], or null when the surface has no page of its own or inherited. */
    fun resolve(surfaceId: String, language: String, elementAnchor: String? = null): HelpTarget? {
        val lang = language.lowercase()
        val ref = helpRefFor(surfaceId)
        val page = ref?.let { bundledPage(it.first, lang) }
        return if (ref == null || page == null) null else buildTarget(ref, page, lang, elementAnchor)
    }

    /** True when [surfaceId] reaches a page that is in the package, so an F1 handler knows whether to take the key. */
    fun hasPage(surfaceId: String): Boolean = resolve(surfaceId, map.fallbackLocale) != null

    private fun buildTarget(
        ref: Pair<String, String?>,
        page: String,
        lang: String,
        elementAnchor: String?,
    ): HelpTarget {
        val anchor = elementAnchor ?: ref.second
        val fragment = anchor?.let { "#$it" }.orEmpty()
        return HelpTarget(
            helpId = ref.first,
            anchor = anchor,
            assetUrl = ASSET_ROOT + page + fragment,
            siteUrl = siteUrl(ref.first, lang) + fragment,
        )
    }

    private fun bundledPage(helpId: String, lang: String): String? =
        pagePath(lang, helpId).takeIf { it in availablePages }
            ?: pagePath(map.fallbackLocale, helpId).takeIf { it in availablePages }

    private fun helpRefFor(surfaceId: String): Pair<String, String?>? = subPageRef(surfaceId) ?: rowRef(surfaceId)

    private fun subPageRef(surfaceId: String): Pair<String, String?>? =
        map.surfaces.firstNotNullOfOrNull { row -> row.pages.firstOrNull { it.id == surfaceId } }
            ?.let { sub -> sub.help?.let { it to sub.anchor } }

    private fun rowRef(surfaceId: String): Pair<String, String?>? {
        val row = followInherit(map.surface(surfaceId))
        val usable = row != null && row.form != HelpForm.NONE && row.form != HelpForm.INHERIT
        return if (usable) row?.help?.let { it to row.anchor } else null
    }

    /** Walks `inherit` rows up to the first row that is not one; a cycle ends at null. */
    private fun followInherit(start: HelpSurface?): HelpSurface? {
        val visited = mutableSetOf<String>()
        var row = start
        while (row != null && row.form == HelpForm.INHERIT) {
            row = if (visited.add(row.id)) row.parent?.let(map::surface) else null
        }
        return row
    }

    private fun pagePath(locale: String, helpId: String): String =
        map.pageTemplate.replace("{locale}", locale).replace("{help}", helpId)

    private fun siteUrl(helpId: String, lang: String): String {
        val suffix = if (lang in InputHelpLinkResolver.TRANSLATED_LANGUAGES) "-$lang" else ""
        return "${InputHelpLinkResolver.SITE_BASE}$SITE_HELP_DIR$helpId$suffix.html"
    }

    companion object {
        const val HELP_ROOT = "help"
        const val MAP_ASSET = "$HELP_ROOT/help-map.json"
        const val ASSET_ROOT = "file:///android_asset/$HELP_ROOT/"
        private const val SITE_HELP_DIR = "documentation/help/"

        /** Reads the shipped map and lists the bundled pages of every locale folder. */
        fun fromAssets(assets: AssetManager): HelpTargetResolver {
            val map = assets.open(MAP_ASSET).bufferedReader().use { HelpMap.parse(it.readText()) }
            val pages = assets.list(HELP_ROOT).orEmpty().flatMap { locale ->
                assets.list("$HELP_ROOT/$locale").orEmpty()
                    .filter { it.endsWith(".html") }
                    .map { "$locale/$it" }
            }.toSet()
            return HelpTargetResolver(map, pages)
        }
    }
}

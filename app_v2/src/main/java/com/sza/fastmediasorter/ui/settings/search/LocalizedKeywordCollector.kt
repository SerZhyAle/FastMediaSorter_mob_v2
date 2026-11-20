package com.sza.fastmediasorter.ui.settings.search

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.ui.settings.SettingsSearchIndex
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the raw entry's title/subtitle/hint references across all `SupportedSearchLocales`
 * (plus the display language when it is outside that set) and packs the results into a
 * `SettingsSearchIndex` consumable by the search UI.
 *
 * The displayed title is the string of the app's display language, so a result reads like the
 * settings row it leads to. The keyword pool is built from the EN/RU/UK strings of the title and
 * subtitle (de-duplicated, lowercased, trimmed), so a query in any authored language still matches.
 * Help-popup text is excluded by design (strategic spec §6.3).
 */
@Singleton
class LocalizedKeywordCollector @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsSearchKeywordCollector {

    private val resourcesByTag = ConcurrentHashMap<String, Resources>()

    override fun displayLanguage(): String = LocaleHelper.getLanguage(context)

    override fun enrich(raw: RawSettingsSearchEntry, displayLanguage: String): SettingsSearchIndex? {
        val tags = searchTags(displayLanguage)
        val titleStrings = resolveTitleStrings(raw, tags)?.takeIf { titles -> titles.values.any { it.isNotBlank() } }
        val displayTitle = titleStrings?.let { pickDisplayTitle(it, displayLanguage) }
        val keywords = titleStrings?.let { buildKeywordPool(it, resolveSubtitleStrings(raw, tags)) }.orEmpty()
        val assignment = SettingsSearchTabMapping.assignmentFor(raw.layoutResId)

        return if (displayTitle == null || keywords.isEmpty() || assignment == null) {
            null
        } else {
            SettingsSearchIndex(
                key = safeKeyFor(raw.viewId),
                title = displayTitle,
                keywords = keywords,
                sectionId = assignment.sectionId,
                destination = assignment.destination,
                viewId = raw.viewId,
                ancestorIds = raw.ancestorIds
            )
        }
    }

    // The display language joins the authored set so a locale outside en/ru/uk still gets a title in
    // its own language instead of falling back to English.
    private fun searchTags(displayLanguage: String): List<String> =
        (SupportedSearchLocales.tags + displayLanguage).distinct()

    private fun resolveTitleStrings(raw: RawSettingsSearchEntry, tags: List<String>): Map<String, String>? = when {
        raw.titleResId != null -> resolveAcrossLocales(raw.titleResId, tags)
        !raw.inlineTitle.isNullOrBlank() -> staticAcrossLocales(raw.inlineTitle, tags)
        raw.hintResId != null -> resolveAcrossLocales(raw.hintResId, tags)
        !raw.inlineHint.isNullOrBlank() -> staticAcrossLocales(raw.inlineHint, tags)
        else -> null
    }

    private fun resolveSubtitleStrings(raw: RawSettingsSearchEntry, tags: List<String>): Map<String, String> = when {
        raw.subtitleResId != null -> resolveAcrossLocales(raw.subtitleResId, tags)
        !raw.inlineSubtitle.isNullOrBlank() -> staticAcrossLocales(raw.inlineSubtitle, tags)
        else -> emptyMap()
    }

    private fun resolveAcrossLocales(resId: Int, tags: List<String>): Map<String, String> {
        val map = LinkedHashMap<String, String>(tags.size)
        for (tag in tags) {
            map[tag] = try {
                resourcesFor(tag).getString(resId)
            } catch (_: Resources.NotFoundException) {
                ""
            }
        }
        return map
    }

    private fun staticAcrossLocales(value: String, tags: List<String>): Map<String, String> =
        tags.associateWith { value }

    private fun resourcesFor(tag: String): Resources = resourcesByTag.getOrPut(tag) {
        val base = Configuration(context.resources.configuration)
        base.setLocale(Locale.forLanguageTag(tag))
        context.createConfigurationContext(base).resources
    }

    // English is the fallback when a locale has no string for the entry, then any locale that has one.
    private fun pickDisplayTitle(localized: Map<String, String>, displayLanguage: String): String? =
        sequenceOf(displayLanguage, "en")
            .mapNotNull { tag -> localized[tag]?.takeIf { it.isNotBlank() } }
            .firstOrNull()
            ?: localized.values.firstOrNull { it.isNotBlank() }

    private fun buildKeywordPool(
        titles: Map<String, String>,
        subtitles: Map<String, String>
    ): List<String> {
        val pool = LinkedHashSet<String>()
        for (value in titles.values) {
            val cleaned = value.trim().lowercase()
            if (cleaned.isNotEmpty()) pool += cleaned
        }
        for (value in subtitles.values) {
            val cleaned = value.trim().lowercase()
            if (cleaned.isNotEmpty()) pool += cleaned
        }
        return pool.toList()
    }

    private fun safeKeyFor(viewId: Int): String = try {
        context.resources.getResourceEntryName(viewId)
    } catch (_: Resources.NotFoundException) {
        "viewId_$viewId"
    }
}

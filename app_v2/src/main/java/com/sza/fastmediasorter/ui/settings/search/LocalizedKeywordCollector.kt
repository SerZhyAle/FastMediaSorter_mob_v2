package com.sza.fastmediasorter.ui.settings.search

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import com.sza.fastmediasorter.ui.settings.SettingsSearchIndex
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the raw entry's title/subtitle/hint references across all `SupportedSearchLocales`
 * and packs the results into a `SettingsSearchIndex` consumable by the search UI.
 *
 * The keyword pool is built from the EN/RU/UK strings of the title and subtitle (de-duplicated,
 * lowercased, trimmed). Help-popup text is excluded by design (strategic spec §6.3).
 */
@Singleton
class LocalizedKeywordCollector @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsSearchKeywordCollector {

    private val localizedResources: Map<String, Resources> by lazy {
        SupportedSearchLocales.tags.associateWith { tag ->
            val base = Configuration(context.resources.configuration)
            base.setLocales(LocaleList(Locale.forLanguageTag(tag)))
            context.createConfigurationContext(base).resources
        }
    }

    override fun enrich(raw: RawSettingsSearchEntry): SettingsSearchIndex? {
        val titleStrings = resolveTitleStrings(raw) ?: return null
        if (titleStrings.values.all { it.isBlank() }) return null

        val displayTitle = pickDisplayTitle(titleStrings) ?: return null

        val subtitleStrings = resolveSubtitleStrings(raw)

        val keywords = buildKeywordPool(titleStrings, subtitleStrings)
        if (keywords.isEmpty()) return null

        val assignment = SettingsSearchTabMapping.assignmentFor(raw.layoutResId) ?: return null

        return SettingsSearchIndex(
            key = safeKeyFor(raw.viewId),
            title = displayTitle,
            keywords = keywords,
            sectionId = assignment.sectionId,
            destination = assignment.destination,
            viewId = raw.viewId
        )
    }

    private fun resolveTitleStrings(raw: RawSettingsSearchEntry): Map<String, String>? {
        if (raw.titleResId != null) return resolveAcrossLocales(raw.titleResId)
        if (!raw.inlineTitle.isNullOrBlank()) return staticAcrossLocales(raw.inlineTitle)
        if (raw.hintResId != null) return resolveAcrossLocales(raw.hintResId)
        if (!raw.inlineHint.isNullOrBlank()) return staticAcrossLocales(raw.inlineHint)
        return null
    }

    private fun resolveSubtitleStrings(raw: RawSettingsSearchEntry): Map<String, String> {
        if (raw.subtitleResId != null) return resolveAcrossLocales(raw.subtitleResId)
        if (!raw.inlineSubtitle.isNullOrBlank()) return staticAcrossLocales(raw.inlineSubtitle)
        return emptyMap()
    }

    private fun resolveAcrossLocales(resId: Int): Map<String, String> {
        val map = LinkedHashMap<String, String>(SupportedSearchLocales.tags.size)
        for (tag in SupportedSearchLocales.tags) {
            val resources = localizedResources[tag] ?: continue
            map[tag] = try {
                resources.getString(resId)
            } catch (_: Resources.NotFoundException) {
                ""
            }
        }
        return map
    }

    private fun staticAcrossLocales(value: String): Map<String, String> =
        SupportedSearchLocales.tags.associateWith { value }

    private fun pickDisplayTitle(localized: Map<String, String>): String? {
        val en = localized["en"]?.takeIf { it.isNotBlank() }
        if (en != null) return en
        return localized.values.firstOrNull { it.isNotBlank() }
    }

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

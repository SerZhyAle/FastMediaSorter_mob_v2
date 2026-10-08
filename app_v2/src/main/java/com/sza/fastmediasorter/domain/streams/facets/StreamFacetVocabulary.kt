package com.sza.fastmediasorter.domain.streams.facets

import java.text.Normalizer
import java.util.Locale

/**
 * Lookups derived from [StreamFacetTables]: what a folded word means as a language, and which codes are
 * real countries. Pure Kotlin - see the tables' note on why nothing here may import Android.
 */
object StreamFacetVocabulary {

    private const val DASHES = "[\\u2010-\\u2015]"
    private const val WHITESPACE = "[\\s\\p{Z}]+"
    private val dashesRegex = Regex(DASHES)
    private val whitespaceRegex = Regex(WHITESPACE)

    /** ISO 639-1 code to its fixed canonical lowercase English name. */
    val languageNamesByCode: Map<String, String> by lazy { parseSpaced(StreamFacetTables.ISO_639_1_TABLE) }

    private val extensionNamesByCode: Map<String, String> by lazy {
        parseSpaced(StreamFacetTables.ISO_639_3_EXTENSION_TABLE)
    }

    /** Every canonical language name: the two-letter set plus the named three-letter extension. */
    val canonicalLanguages: Set<String> by lazy {
        (languageNamesByCode.values + extensionNamesByCode.values).toSet()
    }

    /** Canonical name to its ISO code: two letters where one exists, otherwise the three-letter code. */
    val codeByCanonicalName: Map<String, String> by lazy {
        (extensionNamesByCode + languageNamesByCode).entries.associate { (code, name) -> name to code }
    }

    /**
     * Folded word or phrase to the canonical name, or to several comma-joined names for an alias that
     * stands for a list. Aliases are added last so they win over a plain name, as in the publisher module.
     */
    internal val languageLookup: Map<String, String> by lazy {
        val lookup = LinkedHashMap<String, String>()
        (languageNamesByCode.values + extensionNamesByCode.values).forEach { lookup[foldKey(it)] = it }
        parseAliases(StreamFacetTables.LANGUAGE_ALIAS_TABLE).forEach { (alias, target) ->
            lookup[foldKey(alias)] = target
        }
        lookup
    }

    /** Officially assigned ISO 3166-1 alpha-2 codes, uppercase. */
    val assignedCountries: Set<String> by lazy {
        StreamFacetTables.ASSIGNED_COUNTRY_TABLE.split(Regex("\\s+")).filter(String::isNotEmpty).toSet()
    }

    /**
     * Folded country name or alias to its code. The platform's English region names cover the common
     * names; the alias table carries the ones the platform spells differently or does not have.
     */
    internal val countryLookup: Map<String, String> by lazy {
        val lookup = LinkedHashMap<String, String>()
        Locale.getISOCountries().forEach { code ->
            val name = Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH)
            if (name.isNotBlank()) lookup[foldKey(name)] = code
        }
        parseAliases(StreamFacetTables.COUNTRY_ALIAS_TABLE).forEach { (alias, code) ->
            lookup[foldKey(alias)] = code
        }
        lookup
    }

    /**
     * Lowercase, drop diacritics, unify dashes and collapse whitespace, so `espanol` and `español` meet
     * at one key. Applied to table keys and to input alike, which is what keeps scripts without a
     * lower-case form (and ones decomposed into marks) consistent between the two.
     */
    fun foldKey(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val stripped = buildString(decomposed.length) {
            decomposed.forEach { ch ->
                if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) append(ch)
            }
        }
        return stripped.lowercase(Locale.ROOT)
            .replace(dashesRegex, "-")
            .replace(whitespaceRegex, " ")
            .trim()
    }

    private fun parseSpaced(table: String): Map<String, String> = table.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .associate { line -> line.substringBefore(' ') to line.substringAfter(' ') }

    private fun parseAliases(table: String): List<Pair<String, String>> = table.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { line -> line.substringBefore('|') to line.substringAfter('|') }
        .toList()
}

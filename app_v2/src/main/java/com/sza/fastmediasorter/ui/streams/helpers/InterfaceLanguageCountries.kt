package com.sza.fastmediasorter.ui.streams.helpers

import java.util.Locale

/**
 * S4133: the countries that lead the streams country list for a given interface language - those where
 * that language has official status at national level, a co-official status included. The owner's rule
 * is an alphabetical list except for this block, so a Ukrainian reader meets Ukraine first and a French
 * reader the French-speaking countries; English has no block and the list stays purely alphabetical.
 *
 * One table, one place: a disputed membership is a one-line change here and in its test vector. A
 * language with no row gets no block.
 */
object InterfaceLanguageCountries {

    private val blocksByLanguage: Map<String, Set<String>> = mapOf(
        "uk" to setOf("UA"),
        "ru" to setOf("RU", "BY", "KZ", "KG"),
        "fr" to setOf(
            "FR", "BE", "CH", "LU", "MC", "CA", "HT", "VU",
            "BJ", "BF", "BI", "CM", "CF", "TD", "KM", "CG", "CD", "CI", "DJ", "GQ", "GA", "GN", "MG", "ML",
            "NE", "RW", "SN", "SC", "TG",
            "GF", "GP", "MQ", "RE", "YT", "PM", "BL", "MF", "NC", "PF", "WF",
        ),
        "de" to setOf("DE", "AT", "CH", "LI", "LU", "BE"),
        "es" to setOf(
            "ES", "MX", "AR", "CO", "PE", "VE", "CL", "EC", "GT", "CU", "BO", "DO", "HN", "PY", "SV", "NI",
            "CR", "PA", "UY", "PR", "GQ",
        ),
        "pt" to setOf("PT", "BR", "AO", "MZ", "GW", "CV", "ST", "TL", "GQ", "MO"),
        "it" to setOf("IT", "SM", "VA", "CH"),
        "ar" to setOf(
            "SA", "EG", "DZ", "MA", "TN", "LY", "SD", "IQ", "SY", "JO", "LB", "PS", "KW", "QA", "BH", "AE",
            "OM", "YE", "MR", "DJ", "SO", "KM", "TD", "ER",
        ),
        "zh" to setOf("CN", "TW", "SG", "HK", "MO"),
        "hi" to setOf("IN"),
        "bn" to setOf("BD"),
        "ur" to setOf("PK"),
    )

    /** Interface languages that have a block; English is deliberately absent. */
    val languagesWithBlock: Set<String> get() = blocksByLanguage.keys

    /** The block for [locale]'s language, empty when the language has none (English included). */
    fun forLocale(locale: Locale): Set<String> =
        blocksByLanguage[locale.language.lowercase(Locale.ROOT)].orEmpty()
}

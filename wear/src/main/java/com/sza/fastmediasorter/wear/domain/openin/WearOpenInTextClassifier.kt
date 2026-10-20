package com.sza.fastmediasorter.wear.domain.openin

/** S4381: what a clipboard text is, for choosing the intent that opens it (research 03). */
enum class WearOpenInTextShape { WEB_ADDRESS, LINK, GEO_POINT, PLAIN }

/**
 * S4381: decides whether a text is an address, a link, a place or free text.
 *
 * Pure Kotlin on purpose - no `android.net.Uri` - so a plain JVM test covers every shape. Anything with
 * whitespace inside is free text: a sentence that merely starts with `http:` must not be opened as a link.
 */
object WearOpenInTextClassifier {

    private const val MAX_LATITUDE = 90.0
    private const val MAX_LONGITUDE = 180.0

    private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):(.*)$")
    private val COORDINATES = Regex("^(-?\\d{1,2}(?:\\.\\d+)?)\\s*,\\s*(-?\\d{1,3}(?:\\.\\d+)?)$")
    private val WEB_SCHEMES = setOf("http", "https")
    private val LINK_SCHEMES = setOf("mailto", "tel", "sms", "smsto")

    fun classify(text: String): WearOpenInTextShape {
        val trimmed = text.trim()
        return when {
            isCoordinatePair(trimmed) -> WearOpenInTextShape.GEO_POINT
            trimmed.isEmpty() || trimmed.any { it.isWhitespace() } -> WearOpenInTextShape.PLAIN
            else -> classifyUri(trimmed)
        }
    }

    private fun classifyUri(candidate: String): WearOpenInTextShape {
        val match = SCHEME.matchEntire(candidate) ?: return WearOpenInTextShape.PLAIN
        val scheme = match.groupValues[1].lowercase()
        val rest = match.groupValues[2]
        return when {
            scheme in WEB_SCHEMES && hasHost(rest) -> WearOpenInTextShape.WEB_ADDRESS
            scheme == "geo" && rest.isNotEmpty() -> WearOpenInTextShape.GEO_POINT
            scheme in LINK_SCHEMES && rest.isNotEmpty() -> WearOpenInTextShape.LINK
            else -> WearOpenInTextShape.PLAIN
        }
    }

    private fun hasHost(afterScheme: String): Boolean {
        if (!afterScheme.startsWith("//")) {
            return false
        }
        val authority = afterScheme.removePrefix("//").takeWhile { it != '/' && it != '?' && it != '#' }
        return authority.substringAfterLast('@').substringBefore(':').isNotEmpty()
    }

    private fun isCoordinatePair(text: String): Boolean {
        val match = COORDINATES.matchEntire(text) ?: return false
        val latitude = match.groupValues[1].toDoubleOrNull()
        val longitude = match.groupValues[2].toDoubleOrNull()
        return latitude != null && longitude != null &&
            latitude in -MAX_LATITUDE..MAX_LATITUDE && longitude in -MAX_LONGITUDE..MAX_LONGITUDE
    }
}

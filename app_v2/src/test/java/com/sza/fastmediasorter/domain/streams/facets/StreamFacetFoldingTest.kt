package com.sza.fastmediasorter.domain.streams.facets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Holds the phone's fold to the vector file it shares with the publisher and the watch, and guards the
 * closed tables against drifting from the platform's ISO lists or from the publisher's copy.
 */
class StreamFacetFoldingTest {

    private data class Vector(val kind: String, val input: String, val expected: String)

    private fun goldenVectors(): List<Vector> {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(GOLDEN_RESOURCE)) {
            "missing test resource $GOLDEN_RESOURCE"
        }
        return stream.bufferedReader(Charsets.UTF_8).readLines()
            .filterNot { it.startsWith("#") || it.isBlank() }
            .map { line -> line.split('\t').let { Vector(it[0], it[1], it[2]) } }
    }

    @Test
    fun `every golden vector holds`() {
        val vectors = goldenVectors()
        assertTrue("the golden file is unexpectedly small: ${vectors.size}", vectors.size > MIN_VECTORS)
        val failures = vectors.mapNotNull { v ->
            val actual = when (v.kind) {
                "language" -> StreamFacetFolding.foldLanguages(v.input)
                "country" -> StreamFacetFolding.foldCountry(v.input)
                else -> "unknown kind ${v.kind}"
            }
            if (actual == v.expected) null else "${v.kind} '${v.input}' => '$actual' (want '${v.expected}')"
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `every ISO 639-1 language the JVM lists is in the vocabulary or deliberately left out`() {
        val known = StreamFacetVocabulary.languageNamesByCode.keys
        val missing = Locale.getISOLanguages().filter { it !in known && it !in DEPRECATED_LANGUAGE_CODES }
        assertEquals("add these codes to the vocabulary or to the exclusion set", emptyList<String>(), missing)
    }

    @Test
    fun `every vocabulary language code is a code the JVM knows`() {
        val jvm = Locale.getISOLanguages().toSet()
        val unknown = StreamFacetVocabulary.languageNamesByCode.keys
            .filter { it !in jvm && it !in RETIRED_COLLECTIVE_CODES }
        assertEquals(emptyList<String>(), unknown)
    }

    @Test
    fun `assigned countries equal the JVM's ISO 3166 list`() {
        val jvm = Locale.getISOCountries().toSet()
        val assigned = StreamFacetVocabulary.assignedCountries
        assertEquals("in the JVM list, not in the table", emptySet<String>(), jvm - assigned)
        assertEquals("in the table, not in the JVM list", emptySet<String>(), assigned - jvm)
        assertTrue("AQ" in assigned)
        assertTrue("XX" !in assigned)
    }

    @Test
    fun `table values have the shape the fold relies on`() {
        val nameShape = Regex("^[a-z][a-z -]*$")
        val badNames = StreamFacetVocabulary.canonicalLanguages.filterNot(nameShape::matches)
        assertEquals(emptyList<String>(), badNames)
        val badCountries = StreamFacetVocabulary.assignedCountries
            .filterNot { it.length == 2 && it.all(Char::isUpperCase) }
        assertEquals(emptyList<String>(), badCountries)
    }

    @Test
    fun `every alias points at a vocabulary language`() {
        val canonical = StreamFacetVocabulary.canonicalLanguages
        val dangling = StreamFacetTables.LANGUAGE_ALIAS_TABLE.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .flatMap { it.substringAfter('|').split(',') }
            .filterNot { it in canonical }
            .toList()
        assertEquals(emptyList<String>(), dangling)
    }

    @Test
    fun `every country alias points at an assigned code`() {
        val assigned = StreamFacetVocabulary.assignedCountries
        val dangling = StreamFacetTables.COUNTRY_ALIAS_TABLE.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { it.substringAfter('|') }
            .filterNot { it in assigned }
            .toList()
        assertEquals(emptyList<String>(), dangling)
    }

    @Test
    fun `the tables are the same text as the publisher module`() {
        val script = listOf(
            "../scripts/streams/modules/StreamPublisher.Facets.ps1",
            "scripts/streams/modules/StreamPublisher.Facets.ps1"
        )
            .map(::File).firstOrNull(File::exists)
        assumeTrue("publisher module not reachable from the test working directory", script != null)
        val text = script!!.readText(Charsets.UTF_8).replace("\r\n", "\n")
        val pairs = mapOf(
            "FacetIso6391" to StreamFacetTables.ISO_639_1_TABLE,
            "FacetIso6393Extension" to StreamFacetTables.ISO_639_3_EXTENSION_TABLE,
            "FacetLanguageAliases" to StreamFacetTables.LANGUAGE_ALIAS_TABLE,
            "FacetAssignedCountries" to StreamFacetTables.ASSIGNED_COUNTRY_TABLE,
            "FacetCountryAliases" to StreamFacetTables.COUNTRY_ALIAS_TABLE,
        )
        pairs.forEach { (variable, kotlinTable) ->
            val match = Regex("\\\$script:$variable = @'\\n(.*?)\\n'@", RegexOption.DOT_MATCHES_ALL).find(text)
            assertTrue("$variable not found in the publisher module", match != null)
            val publisher = match!!.groupValues[1].lines().map(String::trim).filter(String::isNotEmpty)
            val phone = kotlinTable.lines().map(String::trim).filter(String::isNotEmpty)
            assertEquals("table $variable differs between publisher and phone", publisher, phone)
        }
    }

    private companion object {
        const val GOLDEN_RESOURCE = "streams/facet-golden.tsv"
        const val MIN_VECTORS = 50

        /** Retired codes a JDK still lists: Hebrew, Indonesian, Yiddish renamed; Moldavian merged into Romanian. */
        val DEPRECATED_LANGUAGE_CODES = setOf("iw", "in", "ji", "mo")

        /** Collective codes the vocabulary keeps although a newer JDK has already dropped them. */
        val RETIRED_COLLECTIVE_CODES = setOf("bh")
    }
}

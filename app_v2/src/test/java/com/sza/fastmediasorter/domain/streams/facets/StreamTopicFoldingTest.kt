package com.sza.fastmediasorter.domain.streams.facets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Holds the topic fold to the vector file it shares with the publisher. The same class runs in the watch
 * module against the watch's own compiled copy of the shared source.
 */
class StreamTopicFoldingTest {

    private data class Vector(val raw: String, val expected: String?)

    // The raw column is untrimmed on purpose: a whitespace-only raw value is a vector, not a blank line.
    private fun goldenVectors(): List<Vector> {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(GOLDEN_RESOURCE)) {
            "missing test resource $GOLDEN_RESOURCE"
        }
        return stream.bufferedReader(Charsets.UTF_8).readLines()
            .filterNot { it.isEmpty() || it.startsWith("#") }
            .map { line ->
                val cells = line.split('\t')
                Vector(cells[0], cells.getOrNull(1)?.takeIf { it.isNotEmpty() })
            }
    }

    @Test
    fun `every golden vector holds`() {
        val vectors = goldenVectors()
        assertTrue("the golden file is unexpectedly small: ${vectors.size}", vectors.size > MIN_VECTORS)
        val failures = vectors.mapNotNull { v ->
            val actual = StreamTopicFolding.canonicalTopic(v.raw)
            if (actual == v.expected) null else "'${v.raw}' => '$actual' (want '${v.expected}')"
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `the golden file covers every rubric id in both cases`() {
        val raws = goldenVectors().map { it.raw }.toSet()
        val missing = StreamTopicFolding.rubricIds.flatMap { listOf(it, it.lowercase(Locale.ROOT)) }
            .filter { it !in raws }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `folding is idempotent`() {
        val drifted = goldenVectors().mapNotNull { it.expected }.distinct()
            .filter { StreamTopicFolding.canonicalTopic(it) != it }
        assertEquals(emptyList<String>(), drifted)
    }

    @Test
    fun `null stays null`() {
        assertNull(StreamTopicFolding.canonicalTopic(null))
    }

    private companion object {
        const val GOLDEN_RESOURCE = "stream-bank/topic-fold-v1.tsv"
        const val MIN_VECTORS = 64
    }
}

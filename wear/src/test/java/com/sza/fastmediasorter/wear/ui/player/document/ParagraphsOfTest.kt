package com.sza.fastmediasorter.wear.ui.player.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2886: the reader's line split, pinned by its result and by its cost.
 *
 * The behaviour cases were written against the tail-rebuilding implementation and pass unchanged
 * against the index walk that replaced it. That equality is the whole safety of the rewrite:
 * strategic goal 4 asks for an identical split on every input, and the split has no specification
 * beyond what it already produced.
 */
class ParagraphsOfTest {

    @Test
    fun `a line within the limit passes through whole`() {
        val line = "x".repeat(LIMIT - 1)

        assertEquals(listOf(line), paragraphsOf(line))
    }

    @Test
    fun `blank lines are dropped and the rest is trimmed`() {
        assertEquals(listOf("First", "Second"), paragraphsOf("  First  \n\n   \nSecond\n"))
    }

    @Test
    fun `a long line is cut at the last space inside the window`() {
        val head = "a".repeat(LIMIT - 1)
        val tail = "b".repeat(LIMIT / 2)

        assertEquals(listOf(head, tail), paragraphsOf("$head $tail"))
    }

    @Test
    fun `a line with no space is cut at the limit`() {
        val remainder = 10
        val line = "j".repeat(LIMIT + remainder)

        assertEquals(listOf("j".repeat(LIMIT), "j".repeat(remainder)), paragraphsOf(line))
    }

    @Test
    fun `a line exactly at the limit stays one piece`() {
        val line = "k".repeat(LIMIT)

        assertEquals(listOf(line), paragraphsOf(line))
    }

    /** A seam of several spaces is one seam: it must not survive into a piece or become an empty one. */
    @Test
    fun `a run of spaces at the cut is consumed once`() {
        val head = "a".repeat(LIMIT - 4)
        val tail = "b".repeat(LIMIT)

        assertEquals(listOf(head, tail), paragraphsOf("$head   $tail"))
    }

    @Test
    fun `no piece exceeds the limit and the pieces rebuild the source`() {
        val source = (1..WORD_COUNT).joinToString(" ") { "word$it".repeat(3) }

        val pieces = paragraphsOf(source)

        assertTrue(pieces.all { it.length <= LIMIT })
        assertEquals(source.filterNot { it == ' ' }, pieces.joinToString("").filterNot { it == ' ' })
    }

    /**
     * The cost guard rather than a behaviour case (strategic goal 1).
     *
     * The string carries no space on purpose: that is the input on which the tail-rebuilding pass
     * copied everything still unread at every cut, and also the one on which a cut search bounded
     * only from above reaches back to the start of the line and restores the same quadratic cost.
     *
     * The length is set from a measurement, not from an estimate. On 2026-09-10 the previous
     * implementation split a space-free 1,056,000-character string in 0.373 s on the build host, so
     * a guard at that length passes on the defect and proves nothing. Cost grows as the square, so
     * the eightfold length here puts the old pass near 24 s against this 5 s timeout, while one walk
     * over the string measures in milliseconds - roughly five times the margin either way, which
     * survives a slow host without going flaky.
     */
    @Test(timeout = SPLIT_TIMEOUT_MS)
    fun `a space-free string far past the read cap splits in one pass`() {
        val line = "z".repeat(LIMIT * SPACE_FREE_PIECES)

        assertEquals(SPACE_FREE_PIECES, paragraphsOf(line).size)
    }

    private companion object {
        /** Mirrors `MAX_PARAGRAPH_CHARS`, which is private to the view model's file. */
        const val LIMIT = 240

        /** Puts the space-free case near 8 MiB, sixty-four times the reader's 128 KiB read cap. */
        const val SPACE_FREE_PIECES = 35_000

        const val SPLIT_TIMEOUT_MS = 5_000L

        const val WORD_COUNT = 40
    }
}

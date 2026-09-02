package com.sza.fastmediasorter.ocrbench

import android.graphics.Rect

/**
 * S1716: ground truth for one bench scene.
 *
 * Two areas are kept apart on purpose. [textAreas] say where the source text stands; [paintableAreas] say
 * where a plate is allowed to draw. Without the separation a metric cannot tell "covered its own text" from
 * "painted over the picture", and the distinction cannot be added afterwards - it has to be authored with
 * the scene.
 *
 * [provenance] carries who annotated the scene and when, and whether the annotation is a draft. A draft is
 * one filled from a recogniser's own output: usable as a starting point for a human, never as ground truth,
 * because scoring a recogniser against itself measures nothing.
 */
data class SceneAnnotation(
    val version: Int,
    val sceneId: String,
    val widthPx: Int,
    val heightPx: Int,
    val textAreas: List<TextArea>,
    val paintableAreas: List<PaintableArea>,
    /** False when a human cannot read the scene at all - such a scene scores nothing but stays in the count. */
    val readable: Boolean,
    val provenance: Provenance,
) {

    /**
     * True when this annotation may be used to score a run. A draft may not, and neither may an unreadable
     * scene - both are counted as unmeasured rather than silently treated as a perfect result.
     */
    fun isScorable(): Boolean = !provenance.draft && readable && textAreas.isNotEmpty()

    companion object {
        /** Bump when a field changes meaning; a reader refuses a version it does not know. */
        const val CURRENT_VERSION: Int = 2

        /**
         * Versions this reader accepts. Version 1 predates word geometry (S2036): its text areas carry
         * no words by construction and it stays scorable on every axis it could already answer. Dropping
         * it would have forced every annotated scene to be redone before the word axes worked at all,
         * and the cheap way out of that is a draft filled from a recogniser - which [Provenance] exists
         * to forbid.
         */
        val SUPPORTED_VERSIONS: Set<Int> = setOf(1, 2)
    }
}

/** One run of source text in the scene, with the text a perfect recogniser would return. */
data class TextArea(
    val text: String,
    val box: Rect,
    /**
     * S2036: the words of this line, when someone annotated them.
     *
     * Empty means *not annotated at word level* - never *this line holds no word*. The distinction is
     * the whole point: an axis reading this must report [Measured.Unmeasured] on an empty list, because
     * treating it as zero words would make an un-annotated scene score like a measured one.
     */
    val words: List<AnnotatedWord> = emptyList(),
)

/**
 * S2036: one word of a line, with the box a perfect recogniser would return for it.
 *
 * Word geometry is what the inherited height multiplier is defined against - it compares a word's
 * height to the median of the word heights on its line - so a corpus carrying line boxes alone has
 * nothing to compare and cannot judge that number.
 */
data class AnnotatedWord(
    val text: String,
    val box: Rect,
)

/**
 * A region a plate may cover. Deliberately not derived from [TextArea]: a plate is allowed to spill onto the
 * paper around its line, and forbidden to spill onto the drawing next to it, and only the scene's author
 * knows which is which.
 */
data class PaintableArea(
    val box: Rect,
)

/** Who produced the annotation, when, and whether it is a recogniser-filled draft. */
data class Provenance(
    val author: String,
    val annotatedOn: String,
    val draft: Boolean,
)

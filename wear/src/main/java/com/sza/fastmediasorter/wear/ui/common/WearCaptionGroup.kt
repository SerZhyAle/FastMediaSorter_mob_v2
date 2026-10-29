package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.wear.compose.material.LocalTextStyle
import kotlin.math.ceil

/**
 * One caption size shared by every caption of a grid (S4469).
 *
 * [WearCaptionText] sizes each caption on its own, which is right for a list of file names and wrong for
 * a grid of fixed labels: five category labels settled on 16, 15, 14, 13 and 12sp, each at the largest
 * size its own word allowed. Inside a group every caption draws at [size], and any caption that
 * overflows steps that one value down, so the grid ends on the largest size at which all of its
 * captions fit.
 *
 * The size only goes down. Cells report from separate layout passes, and a report that arrived late at
 * a stale size must not lift the value another cell already lowered - that would make the grid
 * oscillate instead of settling.
 */
@Stable
class WearCaptionGroup {

    /** The size every caption in the group draws at; starts at [WearCaptionScale.Ceiling]. */
    var size: TextUnit by mutableStateOf(WearCaptionScale.Ceiling)
        private set

    /** Lowers [size] to [candidate]; a candidate that is not smaller changes nothing. */
    fun lowerTo(candidate: TextUnit) {
        if (candidate.value < size.value) {
            size = candidate
        }
    }
}

/** The group the captions below this point belong to, or null for a caption that sizes itself. */
val LocalWearCaptionGroup = compositionLocalOf<WearCaptionGroup?> { null }

/**
 * A [WearCaptionGroup] that starts again at the ceiling when the labels or the density change.
 *
 * The size only goes down, so a group that outlived a language switch or a font-scale change would
 * keep the smaller size that the previous labels needed. Density carries the font scale, which is why
 * it is a key here.
 */
@Composable
fun rememberWearCaptionGroup(labels: List<String>): WearCaptionGroup {
    val density = LocalDensity.current
    return remember(labels, density) { WearCaptionGroup() }
}

/** Where a label may break onto the next line; a stretch between two of these is a word that cannot. */
private val WORD_BREAK = Regex("\\s+")

/**
 * Width in dp of the widest single word among [labels] when drawn at [WearCaptionScale.Floor].
 *
 * A word is the measuring unit because a cell that allows a second line breaks a label between words,
 * so "Голосовые заметки" needs room for "Голосовые" and not for the whole phrase. The floor is the
 * measuring size because it is the narrowest a caption can get: a cell narrower than this cannot show
 * the word whole at any size, so it is the width a grid has to give. Rounded up, so a cell of exactly
 * this width never truncates by a fraction of a pixel.
 */
@Composable
fun rememberWidestCaptionWordDp(labels: List<String>): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = LocalTextStyle.current.copy(fontSize = WearCaptionScale.Floor)
    return remember(labels, measurer, density, style) {
        val widestPx = labels
            .flatMap { label -> label.split(WORD_BREAK) }
            .filter { word -> word.isNotEmpty() }
            .maxOfOrNull { word ->
                measurer.measure(text = word, style = style, softWrap = false, maxLines = 1).size.width
            } ?: 0
        ceil(widestPx / density.density).toInt()
    }
}

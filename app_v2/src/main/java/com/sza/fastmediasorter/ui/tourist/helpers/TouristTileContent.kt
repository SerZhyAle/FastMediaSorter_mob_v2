package com.sza.fastmediasorter.ui.tourist.helpers

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * S4145: everything one Tourist tile shows, built once and read by both the focused card and the grid,
 * so the two can never disagree about a value, a unit or a status line.
 *
 * [spokenDescription] is the whole tile as one phrase - name, value, unit and status - because a screen
 * reader that announces the number without its unit or warning tells the user nothing (strategic §3.2).
 */
data class TouristTileContent(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
    val value: String,
    val unit: String,
    val detail: String?,
    @ColorRes val backgroundRes: Int,
    val visual: HeroVisual,
    val spokenDescription: String,
)

/** The card's optional picture; the grid ignores it. */
sealed interface HeroVisual {

    data object None : HeroVisual

    data class Coordinates(val latitude: Double?, val longitude: Double?) : HeroVisual

    data class CompassRose(val azimuthDegrees: Float) : HeroVisual

    data class Arrow(val rotationDegrees: Float) : HeroVisual

    data class Level(val pitchDegrees: Float, val rollDegrees: Float) : HeroVisual
}

/**
 * Builds a [TouristTileContent] and its spoken phrase. A missing reading is spoken as its status line
 * alone - "--" read aloud is noise.
 */
internal fun tileContent(
    context: Context,
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    reading: TouristTileValueFormatter.TileValue,
    detail: String?,
    @ColorRes backgroundRes: Int,
    visual: HeroVisual = HeroVisual.None,
): TouristTileContent {
    val hasValue = reading.value != TouristTileValueFormatter.NO_VALUE
    val spoken = listOfNotNull(
        context.getString(labelRes),
        reading.value.takeIf { hasValue },
        reading.unit.takeIf { hasValue && it.isNotBlank() },
        detail?.takeIf { it.isNotBlank() },
    ).joinToString(SPOKEN_SEPARATOR)
    return TouristTileContent(iconRes, labelRes, reading.value, reading.unit, detail, backgroundRes, visual, spoken)
}

private const val SPOKEN_SEPARATOR = ", "

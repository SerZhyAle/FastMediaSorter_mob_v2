package com.sza.fastmediasorter.ui.broadcast.helpers

import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BroadcastLensOption
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Names the lenses a person picks from. A phone has several back lenses behind one facing word, so the
 * magnification is what tells them apart; front lenses all carry the neutral multiplier, so identical
 * names are numbered instead.
 */
object BroadcastLensLabelFormatter {

    fun labels(options: List<BroadcastLensOption>, facingName: (String) -> String): List<String> {
        val base = options.map { option ->
            val name = facingName(option.facing)
            if (option.facing == BroadcastLensOption.FACING_BACK && option.zoomMultiplier > 0f) {
                "$name ${zoomText(option.zoomMultiplier)}"
            } else {
                name
            }
        }
        val totals = base.groupingBy { it }.eachCount()
        val seen = mutableMapOf<String, Int>()
        return base.map { label ->
            if ((totals[label] ?: 0) < DUPLICATE_THRESHOLD) {
                label
            } else {
                val index = (seen[label] ?: 0) + 1
                seen[label] = index
                "$label $index"
            }
        }
    }

    @StringRes
    fun facingNameRes(facing: String): Int = when (facing) {
        BroadcastLensOption.FACING_BACK -> R.string.broadcast_lens_back
        BroadcastLensOption.FACING_FRONT -> R.string.broadcast_lens_front
        else -> R.string.broadcast_lens_external
    }

    internal fun zoomText(multiplier: Float): String {
        val tenths = (multiplier * TENTHS).roundToInt()
        return if (tenths % TENTHS_PER_UNIT == 0) {
            "${tenths / TENTHS_PER_UNIT}x"
        } else {
            String.format(Locale.ROOT, "%.1fx", tenths / TENTHS)
        }
    }

    private const val TENTHS = 10f
    private const val TENTHS_PER_UNIT = 10
    private const val DUPLICATE_THRESHOLD = 2
}

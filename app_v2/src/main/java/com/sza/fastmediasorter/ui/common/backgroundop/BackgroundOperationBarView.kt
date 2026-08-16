package com.sza.fastmediasorter.ui.common.backgroundop

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.sza.fastmediasorter.R

/**
 * Hairline progress bar for a background file operation, laid over the bottom of a screen.
 *
 * Configures itself entirely here so an attach site never restates thickness, colour or the
 * accessibility flags. Passive by design (S1398 §6.8): it takes neither touch nor focus, so a
 * control sitting under it keeps receiving taps.
 */
class BackgroundOperationBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearProgressIndicator(context, attrs) {

    init {
        trackThickness = resources.getDimensionPixelSize(R.dimen.background_operation_bar_height)
        trackCornerRadius = 0
        setIndicatorColor(
            MaterialColors.getColor(
                this,
                androidx.appcompat.R.attr.colorPrimary,
                Color.TRANSPARENT,
            ),
        )
        // Nothing is painted behind the indicator: an idle screen must show no line at all.
        setTrackColor(Color.TRANSPARENT)
        max = PERCENT_MAX
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        contentDescription = context.getString(R.string.background_operation_bar_description)
        isVisible = false
    }

    fun render(state: BackgroundOperationBarState) {
        when (state) {
            BackgroundOperationBarState.Hidden -> isVisible = false

            BackgroundOperationBarState.Indeterminate -> {
                isIndeterminate = true
                isVisible = true
            }

            is BackgroundOperationBarState.Determinate -> {
                isIndeterminate = false
                isVisible = true
                setProgressCompat(state.percent, true)
            }
        }
    }

    private companion object {
        const val PERCENT_MAX = 100
    }
}

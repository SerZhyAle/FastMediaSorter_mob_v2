package com.sza.fastmediasorter.ui.player.views

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * Shared container for the player's floating overlay surfaces (draw toolbar, translation card,
 * text-viewer bars, reader controls, search panel, transfer panels).
 *
 * Carries no behaviour on purpose: it exists so the scrim and the elevation token live in one
 * style instead of being restated per panel, which is how the PDF and EPUB control twins drifted
 * to 16dp and no elevation respectively. Panel logic stays in the ui/player/helpers managers.
 */
class PlayerOverlayPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(
    context,
    attrs,
    defStyleAttr,
    R.style.Widget_FastMediaSorter_Player_OverlayPanel,
) {
    init {
        Timber.d("S3250: PlayerOverlayPanel inflated")
    }
}

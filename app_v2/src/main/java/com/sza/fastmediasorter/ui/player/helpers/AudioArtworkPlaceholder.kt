package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * The audio glyph (content.audio) as the stand-in for missing album art on the player's artwork view.
 *
 * That view has a fixed black background whatever the theme, so the glyph's own theme tint would be
 * dark on black in a light theme. The tint cannot go on the view either: the same view shows the album
 * art, which a view tint would repaint. So the placeholder drawable itself carries the overlay colour
 * (ICON-EXTERNAL rule 4: a missing picture is replaced by the glyph of what it stands for).
 */
object AudioArtworkPlaceholder {

    fun onDarkSurface(context: Context): Drawable? {
        Timber.d("S3430: audio artwork placeholder drawn on the dark surface")
        return AppCompatResources.getDrawable(context, R.drawable.ic_audio)?.mutate()?.apply {
            setTint(ContextCompat.getColor(context, R.color.player_overlay_text_secondary))
        }
    }

    /**
     * The image glyph (content.image) for a full-screen photo slot on the same dark surface.
     *
     * The slot scales its content to the screen, which would blow a bare 24 dp glyph up to the
     * whole display; the inset keeps the glyph at a quarter of the shorter side.
     */
    fun imageOnDarkSurface(context: Context): Drawable? {
        val glyph = AppCompatResources.getDrawable(context, R.drawable.ic_image)?.mutate()?.apply {
            setTint(ContextCompat.getColor(context, R.color.player_overlay_text_secondary))
        } ?: return null
        val inset = (glyph.intrinsicWidth * FULL_SCREEN_GLYPH_INSET_RATIO).toInt()
        return LayerDrawable(arrayOf(glyph)).apply { setLayerInset(0, inset, inset, inset, inset) }
    }

    private const val FULL_SCREEN_GLYPH_INSET_RATIO = 1.5f
}

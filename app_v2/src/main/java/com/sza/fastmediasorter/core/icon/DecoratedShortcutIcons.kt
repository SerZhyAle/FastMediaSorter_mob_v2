package com.sza.fastmediasorter.core.icon

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.IconCompat
import kotlin.math.roundToInt

/**
 * S3433: the decorated look on the launcher (ICON-RENDER 0.10 section 10, item E).
 *
 * A plain vector published as a shortcut icon is shrunk by the launcher onto a white disc, which is what
 * every app shortcut showed before this ticket. On API 26+ the icon is adaptive instead: a background filled
 * edge to edge with the plate hue and the glyph at 44 dp in the 72 dp safe zone, so the launcher's own mask
 * shapes it. API 25 (the `legacy` edition) has no adaptive icons and gets the platform's 7.1 shape - a 44 dp
 * circle and a 24 dp glyph in a 48 dp asset.
 *
 * The hue is resolved in day mode whatever the app's theme: the plate brings its own background, and the
 * launcher that draws it does not follow the app's night setting.
 */
object DecoratedShortcutIcons {

    private const val ADAPTIVE_SIDE_DP = 108f
    private const val ADAPTIVE_GLYPH_DP = 44f
    private const val LEGACY_SIDE_DP = 48f
    private const val LEGACY_PLATE_DP = 44f
    private const val LEGACY_GLYPH_DP = 24f

    fun forGlyph(context: Context, @DrawableRes glyphRes: Int, @ColorRes hueRes: Int): IconCompat =
        forGlyph(context, glyphRes, hueRes, Build.VERSION.SDK_INT)

    /** [sdkInt] is a parameter so the API 25 branch - the `legacy` edition's only shape - runs in a test. */
    @VisibleForTesting
    internal fun forGlyph(
        context: Context,
        @DrawableRes glyphRes: Int,
        @ColorRes hueRes: Int,
        sdkInt: Int,
    ): IconCompat {
        val day = dayContext(context)
        val plate = ContextCompat.getColor(day, hueRes)
        val glyph = AppCompatResources.getDrawable(day, glyphRes)?.mutate()
            ?: return IconCompat.createWithResource(context, glyphRes)
        DrawableCompat.setTint(glyph, PlateContrast.onPlateColour(plate))
        val density = context.resources.displayMetrics.density
        return if (sdkInt >= Build.VERSION_CODES.O) {
            val bitmap = render(density, ADAPTIVE_SIDE_DP, null, ADAPTIVE_GLYPH_DP, plate, glyph)
            IconCompat.createWithAdaptiveBitmap(bitmap)
        } else {
            val bitmap = render(density, LEGACY_SIDE_DP, LEGACY_PLATE_DP, LEGACY_GLYPH_DP, plate, glyph)
            IconCompat.createWithBitmap(bitmap)
        }
    }

    /** [plateDp] null fills the whole square (the adaptive background); otherwise a centred circle. */
    @VisibleForTesting
    internal fun render(
        density: Float,
        sideDp: Float,
        plateDp: Float?,
        glyphDp: Float,
        plate: Int,
        glyph: Drawable,
    ): Bitmap {
        val side = (sideDp * density).roundToInt()
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (plateDp == null) {
            canvas.drawColor(plate)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = plate }
            canvas.drawCircle(side / 2f, side / 2f, plateDp * density / 2f, paint)
        }
        val glyphSide = (glyphDp * density).roundToInt()
        val inset = (side - glyphSide) / 2
        glyph.setBounds(inset, inset, inset + glyphSide, inset + glyphSide)
        glyph.draw(canvas)
        return bitmap
    }

    private fun dayContext(context: Context): Context {
        val config = Configuration(context.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        return context.createConfigurationContext(config)
    }
}

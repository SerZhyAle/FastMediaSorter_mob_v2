package com.sza.fastmediasorter.ui.launcher.signal

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.google.android.material.color.MaterialColors
import timber.log.Timber

/**
 * Draws a signal's icon, whichever of [LauncherSignalIcon]'s two cases it names.
 *
 * Shared by the strip's chips and the overflow sheet's rows rather than written twice: the two surfaces show
 * the same signal, and a second resolution path is how they start disagreeing about what an application that
 * has just been uninstalled looks like (S1465).
 */
internal object LauncherSignalIconBinder {

    /** What [resolve] decided a chip should draw. */
    sealed interface Resolved {
        data class FromDrawable(val drawable: Drawable) : Resolved
        data class FromResource(@param:DrawableRes val res: Int) : Resolved
    }

    fun bind(target: ImageView, icon: LauncherSignalIcon) {
        when (val resolved = resolve(target.context, icon)) {
            is Resolved.FromDrawable -> {
                // The chip layout tints its drawable with ?attr/colorOnSurface so that this app's own
                // monochrome glyphs follow the theme. A foreign application's icon is already coloured, and
                // that tint would flatten every one of them into the same silhouette - which is the whole
                // point of the row lost. Cleared per bind rather than in the layout, because the same view
                // is recycled for both cases.
                target.imageTintList = null
                target.setImageDrawable(resolved.drawable)
            }
            is Resolved.FromResource -> {
                // Restored rather than assumed intact: the overflow sheet recycles its rows, so a row that
                // last showed a foreign icon arrives here with the tint already cleared. Both layouts that
                // host a signal icon declare the same ?attr/colorOnSurface, so the theme is the source of
                // truth for what to put back.
                target.imageTintList = ColorStateList.valueOf(
                    MaterialColors.getColor(target, com.google.android.material.R.attr.colorOnSurface),
                )
                target.setImageResource(resolved.res)
            }
        }
    }

    /**
     * Kept apart from [bind] so that the fallback rule can be asserted without a rendered view. Whether an
     * uninstalled application still yields a chip is a decision, and a decision observable only as a
     * `Drawable` instance on an `ImageView` is one no test re-checks.
     */
    fun resolve(context: Context, icon: LauncherSignalIcon): Resolved = when (icon) {
        is LauncherSignalIcon.Resource -> Resolved.FromResource(icon.res)
        is LauncherSignalIcon.Application -> resolveApplication(context, icon)
    }

    private fun resolveApplication(context: Context, icon: LauncherSignalIcon.Application): Resolved {
        // Ordinary, not exceptional: a package can be uninstalled between a signal's emission and the bind
        // that draws it, and the row must keep the chip rather than open a hole in itself. Both answers a
        // package manager gives for a package it does not know are handled - the documented exception, and
        // the bare null some implementations return instead. The declared type is non-null, so an unchecked
        // null would crash the home screen at the exact moment this fallback exists to prevent.
        val drawable: Drawable? = try {
            context.packageManager.getApplicationIcon(icon.packageName)
        } catch (notInstalled: PackageManager.NameNotFoundException) {
            Timber.d(notInstalled, "Launcher signal icon: %s is gone, drawing the fallback", icon.packageName)
            null
        }
        return drawable?.let(Resolved::FromDrawable) ?: Resolved.FromResource(icon.fallbackRes)
    }
}

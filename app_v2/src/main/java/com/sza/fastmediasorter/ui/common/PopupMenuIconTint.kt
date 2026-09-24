package com.sza.fastmediasorter.ui.common

import android.content.Context
import android.graphics.Color
import android.view.Menu
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.material.color.MaterialColors
import timber.log.Timber

/**
 * The colour a popup draws its item icons in: the theme's control colour, the same role the popup's
 * own text and check marks follow. PopupMenu and ListPopupWindow leave item icons untinted, and many
 * glyphs are shared with the dark player overlays, so a literal colour here is invisible on one theme
 * or the other (ICON-RENDER rule 2).
 */
@ColorInt
fun Context.popupIconColor(): Int =
    MaterialColors.getColor(this, androidx.appcompat.R.attr.colorControlNormal, Color.GRAY)

/**
 * Tints every item icon of this menu, section children included, with [popupIconColor] of
 * [context]. Mutated copies are tinted so a drawable shared with another surface keeps its own tint.
 * Call once the menu is populated: an item added afterwards is not reached.
 */
fun Menu.tintIconsFromTheme(context: Context) {
    Timber.d("S3430: popup menu icons tinted from the theme control colour")
    tintIcons(this, context.popupIconColor())
}

private fun tintIcons(menu: Menu, @ColorInt color: Int) {
    for (i in 0 until menu.size()) {
        val item = menu.getItem(i)
        item.icon?.let { icon ->
            val tinted = DrawableCompat.wrap(icon.mutate())
            DrawableCompat.setTint(tinted, color)
            item.icon = tinted
        }
        item.subMenu?.let { tintIcons(it, color) }
    }
}

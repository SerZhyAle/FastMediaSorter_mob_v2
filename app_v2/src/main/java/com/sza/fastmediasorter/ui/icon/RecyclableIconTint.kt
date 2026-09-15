package com.sza.fastmediasorter.ui.icon

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.widget.ImageView

/**
 * Recolours an icon on a recycled view in a way that can be UNDONE.
 *
 * S3080: `ImageView.setImageTintList(null)` does not mean "this view applies no tint" - it sets the
 * view's own tint flag and then calls `drawable.mutate().setTintList(null)`, which erases the
 * `android:tint` the vector declares for itself. Every one of the hundred `ico_*` resource glyphs is
 * authored as a black `fillColor` plus that declared tint, so clearing left them pure black on the
 * launcher desktop. A colour filter overrides the declared tint while it is set and hands the
 * rendering back to it when removed, so clearing is symmetric with applying.
 */
object RecyclableIconTint {

    /** Applies [color] over [view]'s current drawable, or restores the drawable's own tint when null. */
    fun apply(view: ImageView, color: Int?) {
        view.colorFilter = color?.let { PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
    }
}

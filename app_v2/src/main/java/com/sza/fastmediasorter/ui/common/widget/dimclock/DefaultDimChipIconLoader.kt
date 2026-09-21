package com.sza.fastmediasorter.ui.common.widget.dimclock

import android.graphics.drawable.Drawable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3366: the non-launcher dim overlay keeps the static fallback icon for every chip.
 *
 * The launcher flavor binds the package-resolving implementation; binding this default there would
 * put the same grid glyph on every notification, which is the defect S3366 exists to remove.
 */
@Singleton
class DefaultDimChipIconLoader @Inject constructor() : DimChipIconLoader {
    override suspend fun load(packageName: String): Drawable? = null
}

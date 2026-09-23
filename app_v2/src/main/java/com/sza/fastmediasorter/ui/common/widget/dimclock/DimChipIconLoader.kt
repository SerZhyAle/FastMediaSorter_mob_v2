package com.sza.fastmediasorter.ui.common.widget.dimclock

import android.graphics.drawable.Drawable

/**
 * S3366: resolves a foreign-notification chip's real application icon for the dim overlay.
 *
 * Called off the main thread from the chip binding: a package-manager lookup is binder IPC, and the
 * dim screen is the last surface that may pay it on Main. Null means the package has no readable
 * icon any more - uninstalled between the count arriving and this bind - and the chip keeps its
 * static fallback resource.
 */
interface DimChipIconLoader {
    suspend fun load(packageName: String): Drawable?
}

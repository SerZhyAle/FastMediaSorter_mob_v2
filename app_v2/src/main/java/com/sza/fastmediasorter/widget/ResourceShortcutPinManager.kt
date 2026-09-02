package com.sza.fastmediasorter.widget

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.sza.fastmediasorter.ui.browse.BrowseActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pins a per-resource launcher shortcut via `requestPinShortcut`. This replaces the older
 * `requestPinAppWidget` path, which Samsung One UI silently drops (no dialog, no widget). A pinned
 * shortcut is reliable across launchers, carries the resource's own icon, and always yields a
 * launcher confirmation. The caller owns the success/unsupported message.
 */
@Singleton
class ResourceShortcutPinManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Requests the launcher to pin a shortcut opening [resourceId], labelled [label] and drawn with
     * [icon] (the same drawable the resource shows elsewhere). Returns [PinResult.Unsupported] when
     * the launcher cannot pin, so the caller can report it.
     */
    fun requestPin(resourceId: Long, label: String, icon: Drawable): PinResult {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return PinResult.Unsupported
        val bitmap = icon.toBitmap(SHORTCUT_ICON_SIZE_PX, SHORTCUT_ICON_SIZE_PX)
        val info = ShortcutInfoCompat.Builder(context, "resource_$resourceId")
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithBitmap(bitmap))
            .setIntent(BrowseActivity.createLaunchShortcutIntent(context, resourceId))
            .build()
        return if (ShortcutManagerCompat.requestPinShortcut(context, info, null)) {
            PinResult.Requested
        } else {
            PinResult.Unsupported
        }
    }

    enum class PinResult {
        Requested,
        Unsupported,
    }

    private companion object {
        // Rendered once and handed to the launcher, which masks/scales it - a crisp source beats intrinsic VD size.
        const val SHORTCUT_ICON_SIZE_PX = 192
    }
}

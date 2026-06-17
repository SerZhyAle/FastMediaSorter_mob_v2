package com.sza.fastmediasorter.core.share

import android.content.Context
import android.content.pm.PackageManager
import com.sza.fastmediasorter.util.getApplicationInfoCompat
import com.sza.fastmediasorter.util.getPackageInfoCompat
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the icon for a [ShareTarget] in the unified «Send to..» menu (S0459 ADR-5; reversed by
 * S0478).
 *
 * Hybrid policy: a package-backed target still prefers the installed app's launcher icon (most
 * recognisable); this resolver returns null for a logical or not-installed target. S0478 reverses
 * the original ADR-5 stance of falling back to a single generic share glyph: the caller now uses
 * each receiver's own meaningful per-target [ShareTarget.iconRes] glyph (a neutral-named vector
 * analog for brands), so every menu row is visually distinguishable.
 */
@Singleton
class ShareTargetIconResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** @return the installed receiver app's icon, or null to use the neutral glyph. */
    fun resolveIcon(target: ShareTarget): Drawable? {
        val pm = context.packageManager
        val pkg = target.packages.firstOrNull { isInstalled(pm, it) } ?: return null
        return try {
            pm.getApplicationIcon(pkg)
        } catch (_: PackageManager.NameNotFoundException) {
            // Resolved as installed just above; a race here only means "fall back to the glyph".
            Timber.i("ShareTargetIconResolver: icon unavailable for %s, using glyph", pkg)
            null
        }
    }

    /**
     * @return the installed receiver app's label for a package-backed target, or null for a logical
     * target (caller falls back to [ShareTarget.titleRes]). Keeps forbidden brand literals (e.g.
     * Instagram, denied by `verifyNoPlatformNames`) out of the codebase (S0459 ADR-5). Used by both
     * menu presentations so the sheet and overflow submenu show the same label.
     */
    fun resolveLabel(target: ShareTarget): CharSequence? {
        if (target.packages.isEmpty()) return null
        val pm = context.packageManager
        for (pkg in target.packages) {
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfoCompat(pkg))
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
            if (label != null) return label
        }
        return null
    }

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean = try {
        pm.getPackageInfoCompat(pkg)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        // Expected on devices without this receiver app; absence is not an error.
        false
    }
}

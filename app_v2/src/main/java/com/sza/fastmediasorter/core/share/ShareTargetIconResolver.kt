package com.sza.fastmediasorter.core.share

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the icon for a [ShareTarget] in the unified «Send to..» menu (S0459 ADR-5).
 *
 * Hybrid policy: a package-backed target shows the installed app's launcher icon (recognisable,
 * no bundled brand logo - trademark / Play safety); a logical target returns null so the caller
 * falls back to the target's neutral `?attr`-tinted [ShareTarget.iconRes] glyph.
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

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        // Expected on devices without this receiver app; absence is not an error.
        false
    }
}

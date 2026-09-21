package com.sza.fastmediasorter.ui.launcher.dimclock

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.sza.fastmediasorter.ui.common.widget.dimclock.DimChipIconLoader
import com.sza.fastmediasorter.ui.launcher.signal.LauncherSignalIconBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3366: the dim chip's real application icon, drawn by the launcher strip's own glyph rule.
 *
 * [LauncherSignalIconBinder] already holds one rule for adaptive foregrounds, legacy icons and the
 * uninstalled-package fallback - reusing it here is what keeps the dim row and the strip from
 * drifting apart into two answers about the same notification (strategic §7 risk 4). Resolved off
 * Main, because the lookup is binder IPC and the dim screen is the last surface that may pay it
 * there; cached per package, because the status row re-binds on every snapshot and one application's
 * icon does not change between them.
 */
@Singleton
class LauncherDimChipIconLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DimChipIconLoader {

    private val cache = ConcurrentHashMap<String, Optional<Drawable>>()

    override suspend fun load(packageName: String): Drawable? = withContext(Dispatchers.IO) {
        cache.computeIfAbsent(packageName) { resolveGlyph(it) }.orElse(null)
    }

    private fun resolveGlyph(packageName: String): Optional<Drawable> = try {
        Optional.of(
            LauncherSignalIconBinder.applicationGlyph(
                context.packageManager.getApplicationIcon(packageName),
            ),
        )
    } catch (notInstalled: PackageManager.NameNotFoundException) {
        Timber.d(notInstalled, "Dim chip icon: %s is gone, chip keeps the fallback", packageName)
        Optional.empty()
    }
}

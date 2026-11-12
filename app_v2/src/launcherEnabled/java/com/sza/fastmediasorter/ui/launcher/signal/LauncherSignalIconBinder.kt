package com.sza.fastmediasorter.ui.launcher.signal

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.google.android.material.color.MaterialColors
import com.sza.fastmediasorter.R
import timber.log.Timber
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Draws a signal's icon, whichever of [LauncherSignalIcon]'s two cases it names.
 *
 * Shared by the strip's chips and the overflow sheet's rows rather than written twice: the two surfaces show
 * the same signal, and a second resolution path is how they start disagreeing about what an application that
 * has just been uninstalled looks like (S1465).
 */
internal object LauncherSignalIconBinder {

    private const val ICON_CACHE_SIZE = 64
    private const val ICON_CACHE_LOAD_FACTOR = 0.75f

    // S4090: a miss is logged once per package per process. Every strip re-render binds every chip again,
    // and a package the package manager cannot see used to log a full stack trace on each of them.
    private val reportedMisses = ConcurrentHashMap.newKeySet<String>()

    // S4507: loading an application's icon reads its files, and a chip bind runs on the main thread - the
    // strip re-binds every chip on every re-render. A constant state instantiates from memory alone, which is
    // what keeps the bind off the disk once [warm] or an earlier bind has stored it. Bounded, because the set
    // of applications that post notifications is not.
    private val iconStates: MutableMap<String, Drawable.ConstantState> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Drawable.ConstantState>(ICON_CACHE_SIZE, ICON_CACHE_LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Drawable.ConstantState>): Boolean =
                size > ICON_CACHE_SIZE
        },
    )

    /** What [resolve] decided a chip should draw. */
    sealed interface Resolved {
        data class FromDrawable(val drawable: Drawable) : Resolved
        data class FromResource(@param:DrawableRes val res: Int) : Resolved
    }

    fun bind(target: ImageView, icon: LauncherSignalIcon) {
        when (val resolved = resolve(target.context, icon)) {
            is Resolved.FromDrawable -> {
                bindApplicationDrawable(target, resolved.drawable)
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
                val basePadding = basePadding(target)
                target.setPadding(basePadding, basePadding, basePadding, basePadding)
            }
        }
    }

    private fun basePadding(target: ImageView): Int {
        captureBasePadding(target)
        return target.getTag(R.id.launcherSignalIconBasePadding) as Int
    }

    private fun captureBasePadding(target: ImageView) {
        if (target.getTag(R.id.launcherSignalIconBasePadding) == null) {
            target.setTag(R.id.launcherSignalIconBasePadding, target.paddingLeft)
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

    /**
     * An adaptive icon's background is a plate around its recognisable application glyph. The strip has no
     * room for that plate, so foreign notification chips draw only the foreground; legacy icons already are
     * their glyph and pass through unchanged.
     */
    internal fun applicationGlyph(drawable: Drawable): Drawable {
        if (drawable is AdaptiveIconDrawable) {
            return drawable.foreground
        }
        return drawable
    }

    internal fun bindApplicationDrawable(target: ImageView, drawable: Drawable) {
        // The chip layout tints its drawable with ?attr/colorOnSurface so that this app's own monochrome
        // glyphs follow the theme. A foreign application's icon is already coloured, and that tint would
        // flatten every one of them into the same silhouette - which is the whole point of the row lost.
        // Cleared per bind rather than in the layout, because the same view is recycled for both cases.
        target.imageTintList = null
        target.setImageDrawable(applicationGlyph(drawable))
        captureBasePadding(target)
        target.setPadding(0, 0, 0, 0)
    }

    private fun resolveApplication(context: Context, icon: LauncherSignalIcon.Application): Resolved {
        // Ordinary, not exceptional: a package can be uninstalled between a signal's emission and the bind
        // that draws it, and the row must keep the chip rather than open a hole in itself. Both answers a
        // package manager gives for a package it does not know are handled - the documented exception, and
        // the bare null some implementations return instead. The declared type is non-null, so an unchecked
        // null would crash the home screen at the exact moment this fallback exists to prevent.
        //
        // S4090: the system-attached application info is tried first. A package hidden by package-visibility
        // filtering is unknown to a by-name lookup although it is installed, and the info loads its icon
        // straight from the application's own files.
        val info = icon.applicationInfo
        val drawable: Drawable? = if (info != null) {
            loadApplicationIcon(context, icon, info)
        } else {
            try {
                context.packageManager.getApplicationIcon(icon.packageName)
            } catch (notVisible: PackageManager.NameNotFoundException) {
                if (reportedMisses.add(icon.packageName)) {
                    Timber.d(
                        "Launcher signal icon: %s not resolvable, drawing the fallback (%s)",
                        icon.packageName,
                        notVisible.message,
                    )
                }
                null
            }
        }
        return drawable?.let(Resolved::FromDrawable) ?: Resolved.FromResource(icon.fallbackRes)
    }

    /**
     * S4507: loads the application's icon ahead of the bind, so the main-thread bind that follows finds it in
     * memory. Meant for a background thread; the signal source calls it from its IO flow.
     *
     * @return whether an entry for the icon is cached afterwards. An icon without an install directory is never
     * cached, see [cacheKey].
     */
    fun warm(context: Context, icon: LauncherSignalIcon): Boolean {
        val application = icon as? LauncherSignalIcon.Application
        val info = application?.applicationInfo
        val key = application?.let(::cacheKey)
        if (info == null || key == null) {
            return false
        }
        return iconStates.containsKey(key) || remember(key, context.packageManager.getApplicationIcon(info))
    }

    private fun loadApplicationIcon(
        context: Context,
        icon: LauncherSignalIcon.Application,
        info: ApplicationInfo,
    ): Drawable {
        val key = cacheKey(icon)
        val cached = key?.let { iconStates[it] }?.newDrawable(context.resources)
        if (cached != null) {
            return cached
        }
        val loaded = context.packageManager.getApplicationIcon(info)
        if (key != null) {
            remember(key, loaded)
        }
        return loaded
    }

    // The install directory is in the key because an updated application installs to a new one, so it draws
    // its new icon. A package known only by name has no such discriminator - an uninstall and a reinstall under
    // one name would look the same - so it is never cached.
    private fun cacheKey(icon: LauncherSignalIcon.Application): String? =
        icon.applicationInfo?.sourceDir?.let { "${icon.packageName}|$it" }

    private fun remember(key: String, drawable: Drawable): Boolean {
        val state = drawable.constantState ?: return false
        iconStates[key] = state
        return true
    }
}

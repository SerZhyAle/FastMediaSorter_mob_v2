package com.sza.fastmediasorter.ui.launcher.signal

import android.content.pm.ApplicationInfo
import androidx.annotation.DrawableRes

/**
 * Where a signal's chip takes its picture from.
 *
 * A sealed choice rather than a resource id beside a nullable package name: exactly one of the two holds for
 * any signal, while a pair could express both or neither - states the strip would then have to invent a
 * precedence rule for, and a rule invented for an impossible state is a rule nobody maintains (S1465).
 */
sealed interface LauncherSignalIcon {

    /** An icon shipped with this app. Every signal the app produces about its own work uses this case. */
    data class Resource(@param:DrawableRes val res: Int) : LauncherSignalIcon

    /**
     * The icon of an installed application, resolved through the package manager at bind time rather than
     * carried as a `Drawable`: a signal outlives the row that draws it, and a loaded bitmap held in the model
     * would keep the emitting source alive with it.
     *
     * @param fallbackRes drawn when [packageName] is gone by the time the chip binds. An uninstall between a
     * signal's emission and its draw is ordinary, and it must leave a chip rather than a hole.
     * @param applicationInfo S4090: the info the system attached to the application's notification, drawn
     * from in place of a by-name lookup - the only route to the icon of a package that package-visibility
     * filtering hides. Null when the signal has none, which keeps the by-name path.
     */
    data class Application(
        val packageName: String,
        @param:DrawableRes val fallbackRes: Int,
        val applicationInfo: ApplicationInfo? = null,
    ) : LauncherSignalIcon
}

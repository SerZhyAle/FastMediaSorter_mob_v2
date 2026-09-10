package com.sza.fastmediasorter.core.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.core.theme.ColorThemePrefs
import timber.log.Timber
import java.util.Locale

/**
 * S2380: sets one of [ThemeTestHooks.VALUES] on the running debug process and answers with a
 * distinctive result code, so the host-side UI sweep can walk the theme matrix without restarting the
 * app and without polling the view tree for proof.
 *
 * Declared in the debug manifest rather than registered at runtime, for [CameraTestOpenReceiver]'s
 * reason: it must outlive every screen, so it has no symmetric lifecycle edge to unregister on.
 *
 * Usage:
 *   adb shell am broadcast -a com.sza.fastmediasorter.debug.THEME_TEST_SET --es theme DARK_GREEN \
 *     -p com.sza.fastmediasorter.debug
 */
class ThemeTestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val application = context?.applicationContext ?: return
        val value = intent?.getStringExtra(ThemeTestHooks.EXTRA_THEME)
            ?.trim()
            ?.uppercase(Locale.ROOT)
            .orEmpty()
        if (value !in ThemeTestHooks.VALUES) {
            // Named rather than folded to AUTO: a mistyped cell must be visible as a rejection, not
            // photographed as the default theme under the name the sweep asked for.
            Timber.w("ThemeTestReceiver: rejecting theme '$value' - not one of the nine values")
            acknowledge(ThemeTestHooks.ACK_REJECTED)
            return
        }
        // Read before setMode overwrites it - the recreate decision below needs the outgoing value.
        val previous = ColorThemePrefs.getMode(application)
        ColorThemePrefs.setMode(application, value)
        // A manifest receiver runs onReceive on the main thread, which is where AppCompat expects
        // setDefaultNightMode to be called from.
        ColorThemePrefs.applyMode(value)
        if (needsExplicitRecreate(previous, value)) {
            recreateForegroundScreen()
        }
        Timber.i("ThemeTestReceiver: color theme $previous -> $value")
        acknowledge(ThemeTestHooks.ACK_APPLIED)
    }

    /**
     * True when the two values pin the SAME night mode but differ - every accent variant against its
     * own base, e.g. DARK -> DARK_GREEN.
     *
     * AppCompat recreates the Activity stack only when the night mode actually changes, and the accent
     * is carried by `ColorThemePrefs.applyThemeOverlay`, which is read once in `Activity.onCreate`. So
     * on this path nothing on screen would move, and the sweep would capture the old accent under the
     * new name. The differing-night-mode path is left alone; AppCompat already recreates there.
     */
    private fun needsExplicitRecreate(previous: String, requested: String): Boolean =
        previous != requested &&
            ColorThemePrefs.toNightMode(previous) == ColorThemePrefs.toNightMode(requested)

    private fun recreateForegroundScreen() {
        val activity = DebugNotificationCenter.resumedActivity()
        if (activity == null) {
            // Not a failure: with no resumed Activity the next onCreate reads the new value anyway.
            Timber.i("ThemeTestReceiver: no resumed Activity - the accent lands on the next onCreate")
            return
        }
        activity.runOnUiThread { activity.recreate() }
    }

    private fun acknowledge(code: Int) {
        // Ordered because `am broadcast` sends it that way; guarded so an unordered sender cannot
        // crash the process.
        if (isOrderedBroadcast) {
            resultCode = code
        }
    }
}

package com.sza.fastmediasorter.ui.launcher

import android.content.Intent
import com.sza.fastmediasorter.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * S2811: the launcher desktop as the app's own start window.
 *
 * This entry carries no HOME intent-filter and is reachable only from the app's own startup decision,
 * so turning the setting on never offers the app to the system home-screen chooser. Everything the
 * desktop does - rendering, gestures, the idempotent first-entry seeding - is inherited unchanged.
 */
@AndroidEntryPoint
class LauncherStartWindowActivity : LauncherHomeActivity() {

    override val isHomeSurface: Boolean get() = false

    /**
     * The startup decision finished MainActivity to get here, so the task is empty behind this screen -
     * a bare finish() would drop the user onto the device home screen instead of back into the app
     * (device-measured 2026-09-09). The explicit intent carries no ACTION_MAIN, which is what keeps the
     * startup decision from redirecting straight back here.
     */
    override fun leaveDesktop() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

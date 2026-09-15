package com.sza.fastmediasorter.ui.launcher

import android.content.Intent
import com.sza.fastmediasorter.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/** App-only desktop entry with no HOME filter or home-task semantics. */
@AndroidEntryPoint
class LauncherStartWindowActivity : LauncherHomeActivity() {
    override val isHomeSurface: Boolean get() = false

    override fun leaveDesktop() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

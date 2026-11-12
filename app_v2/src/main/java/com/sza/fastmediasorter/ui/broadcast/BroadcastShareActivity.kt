package com.sza.fastmediasorter.ui.broadcast

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.ui.ProgramScreenGuardManager
import com.sza.fastmediasorter.core.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * S3060: Activity proxy. The QR, the link and the export live on [BroadcastControlActivity] now, so
 * anything still aimed at the old share screen - a shortcut, an external intent - lands there.
 *
 * The screen's own launch helpers and url/title/mode extras went with it: the control screen reads the
 * live descriptor from [com.sza.fastmediasorter.broadcast.BroadcastSourceController], so there is
 * nothing for a caller to hand over.
 */
@AndroidEntryPoint
class BroadcastShareActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject lateinit var programScreenGuard: ProgramScreenGuardManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        programScreenGuard.admit(this, InternalRouteCatalog.KEY_BROADCAST) {
            BroadcastControlActivity.launch(this)
            finish()
        }
    }
}

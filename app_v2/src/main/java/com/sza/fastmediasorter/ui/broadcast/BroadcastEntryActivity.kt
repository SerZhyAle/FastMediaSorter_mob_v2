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
 * S2818 / S3060: Activity proxy routing all broadcast entry surfaces (launcher shortcut, Quick Settings
 * tile, home-screen widget, Programs panel) to [BroadcastControlActivity].
 */
@AndroidEntryPoint
class BroadcastEntryActivity : AppCompatActivity() {
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

    companion object {
        const val ACTION_OPEN_BROADCAST_ENTRY = "com.sza.fastmediasorter.action.OPEN_BROADCAST_ENTRY"

        fun launch(context: Context) {
            BroadcastControlActivity.launch(context)
        }
    }
}

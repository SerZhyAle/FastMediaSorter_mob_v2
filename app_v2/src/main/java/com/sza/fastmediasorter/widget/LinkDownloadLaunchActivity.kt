package com.sza.fastmediasorter.widget

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.core.ui.ProgramScreenGuardManager
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.ui.main.helpers.MainLinkDownloadManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * S0912 - transparent, no-UI trampoline that hosts the "Link to download" confirmation dialog for the
 * app-launch panel. Delegates everything to the shared [MainLinkDownloadManager] (Rule 3 - no business
 * logic here) so the dialog and its clipboard-prefill behaviour stay identical to the main-window entry
 * point; finishes once the dialog is dismissed on any path (Ok, Cancel, tap-outside, back).
 */
@AndroidEntryPoint
class LinkDownloadLaunchActivity : AppCompatActivity() {
    // S2930: BaseActivity is generic over a ViewBinding, so the locale wrapper is applied directly here.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject lateinit var programScreenGuard: ProgramScreenGuardManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        programScreenGuard.admit(this, InternalRouteCatalog.KEY_LINK_DOWNLOAD) {
            MainLinkDownloadManager(this) { finish() }.show()
        }
    }
}

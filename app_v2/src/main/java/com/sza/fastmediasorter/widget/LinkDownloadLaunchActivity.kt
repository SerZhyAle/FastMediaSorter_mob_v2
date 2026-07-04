package com.sza.fastmediasorter.widget

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.ui.main.helpers.MainLinkDownloadManager

/**
 * S0912 - transparent, no-UI trampoline that hosts the "Link to download" confirmation dialog for the
 * app-launch panel. Delegates everything to the shared [MainLinkDownloadManager] (Rule 3 - no business
 * logic here) so the dialog and its clipboard-prefill behaviour stay identical to the main-window entry
 * point; finishes once the dialog is dismissed on any path (Ok, Cancel, tap-outside, back).
 */
class LinkDownloadLaunchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MainLinkDownloadManager(this) { finish() }.show()
    }
}

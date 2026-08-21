package com.sza.fastmediasorter.ui.systeminfo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.ui.systeminfo.helpers.SystemInfoDialogManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * A transparent host that shows the system-information report and closes with it.
 *
 * S1733: every entry in the panel route catalog is an `Intent`, and a launcher cell fires with no screen
 * of ours behind it, so "reachable from the launcher" needs a real Activity. Pointing the route at the
 * settings screen instead would defeat the ticket's own goal - reaching system information without going
 * into settings.
 *
 * Holds no logic beyond wiring: the report and the dialog both belong to [SystemInfoDialogManager], so
 * this entrance and the settings button cannot show different content.
 */
@AndroidEntryPoint
class SystemInfoActivity : AppCompatActivity() {

    @Inject lateinit var systemInfoDialogManager: SystemInfoDialogManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only on a fresh start: a rotation would otherwise re-read the whole report and raise a second
        // dialog over the first.
        if (savedInstanceState != null) return
        Timber.d("S1733: system info activity started")
        lifecycleScope.launch {
            val report = systemInfoDialogManager.gather(this@SystemInfoActivity)
            if (isFinishing || isDestroyed) return@launch
            val dialog = systemInfoDialogManager.show(this@SystemInfoActivity, report)
            // No dialog means the host was already going away; finishing here keeps the transparent
            // window from being left on screen with nothing drawn on it.
            if (dialog == null) finish() else dialog.setOnDismissListener { finish() }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent = Intent(context, SystemInfoActivity::class.java)
    }
}

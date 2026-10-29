package com.sza.fastmediasorter.ui.debug

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.LocaleHelper
import com.sza.fastmediasorter.ui.debug.helpers.DriveAppDataProbeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Debug-only screen for the DEVICE-EXCHANGE 8.2 item B premise check: one button writes a probe file into the
 * Drive `appDataFolder`, the other deletes it, and the result lines stay on screen. Reachable from adb:
 *
 *   adb shell am start -n com.sza.fastmediasorter.debug/com.sza.fastmediasorter.ui.debug.DriveAppDataProbeActivity \
 *     --es action write
 *
 * `--es action delete` deletes; with no extra the screen only opens.
 */
@AndroidEntryPoint
class DriveAppDataProbeActivity : AppCompatActivity() {

    // S2930: BaseActivity is generic over a ViewBinding, so the locale wrapper is applied directly here.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    @Inject lateinit var probe: DriveAppDataProbeManager

    private lateinit var report: TextView
    private lateinit var writeButton: MaterialButton
    private lateinit var deleteButton: MaterialButton
    private var running: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drive_appdata_probe)
        applySystemBarInsets(findViewById(R.id.driveProbeRoot))
        report = findViewById(R.id.tvDriveProbeReport)
        writeButton = findViewById(R.id.btnDriveProbeWrite)
        deleteButton = findViewById(R.id.btnDriveProbeDelete)
        writeButton.setOnClickListener { launchProbe { probe.write(this) } }
        deleteButton.setOnClickListener { launchProbe { probe.delete() } }
        if (savedInstanceState == null) {
            runRequested(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        runRequested(intent)
    }

    private fun runRequested(intent: Intent) {
        when (intent.getStringExtra(EXTRA_ACTION)) {
            ACTION_WRITE -> launchProbe { probe.write(this) }
            ACTION_DELETE -> launchProbe { probe.delete() }
        }
    }

    private fun launchProbe(block: suspend () -> String) {
        if (running?.isActive == true) return
        setBusy(true)
        report.setText(R.string.debug_drive_probe_running)
        running = lifecycleScope.launch {
            report.text = block()
            setBusy(false)
        }
    }

    private fun setBusy(busy: Boolean) {
        writeButton.isEnabled = !busy
        deleteButton.isEnabled = !busy
    }

    private fun applySystemBarInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
    }

    private companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_WRITE = "write"
        const val ACTION_DELETE = "delete"
    }
}

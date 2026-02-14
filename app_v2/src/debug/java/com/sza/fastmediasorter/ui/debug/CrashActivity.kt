package com.sza.fastmediasorter.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R

class CrashActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_REPORT = "extra_crash_report"

        fun createIntent(context: Context, report: String): Intent {
            return Intent(context, CrashActivity::class.java).apply {
                putExtra(EXTRA_REPORT, report)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash)

        val report = intent.getStringExtra(EXTRA_REPORT).orEmpty()
        findViewById<TextView>(R.id.tvCrashReport).text = report

        findViewById<MaterialButton>(R.id.btnCopyCrash).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("crash_report", report))
            Toast.makeText(this, R.string.debug_copy_done, Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.btnShareCrash).setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "FastMediaSorter debug crash report")
                putExtra(Intent.EXTRA_TEXT, report)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.debug_share_report)))
        }

        findViewById<MaterialButton>(R.id.btnRestartApp).setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(launchIntent)
            }
            finish()
            Runtime.getRuntime().exit(0)
        }

        findViewById<MaterialButton>(R.id.btnIgnoreCrash).setOnClickListener {
            finish()
        }
    }
}
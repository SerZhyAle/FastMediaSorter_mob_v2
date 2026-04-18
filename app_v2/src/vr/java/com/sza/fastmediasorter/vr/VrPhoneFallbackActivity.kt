package com.sza.fastmediasorter.vr

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Fallback screen shown when the VR APK is installed on a regular phone
 * (no XR runtime available). Advises the user to install the standard edition.
 */
@AndroidEntryPoint
class VrPhoneFallbackActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val paddingPx = dpToPx(24)

        // Simple programmatic layout — no XML needed for a minimal fallback screen
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        }

        val title = TextView(this).apply {
            text = getString(com.sza.fastmediasorter.R.string.vr_phone_fallback_title)
            textSize = 22f
            gravity = Gravity.CENTER
            contentDescription = getString(com.sza.fastmediasorter.R.string.vr_phone_fallback_title)
        }

        val message = TextView(this).apply {
            text = getString(com.sza.fastmediasorter.R.string.vr_phone_fallback_message)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(16), 0, dpToPx(24))
        }

        val openStandardButton = Button(this).apply {
            text = getString(com.sza.fastmediasorter.R.string.vr_phone_fallback_open_standard)
            setOnClickListener { openStandardEdition() }
        }

        val closeButton = Button(this).apply {
            text = getString(com.sza.fastmediasorter.R.string.vr_phone_fallback_close)
            setOnClickListener { finish() }
        }

        layout.addView(title)
        layout.addView(message)
        layout.addView(openStandardButton)
        layout.addView(closeButton)

        setContentView(layout)
    }

    /**
     * Try to launch the standard edition of FastMediaSorter via package name.
     * Falls back to Play Store listing if the app is not installed.
     */
    private fun openStandardEdition() {
        val standardPackage = "com.sza.fastmediasorter"
        val launchIntent = packageManager.getLaunchIntentForPackage(standardPackage)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            // Standard edition not installed — open Play Store listing
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$standardPackage"))
                )
            } catch (_: android.content.ActivityNotFoundException) {
                startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$standardPackage"))
                )
            }
        }
        finish()
    }

    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
}

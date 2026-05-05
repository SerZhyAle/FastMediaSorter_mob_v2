package com.sza.fastmediasorter.ui.main.helpers

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.compat.ChromeOsCompat
import timber.log.Timber

class MainChromeOsBannerManager(private val activity: AppCompatActivity) {

    fun showIfNeeded() {
        if (!ChromeOsCompat.isChromeOs(activity)) return
        val prefs = activity.getSharedPreferences("fms_prefs", Context.MODE_PRIVATE)
        val key = activity.getString(R.string.chromeos_banner_shown_pref_key)
        if (prefs.getBoolean(key, false)) return
        Timber.i("MainChromeOsBannerManager: showing Chrome OS first-launch banner")
        val rootView = activity.window.decorView.rootView
        val text = "${activity.getString(R.string.chromeos_banner_title)}: ${activity.getString(R.string.chromeos_banner_body)}"
        Snackbar.make(rootView, text, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.chromeos_banner_action) { /* dismissed by action */ }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    prefs.edit().putBoolean(key, true).apply()
                }
            })
            .show()
    }
}

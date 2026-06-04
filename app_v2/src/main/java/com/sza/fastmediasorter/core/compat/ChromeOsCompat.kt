package com.sza.fastmediasorter.core.compat

import android.content.Context
import android.os.Build
import android.os.Environment
import timber.log.Timber

object ChromeOsCompat {

    @Volatile private var _isChromeOs: Boolean? = null

    fun isChromeOs(context: Context): Boolean {
        _isChromeOs?.let { return it }
        // Single ARC++ detection site for the whole app (ADR-3). Cover both the
        // standard Chromebook signal and the managed-device variant, so managed
        // Chromebooks that only report device_management are detected too.
        val pm = context.packageManager
        val result = pm.hasSystemFeature("org.chromium.arc") ||
            pm.hasSystemFeature("org.chromium.arc.device_management")
        _isChromeOs = result
        Timber.d("ChromeOsCompat: isChromeOs=$result")
        return result
    }

    fun needsSafFolderPicker(context: Context): Boolean {
        if (isChromeOs(context)) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return !Environment.isExternalStorageManager()
        }
        return false
    }
}

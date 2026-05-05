package com.sza.fastmediasorter.core.compat

import android.content.Context
import android.os.Build
import android.os.Environment
import timber.log.Timber

object ChromeOsCompat {

    @Volatile private var _isChromeOs: Boolean? = null

    fun isChromeOs(context: Context): Boolean {
        _isChromeOs?.let { return it }
        val result = context.packageManager.hasSystemFeature("org.chromium.arc")
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

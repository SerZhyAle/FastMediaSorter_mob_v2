package com.sza.fastmediasorter.utils

import android.util.Log
import com.sza.fastmediasorter.BuildConfig

object Logger {
    fun d(
        tag: String,
        message: String,
    ) {
        if (BuildConfig.ENABLE_LOGGING) {
            Log.d(tag, message)
        }
    }

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (BuildConfig.ENABLE_LOGGING) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
        }
    }

    fun w(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (BuildConfig.ENABLE_LOGGING) {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
        }
    }

    fun i(
        tag: String,
        message: String,
    ) {
        if (BuildConfig.ENABLE_LOGGING) {
            Log.i(tag, message)
        }
    }
}

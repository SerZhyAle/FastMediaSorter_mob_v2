package com.sza.fastmediasorter.core.util

import android.os.Build
import java.net.HttpCookie

/**
 * The HttpOnly flag of [HttpCookie], readable and writable on every supported API level.
 *
 * `isHttpOnly` / `setHttpOnly` became public platform API only in API 24, and the legacy flavor ships
 * to API 23, where either call is a `NoSuchMethodError`. Below 24 the flag reads as false and a write is
 * dropped: the cookie still travels, it just loses a hint that only a browser script engine honours.
 */
var HttpCookie.httpOnlyCompat: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isHttpOnly
    set(value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            isHttpOnly = value
        }
    }

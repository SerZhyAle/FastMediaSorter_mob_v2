package com.sza.fastmediasorter.core.util

import android.app.PendingIntent
import android.app.RecoverableSecurityException
import android.os.Build

/**
 * The consent intent of an API 29 [RecoverableSecurityException], or null for any other throwable.
 *
 * The type is matched behind the version check instead of in a catch clause of its own, because a
 * handler naming a class that does not exist below Q is exactly what lint NewApi refuses.
 */
fun Throwable.recoverableSecurityActionIntent(): PendingIntent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && this is RecoverableSecurityException) {
        userAction.actionIntent
    } else {
        null
    }

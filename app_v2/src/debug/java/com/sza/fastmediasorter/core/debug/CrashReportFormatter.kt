package com.sza.fastmediasorter.core.debug

import com.sza.fastmediasorter.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReportFormatter {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun buildReport(thread: Thread, throwable: Throwable): String {
        val sb = StringBuilder(4096)
        sb.append("FastMediaSorter DEBUG CRASH\n")
        sb.append("Timestamp: ${timeFormat.format(Date())}\n")
        // S1685: Thread.getId() is deprecated in newer JDKs in favour of threadId(), which Android does
        // not expose at our minSdk - the id stays and the suppression records why.
        @Suppress("DEPRECATION")
        val threadId = thread.id
        sb.append("Thread: ${thread.name} ($threadId)\n")
        sb.append("Exception: ${throwable.javaClass.name}\n")
        sb.append("Message: ${throwable.message ?: "<no message>"}\n\n")

        sb.append("App:\n")
        sb.append("  Version Name: ${BuildConfig.VERSION_NAME}\n")
        sb.append("  Version Code: ${BuildConfig.VERSION_CODE}\n")
        sb.append("  Build Type: ${BuildConfig.BUILD_TYPE}\n")
        sb.append("  Flavor: ${BuildConfig.FLAVOR}\n\n")

        sb.append("Device:\n")
        sb.append("  Manufacturer: ${android.os.Build.MANUFACTURER}\n")
        sb.append("  Brand: ${android.os.Build.BRAND}\n")
        sb.append("  Model: ${android.os.Build.MODEL}\n")
        sb.append("  Device: ${android.os.Build.DEVICE}\n")
        sb.append("  Product: ${android.os.Build.PRODUCT}\n")
        sb.append("  Android: ${android.os.Build.VERSION.RELEASE}\n")
        sb.append("  SDK: ${android.os.Build.VERSION.SDK_INT}\n\n")

        sb.append("Stacktrace:\n")
        sb.append(android.util.Log.getStackTraceString(throwable))
        return sb.toString()
    }
}
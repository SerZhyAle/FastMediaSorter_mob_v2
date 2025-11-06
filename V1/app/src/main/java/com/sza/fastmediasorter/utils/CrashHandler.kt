package com.sza.fastmediasorter.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val context: Context,
) : Thread.UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    private val crashLogFile = File(context.getExternalFilesDir(null), "crash_log.txt")

    companion object {
        @Volatile
        private var instance: CrashHandler? = null

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = CrashHandler(context.applicationContext)
                        Thread.setDefaultUncaughtExceptionHandler(instance)
                    }
                }
            }
        }
    }

    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        try {
            val crashInfo = buildCrashInfo(thread, throwable)
            saveCrashLog(crashInfo)
            showCrashDialog(crashInfo)
        } catch (e: Exception) {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun buildCrashInfo(
        thread: Thread,
        throwable: Throwable,
    ): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)

        pw.println("=== CRASH REPORT ===")
        pw.println("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        pw.println("Thread: ${thread.name}")
        pw.println()
        pw.println("Device Info:")
        pw.println("  Manufacturer: ${Build.MANUFACTURER}")
        pw.println("  Model: ${Build.MODEL}")
        pw.println("  Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        pw.println()
        pw.println("Exception:")
        throwable.printStackTrace(pw)
        pw.println()

        return sw.toString()
    }

    private fun saveCrashLog(crashInfo: String) {
        try {
            crashLogFile.appendText(crashInfo)
            crashLogFile.appendText("\n\n" + "=".repeat(80) + "\n\n")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showCrashDialog(crashInfo: String) {
        val activity =
            getTopActivity() ?: run {
                defaultHandler?.uncaughtException(Thread.currentThread(), Throwable(crashInfo))
                return
            }

        activity.runOnUiThread {
            try {
                val textView =
                    TextView(activity).apply {
                        text = crashInfo
                        setTextIsSelectable(true)
                        setPadding(32, 32, 32, 32)
                        textSize = 12f
                    }

                val scrollView =
                    ScrollView(activity).apply {
                        addView(textView)
                    }

                AlertDialog
                    .Builder(activity)
                    .setTitle("Application Crashed")
                    .setMessage("Log saved to:\n${crashLogFile.absolutePath}")
                    .setView(scrollView)
                    .setPositiveButton("Copy & Exit") { _, _ ->
                        copyToClipboard(activity, crashInfo)
                        finishApp(activity)
                    }.setNegativeButton("Exit") { _, _ ->
                        finishApp(activity)
                    }.setCancelable(false)
                    .show()
            } catch (e: Exception) {
                defaultHandler?.uncaughtException(Thread.currentThread(), Throwable(crashInfo))
                finishApp(activity)
            }
        }
    }

    private fun getTopActivity(): Activity? =
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentActivityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null)
            val activities = activityThreadClass.getMethod("getActivities").invoke(currentActivityThread) as? Map<*, *>

            activities
                ?.values
                ?.firstOrNull { record ->
                    val activityField = record?.javaClass?.getDeclaredField("activity")
                    activityField?.isAccessible = true
                    val activity = activityField?.get(record) as? Activity
                    activity != null && !activity.isFinishing
                }?.let { record ->
                    val activityField = record.javaClass.getDeclaredField("activity")
                    activityField.isAccessible = true
                    activityField.get(record) as? Activity
                }
        } catch (e: Exception) {
            null
        }

    private fun copyToClipboard(
        context: Context,
        text: String,
    ) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Crash Log", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Crash log copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun finishApp(activity: Activity) {
        activity.finish()
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(10)
    }
}

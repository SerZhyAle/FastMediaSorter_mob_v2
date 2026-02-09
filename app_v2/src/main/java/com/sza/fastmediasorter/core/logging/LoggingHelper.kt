package com.sza.fastmediasorter.core.logging

import android.content.Context
import com.sza.fastmediasorter.BuildConfig
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper for initializing Timber logging with file support.
 * Handles both console logging (Logcat) and file logging for debugging.
 */
object LoggingHelper {
    
    /**
     * Initialize Timber logging.
     * DEBUG build: All logs to Logcat + file (for debugging without ADB)
     * RELEASE build: Only warnings and errors (w/e) - no debug spam
     */
    fun initialize(context: Context) {
        if (BuildConfig.DEBUG) {
            // Debug build: log everything to Logcat
            Timber.plant(Timber.DebugTree())
            // Also log to file for debugging without ADB connection
            Timber.plant(FileLoggingTree(context))
        } else {
            // Release build: only warnings and errors
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    // Filter: only WARN and ERROR in release
                    if (priority >= android.util.Log.WARN) {
                        android.util.Log.println(priority, tag ?: "FastMediaSorter", message)
                        t?.let { android.util.Log.println(priority, tag ?: "FastMediaSorter", android.util.Log.getStackTraceString(it)) }
                    }
                }
            })
        }
    }
    
    /**
     * Custom Timber Tree that writes logs to a file.
     * File location: /storage/emulated/0/Android/data/com.sza.fastmediasorter.debug/files/logs/
     * 
     * Logs are rotated: keeps last 5 log files, max 5MB each.
     * File naming: fastmediasorter_YYYYMMDD_HHmmss.log
     */
    private class FileLoggingTree(context: Context) : Timber.Tree() {
        
        private val logDir: File = File(context.getExternalFilesDir(null), "logs")
        private val maxFileSize = 5 * 1024 * 1024L // 5 MB
        private val maxLogFiles = 5
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        
        @Volatile
        private var currentLogFile: File? = null
        
        @Volatile
        private var printWriter: PrintWriter? = null
        
        init {
            try {
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                rotateLogFilesIfNeeded()
                openNewLogFile()
            } catch (e: Exception) {
                android.util.Log.e("FileLoggingTree", "Failed to initialize file logging", e)
            }
        }
        
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            try {
                // Downgrade "unimportant" errors to WARN
                var effectivePriority = priority
                if (priority == android.util.Log.ERROR && isUnimportantError(t)) {
                    effectivePriority = android.util.Log.WARN
                }

                val priorityChar = when (effectivePriority) {
                    android.util.Log.VERBOSE -> 'V'
                    android.util.Log.DEBUG -> 'D'
                    android.util.Log.INFO -> 'I'
                    android.util.Log.WARN -> 'W'
                    android.util.Log.ERROR -> 'E'
                    android.util.Log.ASSERT -> 'A'
                    else -> '?'
                }
                
                val timestamp = dateFormat.format(Date())
                
                synchronized(this) {
                    // Check if file needs rotation
                    currentLogFile?.let { file ->
                        if (file.exists() && file.length() > maxFileSize) {
                            closeCurrentFile()
                            rotateLogFilesIfNeeded()
                            openNewLogFile()
                        }
                    }
                    
                    if (effectivePriority == android.util.Log.WARN) {
                        // Warnings: single line, compact exception info
                        var logLine = "$timestamp $priorityChar/${tag ?: "App"}: $message"
                        if (t != null) {
                            logLine += " [${t.javaClass.simpleName}: ${t.message}]"
                        }
                        printWriter?.println(logLine)
                    } else {
                        // Other levels: standard format with stacktrace for errors
                        val logLine = "$timestamp $priorityChar/${tag ?: "App"}: $message"
                        printWriter?.println(logLine)
                        t?.let { throwable ->
                            printWriter?.println(getCompactStackTrace(throwable))
                        }
                    }
                    printWriter?.flush()
                }
            } catch (e: Exception) {
                // Silently fail - don't cause app crash due to logging
            }
        }

        private fun isUnimportantError(t: Throwable?): Boolean {
            if (t == null) return false
            val className = t.javaClass.name
            val message = t.message ?: ""
            
            return className.contains("com.bumptech.glide") || 
                   message.contains("setDataSource failed") ||
                   message.contains("File unsuitable for memory mapping") ||
                   (t is RuntimeException && message.contains("setDataSource"))
        }
        
        /**
         * Compact stacktrace for known verbose errors (Glide, MediaMetadataRetriever)
         */
        private fun getCompactStackTrace(t: Throwable): String {
            val className = t.javaClass.name
            val message = t.message ?: ""
            
            // Glide video decoder errors - compress to single line
            if (className.contains("com.bumptech.glide") || 
                message.contains("setDataSource failed") ||
                message.contains("File unsuitable for memory mapping") ||
                t is RuntimeException && message.contains("setDataSource")) {
                return "[$className: $message]"
            }
            
            // Full stacktrace for everything else
            return android.util.Log.getStackTraceString(t)
        }
        
        private fun openNewLogFile() {
            val fileName = "fastmediasorter_${fileNameFormat.format(Date())}.log"
            currentLogFile = File(logDir, fileName)
            printWriter = PrintWriter(FileWriter(currentLogFile, true), true)
            printWriter?.println("=== Log started: ${dateFormat.format(Date())} ===")
            printWriter?.println("=== App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ===")
            printWriter?.flush()
        }
        
        private fun closeCurrentFile() {
            try {
                printWriter?.println("=== Log closed: ${dateFormat.format(Date())} ===")
                printWriter?.close()
            } catch (e: Exception) {
                // Ignore
            }
            printWriter = null
        }
        
        private fun rotateLogFilesIfNeeded() {
            try {
                val logFiles = logDir.listFiles { file -> 
                    file.isFile && file.name.startsWith("fastmediasorter_") && file.name.endsWith(".log")
                }?.sortedByDescending { it.lastModified() } ?: return
                
                // Keep only last maxLogFiles
                if (logFiles.size >= maxLogFiles) {
                    logFiles.drop(maxLogFiles - 1).forEach { it.delete() }
                }
            } catch (e: Exception) {
                // Ignore rotation errors
            }
        }
    }
}

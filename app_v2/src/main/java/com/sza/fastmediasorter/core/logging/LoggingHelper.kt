package com.sza.fastmediasorter.core.logging

import android.content.Context
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.debug.StrictModeHelper
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

    // Renderer diagnostics tags (D.7 - Stabilization)
    private const val TAG_RENDERER = "StaticImageRenderer"

    /** Retained instance of FileLoggingTree to expose log file access. */
    private var fileLoggingTree: FileLoggingTree? = null

    /** Returns all log files managed by the file logging tree. */
    fun getLogFiles(): List<File> = fileLoggingTree?.getLogFiles() ?: emptyList()

    private var previousCrashHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * Install a global uncaught exception handler that writes crash reports to a dedicated
     * log file and flushes the current session log before yielding to the system handler.
     * Safe to call multiple times - installs only once. Call early in attachBaseContext.
     */
    fun installCrashHandler() {
        if (previousCrashHandler != null) return
        previousCrashHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            fileLoggingTree?.writeCrashSynchronously(thread, throwable)
            previousCrashHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Persist a fatal crash report without delegating to the uncaught-exception handler chain.
     * Debug-only crash UIs use this path so they can keep their own shutdown flow while still
     * producing the same on-disk crash artifacts as the standard handler.
     */
    fun persistFatalCrash(thread: Thread, throwable: Throwable) {
        fileLoggingTree?.writeCrashSynchronously(thread, throwable)
    }

    /**
     * Returns true if crash report files from any previous session exist on disk.
     * Use at startup to warn the user to export logs.
     */
    fun hasPreviousCrash(): Boolean = fileLoggingTree?.hasCrashFiles() ?: false

    private const val TAG_PREFETCH = "PrefetchQueue"
    
    /**
     * Log renderer state transition.
     * @param fromState Previous render state (e.g., "Idle", "Loading")
     * @param toState New render state
     * @param trigger What caused the transition (e.g., "render()", "swap()")
     */
    fun logRendererStateTransition(fromState: String, toState: String, trigger: String) {
        Timber.tag(TAG_RENDERER).d("State: $fromState -> $toState [trigger=$trigger]")
    }
    
    /**
     * Log prefetch queue operation.
     * @param operation Operation type (e.g., "offer", "poll", "drop")
     * @param target Target file name or path
     * @param reason Optional reason for the operation
     */
    fun logPrefetch(operation: String, target: String, reason: String? = null) {
        val msg = if (reason != null) {
            "$operation: $target (reason: $reason)"
        } else {
            "$operation: $target"
        }
        Timber.tag(TAG_PREFETCH).d(msg)
    }
    
    /**
     * Log renderer fallback to legacy path.
     * @param reason Why fallback occurred
     * @param context Additional context (file name, state, etc.)
     */
    fun logRendererFallback(reason: String, context: String? = null) {
        val msg = if (context != null) {
            "Fallback: $reason [context=$context]"
        } else {
            "Fallback: $reason"
        }
        Timber.tag(TAG_RENDERER).w(msg)
    }
    
    /**
     * Initialize Timber logging.
     * DEBUG build: All logs to Logcat + file (for debugging without ADB)
     * RELEASE build: Only warnings and errors (w/e) - no debug spam
     */
    fun initialize(context: Context) {
        if (BuildConfig.DEBUG) {
            // Debug build: log to Logcat with system noise filtering
            Timber.plant(object : Timber.DebugTree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    // Filter out noisy system logs
                    if (shouldFilterLog(tag, message)) {
                        return
                    }
                    super.log(priority, tag, message, t)
                }
                
                private fun shouldFilterLog(tag: String?, message: String?): Boolean {
                    if (tag == null || message == null) return false
                    
                    // Filter SurfaceView updateSurface debug logs
                    if (tag.contains("SurfaceView") && message.contains("updateSurface")) {
                        return true
                    }
                    
                    // Filter other noisy system logs
                    if ((tag.contains("ExoPlayer") || tag.contains("MediaCodec")) && 
                        (message.contains("updateSurface") || message.contains("has no frame"))) {
                        return true
                    }
                    
                    return false
                }
            })
            // Also log to file for debugging without ADB connection
            val fileTree = FileLoggingTree(context)
            fileLoggingTree = fileTree
            Timber.plant(fileTree)
        } else {
            // Release build: WARN/ERROR to Logcat
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    // Filter: only WARN and ERROR in release
                    if (priority >= android.util.Log.WARN) {
                        android.util.Log.println(priority, tag ?: "FastMediaSorter", message)
                        t?.let { android.util.Log.println(priority, tag ?: "FastMediaSorter", android.util.Log.getStackTraceString(it)) }
                    }
                }
            })
            // Also persist WARN/ERROR to file so users can share diagnostics without ADB
            val fileTree = FileLoggingTree(context, minPriority = android.util.Log.WARN)
            fileLoggingTree = fileTree
            Timber.plant(fileTree)
        }
    }
    
    /**
     * Custom Timber Tree that writes logs to a file.
     * File location: /storage/emulated/0/Android/data/com.sza.fastmediasorter.debug/files/logs/
     * 
     * Logs are rotated: keeps last 5 log files, max 5MB each.
     * File naming: fastmediasorter_YYYYMMDD_HHmmss.log
     */
    private class FileLoggingTree(
        context: Context,
        /** Minimum log priority to persist. VERBOSE by default (log everything). */
        private val minPriority: Int = android.util.Log.VERBOSE
    ) : Timber.Tree() {
        
        private val logDir: File = File(
            // getExternalFilesDir() can return null if external storage is unmounted
            // (e.g. on first boot, encrypted storage not yet ready, or no SD card).
            // Fall back to internal storage so log files are always created.
            context.getExternalFilesDir(null) ?: context.filesDir,
            "logs"
        )
        private val maxFileSize = 5 * 1024 * 1024L // 5 MB
        private val maxLogFiles = 5
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        private val fileNameFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        
        @Volatile
        private var currentLogFile: File? = null
        
        @Volatile
        private var printWriter: PrintWriter? = null
        
        init {
            // Wrap file I/O operations in StrictModeHelper
            StrictModeHelper.allowDiskIO {
                try {
                    if (!logDir.exists()) {
                        logDir.mkdirs()
                    }
                    rotateLogFilesIfNeeded()
                    openNewLogFile()
                } catch (e: Exception) {
                    Timber.tag("FileLoggingTree").e(e, "Failed to initialize file logging")
                }
            }
        }
        
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority < minPriority) return  // Skip below threshold (e.g. VERBOSE/DEBUG in release)
            // Wrap file I/O in StrictModeHelper to avoid violations
            // File logging is an expected debug operation, not a bug
            // Use allowDiskIO (not just allowDiskWrites) because we also check file.exists() and file.length()
            StrictModeHelper.allowDiskIO {
                try {
                    // Sanitize message for security
                    val sanitizedMessage = com.sza.fastmediasorter.core.security.SecretMasker.sanitize(message)

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
                            var logLine = "$timestamp $priorityChar/${tag ?: "App"}: $sanitizedMessage"
                            if (t != null) {
                                logLine += " [${t.javaClass.simpleName}: ${t.message}]"
                            }
                            printWriter?.println(logLine)
                        } else {
                            // Other levels: standard format with stacktrace for errors
                            val logLine = "$timestamp $priorityChar/${tag ?: "App"}: $sanitizedMessage"
                            printWriter?.println(logLine)
                            t?.let { throwable ->
                                val stackTrace = getCompactStackTrace(throwable)
                                val sanitizedStackTrace = com.sza.fastmediasorter.core.security.SecretMasker.sanitize(stackTrace)
                                printWriter?.println(sanitizedStackTrace)
                            }
                        }
                        printWriter?.flush()
                    }
                } catch (e: Exception) {
                    // Silently fail - don't cause app crash due to logging
                }
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

        /**
         * Write crash report synchronously without going through Timber.
         * Called from UncaughtExceptionHandler - must not throw, must not use coroutines.
         * Creates a dedicated crash file AND marks the current session log.
         */
        fun writeCrashSynchronously(thread: Thread, throwable: Throwable) {
            try {
                val now = Date()
                val timestamp = dateFormat.format(now)
                val crashFile = File(logDir, "fastmediasorter_crash_${fileNameFormat.format(now)}.log")

                PrintWriter(FileWriter(crashFile, false), true).use { pw ->
                    pw.println("=== CRASH REPORT: $timestamp ===")
                    pw.println("=== App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ===")
                    pw.println("=== Thread: ${thread.name} [id=${thread.id}] ===")
                    pw.println("=== ${throwable.javaClass.name}: ${throwable.message} ===")
                    pw.println(android.util.Log.getStackTraceString(throwable))
                    pw.println("=== END CRASH REPORT ===")
                }

                // Also mark the current session log for correlation
                synchronized(this) {
                    printWriter?.apply {
                        println("$timestamp E/CrashHandler: *** FATAL CRASH *** ${throwable.javaClass.simpleName}: ${throwable.message}")
                        println(android.util.Log.getStackTraceString(throwable))
                        flush()
                    }
                }
            } catch (_: Exception) {
                // Never interfere with crash flow
            }
        }

        /** Returns true if crash files from any previous session exist on disk. */
        fun hasCrashFiles(): Boolean =
            logDir.listFiles { f ->
                f.isFile && f.name.startsWith("fastmediasorter_crash_")
            }?.isNotEmpty() ?: false

        fun getLogDir(): File = logDir

        fun getLogFiles(): List<File> {
            return logDir.listFiles { file ->
                file.isFile && file.name.startsWith("fastmediasorter_") && file.name.endsWith(".log")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()
        }
    }
}

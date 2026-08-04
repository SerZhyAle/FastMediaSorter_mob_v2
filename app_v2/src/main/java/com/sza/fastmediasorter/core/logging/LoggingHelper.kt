package com.sza.fastmediasorter.core.logging

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Helper for initializing Timber logging with file support.
 * Handles both console logging (Logcat) and file logging for debugging.
 */
object LoggingHelper {

    // Renderer diagnostics tags (D.7 - Stabilization)
    private const val TAG_RENDERER = "StaticImageRenderer"

    /** Retained instance of FileLoggingTree to expose log file access. */
    private var fileLoggingTree: FileLoggingTree? = null

    /**
     * Application context kept from [initialize] so the S1357 opt-in can be read here instead of
     * being threaded through every caller of [updateDebugMirrorTargetFromPath].
     */
    private var appContext: Context? = null

    /** Returns all log files managed by the file logging tree. */
    fun getLogFiles(): List<File> = fileLoggingTree?.getLogFiles() ?: emptyList()

    /**
     * Debug-only hint: mirror the active session log into the currently opened local file folder
     * so reproductions from another machine can be shared without digging into app sandbox paths.
     *
     * S1357: gated here rather than at the call site because this is the only entry into the
     * mechanism, so every present and future caller inherits the opt-in.
     */
    fun updateDebugMirrorTargetFromPath(path: String) {
        if (!BuildConfig.DEBUG) return
        val context = appContext ?: return
        // First SharedPreferences read is disk I/O and this arrives on the viewer thread.
        val enabled = StrictModeHelper.allowDiskIO { DebugLogMirrorPrefs.isEnabled(context) }
        Timber.d("S1357: mirror target request, opt-in=$enabled")
        if (enabled) {
            fileLoggingTree?.updateDebugMirrorTargetFromPath(path)
        }
    }

    /**
     * Stop mirroring into the folder already selected this session. Without this, switching the
     * opt-in off would keep feeding the target chosen before it until the process restarts.
     */
    fun clearDebugMirrorTarget() {
        fileLoggingTree?.clearDebugMirrorTarget()
    }

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

    /** Newest uncaught-crash log file, or null if none. [getLogFiles] is already sorted newest-first. */
    fun getLatestCrashFile(): File? =
        getLogFiles().firstOrNull { it.name.startsWith("fastmediasorter_crash_") && it.name.endsWith(".log") }

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
        appContext = context.applicationContext
        // S1253: Robolectric boots the real Application, which used to plant the FILE tree in
        // every unit-test class - the "fms-log-io" executor then churned file IO and memory for
        // thousands of tests and was the thread the JVM's OOM storm named right before the
        // instrument-agent assertion killed the worker with exit value 10. Unit tests keep the
        // logcat tree only; devices are unaffected (fingerprint is never "robolectric" there).
        if (android.os.Build.FINGERPRINT == "robolectric") {
            Timber.plant(Timber.DebugTree())
            return
        }
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
                        // S1248: Timber's prepareLog already appended the throwable's stack trace to
                        // [message]; printing it again doubled every trace in logcat.
                        android.util.Log.println(priority, tag ?: "FastMediaSorter", message)
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

        /** S1310: hard cap for the debug mirror file, which appends across source-log rotations. */
        private val debugMirrorMaxBytes = 2 * maxFileSize
        private val maxLogFiles = 5
        private val dateFormat = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        }
        private val fileNameFormat = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        }
        private fun formatTs(d: Date): String = dateFormat.get()!!.format(d)
        private fun formatFileName(d: Date): String = fileNameFormat.get()!!.format(d)
        private val debugMirrorFileName = "fastmediasorter_debug_live.log"

        /**
         * S1203: one thread owns both the file append and the 10-second debug mirror.
         *
         * They used to run on two different threads that serialised on this object's monitor, so a
         * caller appending a single line waited out an entire mirror copy - on a slow emulator that
         * froze the main thread long enough to raise repeated ANRs. Sharing one FIFO executor means
         * the two never contend for the monitor at all.
         */
        private val logIoExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "fms-log-io").apply { isDaemon = true }
        }

        // Callers no longer block on the write, so nothing throttles them either. These bound the
        // queue that replaced that backpressure and keep the loss visible instead of silent.
        private val pendingWrites = AtomicInteger(0)
        private val droppedWrites = AtomicInteger(0)
        
        @Volatile
        private var currentLogFile: File? = null
        
        @Volatile
        private var printWriter: PrintWriter? = null

        @Volatile
        private var debugMirrorFile: File? = null

        @Volatile
        private var debugMirrorSourcePath: String? = null

        @Volatile
        private var debugMirrorSourceOffsetBytes: Long = 0L
        
        init {
            // Wrap file I/O operations in StrictModeHelper
            StrictModeHelper.allowDiskIO {
                try {
                    if (!logDir.exists()) {
                        logDir.mkdirs()
                    }
                    rotateLogFilesIfNeeded()
                    openNewLogFile()
                    if (BuildConfig.DEBUG) {
                        startDebugMirrorScheduler()
                    }
                } catch (e: Exception) {
                    Timber.tag("FileLoggingTree").e(e, "Failed to initialize file logging")
                }
            }
        }

        fun updateDebugMirrorTargetFromPath(path: String) {
            val targetDir = resolveDebugMirrorDirectory(path) ?: return
            StrictModeHelper.allowDiskIO {
                try {
                    if (!targetDir.exists() || !targetDir.isDirectory) return@allowDiskIO
                    val newMirrorFile = File(targetDir, debugMirrorFileName)
                    var targetChanged = false
                    synchronized(this) {
                        if (debugMirrorFile?.absolutePath != newMirrorFile.absolutePath) {
                            debugMirrorFile = newMirrorFile
                            debugMirrorSourcePath = null
                            debugMirrorSourceOffsetBytes = 0L
                            targetChanged = true
                        }
                    }
                    if (targetChanged) {
                        val sanitizedTarget = com.sza.fastmediasorter.core.security.SecretMasker
                            .sanitize(newMirrorFile.absolutePath)
                        val notice =
                            "${formatTs(Date())} I/DebugLogMirror: mirroring session log to $sanitizedTarget"
                        synchronized(this) {
                            printWriter?.println(notice)
                            printWriter?.flush()
                        }
                        flushDebugMirrorDelta()
                    }
                } catch (_: Exception) {
                    // Mirror failures must never break the active session logger.
                }
            }
        }

        /** Drops the current mirror target and its copy offset, so nothing more is appended to it. */
        fun clearDebugMirrorTarget() {
            synchronized(this) {
                debugMirrorFile = null
                debugMirrorSourcePath = null
                debugMirrorSourceOffsetBytes = 0L
            }
        }

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority < minPriority) return  // Skip below threshold (e.g. VERBOSE/DEBUG in release)
            // S1203: the timestamp is taken on the calling thread so the file keeps call order rather
            // than drain order; sanitising, formatting and the write itself all move to the log I/O
            // thread, so no Timber call can put a caller on the disk or on the mirror's monitor.
            val timestamp = formatTs(Date())
            if (pendingWrites.incrementAndGet() > MAX_PENDING_WRITES) {
                pendingWrites.decrementAndGet()
                droppedWrites.incrementAndGet()
                return
            }
            try {
                logIoExecutor.execute {
                    try {
                        writeEntry(timestamp, priority, tag, message, t)
                    } finally {
                        pendingWrites.decrementAndGet()
                    }
                }
            } catch (_: RejectedExecutionException) {
                // The executor is shutting down with the process; account for the lost line rather
                // than let a logging call take the app down on its way out.
                pendingWrites.decrementAndGet()
                droppedWrites.incrementAndGet()
            }
        }

        /** Runs on the log I/O thread: formats one queued entry and appends it to the session file. */
        private fun writeEntry(timestamp: String, priority: Int, tag: String?, message: String, t: Throwable?) {
            // Wrap file I/O in StrictModeHelper to avoid violations
            // File logging is an expected debug operation, not a bug
            // Use allowDiskIO (not just allowDiskWrites) because we also check file.exists() and file.length()
            StrictModeHelper.allowDiskIO {
                try {
                    // S1248: Timber's prepareLog glues the throwable's stack trace onto [message]
                    // before any tree sees it. This tree renders the trace itself (compacted for
                    // known-noisy errors, single-line for warnings), so the glued copy must come
                    // off first - leaving it doubled every ERROR trace in the session file.
                    val bareMessage = stripTimberAppendedTrace(message, t)
                    // Sanitize message for security
                    val sanitizedMessage = com.sza.fastmediasorter.core.security.SecretMasker.sanitize(bareMessage)

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
                    
                    synchronized(this) {
                        // Check if file needs rotation
                        currentLogFile?.let { file ->
                            if (file.exists() && file.length() > maxFileSize) {
                                closeCurrentFile()
                                rotateLogFilesIfNeeded()
                                openNewLogFile()
                            }
                        }
                        reportDroppedWrites(timestamp)

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

        /**
         * S1203: announces the entries the queue cap had to drop, so a burst that outran the disk
         * leaves a mark in the log instead of a silent gap. Caller holds the file monitor.
         */
        private fun reportDroppedWrites(timestamp: String) {
            val dropped = droppedWrites.getAndSet(0)
            if (dropped > 0) {
                printWriter?.println("$timestamp W/FileLoggingTree: dropped $dropped entries - log queue full")
            }
        }

        /**
         * S1203: blocks until everything queued before this call has reached the file. The executor is
         * single-threaded and FIFO, so an empty task finishing means every earlier one already did.
         */
        private fun flushPendingWrites() {
            val drained = try {
                logIoExecutor.submit { }
            } catch (_: RejectedExecutionException) {
                return
            }
            try {
                drained.get(FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            } catch (_: Exception) {
                // A crash dump must never hang on a stuck log queue: abandon the tail and let the
                // crash file be written anyway.
                drained.cancel(true)
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

        private fun startDebugMirrorScheduler() {
            logIoExecutor.scheduleAtFixedRate(
                {
                    StrictModeHelper.allowDiskIO {
                        flushDebugMirrorDelta()
                    }
                },
                10,
                10,
                TimeUnit.SECONDS,
            )
        }

        private fun resolveDebugMirrorDirectory(path: String): File? {
            val normalizedPath = path.trim()
            if (normalizedPath.isEmpty()) return null

            val localPath = when {
                normalizedPath.startsWith("file://") -> Uri.parse(normalizedPath).path
                normalizedPath.contains("://") -> null
                else -> normalizedPath
            } ?: return null

            return File(localPath).parentFile
        }

        private fun flushDebugMirrorDelta() {
            val sourceFile = currentLogFile ?: return
            val targetFile = debugMirrorFile ?: return

            synchronized(this) {
                if (!sourceFile.exists()) return
                if (debugMirrorSourcePath != sourceFile.absolutePath) {
                    debugMirrorSourcePath = sourceFile.absolutePath
                    debugMirrorSourceOffsetBytes = 0L
                }

                val sourceLength = sourceFile.length()
                if (sourceLength <= 0L) return
                if (sourceLength < debugMirrorSourceOffsetBytes) {
                    debugMirrorSourceOffsetBytes = 0L
                }
                if (sourceLength == debugMirrorSourceOffsetBytes) return

                targetFile.parentFile?.mkdirs()
                // S1310: the mirror only ever appended - across source-log rotations it re-copied
                // every new file in full, so a long debug session grew it without bound while the
                // source itself stayed capped at maxFileSize. Restart the mirror at the cap instead.
                if (targetFile.length() >= debugMirrorMaxBytes) {
                    val capMb = debugMirrorMaxBytes / 1024 / 1024
                    FileOutputStream(targetFile, false).use { reset ->
                        reset.write("=== mirror restarted (cap ${capMb}MB) ===\n".toByteArray())
                    }
                    debugMirrorSourceOffsetBytes = maxOf(0L, sourceLength - DEFAULT_BUFFER_SIZE)
                }
                RandomAccessFile(sourceFile, "r").use { input ->
                    input.seek(debugMirrorSourceOffsetBytes)
                    FileOutputStream(targetFile, true).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                    debugMirrorSourceOffsetBytes = input.filePointer
                }
            }
        }
        
        private fun openNewLogFile() {
            val fileName = "fastmediasorter_${formatFileName(Date())}.log"
            currentLogFile = File(logDir, fileName)
            // Explicit UTF-8: platform default charset on some devices is CP866/OEM,
            // which corrupts Cyrillic resource/file names in the log file.
            printWriter = PrintWriter(OutputStreamWriter(FileOutputStream(currentLogFile, true), Charsets.UTF_8), true)
            printWriter?.println("=== Log started: ${formatTs(Date())} ===")
            printWriter?.println("=== App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ===")
            printWriter?.flush()
        }
        
        private fun closeCurrentFile() {
            try {
                printWriter?.println("=== Log closed: ${formatTs(Date())} ===")
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
                // S1203: entries queued moments before the crash are still on the log I/O thread;
                // drain them first so the session log ends where the crash actually happened.
                flushPendingWrites()
                val now = Date()
                val timestamp = formatTs(now)
                val crashFile = File(logDir, "fastmediasorter_crash_${formatFileName(now)}.log")

                PrintWriter(OutputStreamWriter(FileOutputStream(crashFile, false), Charsets.UTF_8), true).use { pw ->
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

        private companion object {
            // S1203: deep enough that ordinary bursts never reach it, shallow enough that a runaway
            // logger cannot grow the queue without bound now that callers no longer wait on a write.
            const val MAX_PENDING_WRITES = 4096

            // How long the crash path waits for the queue before giving up on its tail.
            const val FLUSH_TIMEOUT_SECONDS = 2L
        }
    }
}

/**
 * S1248: undo `Timber.Tree.prepareLog`'s message mutation. When a throwable is passed, Timber
 * appends `"\n" + Log.getStackTraceString(t)` to the message before calling any tree, while also
 * handing the tree the throwable itself - a tree that renders the trace from [t] then writes it
 * twice. Returns the caller's original message text; for a text-less `Timber.e(t)` call (where the
 * whole message IS the trace) it returns an empty string, leaving the tree's own trace rendering
 * as the single copy.
 *
 * Top-level so the rule is unit-testable without touching the file-writing tree.
 */
internal fun stripTimberAppendedTrace(message: String, t: Throwable?): String {
    val trace = t?.let { android.util.Log.getStackTraceString(it).trimEnd('\n') }.orEmpty()
    if (trace.isEmpty()) return message
    val bare = message.trimEnd('\n')
    return if (bare == trace) "" else bare.removeSuffix("\n" + trace)
}

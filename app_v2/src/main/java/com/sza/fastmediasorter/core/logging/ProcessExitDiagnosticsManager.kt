package com.sza.fastmediasorter.core.logging

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.AtomicFile
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException
import java.util.Properties
import java.util.concurrent.TimeUnit

/** Owned by the file logger; every instance method runs on its single IO executor. */
internal class ProcessExitDiagnosticsManager(
    context: Context,
    private val report: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val marker = AtomicFile(File(appContext.noBackupFilesDir, "process-exit.properties"))
    private var exitWatermark = 0L

    fun start() {
        val previous = readMarker()
        exitWatermark = previous.getProperty(EXIT_TIMESTAMP)?.toLongOrNull() ?: 0L
        val history = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) readPlatformHistory() else "API unavailable"
        } catch (_: RuntimeException) {
            "history unavailable"
        }
        report("Process exit: $history; ${previousObservation(previous)}")
        heartbeat()
    }

    fun heartbeat(): Boolean {
        // No wake lock: a delayed sample is a last observation, never an exact death timestamp.
        return try {
            val process = ActivityManager.RunningAppProcessInfo()
            ActivityManager.getMyMemoryState(process)
            val snapshot = Properties().apply {
                setProperty(LAST_SEEN, System.currentTimeMillis().toString())
                setProperty("importance", process.importance.toString())
                setProperty("audioServicePlaying", audioServicePlaying.toString())
                setProperty("lastTrimLevel", lastTrimLevel?.toString() ?: "unknown")
                setProperty(EXIT_TIMESTAMP, exitWatermark.toString())
            }
            writeMarker(snapshot)
        } catch (_: RuntimeException) {
            // An unavailable OS service must not cancel the logger's future scheduled samples.
            false
        }
    }

    private fun readMarker(): Properties = try {
        marker.openRead().use { stream -> Properties().apply { load(stream) } }
    } catch (_: IOException) {
        Properties()
    } catch (_: RuntimeException) {
        Properties()
    }

    private fun writeMarker(snapshot: Properties): Boolean {
        var stream: java.io.FileOutputStream? = null
        return try {
            stream = marker.startWrite()
            snapshot.store(stream, null)
            marker.finishWrite(stream)
            true
        } catch (_: IOException) {
            marker.failWrite(stream)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun readPlatformHistory(): String {
        val manager = appContext.getSystemService(ActivityManager::class.java) ?: return "history unavailable"
        val exits = manager.getHistoricalProcessExitReasons(appContext.packageName, 0, MAX_EXITS)
            .filter { it.processName == appContext.packageName && it.timestamp > exitWatermark }
            .sortedBy { it.timestamp }
        return if (exits.isEmpty()) {
            "no new platform exits"
        } else {
            val summary = exits.joinToString(" | ") { formatExit(it) }
            exitWatermark = exits.maxOf { it.timestamp }
            summary
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun formatExit(exit: ApplicationExitInfo): String =
        "timestamp=${exit.timestamp} reason=${exit.reason} status=${exit.status} " +
            "importance=${exit.importance} pssKiB=${exit.pss} rssKiB=${exit.rss} " +
            "description=${singleLine(exit.description.orEmpty())}"

    private fun previousObservation(previous: Properties): String {
        val lastSeen = previous.getProperty(LAST_SEEN)?.toLongOrNull()
            ?: return "no prior observation; cause unknown"
        return "previous session ended; clean shutdown not observed; cause unknown; " +
            "lastSeen=$lastSeen importance=${previous.getProperty("importance", "unknown")} " +
            "audioServicePlaying=${previous.getProperty("audioServicePlaying", "unknown")} " +
            "lastTrimLevel=${previous.getProperty("lastTrimLevel", "unknown")}"
    }

    companion object {
        val HEARTBEAT_SECONDS: Long = TimeUnit.MINUTES.toSeconds(5)
        private const val MAX_EXITS = 10
        private const val MAX_DESCRIPTION_LENGTH = 160
        private const val LAST_SEEN = "lastSeen"
        private const val EXIT_TIMESTAMP = "exitTimestamp"

        @Volatile
        var audioServicePlaying: Boolean = false

        @Volatile
        var lastTrimLevel: Int? = null

        internal fun singleLine(value: String): String =
            value.take(MAX_DESCRIPTION_LENGTH).replace(Regex("[\\r\\n\\t]"), " ")
    }
}

package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppendToScheduledLogUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val LOG_FILE_NAME = "scheduled_operations_log.txt"
        private const val MAX_FILE_SIZE_BYTES = 1_048_576L  // 1 MB
        private const val MAX_LINES_AFTER_TRIM = 500
    }

    operator fun invoke(line: String) {
        try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            logFile.appendText("$line\n")
            if (logFile.length() > MAX_FILE_SIZE_BYTES) {
                trimLog(logFile)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to append to scheduled operations log")
        }
    }

    private fun trimLog(file: File) {
        try {
            val lines = file.readLines()
            if (lines.size > MAX_LINES_AFTER_TRIM) {
                file.writeText(lines.takeLast(MAX_LINES_AFTER_TRIM).joinToString("\n") + "\n")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to trim scheduled operations log")
        }
    }
}

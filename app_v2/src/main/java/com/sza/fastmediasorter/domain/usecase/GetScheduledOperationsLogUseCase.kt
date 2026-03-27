package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetScheduledOperationsLogUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    operator fun invoke(): String {
        val logFile = File(context.filesDir, AppendToScheduledLogUseCase.LOG_FILE_NAME)
        return if (logFile.exists()) logFile.readText() else ""
    }
}

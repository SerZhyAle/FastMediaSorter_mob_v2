package com.sza.fastmediasorter.domain.usecase

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClearScheduledOperationsLogUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    operator fun invoke() {
        File(context.filesDir, AppendToScheduledLogUseCase.LOG_FILE_NAME).delete()
    }
}

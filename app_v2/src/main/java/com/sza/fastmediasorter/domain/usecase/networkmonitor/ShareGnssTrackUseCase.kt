package com.sza.fastmediasorter.domain.usecase.networkmonitor

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * S1433: hands the recorded GNSS track to the system share sheet.
 *
 * The recorder writes into `filesDir`, which is private to the app: no file manager reaches it and no other
 * app can open it, which is what "stays on the device" means - and also what would make a recorded track
 * unreachable to the person who recorded it. This use case is the one route out, through the same
 * FileProvider authority the history export uses, so the file still needs no storage permission and the
 * recipient gets read access for one share only.
 *
 * Nothing is copied or staged: the session file is offered where it lies, so a track cannot end up duplicated
 * in `cacheDir` where the pruning the recorder does would never reach it.
 */
class ShareGnssTrackUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    // Broad catch is an intentional share-boundary guard: a provider authority or paths entry that stops
    // covering the track directory surfaces as Result.failure (logged), never as a crash on the share tap.
    @Suppress("TooGenericExceptionCaught")
    suspend operator fun invoke(trackFilePath: String): Result<Uri> = withContext(ioDispatcher) {
        try {
            val file = File(trackFilePath)
            if (!file.isFile) {
                Timber.i("GNSS track file is gone before the share; nothing to offer")
                Result.failure(NoSuchFileException(file))
            } else {
                Result.success(FileProvider.getUriForFile(context, authority(), file))
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "GNSS track share failed")
            Result.failure(e)
        }
    }

    private fun authority(): String = "${context.packageName}$AUTHORITY_SUFFIX"

    private companion object {

        const val AUTHORITY_SUFFIX = ".fileprovider"
    }
}

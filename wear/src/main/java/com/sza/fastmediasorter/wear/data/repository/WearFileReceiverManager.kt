package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import android.os.Environment
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages incoming file transfers from phone to watch via Wearable ChannelClient (S1861).
 * Received files are written to app's external downloads directory.
 */
@Singleton
class WearFileReceiverManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Receives bytes from [channel] and saves them to a file named [fileName].
     * Returns true on success, false on failure or cancellation.
     */
    suspend fun receiveFile(channel: ChannelClient.Channel, fileName: String): Boolean = withContext(Dispatchers.IO) {
        val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val targetFile = File(targetDir, fileName)
        val channelClient = Wearable.getChannelClient(context)

        try {
            val inputStream = channelClient.getInputStream(channel).await()
            FileOutputStream(targetFile).use { output ->
                inputStream.copyTo(output)
            }
            runCatching { channelClient.close(channel).await() }
            true
        } catch (e: Exception) {
            Timber.e(e, "failed to receive file %s", fileName)
            if (targetFile.exists()) {
                targetFile.delete()
            }
            false
        }
    }
}

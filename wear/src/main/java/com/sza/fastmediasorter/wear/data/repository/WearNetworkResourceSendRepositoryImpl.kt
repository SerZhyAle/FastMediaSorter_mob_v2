package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.wear.data.network.WearNetworkResourceWriter
import com.sza.fastmediasorter.wear.data.network.WearTransferNetworkManager
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearResourceSendFailure
import com.sza.fastmediasorter.wear.domain.model.WearResourceSendReport
import com.sza.fastmediasorter.wear.domain.model.WearResourceStoreResult
import com.sza.fastmediasorter.wear.domain.repository.NetworkSourceRepository
import com.sza.fastmediasorter.wear.domain.repository.WearResourceSendRepository
import com.sza.fastmediasorter.wear.util.handingOffCloseable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject

/**
 * S4230: one action holds the watch's Wi-Fi or LTE once ([WearTransferNetworkManager]), reads the file as a stream
 * and hands it to [WearNetworkResourceWriter]; the network is given back on every path, cancellation included.
 *
 * The file is opened before the network is requested, so a file that cannot be read never wakes the radio.
 */
class WearNetworkResourceSendRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sourceRepository: NetworkSourceRepository,
    private val transferNetwork: WearTransferNetworkManager,
    private val writer: WearNetworkResourceWriter,
) : WearResourceSendRepository {

    override suspend fun send(sourceId: String, file: WearMediaFile): WearResourceSendReport {
        val source = sourceRepository.getSourceById(sourceId)
        return if (source == null) {
            WearResourceSendReport(null, null, WearResourceSendFailure.UNREACHABLE)
        } else {
            sendTo(source, file)
        }
    }

    private suspend fun sendTo(source: NetworkSource, file: WearMediaFile): WearResourceSendReport {
        val input = openSource(file.uri)
        val result = if (input == null) {
            WearResourceStoreResult.Failed(WearResourceSendFailure.SOURCE_UNREADABLE)
        } else {
            input.use { stream -> store(source, file, stream) }
        }
        Timber.i("Send to resource: %s", if (result is WearResourceStoreResult.Stored) "stored" else "not stored")
        return when (result) {
            is WearResourceStoreResult.Stored -> WearResourceSendReport(source.name, result.finalName, null)
            is WearResourceStoreResult.Failed -> WearResourceSendReport(source.name, null, result.failure)
        }
    }

    private suspend fun store(
        source: NetworkSource,
        file: WearMediaFile,
        stream: InputStream,
    ): WearResourceStoreResult =
        transferNetwork.withTransferNetwork { granted -> writer.store(source, file.name, stream, granted) }
            ?: WearResourceStoreResult.Failed(WearResourceSendFailure.NO_NETWORK)

    // The stream is tracked inside the block so a caller cancelled mid-open never leaves it open (S3831).
    private suspend fun openSource(uri: Uri): InputStream? = handingOffCloseable { handOff ->
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.let { stream -> handOff.track(stream) }
            } catch (e: FileNotFoundException) {
                Timber.w("Send to resource: the file is gone (%s)", e.javaClass.simpleName)
                null
            } catch (e: SecurityException) {
                Timber.w("Send to resource: the file may not be read (%s)", e.javaClass.simpleName)
                null
            }
        }
    }
}

package com.sza.fastmediasorter.wear.data.network

import android.net.Network
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearResourceStoreResult
import java.io.InputStream
import javax.inject.Inject

/**
 * S4230: the one door through which a byte is written to a network resource.
 *
 * It picks the protocol client by source type, as the folder walk does for listing, stores under a free name through
 * a partial file, and turns a failed write into a [WearResourceStoreResult.Failed] - a server's refusal is a result,
 * never an exception for the caller to read. The folder is the source's own root, as the person connected it.
 */
class WearNetworkResourceWriter @Inject constructor(
    private val dataSources: WearNetworkDataSources,
    private val classifier: WearResourceSendFailureClassifier
) {

    suspend fun store(
        source: NetworkSource,
        intendedName: String,
        input: InputStream,
        network: Network?
    ): WearResourceStoreResult {
        val stored = when (source.type) {
            NetworkSourceType.SMB ->
                dataSources.smb.storeFile(source, source.basePath, intendedName, input, network)
            NetworkSourceType.FTP ->
                dataSources.ftp.storeFile(source, source.basePath, intendedName, input, network)
            NetworkSourceType.SFTP ->
                dataSources.sftp.storeFile(source, source.basePath, intendedName, input, network)
        }
        return stored.fold(
            onSuccess = { finalName -> WearResourceStoreResult.Stored(finalName) },
            onFailure = { error -> WearResourceStoreResult.Failed(classifier.classify(error)) }
        )
    }
}

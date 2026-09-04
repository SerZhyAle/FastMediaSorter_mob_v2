package com.sza.fastmediasorter.wear.data.network.ftp

import com.sza.fastmediasorter.wear.data.network.WearEndpointResolver
import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * FTP connection test for Wear OS network sources.
 *
 * S1554: this used to return a fixed failure, explained by a KDoc claiming Apache Commons Net was
 * not a Wear dependency. It is one, and [FtpDataSource] a few files away browses with it - the stub
 * was unfinished wiring, not a decision. The test now connects the same way that listing does, so a
 * green result predicts browsing rather than merely asserting reachability.
 */
class FtpConnectionTest @Inject constructor(
    private val endpointResolver: WearEndpointResolver
) {

    suspend fun testFtp(sourceIn: NetworkSource): Result<Boolean> = withContext(Dispatchers.IO) {
        // S2488: same endpoint choice as browsing, so the test cannot answer differently.
        val source = endpointResolver.resolve(sourceIn)
        val client = FTPClient()
        // Bound the test specifically: a listing may wait on the client's own defaults, but a test
        // the user is watching must come back.
        client.connectTimeout = CONNECT_TIMEOUT_MS
        client.defaultTimeout = CONNECT_TIMEOUT_MS
        try {
            client.openFtpSession(source)
            client.soTimeout = CONNECT_TIMEOUT_MS
            val path = source.basePath.ifBlank { "/" }
            // The base path is what browsing will open, so that is what is verified - reaching the
            // server while the configured folder is missing is not a working source.
            val reachable = client.changeWorkingDirectory(path)
            if (reachable) {
                Result.success(true)
            } else {
                Result.failure(IOException("FTP path not reachable: $path"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            failure(source, e)
        } catch (e: IllegalStateException) {
            failure(source, e)
        } finally {
            runCatching { if (client.isConnected) client.logout() }
            runCatching { if (client.isConnected) client.disconnect() }
        }
    }

    /**
     * Only the two families the session path can raise are caught - an IO answer from the server and
     * the IllegalStateException that `error(..)` throws on a refused connection or a failed login.
     * Anything else is a programming error and belongs to the caller's own wrapper.
     */
    private fun failure(source: NetworkSource, e: Exception): Result<Boolean> {
        Timber.w(e, "FtpConnectionTest: test failed for source '${source.name}'")
        return Result.failure(e)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
    }
}

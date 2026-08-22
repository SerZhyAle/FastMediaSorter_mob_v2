package com.sza.fastmediasorter.data.link

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Streams a direct http(s) URL into a local file so «Send to..» can hand a web source to any
 * file-consuming receiver (S0494).
 *
 * Deliberately does not route through [DirectFileExtractionStrategy]: that strategy gates content
 * through `MediaMimeWhitelist` to protect link *ingest* from arbitrary web content, whereas a share
 * starts from a file the user already has open, so the same whitelist would refuse legitimate
 * shares (tactical ADR-1). The shared `linkDownload` client is reused for its timeouts, cookie jar
 * and http(s)-only redirect guard.
 */
@Singleton
class HttpFileDownloader @Inject constructor(
    @param:Named("linkDownload") private val httpClient: OkHttpClient,
) {

    /**
     * @param onProgress receives 0..100, emitted only when the response announces a positive
     * `Content-Length` - a source of unknown size must leave the caller indeterminate rather than
     * report a made-up percentage.
     * @return true when [target] holds the complete body; false on a malformed URL, a non-2xx
     * response or an I/O failure, in which case no partial file is left behind.
     */
    suspend fun download(url: String, target: File, onProgress: ((Int) -> Unit)?): Boolean =
        withContext(Dispatchers.IO) {
            val httpUrl = url.toHttpUrlOrNull()
            if (httpUrl == null) {
                Timber.i("Send-to http download: not an http(s) url: %s", url)
                false
            } else {
                execute(httpUrl, target, onProgress)
            }
        }

    private fun execute(url: HttpUrl, target: File, onProgress: ((Int) -> Unit)?): Boolean = try {
        httpClient.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body == null) {
                Timber.i("Send-to http download: HTTP %d for %s", response.code, url)
                deletePartial(target)
                false
            } else {
                streamToFile(body, target, onProgress)
                true
            }
        }
    } catch (io: IOException) {
        Timber.w(io, "Send-to http download failed for %s", url)
        deletePartial(target)
        false
    }

    private fun streamToFile(body: ResponseBody, target: File, onProgress: ((Int) -> Unit)?) {
        val reporter = PercentReporter(body.contentLength(), onProgress)
        var written = 0L
        body.byteStream().use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(BUFFER_BYTES)
                var read = input.read(buffer)
                while (read >= 0) {
                    output.write(buffer, 0, read)
                    written += read
                    reporter.report(written)
                    read = input.read(buffer)
                }
                output.flush()
            }
        }
    }

    // A half-written file would be handed to the receiver as a complete one, so a failed transfer
    // must leave nothing behind for the caller's reuse check to pick up.
    private fun deletePartial(target: File) {
        if (target.exists() && !target.delete()) {
            Timber.i("Send-to http download: could not delete partial %s", target.absolutePath)
        }
    }

    // Emits only on a percentage change, so a large body does not flood the share dialog with one
    // callback per 64 KB chunk.
    private class PercentReporter(private val totalBytes: Long, private val sink: ((Int) -> Unit)?) {
        private var lastPercent = -1

        fun report(writtenBytes: Long) {
            if (sink == null || totalBytes <= 0L) {
                return
            }
            val percent = ((writtenBytes * PERCENT_SCALE) / totalBytes).toInt()
            if (percent != lastPercent) {
                lastPercent = percent
                sink(percent)
            }
        }
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        const val PERCENT_SCALE = 100L
    }
}

package com.sza.fastmediasorter.data.link.nolegal

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class NewPipeOkHttpDownloader @Inject constructor(
    @Named("linkDownload") private val httpClient: OkHttpClient,
) : Downloader() {

    @Volatile
    private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(this)
            initialized = true
            // S0117: one global Downloader instance keeps extractor requests on the
            // same OkHttp/cookie stack as the existing URL pipeline.
            Timber.i("S0117: NewPipe extractor initialized with app OkHttp downloader")
        }
    }

    override fun execute(request: Request): Response {
        val method = request.httpMethod().uppercase()
        val builder = OkHttpRequest.Builder().url(request.url())
        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }

        val response = when (method) {
            "GET" -> httpClient.newCall(builder.get().build()).execute()
            "HEAD" -> httpClient.newCall(builder.head().build()).execute()
            else -> {
                val contentType = request.headers()["Content-Type"]?.firstOrNull()
                    ?: "application/octet-stream"
                val body = (request.dataToSend() ?: ByteArray(0))
                    .toRequestBody(contentType.toMediaType())
                httpClient.newCall(builder.method(method, body).build()).execute()
            }
        }

        try {
            val headers = linkedMapOf<String, List<String>>()
            for (name in response.headers.names()) {
                headers[name] = response.headers.values(name)
            }
            return Response(
                response.code,
                response.message,
                headers,
                response.body?.string().orEmpty(),
                response.request.url.toString(),
            )
        } catch (io: IOException) {
            response.close()
            throw io
        } finally {
            response.close()
        }
    }
}
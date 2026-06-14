package com.sza.fastmediasorter.data.cloud.glide

import android.content.Context
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.domain.identity.GoogleIdentityRepository
import com.sza.fastmediasorter.domain.identity.GoogleScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Glide ModelLoader for Google Drive thumbnail URLs.
 * Handles authentication by adding access token to requests.
 */
class GoogleDriveThumbnailModelLoader(
    private val context: Context
) : ModelLoader<GoogleDriveThumbnailData, InputStream> {

    override fun buildLoadData(
        model: GoogleDriveThumbnailData,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? {
        return ModelLoader.LoadData(
            ObjectKey(model.fileId),
            GoogleDriveThumbnailDataFetcher(context, model)
        )
    }

    override fun handles(model: GoogleDriveThumbnailData): Boolean = true

    class Factory(private val context: Context) : ModelLoaderFactory<GoogleDriveThumbnailData, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<GoogleDriveThumbnailData, InputStream> {
            return GoogleDriveThumbnailModelLoader(context)
        }

        override fun teardown() {
            // No-op
        }
    }
}

/**
 * DataFetcher that downloads Google Drive thumbnails with authentication.
 */
class GoogleDriveThumbnailDataFetcher(
    private val context: Context,
    private val model: GoogleDriveThumbnailData
) : DataFetcher<InputStream> {

    @Volatile
    private var isCancelled = false
    private var connection: HttpURLConnection? = null
    private var resultStream: InputStream? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        if (isCancelled) {
            callback.onLoadFailed(Exception("Request was cancelled"))
            return
        }

        Thread {
            try {
                val accessToken = getAccessToken()
                if (accessToken == null) {
                    callback.onLoadFailed(Exception("No Google Drive access token available"))
                    return@Thread
                }

                if (isCancelled) {
                    callback.onLoadFailed(Exception("Request was cancelled"))
                    return@Thread
                }

                // Download thumbnail or full image with auth header
                val imageUrl = if (model.loadFullImage) {
                    // Full image: https://www.googleapis.com/drive/v3/files/{fileId}?alt=media
                    val fullImageUrl = "https://www.googleapis.com/drive/v3/files/${model.fileId}?alt=media"
                    Timber.d("GoogleDriveThumbnailDataFetcher: Loading FULL IMAGE from: $fullImageUrl")
                    fullImageUrl
                } else {
                    // Thumbnail URL from metadata
                    Timber.d("GoogleDriveThumbnailDataFetcher: Loading THUMBNAIL from: ${model.thumbnailUrl}")
                    model.thumbnailUrl
                }
                
                Timber.d("GoogleDriveThumbnailDataFetcher: loadFullImage flag = ${model.loadFullImage}, fileId = ${model.fileId}")
                val url = URL(imageUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $accessToken")
                    connectTimeout = 15000
                    readTimeout = if (model.loadFullImage) 60000 else 30000  // Longer timeout for full images
                }

                val responseCode = connection!!.responseCode
                Timber.d("GoogleDriveThumbnailDataFetcher: HTTP response code = $responseCode")
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Buffer the entire response to avoid thread interrupt issues
                    val buffer = ByteArrayOutputStream()
                    BufferedInputStream(connection!!.inputStream).use { input ->
                        input.copyTo(buffer)
                    }
                    val imageSize = buffer.size()
                    Timber.d("GoogleDriveThumbnailDataFetcher: Downloaded image size = $imageSize bytes (${imageSize / 1024} KB)")
                    resultStream = ByteArrayInputStream(buffer.toByteArray())
                    callback.onDataReady(resultStream)
                } else {
                    // thumbnailLink is a short-lived signed URL (~1h TTL).
                    // On 404, fetch a fresh one from the metadata API and retry once.
                    if (responseCode == HttpURLConnection.HTTP_NOT_FOUND && !model.loadFullImage) {
                        connection?.disconnect()
                        val freshUrl = fetchFreshThumbnailUrl(model.fileId, "Bearer $accessToken")
                        if (freshUrl != null) {
                            Timber.w("GoogleDriveThumbnailDataFetcher: thumbnailLink expired (404) - retrying with fresh URL for ${model.fileId}")
                            downloadWithFreshUrl(freshUrl, "Bearer $accessToken", callback)
                        } else {
                            Timber.e("Google Drive thumbnail failed: $responseCode - no fresh thumbnailLink for ${model.fileId}")
                            callback.onLoadFailed(Exception("HTTP $responseCode"))
                        }
                    } else {
                        val errorBody = try {
                            connection!!.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                        } catch (e: Exception) {
                            "Could not read error"
                        }
                        Timber.e("Google Drive thumbnail failed: $responseCode - $errorBody")
                        callback.onLoadFailed(Exception("HTTP $responseCode"))
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load Google Drive thumbnail")
                callback.onLoadFailed(e)
            }
        }.start()
    }

    private fun fetchFreshThumbnailUrl(fileId: String, authHeader: String): String? {
        return try {
            val conn = (URL("https://www.googleapis.com/drive/v3/files/$fileId?fields=thumbnailLink")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", authHeader)
                connectTimeout = 10000
                readTimeout = 10000
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                JSONObject(body).optString("thumbnailLink").takeIf { it.isNotEmpty() }
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            Timber.w(e, "GoogleDriveThumbnailDataFetcher: failed to fetch fresh thumbnailLink for $fileId")
            null
        }
    }

    private fun downloadWithFreshUrl(
        imageUrl: String,
        authHeader: String,
        callback: DataFetcher.DataCallback<in InputStream>
    ) {
        try {
            val conn = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", authHeader)
                connectTimeout = 15000
                readTimeout = 30000
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val buffer = ByteArrayOutputStream()
                BufferedInputStream(conn.inputStream).use { it.copyTo(buffer) }
                conn.disconnect()
                resultStream = ByteArrayInputStream(buffer.toByteArray())
                callback.onDataReady(resultStream)
            } else {
                Timber.e("GoogleDriveThumbnailDataFetcher: fresh URL also failed: ${conn.responseCode}")
                conn.disconnect()
                callback.onLoadFailed(Exception("HTTP ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "GoogleDriveThumbnailDataFetcher: downloadWithFreshUrl failed")
            callback.onLoadFailed(e)
        }
    }

    /**
     * S0200 Phase 04b: fetch a Drive read-only access token from the identity domain via the
     * `ThumbnailIdentityEntryPoint` Hilt accessor. The fetcher is created by Glide's
     * `ModelLoaderFactory` and is NOT Hilt-managed, so it cannot inject `GoogleIdentityRepository`
     * via the constructor - `EntryPointAccessors.fromApplication(..)` reaches into the singleton
     * graph instead.
     *
     * Cold-start race: if Glide triggers `loadData` before `Application.onCreate` has finalised
     * the Hilt singleton component, `EntryPointAccessors.fromApplication(..)` may throw
     * `IllegalStateException`. The catch block treats that as "no token now" - Glide reports
     * onLoadFailed and the next thumbnail attempt (after Hilt is ready) succeeds.
     */
    private fun getAccessToken(): String? {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ThumbnailIdentityEntryPoint::class.java
            )
            val identityRepo: GoogleIdentityRepository = entryPoint.identityRepository()
            runBlocking { identityRepo.getAccessToken(THUMBNAIL_SCOPES)?.token }
        } catch (e: Exception) {
            Timber.e(e, "Error getting Drive access token via identity-domain (cold-start race? primary unbound?)")
            null
        }
    }

    companion object {
        private val THUMBNAIL_SCOPES: Set<GoogleScope> = setOf(GoogleScope.DRIVE_READONLY)
    }

    override fun cleanup() {
        try {
            resultStream?.close()
        } catch (e: Exception) {
            // Ignore
        }
        try {
            connection?.disconnect()
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun cancel() {
        isCancelled = true
        try {
            connection?.disconnect()
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    override fun getDataSource(): DataSource = DataSource.REMOTE

    /**
     * S0200 Phase 04a - EntryPoint scaffold for Hilt access from the non-managed Glide
     * `DataFetcher` instance. Phase 04b activates this accessor inside
     * [getAccessToken] to source tokens from the identity domain instead of GoogleSignIn.
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ThumbnailIdentityEntryPoint {
        fun identityRepository(): GoogleIdentityRepository
    }
}

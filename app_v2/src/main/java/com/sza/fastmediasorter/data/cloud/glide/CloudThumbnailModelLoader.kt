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
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import dagger.hilt.android.EntryPointAccessors
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking

/**
 * Entry point for accessing cloud clients via Hilt
 */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface CloudThumbnailEntryPoint {
    fun oneDriveClient(): OneDriveRestClient
    fun dropboxClient(): DropboxClient
}

/**
 * Glide ModelLoader for cloud thumbnail URLs.
 * Supports Google Drive, OneDrive, and Dropbox.
 * Handles authentication by adding access token to requests.
 */
class CloudThumbnailModelLoader(
    private val context: Context
) : ModelLoader<CloudThumbnailData, InputStream> {

    override fun buildLoadData(
        model: CloudThumbnailData,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? {
        return ModelLoader.LoadData(
            ObjectKey("${model.cloudProvider.name}_${model.fileId}"),
            CloudThumbnailDataFetcher(context, model)
        )
    }

    override fun handles(model: CloudThumbnailData): Boolean = true

    class Factory(private val context: Context) : ModelLoaderFactory<CloudThumbnailData, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<CloudThumbnailData, InputStream> {
            return CloudThumbnailModelLoader(context)
        }

        override fun teardown() {
            // No-op
        }
    }
}

/**
 * DataFetcher that downloads cloud images with authentication.
 * Routes to correct cloud provider based on model.cloudProvider.
 */
class CloudThumbnailDataFetcher(
    private val context: Context,
    private val model: CloudThumbnailData
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
                when (model.cloudProvider) {
                    CloudProvider.GOOGLE_DRIVE -> loadGoogleDriveImage(callback)
                    CloudProvider.ONEDRIVE -> loadOneDriveImage(callback)
                    CloudProvider.DROPBOX -> loadDropboxImage(callback)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load cloud image for provider ${model.cloudProvider}")
                callback.onLoadFailed(e)
            }
        }.start()
    }

    private fun loadGoogleDriveImage(callback: DataFetcher.DataCallback<in InputStream>) {
        // Get access token
        val accessToken = getGoogleAccessToken()
        if (accessToken == null) {
            callback.onLoadFailed(Exception("No Google Drive access token available"))
            return
        }

        if (isCancelled) {
            callback.onLoadFailed(Exception("Request was cancelled"))
            return
        }

        // Download thumbnail or full image with auth header
        val imageUrl = if (model.loadFullImage) {
            val fullImageUrl = "https://www.googleapis.com/drive/v3/files/${model.fileId}?alt=media"
            Timber.d("CloudThumbnailDataFetcher: Loading Google Drive FULL IMAGE from: $fullImageUrl")
            fullImageUrl
        } else {
            Timber.d("CloudThumbnailDataFetcher: Loading Google Drive THUMBNAIL from: ${model.thumbnailUrl}")
            model.thumbnailUrl
        }

        if (imageUrl.isNullOrBlank()) {
            Timber.w("CloudThumbnailDataFetcher: No thumbnail URL available for Google Drive file ${model.fileId}")
            callback.onLoadFailed(Exception("No thumbnail URL"))
            return
        }

        downloadWithAuth(imageUrl, "Bearer $accessToken", callback)
    }

    private fun loadOneDriveImage(callback: DataFetcher.DataCallback<in InputStream>) {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context,
                CloudThumbnailEntryPoint::class.java
            )
            val oneDriveClient = entryPoint.oneDriveClient()

            if (!oneDriveClient.isAuthenticated()) {
                callback.onLoadFailed(Exception("OneDrive not authenticated"))
                return
            }

            if (isCancelled) {
                callback.onLoadFailed(Exception("Request was cancelled"))
                return
            }

            // Use download method from OneDriveRestClient
            val outputStream = ByteArrayOutputStream()
            val result = runBlocking {
                oneDriveClient.downloadFile(model.fileId, outputStream, null)
            }

            when (result) {
                is CloudResult.Success -> {
                    val imageData = outputStream.toByteArray()
                    Timber.d("CloudThumbnailDataFetcher: OneDrive downloaded ${imageData.size} bytes")
                    resultStream = ByteArrayInputStream(imageData)
                    callback.onDataReady(resultStream)
                }
                is CloudResult.Error -> {
                    Timber.e("CloudThumbnailDataFetcher: OneDrive download failed: ${result.message}")
                    callback.onLoadFailed(Exception(result.message))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "CloudThumbnailDataFetcher: Failed to load OneDrive image")
            callback.onLoadFailed(e)
        }
    }

    private fun loadDropboxImage(callback: DataFetcher.DataCallback<in InputStream>) {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context,
                CloudThumbnailEntryPoint::class.java
            )
            val dropboxClient = entryPoint.dropboxClient()

            if (!dropboxClient.isAuthenticated()) {
                callback.onLoadFailed(Exception("Dropbox not authenticated"))
                return
            }

            if (isCancelled) {
                callback.onLoadFailed(Exception("Request was cancelled"))
                return
            }

            // Convert cloud:// path to Dropbox path format
            // model.fileId can be either:
            // 1. Just filename: "image1.jpg" 
            // 2. Full cloud path: "cloud://dropbox/test_media/image1.jpg" or "cloud://dropbox//test_media/..."
            val dropboxPath = if (model.fileId.startsWith("cloud://")) {
                // Extract path after provider: cloud://dropbox/test_media/file.jpg -> /test_media/file.jpg
                // Handle both single and double slashes after provider
                val afterScheme = model.fileId.substringAfter("cloud://dropbox")
                val cleanPath = afterScheme.trimStart { it == '/' } // Remove ALL leading slashes
                "/$cleanPath" // Add single leading slash
            } else if (model.fileId.startsWith("/")) {
                // Already in correct format
                model.fileId
            } else {
                // Just filename, assume root
                "/${model.fileId}"
            }
            
            Timber.d("CloudThumbnailDataFetcher: Dropbox downloading from path: $dropboxPath (original fileId: ${model.fileId})")

            // Use download method from DropboxClient
            val outputStream = ByteArrayOutputStream()
            val result = runBlocking {
                dropboxClient.downloadFile(dropboxPath, outputStream, null)
            }

            when (result) {
                is CloudResult.Success -> {
                    val imageData = outputStream.toByteArray()
                    Timber.d("CloudThumbnailDataFetcher: Dropbox downloaded ${imageData.size} bytes")
                    resultStream = ByteArrayInputStream(imageData)
                    callback.onDataReady(resultStream)
                }
                is CloudResult.Error -> {
                    Timber.e("CloudThumbnailDataFetcher: Dropbox download failed: ${result.message}")
                    callback.onLoadFailed(Exception(result.message))
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "CloudThumbnailDataFetcher: Failed to load Dropbox image")
            callback.onLoadFailed(e)
        }
    }

    private fun downloadWithAuth(
        imageUrl: String,
        authHeader: String,
        callback: DataFetcher.DataCallback<in InputStream>
    ) {
        val url = URL(imageUrl)
        connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", authHeader)
            connectTimeout = 15000
            readTimeout = if (model.loadFullImage) 60000 else 30000
        }

        val responseCode = connection!!.responseCode
        Timber.d("CloudThumbnailDataFetcher: HTTP response code = $responseCode")
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val buffer = ByteArrayOutputStream()
            BufferedInputStream(connection!!.inputStream).use { input ->
                input.copyTo(buffer)
            }
            val imageSize = buffer.size()
            Timber.d("CloudThumbnailDataFetcher: Downloaded image size = $imageSize bytes (${imageSize / 1024} KB)")
            resultStream = ByteArrayInputStream(buffer.toByteArray())
            callback.onDataReady(resultStream)
        } else {
            val errorBody = try {
                connection!!.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            } catch (e: Exception) {
                "Could not read error"
            }
            Timber.e("Cloud thumbnail failed: $responseCode - $errorBody")
            callback.onLoadFailed(Exception("HTTP $responseCode"))
        }
    }

    private fun getGoogleAccessToken(): String? {
        return try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account?.account == null) {
                Timber.w("No Google account signed in")
                return null
            }

            GoogleAuthUtil.getToken(
                context,
                account.account!!,
                "oauth2:https://www.googleapis.com/auth/drive.readonly"
            )
        } catch (e: Exception) {
            Timber.e(e, "Error getting Google access token")
            null
        }
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
}

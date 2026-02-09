package com.sza.fastmediasorter.data.cloud.helpers

import androidx.annotation.Keep
import com.sza.fastmediasorter.data.cloud.CloudResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles HTTP requests for Google Drive REST API.
 * Encapsulates HttpURLConnection setup and stream management.
 * 
 * Note: 401 retry logic handled at GoogleDriveRestClient level via silentSignIn().
 */
@Singleton
class GoogleDriveHttpClient @Inject constructor() {

    /**
     * Make authenticated HTTP request to Drive API.
     *
     * @param url Target URL
     * @param method HTTP method (GET, POST, PATCH, DELETE)
     * @param token OAuth2 access token
     * @param body Optional request body (JSON string)
     * @return ApiResponse with success status, data, and error message
     */
    suspend fun makeAuthenticatedRequest(
        url: URL,
        method: String,
        token: String,
        body: String? = null
    ): ApiResponse {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = method
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json")
                
                if (body != null) {
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connection.doOutput = true
                    connection.outputStream.bufferedWriter().use { it.write(body) }
                }
                
                val responseCode = connection.responseCode
                
                if (responseCode in 200..299) {
                    val data = if (method == "DELETE") {
                        "{}" // DELETE returns empty response
                    } else {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    }
                    ApiResponse(isSuccess = true, data = data, errorMessage = null, httpCode = responseCode)
                } else {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                    ApiResponse(isSuccess = false, data = null, errorMessage = error, httpCode = responseCode)
                }
            } catch (e: Exception) {
                Timber.e(e, "Request failed: $method $url")
                ApiResponse(isSuccess = false, data = null, errorMessage = e.message, httpCode = null)
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * Get authenticated InputStream for media file with range support (for ExoPlayer).
     * Returns InputStream directly - caller must close connection after consuming stream.
     *
     * @param fileId Google Drive file ID
     * @param driveApiBase Base URL for Drive API (e.g., "https://www.googleapis.com/drive/v3")
     * @param token OAuth2 access token
     * @param position Starting byte position (0 for start of file)
     * @param length Number of bytes to read (C.LENGTH_UNSET for entire file)
     * @return CloudResult with InputStream (caller must close) or error with httpCode for 401 handling
     */
    suspend fun getFileInputStream(
        fileId: String,
        driveApiBase: String,
        token: String,
        position: Long = 0,
        length: Long = androidx.media3.common.C.LENGTH_UNSET.toLong()
    ): StreamResult {
        return withContext(Dispatchers.IO) {
            try {
                // Detect OneDrive file IDs (contain '!' character)
                if (fileId.contains("!")) {
                    Timber.e("GoogleDriveHttpClient.getFileInputStream: WARNING - Detected OneDrive file ID format (contains '!') being passed to Google Drive client: '$fileId'")
                    Timber.e("GoogleDriveHttpClient.getFileInputStream: This indicates a cloud provider routing issue - OneDrive files should not be handled by GoogleDrive client")
                }
                
                Timber.d("GoogleDriveHttpClient.getFileInputStream: fileId='$fileId', position=$position, length=$length")
                
                val url = URL("$driveApiBase/files/$fileId?alt=media")
                Timber.d("GoogleDriveHttpClient.getFileInputStream: Request URL: $url")
                
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                
                // Add Range header if position/length specified
                if (position > 0 || length != androidx.media3.common.C.LENGTH_UNSET.toLong()) {
                    val rangeEnd = if (length != androidx.media3.common.C.LENGTH_UNSET.toLong()) {
                        position + length - 1
                    } else {
                        "" // Open-ended range (from position to end)
                    }
                    connection.setRequestProperty("Range", "bytes=$position-$rangeEnd")
                    Timber.d("Requesting range bytes=$position-$rangeEnd")
                }
                
                val responseCode = connection.responseCode
                
                // 200 OK (full file) or 206 Partial Content (range request)
                if (responseCode == 200 || responseCode == 206) {
                    Timber.d("Stream opened successfully (HTTP $responseCode)")
                    // Return InputStream directly - don't close connection until stream is consumed
                    return@withContext StreamResult.Success(connection.inputStream)
                } else {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() } 
                        ?: "HTTP $responseCode"
                    connection.disconnect()
                    Timber.e("Failed with HTTP $responseCode - $error")
                    return@withContext StreamResult.Error("Download failed: $error", responseCode)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get input stream for fileId='$fileId'")
                StreamResult.Error("Failed to get input stream: ${e.message}", null)
            }
        }
    }

    /**
     * Download file as InputStream (for thumbnails or small files).
     * Unlike getFileInputStream, this reads full content and closes connection.
     *
     * @param fileId Google Drive file ID
     * @param driveApiBase Base URL for Drive API
     * @param token OAuth2 access token
     * @return CloudResult with InputStream containing file bytes
     */
    suspend fun downloadFileAsStream(
        fileId: String,
        driveApiBase: String,
        token: String
    ): CloudResult<InputStream> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$driveApiBase/files/$fileId?alt=media")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Authorization", "Bearer $token")
                
                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    val bytes = connection.inputStream.readBytes()
                    connection.disconnect()
                    CloudResult.Success(bytes.inputStream())
                } else {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    connection.disconnect()
                    CloudResult.Error("Download failed: $error")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to download file as stream")
                CloudResult.Error("Download failed: ${e.message}", e)
            }
        }
    }

    /**
     * API response wrapper with HTTP code for 401 detection
     */
    @Keep
    data class ApiResponse(
        val isSuccess: Boolean,
        val data: String?,
        val errorMessage: String?,
        val httpCode: Int? = null
    )

    /**
     * Stream result with HTTP code for 401 detection
     */
    @Keep
    sealed class StreamResult {
        data class Success(val stream: InputStream) : StreamResult()
        data class Error(val message: String, val httpCode: Int?) : StreamResult()
    }
}

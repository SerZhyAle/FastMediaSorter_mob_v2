package com.sza.fastmediasorter.data.cloud

import android.os.Build
import com.dropbox.core.DbxException
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.files.FileMetadata
import kotlinx.coroutines.delay
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.Metadata
import com.sza.fastmediasorter.domain.model.MediaExtensions
import org.json.JSONObject
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure helpers for DropboxClient: error-message classification, TLS diagnostics logging,
 * credential JSON serialization, Dropbox Metadata → CloudFile mapping, and MIME-type guessing.
 *
 * Extracted to keep DropboxClient below the 1000-line cap.
 */
object DropboxClientUtils {

    /** Map a Dropbox auth/network error to a localized-style, user-friendly message. */
    fun buildUserFriendlyErrorMessage(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" | ")

        return when {
            chain.contains("Trust anchor for certification path not found", ignoreCase = true) ||
                chain.contains("SSLHandshakeException", ignoreCase = true) ||
                chain.contains("CertPathValidatorException", ignoreCase = true) -> {
                logTlsDiagnostics(error, "dropbox_tls_validation")
                "TLS/SSL certificate validation failed. Device/emulator trust store cannot validate Dropbox certificate. " +
                    "Check date/time, proxy/VPN/antivirus interception, and update CA certificates/system image."
            }
            chain.contains("timeout", ignoreCase = true) ->
                "Network timeout while connecting to Dropbox. Check internet connection and retry."
            chain.contains("network", ignoreCase = true) || chain.contains("connection", ignoreCase = true) ->
                "Network connection error while contacting Dropbox. Check internet/proxy settings and retry."
            else -> {
                val rawMessage = error.message?.takeIf { it.isNotBlank() }
                    ?: error.cause?.message
                    ?: "Unknown Dropbox authentication error"
                "Dropbox authentication failed: $rawMessage"
            }
        }
    }

    /** Emit a structured log line with TLS-relevant device/proxy state - used when cert validation fails. */
    fun logTlsDiagnostics(error: Throwable, stage: String) {
        try {
            val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }.format(Date())

            val exceptionChain = generateSequence(error) { it.cause }
                .joinToString(" -> ") { throwable ->
                    val msg = throwable.message?.replace("\n", " ") ?: "<no-message>"
                    "${throwable::class.java.simpleName}: $msg"
                }

            val isEmulator = Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
                Build.MODEL.contains("Emulator", ignoreCase = true) ||
                Build.PRODUCT.contains("sdk", ignoreCase = true)

            val httpProxyHost = System.getProperty("http.proxyHost")
            val httpProxyPort = System.getProperty("http.proxyPort")
            val httpsProxyHost = System.getProperty("https.proxyHost")
            val httpsProxyPort = System.getProperty("https.proxyPort")

            Timber.e(
                "DROPBOX_TLS_DIAG stage=%s now=%s tz=%s sdk=%s device=%s/%s model=%s emulator=%s httpProxy=%s:%s httpsProxy=%s:%s exceptions=%s",
                stage,
                nowIso,
                TimeZone.getDefault().id,
                Build.VERSION.SDK_INT,
                Build.MANUFACTURER,
                Build.BRAND,
                Build.MODEL,
                isEmulator,
                httpProxyHost ?: "<none>",
                httpProxyPort ?: "<none>",
                httpsProxyHost ?: "<none>",
                httpsProxyPort ?: "<none>",
                exceptionChain
            )
        } catch (diagnosticError: Exception) {
            Timber.e(diagnosticError, "DROPBOX_TLS_DIAG failed")
        }
    }

    fun serializeAccessToken(accessToken: String): String =
        JSONObject().apply {
            put("access_token", accessToken)
            put("type", "legacy")
        }.toString()

    fun serializeCredential(credential: DbxCredential): String =
        JSONObject().apply {
            put("access_token", credential.accessToken)
            credential.refreshToken?.let { put("refresh_token", it) }
            credential.expiresAt?.let { put("expires_at", it) }
            put("app_key", credential.appKey)
        }.toString()

    /** Parse a stored credential JSON. [fallbackAppKey] is used when the JSON predates app_key persistence. */
    fun deserializeCredential(json: String, fallbackAppKey: String): DbxCredential? = try {
        val obj = JSONObject(json)
        val accessToken = obj.getString("access_token")
        val refreshToken = if (obj.has("refresh_token")) obj.getString("refresh_token") else null
        val expiresAt = obj.optLong("expires_at", -1L).takeIf { it > 0 }
        val appKey = obj.optString("app_key").takeIf { it.isNotEmpty() } ?: fallbackAppKey

        DbxCredential(accessToken, expiresAt, refreshToken, appKey)
    } catch (e: Exception) {
        Timber.e(e, "Failed to deserialize Dropbox credential")
        null
    }

    /** Convert Dropbox SDK [Metadata] (file/folder/other) to the app's [CloudFile] model. */
    fun metadataToCloudFile(metadata: Metadata, parentPath: String): CloudFile = when (metadata) {
        is FileMetadata -> CloudFile(
            id = metadata.pathDisplay ?: metadata.pathLower ?: "",
            name = metadata.name,
            path = parentPath,
            isFolder = false,
            size = metadata.size,
            modifiedDate = metadata.serverModified?.time ?: 0L,
            mimeType = guessMimeType(metadata.name),
            thumbnailUrl = null, // Thumbnails fetched separately
            webViewUrl = null
        )
        is FolderMetadata -> CloudFile(
            id = metadata.pathDisplay ?: metadata.pathLower ?: "",
            name = metadata.name,
            path = parentPath,
            isFolder = true,
            size = 0,
            modifiedDate = 0,
            mimeType = null,
            thumbnailUrl = null,
            webViewUrl = null
        )
        else -> CloudFile(
            id = metadata.pathDisplay ?: metadata.pathLower ?: "",
            name = metadata.name,
            path = parentPath,
            isFolder = false,
            size = 0,
            modifiedDate = 0,
            mimeType = null,
            thumbnailUrl = null,
            webViewUrl = null
        )
    }

    fun guessMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when {
            MediaExtensions.isImage(extension) -> "image/$extension"
            MediaExtensions.isVideo(extension) -> "video/$extension"
            MediaExtensions.isAudio(extension) -> "audio/$extension"
            else -> null
        }
    }

    /**
     * Retry wrapper for Dropbox API calls. Retries auth/network/timeout failures with the given
     * back-off; throws non-retriable errors immediately.
     */
    suspend fun <T> withRetry(
        operation: String,
        maxAttempts: Int,
        retryDelayMs: Long,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: DbxException) {
                lastException = e
                val isRetryable = e.message?.let { msg ->
                    msg.contains("401") ||
                        msg.contains("unauthorized", ignoreCase = true) ||
                        msg.contains("expired", ignoreCase = true) ||
                        msg.contains("timeout", ignoreCase = true) ||
                        msg.contains("network", ignoreCase = true)
                } ?: false

                if (isRetryable && attempt < maxAttempts - 1) {
                    Timber.w(e, "$operation failed (attempt ${attempt + 1}/$maxAttempts): ${e.message}. Retrying..")
                    delay(retryDelayMs)
                } else {
                    if (attempt == maxAttempts - 1) {
                        Timber.e(e, "$operation failed after $maxAttempts attempts")
                    }
                    throw e
                }
            } catch (e: Exception) {
                lastException = e
                val isNetworkError = e.message?.let { msg ->
                    msg.contains("timeout", ignoreCase = true) ||
                        msg.contains("network", ignoreCase = true) ||
                        msg.contains("connection", ignoreCase = true)
                } ?: false

                if (isNetworkError && attempt < maxAttempts - 1) {
                    Timber.w(e, "$operation network error (attempt ${attempt + 1}/$maxAttempts). Retrying..")
                    delay(retryDelayMs)
                } else {
                    throw e
                }
            }
        }
        throw lastException ?: Exception("$operation failed after $maxAttempts attempts")
    }
}

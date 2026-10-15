package com.sza.fastmediasorter.data.cloud.helpers

import org.json.JSONObject
import java.io.IOException

/**
 * S4231: why a Drive appdata call failed, in the classes contract DEVICE-EXCHANGE items CQ and CS act on
 * differently - a rate limit backs off, the daily upload limit is reported and never retried in a loop,
 * a full Drive refuses before any byte, a lost token asks for sign-in and deletes nothing.
 */
sealed class DriveAppDataFailure(message: String) : IOException(message) {

    class AuthRequired : DriveAppDataFailure("Google Drive sign-in required")

    class RateLimited(val httpCode: Int) : DriveAppDataFailure("Google Drive rate limit (HTTP $httpCode)")

    class DailyLimit : DriveAppDataFailure("Google Drive daily upload limit reached")

    class StorageFull : DriveAppDataFailure("Google Drive storage is full")

    class ServerError(val httpCode: Int) : DriveAppDataFailure("Google Drive server error (HTTP $httpCode)")

    class NotFound : DriveAppDataFailure("Google Drive entry not found")

    class Network(cause: Throwable?) : DriveAppDataFailure("Google Drive unreachable") {
        init {
            cause?.let(::initCause)
        }
    }

    class Other(val httpCode: Int) : DriveAppDataFailure("Google Drive request failed (HTTP $httpCode)")

    /** True for the failures item CQ answers with back-off rather than a stop. */
    val isTransient: Boolean
        get() = this is RateLimited || this is ServerError || this is Network

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR_MIN = 500
        private const val HTTP_SERVER_ERROR_MAX = 599

        private val rateLimitReasons = setOf("rateLimitExceeded", "userRateLimitExceeded")
        private val dailyLimitReasons = setOf("dailyLimitExceeded", "uploadLimitExceeded")
        private const val STORAGE_REASON = "storageQuotaExceeded"

        /** Classify a non-2xx response; [httpCode] null means no response reached the device. */
        fun classify(httpCode: Int?, errorBody: String?): DriveAppDataFailure {
            val reason = reasonOf(errorBody)
            return when {
                httpCode == null -> Network(null)
                httpCode == HTTP_UNAUTHORIZED -> AuthRequired()
                httpCode == HTTP_TOO_MANY_REQUESTS || reason in rateLimitReasons -> RateLimited(httpCode)
                reason in dailyLimitReasons -> DailyLimit()
                reason == STORAGE_REASON -> StorageFull()
                httpCode == HTTP_NOT_FOUND -> NotFound()
                httpCode in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX -> ServerError(httpCode)
                else -> Other(httpCode)
            }
        }

        // Drive's error body: {"error":{"errors":[{"reason":"rateLimitExceeded", ..}], "code":403, ..}}
        private fun reasonOf(errorBody: String?): String? = errorBody?.let { body ->
            runCatching {
                JSONObject(body).optJSONObject("error")
                    ?.optJSONArray("errors")
                    ?.optJSONObject(0)
                    ?.optString("reason")
                    ?.ifEmpty { null }
            }.getOrNull()
        }
    }
}

package com.sza.fastmediasorter.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sza.fastmediasorter.data.local.db.PendingRevocationDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * Retries OAuth token revocation requests that failed during sign-out (B5-T3).
 *
 * Each pending record stores the provider name, token, and the revoke URL.
 * On success (HTTP 200 or 400 which means "token already invalid") the record is deleted.
 * On network failure the attempt count is incremented. Entries exhausting [MAX_ATTEMPTS] are
 * pruned automatically.
 */
@HiltWorker
class PendingRevocationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val pendingRevocationDao: PendingRevocationDao
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "pending_revocation_worker"
        private const val MAX_ATTEMPTS = 10
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Timber.d("PendingRevocationWorker: start")

        // Drop exhausted entries first so we don't waste time retrying hopeless cases
        pendingRevocationDao.deleteExhausted(MAX_ATTEMPTS)

        val pending = pendingRevocationDao.getAll()
        if (pending.isEmpty()) {
            Timber.d("PendingRevocationWorker: nothing to revoke")
            return@withContext Result.success()
        }

        var anyFailed = false

        for (entry in pending) {
            try {
                val revoked = revokeToken(entry.revokeUrl, entry.token, entry.provider)
                if (revoked) {
                    pendingRevocationDao.deleteById(entry.id)
                    Timber.i("PendingRevocationWorker: revoked ${entry.provider} token (id=${entry.id})")
                } else {
                    pendingRevocationDao.incrementAttemptCount(entry.id)
                    anyFailed = true
                    Timber.w("PendingRevocationWorker: revoke failed for ${entry.provider}, attempt=${entry.attemptCount + 1}")
                }
            } catch (e: Exception) {
                pendingRevocationDao.incrementAttemptCount(entry.id)
                anyFailed = true
                Timber.e(e, "PendingRevocationWorker: exception for ${entry.provider}")
            }
        }

        if (anyFailed) Result.retry() else Result.success()
    }

    /**
     * Performs the HTTP POST revoke call.
     *
     * @return true when the token is considered revoked (HTTP 200/400/401 "invalid_token"),
     *         false on transient network errors worth retrying.
     */
    private fun revokeToken(revokeUrl: String, token: String, provider: String): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(revokeUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            when (provider) {
                "google" -> {
                    // Google: token in query string, no body required
                    val targetUrl = URL("$revokeUrl?token=$token")
                    connection.disconnect()
                    connection = targetUrl.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 10_000
                    connection.readTimeout = 10_000
                    connection.connect()
                }
                "onedrive" -> {
                    // Microsoft: revoke via logout endpoint POST body
                    val body = "token=$token"
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    connection.doOutput = true
                    connection.connect()
                    connection.outputStream.bufferedWriter().use { it.write(body) }
                }
                else -> connection.connect()
            }

            val code = connection.responseCode
            // 200 = revoked; 400 = token invalid/already revoked → either way we're done
            code == 200 || code == 400 || code == 401
        } finally {
            connection?.disconnect()
        }
    }
}

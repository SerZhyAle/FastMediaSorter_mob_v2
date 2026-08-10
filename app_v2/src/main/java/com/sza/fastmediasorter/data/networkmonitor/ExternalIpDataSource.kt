package com.sza.fastmediasorter.data.networkmonitor

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** S1433: what the public echo services could tell us about this device's external address. */
sealed interface ExternalIpResult {

    data class Resolved(val address: String) : ExternalIpResult

    /** Every service refused, timed out, or answered something that is not an address. */
    data object Unavailable : ExternalIpResult
}

/**
 * S1433: asks public IP-echo services for the address the internet sees this device as.
 *
 * Strategic §7 rates the external address a high privacy risk, so the contract is the mitigation: the
 * value is returned to the caller and nowhere else. It is never cached, never written to a preference,
 * never put in the history row, and never logged - only the name of a service that failed is.
 *
 * That promise is why this class does not reuse the application-wide [OkHttpClient] as-is. In debug builds
 * that client carries a Chucker interceptor which persists every response body to its own database, plus
 * an error interceptor that logs the failing URL - and here the response body IS the address. The shared
 * client is therefore taken only for its connection pool and dispatcher, with the interceptor chain
 * dropped, so no debug tooling can quietly retain what this class refuses to keep.
 *
 * Services are asked one at a time in the order fixed by strategic §6.5, the next one only after the
 * previous failed, and the whole attempt is bounded by [TOTAL_BUDGET_MS].
 */
@Singleton
class ExternalIpDataSource @Inject constructor(sharedClient: OkHttpClient) {

    private val client: OkHttpClient = sharedClient.newBuilder()
        .connectTimeout(PER_SERVICE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PER_SERVICE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .build()

    /** Runs only when explicitly invoked; there is no polling and no warm-up call anywhere. */
    suspend fun resolve(): ExternalIpResult {
        val address = withTimeoutOrNull(TOTAL_BUDGET_MS) {
            ECHO_SERVICES.firstNotNullOfOrNull { service ->
                ensureActive()
                query(service)
            }
        }
        return address?.let { ExternalIpResult.Resolved(it) } ?: ExternalIpResult.Unavailable
    }

    /**
     * GET, never HEAD: `api.ipify.org` answers 520 to a HEAD probe and 200 to a GET, so a HEAD-based
     * liveness check would report a healthy service as dead (strategic §6.5).
     */
    private suspend fun query(service: String): String? = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(service).get().build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(EchoCallback(service, continuation))
    }

    private class EchoCallback(
        private val service: String,
        private val continuation: CancellableContinuation<String?>,
    ) : Callback {

        override fun onFailure(call: Call, e: IOException) {
            // A blocked or dead echo service is the expected case, not an error: the caller falls through
            // to the next one. Only the service name is recorded - the address must never reach a log.
            Timber.d("External IP: %s did not answer (%s)", service, e.javaClass.simpleName)
            continuation.resume(null)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response.use { it.readPlausibleAddress() })
        }
    }

    private companion object {

        /**
         * Order fixed by strategic §6.5 and re-verified live on 2026-08-08; the last two are spares asked
         * only after the first three have each failed.
         */
        val ECHO_SERVICES = listOf(
            "https://checkip.amazonaws.com",
            "https://api.ipify.org",
            "https://icanhazip.com",
            "https://ident.me",
            "https://ifconfig.me/ip",
        )

        const val PER_SERVICE_TIMEOUT_MS = 4_000L
        const val TOTAL_BUDGET_MS = 12_000L

        /** 45 chars is the longest possible IPv6 literal in its IPv4-mapped form. */
        const val MAX_ADDRESS_LENGTH = 45

        val IPV4_PATTERN = Regex("""\d{1,3}(\.\d{1,3}){3}""")
        val IPV6_PATTERN = Regex("""[0-9a-fA-F:]{2,45}""")

        /**
         * A service behind a captive portal answers 200 with an HTML page, so the body is accepted only
         * when it actually looks like a bare address.
         */
        fun Response.readPlausibleAddress(): String? {
            if (!isSuccessful) return null
            val text = body?.string()?.trim().orEmpty()
            return text.takeIf { it.isPlausibleAddress() }
        }

        fun String.isPlausibleAddress(): Boolean = length <= MAX_ADDRESS_LENGTH &&
            (matches(IPV4_PATTERN) || (contains(':') && matches(IPV6_PATTERN)))
    }
}

package com.sza.fastmediasorter.data.network.exceptions

import timber.log.Timber

/**
 * Normalizes handled network-control outcomes so background/manual sync paths
 * stop reporting expected external failures as throwable-level defects.
 */
object HandledNetworkOutcomeLogger {

    fun logHandledSyncFailure(
        scope: String,
        resourceLabel: String,
        throwable: Throwable
    ) {
        val result = NetworkErrorClassifier.classifyDetailedSilently(throwable)
        val failureClass = failureClassOf(result.exception)
        val prefix = "[scope=$scope resource=$resourceLabel failureClass=$failureClass]"

        when {
            isPolicyOutcome(result.exception) -> {
                Timber.i("$prefix handled network policy skip")
            }
            !result.usedFallback -> {
                Timber.w("$prefix handled network sync failure")
            }
            else -> {
                Timber.e(throwable, "$prefix unexpected network sync failure")
            }
        }
    }

    private fun isPolicyOutcome(exception: NetworkException): Boolean {
        return exception is WifiRequiredException ||
            exception is LocalNetworkPermissionDeniedException
    }

    private fun failureClassOf(exception: NetworkException): String {
        return when (exception) {
            is WifiRequiredException -> "wifi-required"
            is LocalNetworkPermissionDeniedException -> "local-network-permission"
            is NetworkAccessDeniedException -> "access-denied"
            is NetworkHostKeyChangedException -> "host-key-changed"
            is NetworkTimeoutException -> "timeout"
            is ScanTimeoutException -> "scan-timeout"
            is NetworkFileNotFoundException -> "not-found"
            is NetworkConnectionLostException -> "connection-lost"
            is NetworkUnsupportedOperationException -> "unsupported"
            is NetworkServerErrorException -> "server-error"
            is NetworkRateLimitException -> "rate-limit"
        }
    }
}

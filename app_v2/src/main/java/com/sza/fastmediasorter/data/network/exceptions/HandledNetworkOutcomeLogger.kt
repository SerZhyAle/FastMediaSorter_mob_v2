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
        val prefix = prefixOf(scope, resourceLabel, result.exception)

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

    /**
     * Interactive (user-triggered) connection-test outcome. A precondition the user configured -
     * Wi-Fi-only transport, denied local-network permission - is not a defect: NetworkReachabilityGate
     * already recorded the refusal once at warning level, so repeating it at error level with a full
     * coroutine stack turns an expected outcome into the only red lines in the log. Every other
     * outcome keeps the error-level stack it had before.
     */
    fun logConnectionTestFailure(
        scope: String,
        resourceLabel: String,
        throwable: Throwable,
        message: String
    ) {
        val classified = NetworkErrorClassifier.classifyDetailedSilently(throwable).exception
        val prefix = prefixOf(scope, resourceLabel, classified)

        if (isPolicyOutcome(classified)) {
            Timber.d("$prefix $message - skipped by network policy")
        } else {
            Timber.e(throwable, "$prefix $message")
        }
    }

    private fun prefixOf(scope: String, resourceLabel: String, exception: NetworkException): String =
        "[scope=$scope resource=$resourceLabel failureClass=${failureClassOf(exception)}]"

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

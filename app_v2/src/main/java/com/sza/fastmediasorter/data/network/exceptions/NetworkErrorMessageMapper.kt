package com.sza.fastmediasorter.data.network.exceptions

import android.content.Context
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.network.NetworkContextAnalyzer
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.common.copy.UiMessageFamily
import com.sza.fastmediasorter.ui.common.copy.UiMessageSpec

/**
 * Maps [NetworkException] subtypes to user-facing string resource IDs.
 * Use from Fragment/Activity via context.getString(NetworkErrorMessageMapper.toMessageRes(e)).
 */
object NetworkErrorMessageMapper {

    @StringRes
    fun toMessageRes(exception: NetworkException): Int = when (exception) {
        is LocalNetworkPermissionDeniedException -> R.string.local_network_permission_rationale_message
        is NetworkRateLimitException -> R.string.error_network_rate_limit
        is NetworkServerErrorException -> R.string.error_network_server_error
        is NetworkTimeoutException -> R.string.error_network_timeout
        is ScanTimeoutException -> R.string.error_scan_timeout
        is NetworkAccessDeniedException -> R.string.error_network_access_denied
        is NetworkFileNotFoundException -> R.string.error_network_not_found
        // WifiRequiredException is a NetworkConnectionLostException subclass - must come first
        // so the more specific branch wins before the generic connection-lost branch.
        is WifiRequiredException -> R.string.error_wifi_required_smb
        is NetworkConnectionLostException -> R.string.error_network_connection_lost
        is NetworkUnsupportedOperationException -> R.string.error_network_unsupported
    }

    @StringRes
    fun toMessageRes(throwable: Throwable): Int =
        toMessageRes(NetworkErrorClassifier.classify(throwable))

    /**
     * Returns a context-aware formatted error message string for connectivity errors.
     *
     * Applies enhanced diagnostics for SMB resources:
     * - SMB + cellular → user is not on a local network; shows device IP
     * - SMB + private IP + Wi-Fi timeout → user may be on a different local network
     *
     * Falls back to [toMessageRes] for all other cases.
     *
     * @param context Android context for string formatting
     * @param exception Classified network exception
     * @param resourceType Type of the resource (SMB, FTP, SFTP, CLOUD, LOCAL)
     * @param resourcePath Full path of the resource (used to extract the host)
     * @param contextAnalyzer Provides current network transport and host type info
     */
    fun toContextAwareMessage(
        context: Context,
        exception: NetworkException,
        resourceType: ResourceType,
        resourcePath: String,
        contextAnalyzer: NetworkContextAnalyzer
    ): String {
        val isConnectivityError = exception is NetworkConnectionLostException
                || exception is NetworkTimeoutException

        if (isConnectivityError && !contextAnalyzer.hasAnyNetwork()) {
            return context.getString(R.string.error_network_connection_lost)
        }

        if (isConnectivityError && resourceType == ResourceType.SMB) {
            val host = contextAnalyzer.extractHost(resourcePath)
            if (contextAnalyzer.isCellularNetwork()) {
                return context.getString(R.string.error_smb_mobile_network, host)
            }
            if (contextAnalyzer.isPrivateIpAddress(host)) {
                return context.getString(R.string.error_smb_not_on_local_network)
            }
        }

        return context.getString(toMessageRes(exception))
    }

    /**
     * S0118: Wrap [toContextAwareMessage] as a [UiMessageSpec] so error callers
     * can route through [com.sza.fastmediasorter.ui.common.copy.UiMessageProjector]
     * without rebuilding the friendly-copy contract per surface.
     */
    fun toUiSpec(
        context: Context,
        exception: NetworkException,
        resourceType: ResourceType,
        resourcePath: String,
        contextAnalyzer: NetworkContextAnalyzer,
    ): UiMessageSpec = UiMessageSpec(
        family = UiMessageFamily.ERROR,
        shortMessage = toContextAwareMessage(context, exception, resourceType, resourcePath, contextAnalyzer),
    )
}

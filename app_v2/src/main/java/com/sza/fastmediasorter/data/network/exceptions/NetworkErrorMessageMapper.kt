package com.sza.fastmediasorter.data.network.exceptions

import android.content.Context
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.network.NetworkContextAnalyzer
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.ui.common.copy.UiMessageFamily
import com.sza.fastmediasorter.ui.common.copy.UiMessageSpec
import timber.log.Timber

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
        is NetworkHostKeyChangedException -> R.string.error_network_host_key_changed
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
        contextAnalyzer: NetworkContextAnalyzer,
        accessNote: String? = null
    ): String {
        // S1055: on the resource-open/navigation surface an access-denied on a companion (SFTP/FTP)
        // resource means a credential failure (share deleted+recreated), not a file-permission result,
        // so guide the user to re-pair rather than showing the generic "access denied". Host-key changes
        // are already handled by the exhaustive toMessageRes branch below (non-connectivity early return).
        if (exception is NetworkAccessDeniedException &&
            (resourceType == ResourceType.SFTP || resourceType == ResourceType.FTP)) {
            return context.getString(R.string.error_companion_repair_needed)
        }
        val isConnectivityError = exception is NetworkConnectionLostException
                || exception is NetworkTimeoutException
        if (!isConnectivityError) {
            return context.getString(toMessageRes(exception))
        }

        val companionResource = resourceType == ResourceType.SFTP || resourceType == ResourceType.FTP
        if (!accessNote.isNullOrBlank() || companionResource) {
            Timber.d("S1014: connection guidance shown for $resourceType, accessNote=${!accessNote.isNullOrBlank()}")
        }

        val contextual: String? = when {
            // S1014: the companion knows its own network situation - prefer its guidance verbatim.
            !accessNote.isNullOrBlank() -> accessNote
            !contextAnalyzer.hasAnyNetwork() -> context.getString(R.string.error_network_connection_lost)
            resourceType == ResourceType.SMB -> smbConnectivityMessage(context, resourcePath, contextAnalyzer)
            // S1014: companion-style resources (SFTP/FTP) get actionable access guidance, not a bare timeout.
            companionResource -> context.getString(R.string.error_companion_connect_guidance)
            else -> null
        }
        return contextual ?: context.getString(toMessageRes(exception))
    }

    /** SMB-specific connectivity hint (cellular / off-local-network), or null to fall back to the default. */
    private fun smbConnectivityMessage(
        context: Context,
        resourcePath: String,
        contextAnalyzer: NetworkContextAnalyzer
    ): String? {
        val host = contextAnalyzer.extractHost(resourcePath)
        return when {
            contextAnalyzer.isCellularNetwork() -> context.getString(R.string.error_smb_mobile_network, host)
            contextAnalyzer.isPrivateIpAddress(host) -> context.getString(R.string.error_smb_not_on_local_network)
            else -> null
        }
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
        accessNote: String? = null,
    ): UiMessageSpec = UiMessageSpec(
        family = UiMessageFamily.ERROR,
        shortMessage = toContextAwareMessage(
            context,
            exception,
            resourceType,
            resourcePath,
            contextAnalyzer,
            accessNote,
        ),
    )
}

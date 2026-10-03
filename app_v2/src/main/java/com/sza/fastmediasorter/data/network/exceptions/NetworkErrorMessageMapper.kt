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
        // S1436: the same sentence the permission's own row and its rationale dialog use - an error
        // about a missing permission and the request for it must not word it differently.
        is LocalNetworkPermissionDeniedException -> R.string.perm_rationale_access_local_network
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
        contextAnalyzer: NetworkContextAnalyzer
    ): String {
        // S1055: on the resource-open/navigation surface a credential failure on a companion resource
        // (share deleted+recreated) guides the user to re-pair rather than showing "access denied".
        // SHARE-SESSION rule 7: on SFTP only the typed SSH auth rejection means that - a plain SFTP
        // "permission denied" is an application result (read-only root) and keeps its own message.
        // FTP has no typed auth verdict, so its access-denied keeps the re-pair guidance.
        // Host-key changes are already handled by the exhaustive toMessageRes branch below.
        val isConnectivityError = exception is NetworkConnectionLostException ||
            exception is NetworkTimeoutException
        val isCompanionAuthFailure = when (resourceType) {
            ResourceType.SFTP -> exception is NetworkAuthRejectedException
            ResourceType.FTP -> exception is NetworkAccessDeniedException
            else -> false
        }
        return when {
            isCompanionAuthFailure -> {
                Timber.d("S4033: SFTP/FTP auth rejection mapped to re-pair guidance")
                context.getString(R.string.error_companion_repair_needed)
            }
            !isConnectivityError -> context.getString(toMessageRes(exception))
            else -> {
                val companionResource = resourceType == ResourceType.SFTP || resourceType == ResourceType.FTP
                val contextual: String? = when {
                    !contextAnalyzer.hasAnyNetwork() -> {
                        context.getString(R.string.error_network_connection_lost)
                    }
                    resourceType == ResourceType.SMB -> {
                        smbConnectivityMessage(context, resourcePath, contextAnalyzer)
                    }
                    // Companion notes are export-time snapshots, so use current failure diagnostics instead.
                    companionResource -> context.getString(R.string.error_companion_connect_guidance)
                    else -> null
                }
                contextual ?: context.getString(toMessageRes(exception))
            }
        }
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
    ): UiMessageSpec = UiMessageSpec(
        family = UiMessageFamily.ERROR,
        shortMessage = toContextAwareMessage(
            context,
            exception,
            resourceType,
            resourcePath,
            contextAnalyzer,
        ),
    )
}

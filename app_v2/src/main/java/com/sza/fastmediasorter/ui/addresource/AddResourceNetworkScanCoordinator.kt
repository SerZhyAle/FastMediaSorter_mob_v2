package com.sza.fastmediasorter.ui.addresource

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.usecase.DiscoverNetworkResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.SmbOperationsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Host discovery (ARP/NetBIOS streaming scan) + SMB share listing for a known server.
 * Discovery must respond to cancellation within 1s (spec NF-05).
 */
internal class AddResourceNetworkScanCoordinator(
    private val context: Context,
    private val discoverNetworkResourcesUseCase: DiscoverNetworkResourcesUseCase,
    private val smbOperationsUseCase: SmbOperationsUseCase,
    private val bridge: AddResourceBridge
) {

    // active scan job — held so stopScan() can cancel it mid-stream
    private var networkScanJob: Job? = null

    fun scanNetwork() {
        // kill any in-flight scan before restarting — prevents interleaved emissions
        networkScanJob?.cancel()
        networkScanJob = bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.mutate { it.copy(isScanning = true, foundNetworkHosts = emptyList()) }
            try {
                discoverNetworkResourcesUseCase.execute().collect { host ->
                    // stream each host as soon as it's discovered so the list fills progressively
                    bridge.mutate { state ->
                        state.copy(foundNetworkHosts = state.foundNetworkHosts + host)
                    }
                }
            } catch (e: CancellationException) {
                Timber.d("Network scan cancelled by user")
            } catch (e: Exception) {
                Timber.e(e, "Error scanning network")
                bridge.emit(AddResourceEvent.ShowError("Network scan failed: ${e.message}"))
            } finally {
                bridge.mutate { it.copy(isScanning = false) }
            }
        }
    }

    fun stopScan() {
        networkScanJob?.cancel()
        networkScanJob = null
        bridge.mutate { it.copy(isScanning = false) }
    }

    fun scanShares(
        server: String,
        username: String,
        password: String,
        domain: String,
        port: Int
    ) {
        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.mutate { it.copy(isScanningShares = true, foundShares = emptyList()) }
            try {
                smbOperationsUseCase.listShares(server, username, password, domain, port)
                    .onSuccess { shares ->
                        bridge.mutate { it.copy(foundShares = shares, isScanningShares = false) }
                        if (shares.isNotEmpty()) {
                            bridge.emit(AddResourceEvent.ShowSharePicker(server, shares))
                        } else {
                            // empty result — manual entry remains the primary fallback
                            bridge.emit(AddResourceEvent.ShowMessage(
                                context.getString(R.string.msg_no_shares_found)
                            ))
                        }
                    }
                    .onFailure { e ->
                        Timber.e(e, "Failed to scan shares for $server")
                        bridge.mutate { it.copy(isScanningShares = false) }
                        bridge.emit(AddResourceEvent.ShowError(
                            context.getString(R.string.msg_share_scan_failed, e.message ?: "")
                        ))
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during share scan")
                bridge.mutate { it.copy(isScanningShares = false) }
                bridge.emit(AddResourceEvent.ShowError(
                    context.getString(R.string.msg_share_scan_failed, e.message ?: "")
                ))
            }
        }
    }
}

package com.sza.fastmediasorter.ui.addresource

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
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
    // S0064: injected to load per-server history of previously used share names.
    private val credentialsRepository: NetworkCredentialsRepository,
    private val bridge: AddResourceBridge
) {

    // active scan job — held so stopScan() can cancel it mid-stream
    private var networkScanJob: Job? = null

    fun scanNetwork() {
        if (!PermissionHelper.hasLocalNetworkPermission(context)) {
            bridge.emit(AddResourceEvent.ShowLocalNetworkPermission)
            return
        }
        // kill any in-flight scan before restarting — prevents interleaved emissions
        networkScanJob?.cancel()
        networkScanJob = bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.mutate { it.copy(isScanning = true, foundNetworkHosts = emptyList(), scanProgress = null) }
            try {
                discoverNetworkResourcesUseCase.execute(
                    onProgress = { subnet, probed, total ->
                        // Called from IO threads — StateFlow.update is thread-safe.
                        bridge.mutate { it.copy(scanProgress = ScanProgress(subnet, probed, total)) }
                    }
                ).collect { host ->
                    // Stream each host as soon as it's discovered so the list fills progressively.
                    bridge.mutate { state ->
                        state.copy(foundNetworkHosts = state.foundNetworkHosts + host)
                    }
                }
            } catch (e: CancellationException) {
                Timber.d("Network scan cancelled by user")
            } catch (e: Exception) {
                Timber.e(e, "Error scanning network")
                bridge.emit(AddResourceEvent.ShowError(context.getString(R.string.addresource_scan_failed_short)))
            } finally {
                bridge.mutate { it.copy(isScanning = false, scanProgress = null) }
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
        if (!PermissionHelper.hasLocalNetworkPermission(context)) {
            bridge.emit(AddResourceEvent.ShowLocalNetworkPermission)
            return
        }
        bridge.vmScope.launch(bridge.ioDispatcher + bridge.exHandler) {
            bridge.mutate { it.copy(isScanningShares = true, foundShares = emptyList()) }
            try {
                // S0064: Load previously saved share names for this server in parallel with the scan.
                val manualShares = try {
                    credentialsRepository.getManualShareNamesForServer(server, port)
                } catch (e: Exception) {
                    Timber.w(e, "Could not load manual share names for $server:$port")
                    emptyList()
                }

                smbOperationsUseCase.listShares(server, username, password, domain, port)
                    .onSuccess { shares ->
                        bridge.mutate { it.copy(foundShares = shares, isScanningShares = false) }
                        if (shares.isNotEmpty() || manualShares.isNotEmpty()) {
                            bridge.emit(AddResourceEvent.ShowSharePicker(server, shares, manualShares))
                        } else {
                            // Both lists empty — guide user to manual entry
                            bridge.emit(AddResourceEvent.ShowMessage(
                                context.getString(R.string.msg_no_shares_found)
                            ))
                        }
                    }
                    .onFailure { e ->
                        Timber.e(e, "Failed to scan shares for $server")
                        bridge.mutate { it.copy(isScanningShares = false) }
                        // S0064: even when auto-scan fails, show the picker if we have history.
                        if (manualShares.isNotEmpty()) {
                            bridge.emit(AddResourceEvent.ShowSharePicker(server, emptyList(), manualShares))
                        } else {
                            bridge.emit(AddResourceEvent.ShowError(
                                context.getString(R.string.msg_share_scan_failed)
                            ))
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error during share scan")
                bridge.mutate { it.copy(isScanningShares = false) }
                bridge.emit(AddResourceEvent.ShowError(
                    context.getString(R.string.msg_share_scan_failed)
                ))
            }
        }
    }
}

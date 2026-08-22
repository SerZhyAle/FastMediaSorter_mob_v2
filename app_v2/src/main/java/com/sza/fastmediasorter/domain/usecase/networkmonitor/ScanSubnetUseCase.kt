package com.sza.fastmediasorter.domain.usecase.networkmonitor

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurement
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurementKind
import com.sza.fastmediasorter.domain.repository.NetworkMeasurementHistoryRepository
import com.sza.fastmediasorter.domain.usecase.DiscoverNetworkResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.NetworkHost
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/** S1433: what the scan should cover. */
sealed interface SubnetScanTarget {

    /** The device's own /24, addresses 1..254. */
    data object DeviceSubnet : SubnetScanTarget

    /** An explicit inclusive IPv4 range, both ends as dotted quads. */
    data class AddressRange(val firstAddress: String, val lastAddress: String) : SubnetScanTarget
}

/** S1433: progress and outcome of one subnet scan. */
sealed interface SubnetScanState {

    data class Started(val addressCount: Int) : SubnetScanState

    data class HostFound(val host: NetworkHost) : SubnetScanState

    data class Progress(val probed: Int, val total: Int) : SubnetScanState

    data class Finished(val hostsFound: Int, val probed: Int) : SubnetScanState

    /** API 37 and above gate local-network traffic behind a runtime grant the user has not given. */
    data object LocalNetworkPermissionMissing : SubnetScanState

    /** The device has no usable IPv4 address, so there is no own subnet to derive. */
    data object SubnetUnknown : SubnetScanState

    /** The supplied range is refused outright rather than silently truncated to the cap. */
    data class RangeTooLarge(val requested: Long, val cap: Int) : SubnetScanState

    data class RangeInvalid(val reason: String) : SubnetScanState
}

/**
 * S1433: probes the local network for reachable hosts, bounded in every direction.
 *
 * The probing itself is [DiscoverNetworkResourcesUseCase]'s, not a second implementation: that class
 * already solves the per-port timeouts and the concurrent-probe pattern, and two probers would drift.
 * What this use case adds is the address enumeration the discovery flow does not have - an explicit range
 * instead of a fixed /24 - and the bounds strategic §7 asks for, because an unbounded sweep on a corporate
 * network reads as an attack and can get the device blocked.
 *
 * A range above [MAX_SCANNED_ADDRESSES] is refused, never truncated: quietly scanning the first 1024
 * addresses of a /16 would report "no hosts" about a network it barely looked at.
 */
class ScanSubnetUseCase @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val prober: DiscoverNetworkResourcesUseCase,
    private val historyRepository: NetworkMeasurementHistoryRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    operator fun invoke(
        target: SubnetScanTarget,
        networkLabel: String
    ): Flow<SubnetScanState> = channelFlow {
        if (!hasLocalNetworkAccess()) {
            send(SubnetScanState.LocalNetworkPermissionMissing)
            return@channelFlow
        }
        val addresses = when (val resolved = resolveAddresses(target)) {
            is AddressResolution.Refused -> {
                send(resolved.state)
                return@channelFlow
            }
            is AddressResolution.Resolved -> resolved.addresses
        }

        send(SubnetScanState.Started(addresses.size))
        val found = AtomicInteger(0)
        val probed = AtomicInteger(0)
        try {
            probeAll(
                addresses = addresses,
                onProbed = { done -> if (isActive) send(SubnetScanState.Progress(done, addresses.size)) },
                onHost = { host ->
                    found.incrementAndGet()
                    if (isActive) send(SubnetScanState.HostFound(host))
                },
                probed = probed
            )
            send(SubnetScanState.Finished(found.get(), probed.get()))
        } finally {
            // The summary is recorded on cancellation too - a scan the user stopped halfway is still a
            // measurement, and NonCancellable is what lets the write survive the cancelled scope.
            withContext(NonCancellable) {
                historyRepository.record(
                    summary(networkLabel, addresses.size, found.get(), probed.get())
                )
            }
        }
    }.flowOn(ioDispatcher)

    private suspend fun probeAll(
        addresses: List<String>,
        probed: AtomicInteger,
        onProbed: suspend (Int) -> Unit,
        onHost: suspend (NetworkHost) -> Unit
    ) = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENT_PROBES)
        addresses.map { address ->
            async {
                semaphore.withPermit {
                    if (!isActive) return@withPermit
                    val openPorts = prober.probePorts(address)
                    onProbed(probed.incrementAndGet())
                    if (openPorts.isNotEmpty()) {
                        onHost(NetworkHost(ip = address, hostname = address, openPorts = openPorts))
                    }
                }
            }
        }.awaitAll()
    }

    private fun resolveAddresses(target: SubnetScanTarget): AddressResolution = when (target) {
        SubnetScanTarget.DeviceSubnet -> resolveDeviceSubnet()
        is SubnetScanTarget.AddressRange -> resolveRange(target)
    }

    /**
     * S1617: the range [SubnetScanTarget.DeviceSubnet] would scan, or null when the device has no usable
     * IPv4 address.
     *
     * Exposed so the screen can show the default instead of leaving two empty fields that silently mean
     * "this network". The screen must not derive the range itself: two derivations of one range drift the
     * moment either is touched, and the scan is the one that has to be right.
     *
     * Suspending on the IO dispatcher rather than a plain call: finding the address enumerates every
     * network interface on the device, and the first version of this did it on the main thread at screen
     * open - blocking work for a value that is only a hint.
     */
    suspend fun deviceSubnetRange(): SubnetScanTarget.AddressRange? = withContext(ioDispatcher) {
        val prefix = deviceSubnetPrefix() ?: return@withContext null
        SubnetScanTarget.AddressRange("$prefix.$FIRST_HOST_OCTET", "$prefix.$LAST_HOST_OCTET")
    }

    private fun deviceSubnetPrefix(): String? =
        prober.getLocalIpAddress()?.let { localIp -> prober.extractSubnet(localIp) }

    private fun resolveDeviceSubnet(): AddressResolution {
        val prefix = deviceSubnetPrefix()
            ?: return AddressResolution.Refused(SubnetScanState.SubnetUnknown)
        val localIp = prober.getLocalIpAddress()
        val addresses = (FIRST_HOST_OCTET..LAST_HOST_OCTET)
            .map { octet -> "$prefix.$octet" }
            .filterNot { it == localIp }
        return AddressResolution.Resolved(addresses)
    }

    private fun resolveRange(range: SubnetScanTarget.AddressRange): AddressResolution {
        val first = range.firstAddress.toIpv4Long()
        val last = range.lastAddress.toIpv4Long()
        val count = if (first != null && last != null) last - first + 1 else 0L
        return when {
            first == null -> AddressResolution.Refused(SubnetScanState.RangeInvalid(range.firstAddress))
            last == null -> AddressResolution.Refused(SubnetScanState.RangeInvalid(range.lastAddress))
            count <= 0L -> AddressResolution.Refused(
                SubnetScanState.RangeInvalid("${range.firstAddress}..${range.lastAddress}")
            )
            count > MAX_SCANNED_ADDRESSES -> AddressResolution.Refused(
                SubnetScanState.RangeTooLarge(count, MAX_SCANNED_ADDRESSES)
            )
            else -> AddressResolution.Resolved((first..last).map { it.toIpv4String() })
        }
    }

    private fun hasLocalNetworkAccess(): Boolean {
        // The constant does not exist in compileSdk 36, so the permission is named as a literal and the
        // level as a number; the grant only becomes relevant on the API level that introduces it.
        if (Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_SDK) return true
        val granted = ContextCompat.checkSelfPermission(context, ACCESS_LOCAL_NETWORK)
        return granted == PackageManager.PERMISSION_GRANTED
    }

    private fun summary(
        networkLabel: String,
        addressCount: Int,
        hostsFound: Int,
        probed: Int
    ): NetworkMeasurement = NetworkMeasurement(
        takenAtMillis = System.currentTimeMillis(),
        kind = NetworkMeasurementKind.SUBNET_SCAN,
        networkLabel = networkLabel,
        resultText = "$hostsFound host(s) on $probed of $addressCount address(es)",
        succeeded = hostsFound > 0
    )

    private fun String.toIpv4Long(): Long? {
        val octets = split(".").mapNotNull { it.toIntOrNull() }
        val wellFormed = octets.size == IPV4_OCTETS && octets.all { it in 0..MAX_OCTET }
        return if (wellFormed) {
            octets.fold(0L) { packed, octet -> (packed shl OCTET_BITS) or octet.toLong() }
        } else {
            null
        }
    }

    private fun Long.toIpv4String(): String =
        (IPV4_OCTETS - 1 downTo 0).joinToString(".") { position ->
            ((this shr (OCTET_BITS * position)) and MAX_OCTET.toLong()).toString()
        }

    private sealed interface AddressResolution {
        data class Resolved(val addresses: List<String>) : AddressResolution
        data class Refused(val state: SubnetScanState) : AddressResolution
    }

    private companion object {
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        const val LOCAL_NETWORK_PERMISSION_SDK = 37
        const val MAX_SCANNED_ADDRESSES = 1024
        const val MAX_CONCURRENT_PROBES = 20
        const val FIRST_HOST_OCTET = 1
        const val LAST_HOST_OCTET = 254
        const val IPV4_OCTETS = 4
        const val MAX_OCTET = 255
        const val OCTET_BITS = 8
    }
}

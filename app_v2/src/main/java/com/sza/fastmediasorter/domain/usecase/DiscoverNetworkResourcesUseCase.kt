package com.sza.fastmediasorter.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

data class NetworkHost(
    val ip: String,
    val hostname: String,
    val openPorts: List<Int> // 445/139 (SMB), 21 (FTP), 22 (SFTP)
)

/**
 * Discovers SMB/FTP/SFTP-capable hosts on the local network using TCP port probing.
 *
 * Strategy:
 * 1. Detect the active IPv4 subnet from the device's network interfaces.
 * 2. Probe all 254 hosts in that subnet with bounded concurrency (max 20 concurrent probes).
 * 3. For SMB: probe port 445 first; fall back to port 139 only if 445 is closed.
 * 4. Emit each discovered host to the Flow as soon as it is found (streaming).
 * 5. If the primary subnet yields no results, try fallback private subnets
 *    (192.168.0.* and 192.168.1.*), skipping any that match the detected subnet.
 *
 * Cancellation: the caller cancels the collecting coroutine to stop the scan.
 * Structured concurrency guarantees all child probes are cancelled within <1 second.
 */
open class DiscoverNetworkResourcesUseCase @Inject constructor() {

    companion object {
        // Timeout per SMB port probe. Spec NF-01: 200-500 ms.
        private const val SMB_PROBE_TIMEOUT_MS = 300
        // Shorter timeout for FTP/SFTP (usually faster to reject or accept).
        private const val OTHER_PROBE_TIMEOUT_MS = 200
        // Spec NF-03: max 10-20 concurrent probes.
        private const val MAX_CONCURRENT_PROBES = 20
        // Fallback private subnets to try when the primary scan finds nothing.
        internal val FALLBACK_SUBNETS = listOf("192.168.0", "192.168.1")
    }

    /**
     * Returns a cold Flow that emits discovered [NetworkHost] objects in real time.
     * Cancel the collecting coroutine to stop the scan immediately.
     *
     * @param onProgress called from background threads after each IP is probed;
     *   args: subnet prefix (e.g. "192.168.1"), probed count, total (always 254).
     *   Safe to mutate UI state via StateFlow - never called from the main thread.
     */
    suspend fun execute(
        onProgress: ((subnet: String, probed: Int, total: Int) -> Unit)? = null
    ): Flow<NetworkHost> = channelFlow {
        val localIp = getLocalIpAddress() ?: run {
            Timber.w("DiscoverNetworkResourcesUseCase: could not detect local IPv4 address")
            return@channelFlow
        }
        val detectedSubnet = extractSubnet(localIp)
        Timber.d("Network scan: primary subnet=$detectedSubnet.*, localIp=$localIp")

        // Phase 1: scan the detected subnet.
        val primaryCount = scanSubnet(detectedSubnet, skipIp = localIp, onProgress = onProgress) { host ->
            if (isActive) send(host)
        }
        Timber.d("Network scan: primary subnet found $primaryCount host(s)")

        // Phase 2: if primary was empty, try fallback private subnets.
        if (primaryCount == 0 && isActive) {
            val fallbacks = FALLBACK_SUBNETS.filter { it != detectedSubnet }
            Timber.d("Network scan: trying fallback subnets: $fallbacks")
            for (subnet in fallbacks) {
                if (!isActive) break
                val count = scanSubnet(subnet, skipIp = null, onProgress = onProgress) { host ->
                    if (isActive) send(host)
                }
                Timber.d("Network scan: fallback subnet $subnet.* found $count host(s)")
            }
        }
        Timber.d("Network scan complete")
    }

    /**
     * Probes all 254 addresses in [subnet] (x.y.z.1..254) in parallel, bounded
     * by [MAX_CONCURRENT_PROBES]. Returns the number of hosts discovered.
     *
     * [skipIp] is the device's own IP address - skipped to avoid self-probing.
     * [onProgress] receives (subnet, probedCount, 254) after each IP is resolved.
     * [emit] is called for each discovered host; runs on IO, so the caller must
     * guard with `isActive` before forwarding to a channel.
     */
    internal suspend fun scanSubnet(
        subnet: String,
        skipIp: String?,
        onProgress: ((subnet: String, probed: Int, total: Int) -> Unit)? = null,
        emit: suspend (NetworkHost) -> Unit
    ): Int = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENT_PROBES)
        val foundCount = AtomicInteger(0)
        val probedCount = AtomicInteger(0)
        val total = 254

        (1..254).map { i ->
            async(Dispatchers.IO) {
                val ip = "$subnet.$i"
                if (ip == skipIp) {
                    onProgress?.invoke(subnet, probedCount.incrementAndGet(), total)
                    return@async
                }

                semaphore.withPermit {
                    if (!isActive) return@withPermit
                    val openPorts = probePorts(ip)
                    onProgress?.invoke(subnet, probedCount.incrementAndGet(), total)
                    if (openPorts.isNotEmpty()) {
                        foundCount.incrementAndGet()
                        val host = resolveHost(ip, openPorts)
                        emit(host)
                    }
                }
            }
        }.awaitAll()

        foundCount.get()
    }

    /**
     * Probes SMB-oriented ports on [ip] in parallel:
     * - Ports 445, 139, 21, 22 are all fired simultaneously.
     * - 445 and 139 are mutually exclusive in the result: 139 is added only when 445
     *   is closed (legacy NetBIOS fallback). Reporting 139 separately lets the caller
     *   use the correct port for the SMBJ connection.
     * - Worst-case latency per IP: max(SMB_PROBE_TIMEOUT_MS, OTHER_PROBE_TIMEOUT_MS)
     *   instead of their sum - O(max) vs O(sum).
     */
    internal suspend fun probePorts(ip: String): List<Int> = coroutineScope {
        // Fire all four probes concurrently on IO threads.
        val p445 = async(Dispatchers.IO) { isTcpPortOpen(ip, 445, SMB_PROBE_TIMEOUT_MS) }
        val p139 = async(Dispatchers.IO) { isTcpPortOpen(ip, 139, SMB_PROBE_TIMEOUT_MS) }
        val p21  = async(Dispatchers.IO) { isTcpPortOpen(ip, 21,  OTHER_PROBE_TIMEOUT_MS) }
        val p22  = async(Dispatchers.IO) { isTcpPortOpen(ip, 22,  OTHER_PROBE_TIMEOUT_MS) }

        val open = mutableListOf<Int>()
        // SMB port selection: 445 wins; 139 only if 445 is closed (both already resolved).
        if (p445.await()) open.add(445) else if (p139.await()) open.add(139)
        if (p21.await())  open.add(21)
        if (p22.await())  open.add(22)
        open
    }

    /**
     * Attempts a TCP connection to [ip]:[port] with [timeoutMs] timeout.
     * Returns true if the port accepts the connection, false on any failure.
     *
     * Open for override in tests (see DiscoverNetworkResourcesUseCaseTest).
     */
    internal open fun isTcpPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Resolves the hostname for [ip]. Falls back to the IP string itself on failure.
     */
    private fun resolveHost(ip: String, openPorts: List<Int>): NetworkHost {
        val hostname = try {
            InetAddress.getByName(ip).hostName
        } catch (e: Exception) {
            ip
        }
        return NetworkHost(ip, hostname, openPorts)
    }

    /**
     * Extracts the /24 subnet prefix from an IPv4 address (e.g. "192.168.1.5" → "192.168.1").
     */
    internal fun extractSubnet(ip: String): String = ip.substringBeforeLast(".")

    /**
     * Returns the device's first non-loopback IPv4 address, or null if unavailable.
     */
    internal open fun getLocalIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                ?.filter { !it.isLoopback && it.isUp }
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.filterIsInstance<InetAddress>()
                ?.firstOrNull { it is java.net.Inet4Address }
                ?.hostAddress
        } catch (e: Exception) {
            Timber.w(e, "Failed to detect local IP address")
            null
        }
    }
}

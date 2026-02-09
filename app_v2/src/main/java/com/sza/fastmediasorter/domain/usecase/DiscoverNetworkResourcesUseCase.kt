package com.sza.fastmediasorter.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import javax.inject.Inject

data class NetworkHost(
    val ip: String,
    val hostname: String,
    val openPorts: List<Int> // 445 (SMB), 21 (FTP), 22 (SFTP)
)

class DiscoverNetworkResourcesUseCase @Inject constructor() {

    suspend fun execute(): Flow<NetworkHost> = channelFlow {
        val localIp = getLocalIpAddress() ?: return@channelFlow
        val subnet = localIp.substringBeforeLast(".")
        
        // Scan range 1-254
        val range = 1..254
        
        // Chunk the range to avoid too many concurrent coroutines if needed, 
        // but with IO dispatcher thousands are fine. We'll batch slightly to be safe.
        range.chunked(20).forEach { batch ->
            val jobs = batch.map { i ->
                async(Dispatchers.IO) {
                    val ip = "$subnet.$i"
                    if (ip == localIp) return@async null // Skip self
                    
                    // First check if reachable (ICMP or just timeout check)
                    // ICMP might be blocked, so we might just try connect directly
                    // Optimization: check IsReachable first with short timeout?
                    // Android's isReachable uses ICMP but requires root or falls back to port 7 (echo)
                    // Ideally we just try to connect to ports.
                    
                    val openPorts = checkPorts(ip)
                    if (openPorts.isNotEmpty()) {
                        try {
                            val inetAddr = InetAddress.getByName(ip)
                            NetworkHost(ip, inetAddr.hostName, openPorts)
                        } catch (e: Exception) {
                            NetworkHost(ip, ip, openPorts)
                        }
                    } else {
                        null
                    }
                }
            }
            
            jobs.awaitAll().filterNotNull().forEach { host ->
                send(host)
            }
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    // Check for IPv4
                    if (addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun checkPorts(ip: String): List<Int> {
        val openPorts = mutableListOf<Int>()
        val portsToCheck = listOf(445, 21, 22) // SMB, FTP, SFTP
        
        portsToCheck.forEach { port ->
            try {
                Socket().use { socket ->
                    val sockaddr = InetSocketAddress(ip, port)
                    socket.connect(sockaddr, 200) // 200ms timeout per port
                    openPorts.add(port)
                }
            } catch (e: Exception) {
                // Ignore connection failure
            }
        }
        return openPorts
    }
}

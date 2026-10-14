package com.sza.fastmediasorter.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sza.fastmediasorter.domain.model.ExchangeCheckEvidence
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads, without a request and without a permission beyond the network state, the three facts of this device
 * that the check ladder of contract DEVICE-EXCHANGE 25 item CY lets the program test itself: whether a VPN
 * carries the active network and whether that network gives this device an IPv4 and a global IPv6 address.
 * A fact the platform does not report stays null, which the ladder reads as "not tested".
 */
@Singleton
class ExchangeNetworkEvidenceReader @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun read(certificateTimeInvalid: Boolean = false): ExchangeCheckEvidence {
        val manager = connectivityManager
            ?: return ExchangeCheckEvidence(certificateTimeInvalid = certificateTimeInvalid)
        return try {
            val network = manager.activeNetwork
            val addresses = network?.let(manager::getLinkProperties)?.linkAddresses.orEmpty().map { it.address }
            ExchangeCheckEvidence(
                vpnActive = network?.let(manager::getNetworkCapabilities)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                deviceHasIpv4 = network?.let { addresses.any { it is Inet4Address } },
                deviceHasIpv6 = network?.let { addresses.any(::isGlobalIpv6) },
                certificateTimeInvalid = certificateTimeInvalid,
            )
        } catch (e: SecurityException) {
            Timber.w(e, "ExchangeNetworkEvidenceReader: the network state was not readable")
            ExchangeCheckEvidence(certificateTimeInvalid = certificateTimeInvalid)
        }
    }

    // A link-local or unique-local address does not reach a server named by a public IPv6 address.
    private fun isGlobalIpv6(address: InetAddress): Boolean = address is Inet6Address &&
        !address.isLinkLocalAddress &&
        !address.isLoopbackAddress &&
        !address.isSiteLocalAddress &&
        (address.address[0].toInt() and ULA_MASK) != ULA_VALUE

    private companion object {
        const val ULA_MASK = 0xFE
        const val ULA_VALUE = 0xFC
    }
}

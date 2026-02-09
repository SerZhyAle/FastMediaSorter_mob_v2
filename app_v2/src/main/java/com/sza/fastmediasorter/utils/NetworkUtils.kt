package com.sza.fastmediasorter.utils

import android.content.Context
import android.net.wifi.WifiManager
import timber.log.Timber
import java.net.NetworkInterface

object NetworkUtils {
    /**
     * Get device's local IP address
     * Returns IP in format "192.168.1.100" or null if not found
     */
    fun getLocalIpAddress(context: Context): String? {
        try {
            // Try WiFi first
            @Suppress("DEPRECATION")
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            wifiManager?.connectionInfo?.let { wifiInfo ->
                @Suppress("DEPRECATION")
                val ipInt = wifiInfo.ipAddress
                if (ipInt != 0) {
                    return String.format(
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                }
            }

            // Try all network interfaces
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress?.contains(':') == false) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting local IP address")
        }
        return null
    }
}

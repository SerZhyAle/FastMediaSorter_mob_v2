package com.sza.fastmediasorter.data.remote.sftp

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1013: discovers companion SFTP servers announced on the local network as `_sftp-fms._tcp` and keeps a
 * live map of canonical host-key fingerprint -> current LAN endpoint. [SftpEndpointResolver] consults it,
 * so a companion resource on the same Wi-Fi connects via its live local address even when the imported
 * config carried no or a stale LAN address, or the PC changed its address on the network.
 *
 * Match is by host-key fingerprint (companion publishes it in the service TXT record as `fp=`), which is
 * stable across address changes and reuses the S0046 pin. Discovery is scoped to app foreground via
 * [ProcessLifecycleOwner] and holds a Wi-Fi multicast lock only while active, so it costs nothing in the
 * background. When the network blocks mDNS the map stays empty and connection falls back to the config
 * addresses via [SftpEndpointResolver] (S1006).
 */
@Singleton
class CompanionMdnsDiscovery @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DefaultLifecycleObserver {

    // canonical fingerprint -> live LAN endpoint of the announcing companion.
    private val discovered = ConcurrentHashMap<String, HostPort>()

    private val nsdManager: NsdManager? = context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val multicastLock: WifiManager.MulticastLock? = runCatching {
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock(MULTICAST_LOCK_TAG)?.apply { setReferenceCounted(false) }
    }.getOrNull()

    @Volatile
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    // NsdManager.resolveService allows only one in-flight resolve before API 34; serialise them.
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()

    @Volatile
    private var resolveInFlight = false

    init {
        // Attaching the lifecycle observer must run on the main thread; Hilt may build this off it.
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }
    }

    /** Live LAN endpoint of the companion whose announced host key matches [canonicalFingerprint], or null. */
    fun endpointForFingerprint(canonicalFingerprint: String): HostPort? = discovered[canonicalFingerprint]

    override fun onStart(owner: LifecycleOwner) = startDiscovery()

    override fun onStop(owner: LifecycleOwner) = stopDiscovery()

    /**
     * Symmetric teardown for the lifecycle observer attached in [init]: stops any active discovery and
     * detaches the process-lifecycle observer. Not needed during normal app life (this is an
     * application-scoped singleton observing the application-scoped [ProcessLifecycleOwner], both of
     * which live for the process), but present so the registration is reversible for tests and any
     * future scoped ownership.
     */
    fun release() {
        stopDiscovery()
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        }
    }

    @Synchronized
    private fun startDiscovery() {
        val manager = nsdManager ?: return
        if (discoveryListener != null) return
        val listener = buildDiscoveryListener(manager)
        discoveryListener = listener
        runCatching { multicastLock?.acquire() }
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "mDNS discovery start failed")
            discoveryListener = null
            releaseLock()
        }
    }

    @Synchronized
    private fun stopDiscovery() {
        val manager = nsdManager ?: return
        discoveryListener?.let { listener -> runCatching { manager.stopServiceDiscovery(listener) } }
        discoveryListener = null
        resolveQueue.clear()
        discovered.clear()
        releaseLock()
    }

    private fun releaseLock() {
        if (multicastLock?.isHeld == true) runCatching { multicastLock?.release() }
    }

    private fun buildDiscoveryListener(manager: NsdManager) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String?) = Unit
        override fun onDiscoveryStopped(serviceType: String?) = Unit
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
        override fun onServiceLost(serviceInfo: NsdServiceInfo?) = Unit

        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
            serviceInfo ?: return
            if (!serviceInfo.serviceType.orEmpty().contains(SERVICE_TYPE_CORE)) return
            enqueueResolve(manager, serviceInfo)
        }
    }

    @Synchronized
    private fun enqueueResolve(manager: NsdManager, info: NsdServiceInfo) {
        resolveQueue.addLast(info)
        pumpResolveQueue(manager)
    }

    @Synchronized
    private fun pumpResolveQueue(manager: NsdManager) {
        if (resolveInFlight) return
        val next = resolveQueue.pollFirst() ?: return
        resolveInFlight = true
        manager.resolveService(
            next,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) =
                    onResolveDone(manager)

                override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                    serviceInfo?.let { record(it) }
                    onResolveDone(manager)
                }
            }
        )
    }

    @Synchronized
    private fun onResolveDone(manager: NsdManager) {
        resolveInFlight = false
        pumpResolveQueue(manager)
    }

    @Suppress("DEPRECATION") // NsdServiceInfo.host is the cross-version accessor; getHostAddresses is API 34+.
    private fun record(info: NsdServiceInfo) {
        val host = info.host?.hostAddress
        val port = info.port
        val rawFp = info.attributes?.get(TXT_FINGERPRINT)?.toString(Charsets.UTF_8)
        val canonical = rawFp?.let { SshFingerprintNormalizer.canonical(it) }
        if (host == null || port <= 0 || canonical == null) return
        discovered[canonical] = HostPort(host, port)
        Timber.d("S1013: discovered companion $host:$port fp=${canonical.take(FP_LOG_CHARS)}")
    }

    companion object {
        private const val SERVICE_TYPE = "_sftp-fms._tcp"
        private const val SERVICE_TYPE_CORE = "_sftp-fms"
        private const val TXT_FINGERPRINT = "fp"
        private const val MULTICAST_LOCK_TAG = "fms-mdns"
        private const val FP_LOG_CHARS = 24
    }
}

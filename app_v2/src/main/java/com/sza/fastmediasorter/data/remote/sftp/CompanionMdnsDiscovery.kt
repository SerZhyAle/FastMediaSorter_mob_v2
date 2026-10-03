package com.sza.fastmediasorter.data.remote.sftp

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sza.fastmediasorter.domain.model.HostPort
import com.sza.fastmediasorter.utils.SshFingerprintNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.ArrayDeque
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

    private val cache = CompanionServiceCache()

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

    // Bumped by stopDiscovery: a resolve callback from an earlier discovery run must not clear the
    // in-flight flag of a resolve the next run started.
    private var resolveGeneration = 0

    init {
        // Attaching the lifecycle observer must run on the main thread; Hilt may build this off it.
        Handler(Looper.getMainLooper()).post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }
    }

    /**
     * Live LAN endpoint of the companion whose announced host key matches [canonicalFingerprint], or null.
     * An entry past its TTL is still returned while the one re-resolve it triggered runs (LAN-DISCOVERY 5).
     */
    fun endpointForFingerprint(canonicalFingerprint: String): HostPort? {
        val hit = cache.lookup(canonicalFingerprint, SystemClock.elapsedRealtime()) ?: return null
        hit.probeServiceName?.let(::probeService)
        return hit.endpoint
    }

    /** The discovered endpoint of [canonicalFingerprint] failed a connection: probe it once before dropping it. */
    fun onEndpointUnreachable(canonicalFingerprint: String) {
        cache.requestProbeForFingerprint(canonicalFingerprint)?.let(::probeService)
    }

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
            Timber.w(e, "mDNS discovery start rejected")
            abortStart()
        } catch (e: SecurityException) {
            // API 37+ without ACCESS_LOCAL_NETWORK: degrade to config-address fallback (S1006), never crash.
            Timber.w(e, "mDNS discovery blocked by local-network permission")
            abortStart()
        }
    }

    /**
     * Cleanup shared by the synchronous launch failure and the async
     * [NsdManager.DiscoveryListener.onStartDiscoveryFailed] callback: releases the multicast lock
     * acquired in [startDiscovery] and clears [discoveryListener] so a later start can retry. Without
     * it an async start failure (mDNS blocked, or ACCESS_LOCAL_NETWORK denied on API 37+) would leak
     * the lock and wedge discovery until the next onStop.
     */
    @Synchronized
    private fun abortStart() {
        discoveryListener = null
        releaseLock()
    }

    @Synchronized
    private fun stopDiscovery() {
        val manager = nsdManager ?: return
        discoveryListener?.let { listener -> runCatching { manager.stopServiceDiscovery(listener) } }
        discoveryListener = null
        resolveQueue.clear()
        resolveInFlight = false
        resolveGeneration++
        cache.clear()
        releaseLock()
    }

    private fun releaseLock() {
        if (multicastLock?.isHeld == true) runCatching { multicastLock?.release() }
    }

    private fun buildDiscoveryListener(manager: NsdManager) = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String?) = Unit
        override fun onDiscoveryStopped(serviceType: String?) = Unit
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) = abortStart()
        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit

        // A lost announcement is often a Wi-Fi flap, so the entry is re-resolved once, not dropped.
        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            val name = serviceInfo?.serviceName ?: return
            if (cache.requestProbeForService(name)) {
                enqueueResolve(manager, serviceInfo)
            }
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
            serviceInfo ?: return
            if (!serviceInfo.serviceType.orEmpty().contains(SERVICE_TYPE_CORE)) return
            enqueueResolve(manager, serviceInfo)
        }
    }

    /** Queues a re-resolve of [serviceName]; outside an active discovery run the cache is empty anyway. */
    @Synchronized
    private fun probeService(serviceName: String) {
        val manager = nsdManager ?: return
        if (discoveryListener == null) return
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            serviceType = SERVICE_TYPE
        }
        enqueueResolve(manager, info)
    }

    @Synchronized
    private fun enqueueResolve(manager: NsdManager, info: NsdServiceInfo) {
        resolveQueue.addLast(info)
        pumpResolveQueue(manager)
    }

    @Synchronized
    private fun pumpResolveQueue(manager: NsdManager) {
        while (!resolveInFlight) {
            val next = resolveQueue.pollFirst() ?: return
            resolveInFlight = true
            try {
                resolve(manager, next, resolveGeneration)
            } catch (e: IllegalArgumentException) {
                // A rejected resolve delivers no callback; without the reset the queue stays wedged.
                Timber.w(e, "mDNS resolve rejected")
                resolveInFlight = false
            } catch (e: SecurityException) {
                Timber.w(e, "mDNS resolve blocked by local-network permission")
                resolveInFlight = false
            }
        }
    }

    private fun resolve(manager: NsdManager, next: NsdServiceInfo, generation: Int) {
        // S1776 ADR-2: resolveService is deprecated in favour of registerServiceInfoCallback
        // (API 34+), but that replacement is a CONTINUOUS callback with an explicit unregister -
        // a structural rewrite of this one-shot resolve queue - and this file must keep the
        // resolveService path for API 23-33 regardless. A second model in one file adds cost
        // without user benefit, so the one-shot path stays, deliberately.
        @Suppress("DEPRECATION")
        manager.resolveService(
            next,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) =
                    onResolveDone(manager, generation, next.serviceName, resolved = null)

                override fun onServiceResolved(serviceInfo: NsdServiceInfo?) =
                    onResolveDone(manager, generation, next.serviceName, resolved = serviceInfo)
            }
        )
    }

    @Synchronized
    private fun onResolveDone(
        manager: NsdManager,
        generation: Int,
        requestedName: String?,
        resolved: NsdServiceInfo?
    ) {
        if (generation != resolveGeneration) return
        val recorded = requestedName != null && resolved != null && record(resolved, requestedName)
        // Only an entry under probe is dropped, so a failed first resolve never touches the cache.
        if (!recorded && requestedName != null) {
            cache.probeFailed(requestedName)
        }
        resolveInFlight = false
        pumpResolveQueue(manager)
    }

    @Suppress("DEPRECATION") // NsdServiceInfo.host is the cross-version accessor; getHostAddresses is API 34+.
    private fun record(info: NsdServiceInfo, serviceName: String): Boolean {
        val host = info.host?.hostAddress
        val port = info.port
        val rawFp = info.attributes?.get(TXT_FINGERPRINT)?.toString(Charsets.UTF_8)
        val canonical = rawFp?.let { SshFingerprintNormalizer.canonical(it) }
        if (host == null || port <= 0 || canonical == null) return false
        cache.put(canonical, HostPort(host, port), serviceName, SystemClock.elapsedRealtime())
        return true
    }

    companion object {
        private const val SERVICE_TYPE = "_sftp-fms._tcp"
        private const val SERVICE_TYPE_CORE = "_sftp-fms"
        private const val TXT_FINGERPRINT = "fp"
        private const val MULTICAST_LOCK_TAG = "fms-mdns"
    }
}

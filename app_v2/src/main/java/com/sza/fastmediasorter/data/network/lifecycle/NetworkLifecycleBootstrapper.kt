package com.sza.fastmediasorter.data.network.lifecycle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.ProcessLifecycleOwner
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.lifecycle.NetworkLifecycleObserver
import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.data.network.SmbBackgroundLifecycleManager
import com.sza.fastmediasorter.data.network.SmbConnectionManager
import com.sza.fastmediasorter.data.network.SmbResetCallback
import com.sza.fastmediasorter.util.ToastThrottler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-shot bootstrap for process-level network lifecycle infrastructure. S0195.
 *
 * Encapsulates the four sync registrations that used to live inline in
 * `FastMediaSorterApp.onCreate`:
 *  - `ProcessLifecycleOwner.addObserver(smbBackgroundLifecycleManager)` (S0061 Phase 04)
 *  - `networkLifecycleObserver.attach()` (S0067 Phase 06)
 *  - `networkStateMonitor.start()`
 *  - `smbConnectionManager.setResetCallback(...)` (SMB auto-reset UI toast)
 *
 * Every target dependency is held as [dagger.Lazy] so the underlying singleton is
 * not materialised until [ensureInitialized] is first invoked from a consumer-side
 * remote-flow entry boundary (S0195 ADR-1). [AtomicBoolean] guarantees the four
 * registrations execute exactly once across all callers; subsequent invocations
 * are cheap no-ops. Each registration is wrapped in try/catch so a partial failure
 * cannot throw out of [ensureInitialized] — failed registrations are logged via
 * Timber and the process continues with degraded lifecycle semantics.
 *
 * No public API beyond [ensureInitialized]; the registry / observer / manager
 * remain unchanged.
 */
@Singleton
class NetworkLifecycleBootstrapper @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val networkStateMonitor: dagger.Lazy<NetworkStateMonitor>,
    private val smbConnectionManager: dagger.Lazy<SmbConnectionManager>,
    private val smbBackgroundLifecycleManager: dagger.Lazy<SmbBackgroundLifecycleManager>,
    private val networkLifecycleObserver: dagger.Lazy<NetworkLifecycleObserver>,
) {

    private val initialized = AtomicBoolean(false)

    /**
     * Run the four lifecycle registrations exactly once across the process lifetime.
     *
     * Safe to call from any thread. When invoked from a background thread the method
     * dispatches the [LifecycleRegistry.addObserver] calls to the main thread and
     * blocks the calling thread until they complete (required by AndroidX — addObserver
     * must be called on the main thread). The [AtomicBoolean] guard collapses concurrent
     * first calls to a single execution. Subsequent calls return immediately without
     * blocking.
     */
    fun ensureInitialized() {
        if (!initialized.compareAndSet(false, true)) return

        if (Looper.myLooper() == Looper.getMainLooper()) {
            doRegistrations()
        } else {
            // addObserver requires the main thread; synchronously dispatch and wait.
            val latch = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                try {
                    doRegistrations()
                } finally {
                    latch.countDown()
                }
            }
            latch.await()
        }

        Timber.i("S0195: network lifecycle bootstrap complete")
    }

    private fun doRegistrations() {
        // (1) SMB background lifecycle observer — closes UI SMB connections on app stop.
        runCatching {
            ProcessLifecycleOwner.get().lifecycle.addObserver(smbBackgroundLifecycleManager.get())
        }.onFailure { Timber.e(it, "S0195: SmbBackgroundLifecycleManager attach failed") }

        // (2) Protocol-neutral lifecycle observer — closes UI gates on app stop.
        runCatching {
            networkLifecycleObserver.get().attach()
        }.onFailure { Timber.e(it, "S0195: NetworkLifecycleObserver.attach failed") }

        // (3) OS-level network state monitoring (WiFi/IP change detection).
        runCatching {
            networkStateMonitor.get().start()
        }.onFailure { Timber.e(it, "S0195: NetworkStateMonitor.start failed") }

        // (4) SMB auto-reset UI notification callback. Moved verbatim from
        // FastMediaSorterApp.setupSmbAutoReset() — same Toast throttling semantics.
        runCatching {
            smbConnectionManager.get().setResetCallback(object : SmbResetCallback {
                override fun onAutoReset(reason: String) {
                    applicationScope.launch(Dispatchers.Main) {
                        try {
                            val message = context.getString(R.string.smb_auto_reset_notification)
                            ToastThrottler.showNetworkError(
                                context,
                                message,
                                Toast.LENGTH_SHORT,
                            )
                            Timber.d("SMB auto-reset notification shown: $reason")
                        } catch (e: Exception) {
                            Timber.e(e, "S0195: Failed to show SMB auto-reset notification")
                        }
                    }
                }
            })
        }.onFailure { Timber.e(it, "S0195: SmbConnectionManager.setResetCallback failed") }
    }
}

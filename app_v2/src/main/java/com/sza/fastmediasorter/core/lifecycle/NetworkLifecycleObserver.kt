package com.sza.fastmediasorter.core.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sza.fastmediasorter.data.network.lifecycle.ConnectionDiagnostics
import com.sza.fastmediasorter.data.network.lifecycle.ConnectionGateRegistry
import com.sza.fastmediasorter.data.network.lifecycle.ConsumerType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-wide lifecycle observer for the protocol-neutral connection gates. S0067 Phase 06.
 *
 * On `ProcessLifecycleOwner.ON_STOP`:
 *  - iterates every gate in [ConnectionGateRegistry] and calls `closeFor(UI_*)` for each
 *    UI consumer type. `BACKGROUND_WORKER` is never closed by this observer.
 *
 * Independently subscribes to [ConnectionDiagnostics.events] and surfaces
 * [ConnectionDiagnostics.InstabilityWarning] to the user via a throttled snackbar/toast
 * (one toast per `(protocol, resourceKey)` per [INSTABILITY_TOAST_COOLDOWN_MS]).
 */
@Singleton
class NetworkLifecycleObserver @Inject constructor(
    private val registry: ConnectionGateRegistry,
    private val diagnostics: ConnectionDiagnostics
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val toastCooldown = ConcurrentHashMap<String, Long>()

    /** Register on [ProcessLifecycleOwner] and start collecting diagnostics events. */
    fun attach() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            diagnostics.events.collect { warning ->
                handleInstabilityWarning(warning)
            }
        }
        Timber.i("[scope=lifecycle] NetworkLifecycleObserver attached")
    }

    override fun onStop(owner: LifecycleOwner) {
        val gates = registry.all()
        Timber.i("[scope=lifecycle event=ON_STOP gates=${gates.size}] closing UI consumer types")
        for (gate in gates) {
            try {
                gate.closeFor(ConsumerType.UI_SCANNER)
                gate.closeFor(ConsumerType.UI_PLAYER)
                gate.closeFor(ConsumerType.UI_OPERATION)
                // BACKGROUND_WORKER intentionally not invoked.
            } catch (e: Exception) {
                Timber.w(e, "NetworkLifecycleObserver: closeFor failed for ${gate.protocol}")
            }
        }
    }

    private fun handleInstabilityWarning(
        warning: ConnectionDiagnostics.InstabilityWarning
    ) {
        val key = "${warning.protocol}|${warning.resourceKey}"
        val now = System.currentTimeMillis()
        val last = toastCooldown[key]
        if (last != null && now - last < INSTABILITY_TOAST_COOLDOWN_MS) {
            return
        }
        toastCooldown[key] = now
        Timber.w(
            "[scope=lifecycle protocol=${warning.protocol} resource=${warning.resourceKey} " +
                "recreates=${warning.recreateCount}] surfacing instability warning"
        )
        // UI surface is left to the consuming module - intentionally no Toast here so this
        // observer stays free of Android UI dependencies. Phase 06 publishes the warning
        // log; FastMediaSorterApp or a UI bridge may subscribe to diagnostics.events
        // separately to render a snackbar on the active activity.
    }

    companion object {
        private const val INSTABILITY_TOAST_COOLDOWN_MS = 5L * 60 * 1000  // 5 minutes per resource
    }
}

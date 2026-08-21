package com.sza.fastmediasorter.ui.launcher.gadget

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.networkmonitor.NetworkIndicatorFormatter
import com.sza.fastmediasorter.core.panel.InternalRouteCatalog
import com.sza.fastmediasorter.databinding.GadgetLauncherNetworkIndicatorBinding
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkIndicatorReading
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ObserveNetworkIndicatorUseCase
import com.sza.fastmediasorter.domain.usecase.networkmonitor.ResolveNetworkIndicatorOnDemandUseCase
import com.sza.fastmediasorter.widget.networkmonitor.NetworkMonitorIndicator
import com.sza.fastmediasorter.widget.networkmonitor.Refresh
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject

/**
 * S1440: the launcher-desktop counterpart of the Network Monitor home-screen widget.
 *
 * One gadget serves all eight indicators. A desktop cell has no widget id, so the chosen indicator
 * lives in the cell's own `target` param instead of a preferences row - the add flow asks for it at
 * placement and encodes it as `network_indicator:<indicatorKey>` (plus `|<resourceId>` for the
 * reachability indicator).
 *
 * Reads only the Phase-02 seam, which is cold: nothing is observed unless a cell is attached and the
 * host is STARTED, the lifetime [LauncherGadgetView] already enforces.
 */
class NetworkIndicatorGadget @Inject constructor(
    private val observe: Lazy<ObserveNetworkIndicatorUseCase>,
    private val resolve: Lazy<ResolveNetworkIndicatorOnDemandUseCase>,
    private val formatter: Lazy<NetworkIndicatorFormatter>,
) : LauncherGadget {

    override val key: String = LauncherGadgetRegistry.KEY_NETWORK_INDICATOR
    override val defaultSpanW: Int = 2
    override val defaultSpanH: Int = 1
    override val minSpanW: Int = 1
    override val minSpanH: Int = 1
    override val labelRes: Int = R.string.launcher_gadget_network_indicator
    override val iconRes: Int = R.drawable.ic_network_monitor
    override val requiresResourceParam: Boolean = false

    override fun createView(container: FrameLayout, host: LauncherGadgetHost, param: String?): View =
        NetworkIndicatorGadgetView(
            context = container.context,
            host = host,
            param = param,
            observe = observe.get(),
            resolve = resolve.get(),
            formatter = formatter.get(),
        )

    companion object {

        /**
         * Separates the indicator key from an optional resource id inside the cell's param.
         *
         * Deliberately not ':' - `LauncherGadgetRegistry.decodeTarget` splits the stored target on the
         * FIRST ':' only and hands the rest back verbatim, so a second one here would arrive as part of
         * the indicator key.
         *
         * Public because the add flow writes this format and this gadget reads it back; one declaration
         * is what keeps the two halves of a storage format from drifting.
         */
        const val PARAM_SEPARATOR = '|'
    }
}

private class NetworkIndicatorGadgetView(
    context: Context,
    private val host: LauncherGadgetHost,
    param: String?,
    private val observe: ObserveNetworkIndicatorUseCase,
    private val resolve: ResolveNetworkIndicatorOnDemandUseCase,
    private val formatter: NetworkIndicatorFormatter,
) : LauncherGadgetView(context) {

    private val binding =
        GadgetLauncherNetworkIndicatorBinding.inflate(LayoutInflater.from(context), this)

    /** An indicator this build no longer knows resolves to the default rather than blanking the cell. */
    private val indicator = NetworkMonitorIndicator.fromKey(
        param?.substringBefore(NetworkIndicatorGadget.PARAM_SEPARATOR)
    )

    /** Only the reachability indicator carries one; the add flow appends it after the indicator key. */
    private val resourceId =
        param?.substringAfter(NetworkIndicatorGadget.PARAM_SEPARATOR, "")?.toLongOrNull()

    /**
     * The scope [LauncherGadgetView] hands to [onActive], kept so a click can run inside the same
     * attached-and-STARTED lifetime instead of opening one of its own.
     */
    private var activeScope: CoroutineScope? = null

    private var onDemandJob: Job? = null

    init {
        binding.gadgetNetworkIndicatorBody.setOnClickListener { openMonitor() }
        binding.gadgetNetworkIndicatorIcon.setOnClickListener { onIconClicked() }
    }

    override suspend fun CoroutineScope.onActive() {
        Timber.d("S1440: gadget active, indicator=%s", indicator.key)
        activeScope = this
        try {
            observe(indicator, resourceId).collect(::render)
            // The two tap-driven indicators emit Awaiting once and complete. The scope has to outlive
            // that emission or the icon's tap would have nothing left to run its round trip in.
            awaitCancellation()
        } finally {
            activeScope = null
        }
    }

    /**
     * The host owns every launch path (see [LauncherGadgetHost]), so the tile asks for the Monitor
     * route rather than building an Intent of its own.
     *
     * S1440: the indicator's own sub-screen travels with the route as its stable key, so the tile lands
     * where the home widget does; an indicator whose key this build does not know falls back to Summary
     * inside `NetworkMonitorSection.fromKey` rather than failing the open.
     */
    private fun openMonitor() {
        host.run(
            LauncherCellCommand.FeatureSection(
                routeKey = InternalRouteCatalog.KEY_NETWORK_MONITOR,
                sectionKey = indicator.sectionKey,
            )
        )
    }

    /**
     * Strategic 3.4 forbids the surface starting network traffic by itself, so the round trip for the
     * two on-demand indicators begins here and nowhere else. Every other indicator reports by itself,
     * so its icon opens the Monitor like the body does.
     */
    private fun onIconClicked() {
        val scope = activeScope
        if (indicator.refresh != Refresh.ON_TAP || scope == null) {
            openMonitor()
            return
        }
        // A second tap replaces the request in flight; two answers racing into one cell would show
        // whichever finished last rather than the one the user last asked for.
        onDemandJob?.cancel()
        onDemandJob = scope.launch { resolveOnDemand() }
    }

    /**
     * The probe spans DNS, HTTP and Room, so no narrow catch covers what it can raise - and an escaping
     * throw would cancel [onActive] itself, leaving the tile dead until the desktop rebuilt it. A failed
     * probe is an ordinary outcome of asking the network a question, so it renders as one.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun resolveOnDemand() {
        try {
            resolve(indicator, resourceId, networkLabel()).collect(::render)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "NetworkIndicatorGadget: on-demand read failed for %s", indicator.key)
            render(NetworkIndicatorReading.Failed(e.javaClass.simpleName))
        }
    }

    /**
     * The label a measurement is filed under in the Monitor's history.
     *
     * Taken from the local-address reading, whose caption is the current SSID, rather than from the
     * snapshot repository: this tile consumes only the Phase-02 seam, and the fallback matches the one
     * the home widget writes so history rows from both surfaces read the same. Bounded, because a cold
     * indicator flow on a device with no active link may never emit at all.
     */
    private suspend fun networkLabel(): String {
        val reading = withTimeoutOrNull(LABEL_TIMEOUT_MS) {
            observe(NetworkMonitorIndicator.LOCAL_ADDRESS, null).first()
        }
        return (reading as? NetworkIndicatorReading.Value)?.caption ?: UNKNOWN_NETWORK
    }

    private fun render(reading: NetworkIndicatorReading) {
        val formatted = formatter.format(indicator, reading)
        binding.gadgetNetworkIndicatorIcon.setImageResource(formatted.iconRes)
        binding.gadgetNetworkIndicatorValue.text = formatted.primary
        // Without a caption of its own the tile still has to say which of the eight it is showing.
        binding.gadgetNetworkIndicatorCaption.text =
            formatted.caption ?: context.getString(indicator.labelRes)
    }

    private companion object {

        const val UNKNOWN_NETWORK = "unknown"

        const val LABEL_TIMEOUT_MS = 3_000L
    }
}

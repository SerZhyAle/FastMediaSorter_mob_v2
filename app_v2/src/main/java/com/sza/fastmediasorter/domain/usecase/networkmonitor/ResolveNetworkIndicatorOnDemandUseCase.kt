package com.sza.fastmediasorter.domain.usecase.networkmonitor

import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkIndicatorReading
import com.sza.fastmediasorter.domain.model.networkmonitor.SectionAvailability
import com.sza.fastmediasorter.widget.networkmonitor.NetworkMonitorIndicator
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * S1440: the read path for the two indicators that only answer when asked.
 *
 * Separate from the observer because strategic 3.4 forbids refreshing the external address by itself
 * and strategic 4.3 puts both of these in the on-tap class: each costs a network round trip, so it
 * happens on a press and never on a timer.
 *
 * No HTTP client, host list or STUN code is added here. `ExternalIpDataSource` already holds the
 * keyless endpoints strategic 4.5 settled and `RouterWanAddressDataSource` already holds the router
 * probe; a second implementation would drift from the one the Monitor screen shows.
 */
class ResolveNetworkIndicatorOnDemandUseCase @Inject constructor(
    private val resolveExternalIp: Lazy<ResolveExternalIpUseCase>,
    private val checkSelectedResource: Lazy<CheckSelectedResourceUseCase>
) {

    /**
     * The caller is a widget click handler, so an indicator this use case does not own is answered
     * with a [NetworkIndicatorReading.Failed] rather than an exception - a throw crossing a
     * RemoteViews click would take the whole widget down instead of one cell.
     */
    operator fun invoke(
        indicator: NetworkMonitorIndicator,
        resourceId: Long?,
        networkLabel: String
    ): Flow<NetworkIndicatorReading> = when (indicator) {
        NetworkMonitorIndicator.EXTERNAL_ADDRESS -> externalAddress(networkLabel)
        NetworkMonitorIndicator.RESOURCE_REACHABILITY -> resourceReachability(resourceId, networkLabel)
        else -> flowOf(NetworkIndicatorReading.Failed(NOT_ON_DEMAND))
    }

    private fun externalAddress(networkLabel: String): Flow<NetworkIndicatorReading> =
        resolveExternalIp.get().invoke(networkLabel).map { state ->
            when (state) {
                is ExternalIpState.Resolving -> NetworkIndicatorReading.Loading
                // The verdict travels unresolved: strategic 4.6 forbids asserting CGNAT without proof,
                // and only the formatter turns the three cases into their three distinct wordings.
                is ExternalIpState.Resolved -> NetworkIndicatorReading.Value(
                    primary = state.address,
                    caption = null,
                    levelBars = null,
                    verdict = state.verdict
                )

                is ExternalIpState.Unavailable ->
                    NetworkIndicatorReading.Unavailable(SectionAvailability.NoNetwork)
            }
        }

    private fun resourceReachability(
        resourceId: Long?,
        networkLabel: String
    ): Flow<NetworkIndicatorReading> {
        if (resourceId == null) {
            return flowOf(NetworkIndicatorReading.Failed(NO_RESOURCE_CHOSEN))
        }
        return checkSelectedResource.get().invoke(resourceId, networkLabel).map { state ->
            when (state) {
                is ResourceCheckState.Checking -> NetworkIndicatorReading.Loading
                is ResourceCheckState.Reachable -> NetworkIndicatorReading.Value(
                    primary = REACHABLE,
                    caption = state.detail,
                    levelBars = null
                )

                is ResourceCheckState.Unreachable -> NetworkIndicatorReading.Value(
                    primary = UNREACHABLE,
                    caption = state.reason,
                    levelBars = null
                )

                is ResourceCheckState.ResourceMissing ->
                    NetworkIndicatorReading.Failed(RESOURCE_MISSING)
            }
        }
    }

    private companion object {
        const val NOT_ON_DEMAND = "not-an-on-demand-indicator"
        const val NO_RESOURCE_CHOSEN = "no-resource-chosen"
        const val RESOURCE_MISSING = "resource-missing"
        const val REACHABLE = "OK"
        const val UNREACHABLE = "-"
    }
}

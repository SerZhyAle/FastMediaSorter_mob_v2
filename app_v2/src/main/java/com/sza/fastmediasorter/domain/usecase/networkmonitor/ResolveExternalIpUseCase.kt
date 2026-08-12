package com.sza.fastmediasorter.domain.usecase.networkmonitor

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.networkmonitor.ExternalIpDataSource
import com.sza.fastmediasorter.data.networkmonitor.ExternalIpResult
import com.sza.fastmediasorter.data.networkmonitor.RouterWanAddress
import com.sza.fastmediasorter.data.networkmonitor.RouterWanAddressDataSource
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurement
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurementKind
import com.sza.fastmediasorter.domain.repository.NetworkMeasurementHistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * S1433: whether this device sits behind carrier-grade NAT, as far as two addresses can tell.
 *
 * [Unknown] is a first-class answer and not a failure. Most consumer routers answer neither NAT-PMP nor
 * UPnP, and strategic §3.1 item 7 is explicit that a missing router answer proves nothing: reporting CGNAT
 * on silence would tell the majority of users they are behind CGNAT on the strength of no evidence at all.
 */
sealed interface CgnatVerdict {

    /** The router's WAN address equals the address the internet sees - nothing is translating in between. */
    data object Direct : CgnatVerdict

    /** The two differ, which is what a carrier-side translator looks like from here. */
    data object LikelyCgnat : CgnatVerdict

    /** No router answered, so there is no second address to compare against. */
    data object Unknown : CgnatVerdict
}

/** S1433: how the external-address lookup is progressing, and how it ended. */
sealed interface ExternalIpState {

    data object Resolving : ExternalIpState

    data class Resolved(val address: String, val verdict: CgnatVerdict) : ExternalIpState

    /** No echo service answered, so there is no address to show and no comparison to make. */
    data object Unavailable : ExternalIpState
}

/**
 * S1433: reads the external address and pairs it with the router's WAN address to judge CGNAT.
 *
 * The address reaches the caller and the history row does not carry it - [NetworkMeasurement.resultText]
 * records the verdict and which local protocol answered, which is everything a later reader needs and
 * nothing that would turn the history table into a log of where the user has been (strategic §7).
 */
class ResolveExternalIpUseCase @Inject constructor(
    private val externalIpDataSource: ExternalIpDataSource,
    private val routerWanAddressDataSource: RouterWanAddressDataSource,
    private val historyRepository: NetworkMeasurementHistoryRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * [networkLabel] comes from the caller for the same reason as in [CheckSelectedResourceUseCase]: the
     * Monitor screen already holds the snapshot, and resolving it here would start device observation for
     * the length of one measurement.
     */
    operator fun invoke(networkLabel: String): Flow<ExternalIpState> = flow {
        emit(ExternalIpState.Resolving)

        val echoed = externalIpDataSource.resolve()
        val state = if (echoed is ExternalIpResult.Resolved) {
            ExternalIpState.Resolved(echoed.address, verdictFor(echoed.address))
        } else {
            ExternalIpState.Unavailable
        }
        historyRepository.record(state.toMeasurement(networkLabel))
        emit(state)
    }.flowOn(ioDispatcher)

    /**
     * The router is asked only once an echo answered: with no external address there is nothing to compare
     * it against, and probing the gateway anyway would be traffic that cannot change the outcome.
     */
    private suspend fun verdictFor(echoedAddress: String): CgnatVerdict =
        when (val router = routerWanAddressDataSource.resolve()) {
            is RouterWanAddress.Unanswered -> CgnatVerdict.Unknown
            is RouterWanAddress.Known ->
                if (router.address == echoedAddress) CgnatVerdict.Direct else CgnatVerdict.LikelyCgnat
        }

    private fun ExternalIpState.toMeasurement(networkLabel: String): NetworkMeasurement =
        NetworkMeasurement(
            takenAtMillis = System.currentTimeMillis(),
            kind = NetworkMeasurementKind.EXTERNAL_IP,
            networkLabel = networkLabel,
            resultText = when (this) {
                is ExternalIpState.Resolved -> when (verdict) {
                    CgnatVerdict.Direct -> "External address seen; router WAN matches - direct"
                    CgnatVerdict.LikelyCgnat -> "External address seen; router WAN differs - CGNAT likely"
                    CgnatVerdict.Unknown -> "External address seen; router did not answer - CGNAT unknown"
                }
                else -> "No echo service answered"
            },
            succeeded = this is ExternalIpState.Resolved,
        )
}

package com.sza.fastmediasorter.domain.usecase.networkmonitor

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurement
import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurementKind
import com.sza.fastmediasorter.domain.repository.NetworkMeasurementHistoryRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/** S1433: how the check of one saved resource is progressing, and how it ended. */
sealed interface ResourceCheckState {

    data object Checking : ResourceCheckState

    data class Reachable(val detail: String) : ResourceCheckState

    data class Unreachable(val reason: String) : ResourceCheckState

    /** The id no longer resolves - the resource was deleted between selecting it and checking it. */
    data object ResourceMissing : ResourceCheckState
}

/**
 * S1433: checks the one saved resource the user selected, and records the outcome in the history.
 *
 * One resource, never the whole list. Strategic §6.7 narrowed this deliberately: a sweep over every saved
 * resource needs a concurrency limit and can lock out an account whose password has changed, and neither
 * risk buys the user anything a single deliberate check does not.
 *
 * The credentials `testConnection` resolves internally never reach this layer, so neither the emitted state
 * nor the history row can carry one - the outcome text comes from the repository's own `Result`.
 */
class CheckSelectedResourceUseCase @Inject constructor(
    private val resourceRepository: ResourceRepository,
    private val historyRepository: NetworkMeasurementHistoryRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * [networkLabel] is supplied by the caller rather than read from the Monitor: the screen already
     * collects the snapshot, and resolving it here would start device observation for one measurement.
     */
    operator fun invoke(resourceId: Long, networkLabel: String): Flow<ResourceCheckState> = flow {
        emit(ResourceCheckState.Checking)

        val resource = resourceRepository.getResourceById(resourceId)
        if (resource == null) {
            emit(ResourceCheckState.ResourceMissing)
            return@flow
        }

        val outcome = resourceRepository.testConnection(resource)
        val state = outcome.fold(
            onSuccess = { detail -> ResourceCheckState.Reachable(detail) },
            onFailure = { error -> ResourceCheckState.Unreachable(error.message.orEmpty()) }
        )
        historyRepository.record(state.toMeasurement(resource.name, networkLabel))
        emit(state)
    }.flowOn(ioDispatcher)

    private fun ResourceCheckState.toMeasurement(
        resourceName: String,
        networkLabel: String
    ): NetworkMeasurement = NetworkMeasurement(
        takenAtMillis = System.currentTimeMillis(),
        kind = NetworkMeasurementKind.RESOURCE_CHECK,
        networkLabel = networkLabel,
        resultText = when (this) {
            is ResourceCheckState.Reachable -> "$resourceName: $detail"
            is ResourceCheckState.Unreachable -> "$resourceName: $reason"
            else -> resourceName
        },
        succeeded = this is ResourceCheckState.Reachable
    )
}

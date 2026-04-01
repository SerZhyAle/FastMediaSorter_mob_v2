package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.util.DestinationColors
import com.sza.fastmediasorter.domain.model.MediaResource
import timber.log.Timber
import javax.inject.Inject

/**
 * Marks an existing resource as a Quick Sort destination.
 *
 * Assigns the next available slot order and corresponding color,
 * then persists the change via [UpdateResourceUseCase].
 *
 * Returns [Result.failure] if:
 * - the resource is read-only
 * - the resource is already a destination
 * - the destinations list is full (slot order == –1)
 */
class AddResourceAsDestinationUseCase @Inject constructor(
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val updateResourceUseCase: UpdateResourceUseCase
) {
    suspend operator fun invoke(resource: MediaResource): Result<Unit> {
        if (resource.isReadOnly) {
            Timber.w("AddResourceAsDestination: resource '${resource.name}' is read-only")
            return Result.failure(IllegalStateException("Resource is read-only"))
        }
        if (resource.isDestination) {
            Timber.w("AddResourceAsDestination: resource '${resource.name}' is already a destination")
            return Result.failure(IllegalStateException("Resource is already a destination"))
        }
        val order = getDestinationsUseCase.getNextAvailableOrder()
        if (order < 0) {
            Timber.w("AddResourceAsDestination: destinations list is full")
            return Result.failure(IllegalStateException("Destinations list is full"))
        }
        val color = DestinationColors.getColorForDestination(order)
        Timber.d("AddResourceAsDestination: adding '${resource.name}' at order=$order color=$color")
        return updateResourceUseCase(
            resource.copy(
                isDestination = true,
                destinationOrder = order,
                destinationColor = color
            )
        )
    }
}

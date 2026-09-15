package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.core.util.DestinationColors
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.allowsWriteOperations
import timber.log.Timber
import javax.inject.Inject

/**
 * Marks an existing resource as a Quick Sort destination.
 *
 * Assigns the next available slot order and corresponding color,
 * then persists the change via [UpdateResourceUseCase].
 *
 * Returns [Result.failure] if:
 * - the resource does not allow write operations
 * - the resource is already a destination
 * - the destinations list is full (slot order == –1)
 */
class AddResourceAsDestinationUseCase @Inject constructor(
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val updateResourceUseCase: UpdateResourceUseCase
) {
    suspend operator fun invoke(resource: MediaResource): Result<Unit> {
        // S2625: the user flag alone misses the probe for LOCAL/CLOUD and never refuses a stream.
        if (!resource.allowsWriteOperations()) {
            Timber.w("AddResourceAsDestination: resource '${resource.name}' does not allow writes")
            return Result.failure(IllegalStateException("Resource does not allow write operations"))
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

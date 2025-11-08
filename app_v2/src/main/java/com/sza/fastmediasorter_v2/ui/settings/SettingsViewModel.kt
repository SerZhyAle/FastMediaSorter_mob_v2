package com.sza.fastmediasorter_v2.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter_v2.core.util.DestinationColors
import com.sza.fastmediasorter_v2.domain.model.AppSettings
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.repository.SettingsRepository
import com.sza.fastmediasorter_v2.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter_v2.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter_v2.domain.usecase.UpdateResourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val getResourcesUseCase: GetResourcesUseCase,
    private val updateResourceUseCase: UpdateResourceUseCase
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.getSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    val destinations: StateFlow<List<MediaResource>> = getDestinationsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            try {
                settingsRepository.updateSettings(settings)
                Timber.d("Settings updated successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error updating settings")
            }
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            try {
                settingsRepository.resetToDefaults()
                Timber.d("Settings reset to defaults")
            } catch (e: Exception) {
                Timber.e(e, "Error resetting settings")
            }
        }
    }

    fun moveDestination(resource: MediaResource, direction: Int) {
        viewModelScope.launch {
            try {
                val allDestinations = destinations.value
                val currentIndex = allDestinations.indexOfFirst { it.id == resource.id }
                if (currentIndex == -1) return@launch
                
                val targetIndex = currentIndex + direction
                if (targetIndex < 0 || targetIndex >= allDestinations.size) return@launch
                
                val targetResource = allDestinations[targetIndex]
                
                // Swap destination orders
                val currentOrder = resource.destinationOrder ?: return@launch
                val targetOrder = targetResource.destinationOrder ?: return@launch
                
                updateResourceUseCase(resource.copy(destinationOrder = targetOrder))
                updateResourceUseCase(targetResource.copy(destinationOrder = currentOrder))
                
                Timber.d("Destination moved successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error moving destination")
            }
        }
    }

    fun removeDestination(resource: MediaResource) {
        viewModelScope.launch {
            try {
                updateResourceUseCase(resource.copy(
                    isDestination = false,
                    destinationOrder = null
                ))
                Timber.d("Destination removed successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error removing destination")
            }
        }
    }

    suspend fun getWritableNonDestinationResources(): List<MediaResource> {
        return try {
            val allResources = getResourcesUseCase().stateIn(viewModelScope).value
            allResources.filter { it.isWritable && !it.isDestination }
        } catch (e: Exception) {
            Timber.e(e, "Error getting writable resources")
            emptyList()
        }
    }

    fun addDestination(resource: MediaResource) {
        viewModelScope.launch {
            try {
                val nextOrder = getDestinationsUseCase.getNextAvailableOrder()
                if (nextOrder == -1) {
                    Timber.w("Cannot add destination: all 10 slots are full")
                    return@launch
                }
                
                val color = DestinationColors.getColorForDestination(nextOrder)
                updateResourceUseCase(resource.copy(
                    isDestination = true,
                    destinationOrder = nextOrder,
                    destinationColor = color
                ))
                Timber.d("Destination added successfully with order $nextOrder and color $color")
            } catch (e: Exception) {
                Timber.e(e, "Error adding destination")
            }
        }
    }

    fun updateDestinationColor(resource: MediaResource, color: Int) {
        viewModelScope.launch {
            try {
                updateResourceUseCase(resource.copy(destinationColor = color))
                Timber.d("Destination color updated successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error updating destination color")
            }
        }
    }
}

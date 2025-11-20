package com.sza.fastmediasorter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.util.DestinationColors
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val settingsRepository: SettingsRepository,
    val resourceRepository: ResourceRepository,
    val credentialsRepository: NetworkCredentialsRepository,
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
    
    fun resetPlayerFirstRun() {
        viewModelScope.launch {
            try {
                settingsRepository.setPlayerFirstRun(true)
                Timber.d("Player first-run flag reset successfully")
            } catch (e: Exception) {
                Timber.e(e, "Error resetting player first-run flag")
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
                
                // Wait for both updates to complete before continuing
                val result1 = updateResourceUseCase(resource.copy(destinationOrder = targetOrder))
                val result2 = updateResourceUseCase(targetResource.copy(destinationOrder = currentOrder))
                
                if (result1.isSuccess && result2.isSuccess) {
                    Timber.d("Destination moved successfully: ${resource.name} order $currentOrder→$targetOrder, ${targetResource.name} order $targetOrder→$currentOrder")
                } else {
                    Timber.e("Error moving destination: result1=${result1.exceptionOrNull()}, result2=${result2.exceptionOrNull()}")
                }
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
            // Get fresh data from database, not from cached stateIn()
            val allResources = withContext(Dispatchers.IO) {
                getResourcesUseCase().first()
            }
            allResources.filter { it.isWritable && !it.isDestination }
        } catch (e: Exception) {
            Timber.e(e, "Error getting writable resources")
            emptyList()
        }
    }

    fun addDestination(resource: MediaResource) {
        viewModelScope.launch {
            try {
                // Double-check resource is writable before adding to destinations
                if (!resource.isWritable) {
                    Timber.w("Cannot add non-writable resource as destination: ${resource.name}")
                    return@launch
                }
                
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

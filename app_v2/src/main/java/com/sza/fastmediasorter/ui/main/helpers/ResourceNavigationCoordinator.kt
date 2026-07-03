package com.sza.fastmediasorter.ui.main.helpers

import android.content.Context
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.network.NetworkContextAnalyzer
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.data.network.exceptions.NetworkErrorClassifier
import com.sza.fastmediasorter.data.network.exceptions.NetworkErrorMessageMapper
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.UpdateResourceUseCase
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Coordinates resource navigation validation and routing.
 * Handles connection testing, PIN protection, and navigation decisions.
 * 
 * Responsibilities:
 * - Test network resource connections before opening
 * - Update resource availability status
 * - Check PIN protection and request password
 * - Route to Browse or Player based on context
 * - Handle Favorites special navigation
 */
class ResourceNavigationCoordinator(
    private val context: Context,
    // S0869: Lazy so MainViewModel construction on the main thread does not force provideAppDatabase()
    // through this coordinator. The only use site (testConnection) is inside a suspend fun -> off-main.
    private val resourceRepository: dagger.Lazy<ResourceRepository>,
    private val updateResourceUseCase: UpdateResourceUseCase,
    private val networkContextAnalyzer: NetworkContextAnalyzer
) {

    companion object {
        const val FAVORITES_RESOURCE_ID = -100L
    }
    
    /**
     * Result of resource validation and navigation decision.
     */
    sealed class NavigationResult {
        /** Connection OK, navigate to destination */
        data class Navigate(val destination: NavigationDestination) : NavigationResult()
        
        /** PIN required before navigation */
        data class RequestPin(val resource: MediaResource, val forSlideshow: Boolean) : NavigationResult()
        
        /** Connection failed, show error */
        data class Error(val message: String, val details: String?) : NavigationResult()
        
        /** Informational message (e.g., "Favorites slideshow not supported") */
        data class Info(val message: String) : NavigationResult()
    }
    
    /**
     * Navigation destination after validation.
     */
    sealed class NavigationDestination {
        data class Browse(val resourceId: Long, val skipAvailabilityCheck: Boolean = true) : NavigationDestination()
        data class PlayerSlideshow(val resourceId: Long) : NavigationDestination()
        object Favorites : NavigationDestination()
    }
    
    /**
     * Validate resource and determine navigation action.
     *
     * @param resource Resource to validate
     * @param slideshowMode True for slideshow, false for browse
     * @return NavigationResult indicating next action
     */
    suspend fun validateAndNavigate(
        resource: MediaResource,
        slideshowMode: Boolean
    ): NavigationResult {
        if (resource.id == FAVORITES_RESOURCE_ID) {
            return if (!slideshowMode) {
                NavigationResult.Navigate(NavigationDestination.Favorites)
            } else {
                NavigationResult.Info(context.getString(R.string.favorites_slideshow_open_first))
            }
        }
        
        // Check PIN protection first
        if (!resource.accessPin.isNullOrBlank()) {
            return NavigationResult.RequestPin(resource, slideshowMode)
        }
        
        // For network resources, test connection
        val isNetworkResource = resource.type in setOf(
            ResourceType.SMB,
            ResourceType.SFTP,
            ResourceType.FTP,
            ResourceType.CLOUD
        )
        
        if (isNetworkResource) {
            return testConnectionAndNavigate(resource, slideshowMode)
        }
        
        // Local resource - navigate directly
        return createNavigationResult(resource.id, slideshowMode)
    }
    
    /**
     * Test network connection and return navigation result.
     */
    private suspend fun testConnectionAndNavigate(
        resource: MediaResource,
        slideshowMode: Boolean
    ): NavigationResult {
        return try {
            val testResult = resourceRepository.get().testConnection(resource)
            
            testResult.fold(
                onSuccess = { message ->
                    Timber.d("Connection test OK: $message - proceeding to navigation")
                    
                    // Parse subfolder count from statistics message
                    val subfolderCount = parseSubfolderCount(message)
                    
                    // Update availability to true and subfolder count
                    if (!resource.isAvailable || resource.subfolderCount != subfolderCount) {
                        val updatedResource = resource.copy(
                            isAvailable = true,
                            subfolderCount = subfolderCount
                        )
                        updateResourceUseCase(updatedResource)
                    }
                    
                    createNavigationResult(resource.id, slideshowMode)
                },
                onFailure = { error ->
                    Timber.e(error, "Connection test failed for ${resource.name}")
                    
                    // Update availability to false
                    if (resource.isAvailable) {
                        val updatedResource = resource.copy(isAvailable = false)
                        updateResourceUseCase(updatedResource)
                    }
                    
                    // Format error message via NetworkErrorClassifier
                    val classifiedError = NetworkErrorClassifier.classify(error)
                    val userMessage = NetworkErrorMessageMapper.toContextAwareMessage(
                        context, classifiedError, resource.type, resource.path, networkContextAnalyzer
                    )
                    NavigationResult.Error(userMessage, null)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Exception testing connection for ${resource.name}")
            
            // Update availability to false on exception
            if (resource.isAvailable) {
                val updatedResource = resource.copy(isAvailable = false)
                updateResourceUseCase(updatedResource)
            }
            
            // Format error message via NetworkErrorClassifier
            val classifiedError = NetworkErrorClassifier.classify(e)
            val userMessage = NetworkErrorMessageMapper.toContextAwareMessage(
                context, classifiedError, resource.type, resource.path, networkContextAnalyzer
            )
            NavigationResult.Error(userMessage, null)
        }
    }
    
    /**
     * Create navigation result based on mode.
     */
    private fun createNavigationResult(resourceId: Long, slideshowMode: Boolean): NavigationResult {
        val destination = if (slideshowMode) {
            NavigationDestination.PlayerSlideshow(resourceId)
        } else {
            NavigationDestination.Browse(resourceId, skipAvailabilityCheck = true)
        }
        
        return NavigationResult.Navigate(destination)
    }
    
    /**
     * Parse subfolder count from testConnection success message.
     * Message format: "• Subfolders: 35"
     * 
     * @return Parsed count or 0 if not found
     */
    private fun parseSubfolderCount(message: String): Int {
        return try {
            val regex = """Subfolders:\s*(\d+)""".toRegex()
            regex.find(message)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse subfolder count from message: $message")
            0
        }
    }
}

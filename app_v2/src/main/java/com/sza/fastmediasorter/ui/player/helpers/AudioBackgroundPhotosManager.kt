package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.SortMode
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.GetMediaFilesUseCase
import com.sza.fastmediasorter.domain.usecase.ScanProgressCallback
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for displaying random background photos during audio playback.
 * 
 * Logic:
 * - Activated only when audio file is playing AND settings.enablePhotosDuringAudio == true
 * - Loads photos from settings.audioBackgroundPhotosResourceId
 * - Filters only static images (excludes videos and GIFs)
 * - Shuffles list randomly
 * - One song = One photo (circular navigation)
 */
@Singleton
class AudioBackgroundPhotosManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val resourceRepository: ResourceRepository,
    private val getMediaFilesUseCase: GetMediaFilesUseCase
) {
    companion object {
        /** Maximum files to scan in Phase 1 (quick initial batch) before early stop */
        private const val INITIAL_BATCH_SIZE = 100
    }
    
    private var photosList: List<MediaFile> = emptyList()
    private var currentPhotoIndex: Int = 0
    private var isActive: Boolean = false
    
    // Current state tracking
    private var currentResourceId: String? = null
    private var enablePhotosDuringAudio: Boolean = false
    
    // Photo resource info for correct network loading
    private var photoResourceType: ResourceType? = null
    private var photoResourceCredentialsId: String? = null
    
    // Callback for UI updates
    private var onPhotoChangedListener: ((photo: MediaFile?) -> Unit)? = null
    private var onErrorListener: ((errorMessage: String) -> Unit)? = null
    
    // Coroutine scope for background loading
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loadJob: Job? = null
    
    /**
     * Set listener for photo changes (for UI display)
     */
    fun setOnPhotoChangedListener(listener: ((photo: MediaFile?) -> Unit)?) {
        this.onPhotoChangedListener = listener
    }
    
    /**
     * Set listener for errors
     */
    fun setOnErrorListener(listener: ((errorMessage: String) -> Unit)?) {
        this.onErrorListener = listener
    }
    
    /**
     * Initialize manager (if needed)
     */
    fun initialize() {
        Timber.d("AudioBackgroundPhotos: Manager initialized")
    }
    
    /**
     * Update state from PlayerViewModel
     * Determines if feature should be active based on:
     * 1. Current file is AUDIO
     * 2. enablePhotosDuringAudio == true
     * 3. audioBackgroundPhotosResourceId is set
     */
    fun updateState(state: PlayerViewModel.PlayerState) {
        val currentFile = state.currentFile
        val resourceId = state.audioBackgroundPhotosResourceId
        val enabled = state.enablePhotosDuringAudio
        
        // Check if feature should be active
        val shouldBeActive = currentFile?.type == MediaType.AUDIO && enabled && resourceId != null
        
        Timber.d("AudioBackgroundPhotos: updateState - file=${currentFile?.name}, type=${currentFile?.type}, enabled=$enabled, resourceId=$resourceId, shouldBeActive=$shouldBeActive")
        
        if (shouldBeActive) {
            // Feature should be active
            if (!isActive || currentResourceId != resourceId) {
                // First activation or resource changed - load photos
                Timber.d("AudioBackgroundPhotos: Activating feature, loading photos from resource $resourceId")
                loadPhotosPlaylist(resourceId)
                currentResourceId = resourceId
                isActive = true
            }
        } else {
            // Feature should be inactive
            if (isActive) {
                Timber.d("AudioBackgroundPhotos: Deactivating feature")
                deactivate()
            }
        }
        
        enablePhotosDuringAudio = enabled
    }
    
    /**
     * Load photos playlist from resource using two-phase approach:
     * Phase 1: Quick initial scan (up to INITIAL_BATCH_SIZE files) → show immediately
     * Phase 2: Full scan in background → update complete list
     * This prevents long black screen while scanning large folders.
     */
    private fun loadPhotosPlaylist(resourceId: String) {
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val resourceIdLong = resourceId.toLongOrNull()
                if (resourceIdLong == null) {
                    Timber.e("AudioBackgroundPhotos: Invalid resource ID: $resourceId")
                    withContext(Dispatchers.Main) {
                        onErrorListener?.invoke(context.getString(com.sza.fastmediasorter.R.string.resource_not_found))
                    }
                    return@launch
                }
                
                // Get resource
                val resource = resourceRepository.getResourceById(resourceIdLong)
                if (resource == null) {
                    Timber.e("AudioBackgroundPhotos: Resource not found: $resourceIdLong")
                    withContext(Dispatchers.Main) {
                        onErrorListener?.invoke(context.getString(com.sza.fastmediasorter.R.string.resource_not_found))
                    }
                    return@launch
                }
                
                Timber.d("AudioBackgroundPhotos: Loading photos from resource: ${resource.name}")
                
                // Store resource info for network loading
                photoResourceType = resource.type
                photoResourceCredentialsId = resource.credentialsId
                
                // Create image-only resource for efficient scanning
                val imageResource = resource.copy(
                    supportedMediaTypes = setOf(MediaType.IMAGE)
                )
                
                // Phase 1: Quick initial scan with early stop
                var scannedFileCount = 0
                var wasEarlyStopped = false
                
                val initialFiles = getMediaFilesUseCase(
                    resource = imageResource,
                    sortMode = SortMode.NAME_ASC,
                    showHiddenFiles = false,
                    onProgress = object : ScanProgressCallback {
                        override suspend fun onProgress(scannedCount: Int, currentFile: String?) {
                            scannedFileCount = scannedCount
                        }
                        override suspend fun onComplete(totalFiles: Int, durationMs: Long) {}
                        override fun shouldStop(): Boolean {
                            if (scannedFileCount >= INITIAL_BATCH_SIZE) {
                                wasEarlyStopped = true
                                return true
                            }
                            return false
                        }
                    }
                ).first()
                
                // Filter only static images (exclude GIFs)
                val initialImages = initialFiles.filter { file ->
                    file.type == MediaType.IMAGE && !file.name.endsWith(".gif", ignoreCase = true)
                }
                
                if (initialImages.isNotEmpty()) {
                    photosList = initialImages.shuffled()
                    currentPhotoIndex = 0
                    Timber.d("AudioBackgroundPhotos: Phase 1 - showing ${photosList.size} photos (scanned $scannedFileCount files, earlyStopped=$wasEarlyStopped)")
                    
                    // Notify UI with first photo immediately
                    withContext(Dispatchers.Main) {
                        onPhotoChangedListener?.invoke(getCurrentPhoto())
                    }
                }
                
                // Phase 2: Full scan if Phase 1 was early-stopped
                if (wasEarlyStopped) {
                    Timber.d("AudioBackgroundPhotos: Phase 2 - starting full scan for remaining photos")
                    
                    val allFiles = getMediaFilesUseCase(
                        resource = imageResource,
                        sortMode = SortMode.NAME_ASC,
                        showHiddenFiles = false
                    ).first()
                    
                    val allImages = allFiles.filter { file ->
                        file.type == MediaType.IMAGE && !file.name.endsWith(".gif", ignoreCase = true)
                    }
                    
                    if (allImages.size > photosList.size) {
                        // Preserve current photo position after re-shuffle
                        val currentPhoto = getCurrentPhoto()
                        photosList = allImages.shuffled()
                        currentPhotoIndex = if (currentPhoto != null) {
                            photosList.indexOfFirst { it.path == currentPhoto.path }.takeIf { it >= 0 } ?: 0
                        } else 0
                        
                        Timber.d("AudioBackgroundPhotos: Phase 2 - updated to ${photosList.size} photos total")
                    }
                } else {
                    Timber.d("AudioBackgroundPhotos: Full scan completed in Phase 1 (${photosList.size} photos)")
                }
                
                if (photosList.isEmpty()) {
                    Timber.w("AudioBackgroundPhotos: No static images found in resource")
                    withContext(Dispatchers.Main) {
                        onErrorListener?.invoke(
                            context.getString(com.sza.fastmediasorter.R.string.audio_background_photos_no_photos_found)
                        )
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "AudioBackgroundPhotos: Error loading photos playlist")
                withContext(Dispatchers.Main) {
                    onErrorListener?.invoke(
                        context.getString(com.sza.fastmediasorter.R.string.audio_background_photos_load_failed)
                    )
                }
            }
        }
    }
    
    /**
     * Get current photo for display
     */
    fun getCurrentPhoto(): MediaFile? {
        if (photosList.isEmpty()) return null
        return photosList.getOrNull(currentPhotoIndex)
    }

    /**
     * Get next photo for preloading (circular navigation)
     */
    fun getNextPhoto(): MediaFile? {
        if (photosList.isEmpty()) return null
        val nextIndex = (currentPhotoIndex + 1) % photosList.size
        return photosList.getOrNull(nextIndex)
    }
    
    /**
     * Advance to next photo (called on audio track change or slideshow timer)
     */
    @Synchronized
    fun advanceToNextPhoto() {
        if (photosList.isEmpty()) {
            Timber.d("AudioBackgroundPhotos: Cannot advance - photos not yet loaded")
            return
        }
        
        // Circular increment
        currentPhotoIndex = (currentPhotoIndex + 1) % photosList.size
        
        val nextPhoto = getCurrentPhoto()
        // Log with 1-based indexing for user-friendly display (internal uses 0-based)
        Timber.d("AudioBackgroundPhotos: Advanced to photo ${currentPhotoIndex + 1}/${photosList.size}: ${nextPhoto?.name}")
        
        // Notify UI on main thread
        scope.launch {
            withContext(Dispatchers.Main) {
                onPhotoChangedListener?.invoke(nextPhoto)
            }
        }
    }
    
    /**
     * Deactivate feature and clear state
     */
    private fun deactivate() {
        loadJob?.cancel()
        photosList = emptyList()
        currentPhotoIndex = 0
        currentResourceId = null
        photoResourceType = null
        photoResourceCredentialsId = null
        isActive = false
        
        // Notify UI to clear photo (on main thread)
        scope.launch {
            withContext(Dispatchers.Main) {
                onPhotoChangedListener?.invoke(null)
            }
        }
        
        Timber.d("AudioBackgroundPhotos: Deactivated and cleared")
    }
    
    /**
     * Release resources
     */
    fun release() {
        loadJob?.cancel()
        photosList = emptyList()
        onPhotoChangedListener = null
        onErrorListener = null
        photoResourceType = null
        photoResourceCredentialsId = null
        isActive = false
        Timber.d("AudioBackgroundPhotos: Released")
    }

    /**
     * Get photo resource type for correct network loading
     */
    fun getPhotoResourceType(): ResourceType? = photoResourceType

    /**
     * Get photo resource credentials ID for correct network loading
     */
    fun getPhotoCredentialsId(): String? = photoResourceCredentialsId
}

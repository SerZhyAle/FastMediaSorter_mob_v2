package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseViewModel
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.data.local.LocalMediaScanner
import com.sza.fastmediasorter.data.local.db.StereoFormatOverrideDao
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.FavoritesUseCase
import com.sza.fastmediasorter.domain.usecase.LocalPathResolution
import com.sza.fastmediasorter.domain.usecase.ResolveLocalPathFromUriUseCase
import com.sza.fastmediasorter.ui.player.helpers.PlayerStereoModeCoordinator
import com.sza.fastmediasorter.ui.player.standalone.StandaloneFolderPagingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class StandalonePlayerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val favoritesUseCase: FavoritesUseCase,
    private val resolveLocalPathFromUriUseCase: ResolveLocalPathFromUriUseCase,
    private val localMediaScanner: LocalMediaScanner,
    private val settingsRepository: SettingsRepository,
    stereoFormatOverrideDao: StereoFormatOverrideDao
) :
    BaseViewModel<StandalonePlayerViewModel.StandalonePlayerState, StandalonePlayerViewModel.StandalonePlayerEvent>() {

    data class StandalonePlayerState(
        val mediaFile: MediaFile? = null,
        val mediaType: MediaType? = null,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        /** True once the opened file's folder is enumerable and holds >1 host-supported neighbour. */
        val supportsFolderPaging: Boolean = false,
        /** True while slideshow auto-advance is running; the host reflects this in its toggle. */
        val isSlideshowActive: Boolean = false
    )

    sealed class StandalonePlayerEvent {
        data class ShowError(val message: String) : StandalonePlayerEvent()
        object FinishActivity : StandalonePlayerEvent()
    }

    /**
     * Owns the active folder list once the opened file is local. Null until [loadFromUri] resolves a
     * local folder; recreated per host with the host-supported media types.
     */
    private var pagingManager: StandaloneFolderPagingManager? = null
    private var slideshowJob: Job? = null

    /** Media types the active host accepts; set by the activity before [loadFromUri]. */
    private var hostSupportedTypes: Set<MediaType> = emptySet()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _messageFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messageFlow: SharedFlow<String> = _messageFlow.asSharedFlow()

    private val stereoCoordinator = PlayerStereoModeCoordinator(
        stereoFormatOverrideDao = stereoFormatOverrideDao,
        scope = viewModelScope,
        getCurrentFilePath = { state.value.mediaFile?.path }
    )

    val stereoMode: StateFlow<StereoMode> = stereoCoordinator.stereoMode
    val detectedStereoMode: StateFlow<StereoMode> = stereoCoordinator.detectedStereoMode

    fun setStereoMode(mode: StereoMode) = stereoCoordinator.setStereoMode(mode)

    fun rememberStereoModeForCurrentFile(mode: StereoMode) =
        stereoCoordinator.rememberStereoModeForCurrentFile(mode)

    fun setAutoDetectedStereoMode(mode: StereoMode) =
        stereoCoordinator.setAutoDetectedStereoMode(mode)

    fun resetStereoModeForNewFile() =
        stereoCoordinator.resetStereoModeForNewFile(state.value.mediaFile?.path)

    fun showMessage(message: String) {
        _messageFlow.tryEmit(message)
    }

    override fun getInitialState(): StandalonePlayerState = StandalonePlayerState()

    /**
     * Declare the media types the calling host can render. Must be called before [loadFromUri] so
     * folder enumeration filters to the same set the host accepts (e.g. audio host -> AUDIO only).
     */
    fun setHostSupportedTypes(types: Set<MediaType>) {
        hostSupportedTypes = types
    }

    fun loadFromUri(uri: Uri, mimeType: String?, displayName: String?) {
        Timber.d("StandalonePlayer: loadFromUri uri=$uri mime=$mimeType name=$displayName")
        updateState { it.copy(isLoading = true, errorMessage = null) }

        val resolvedName = displayName ?: uri.lastPathSegment ?: "unknown"
        val detectedType = MediaTypeUtils.getMediaTypeFromMimeOrExtension(mimeType, resolvedName)

        if (detectedType == null) {
            Timber.w("StandalonePlayer: unsupported file type for $resolvedName (mime=$mimeType)")
            updateState {
                it.copy(
                    isLoading = false,
                    errorMessage = context.getString(R.string.error_opening_file, resolvedName)
                )
            }
            sendEvent(StandalonePlayerEvent.FinishActivity)
            return
        }

        val mediaFile = MediaFile(
            name = resolvedName,
            path = uri.toString(),
            type = detectedType,
            size = 0L,
            createdDate = System.currentTimeMillis(),
            contentUri = uri.toString()
        )

        Timber.d("StandalonePlayer: resolved type=$detectedType for $resolvedName")
        updateState {
            it.copy(
                mediaFile = mediaFile,
                mediaType = detectedType,
                isLoading = false,
                errorMessage = null
            )
        }

        // Trigger DB lookup for remembered stereo mode so it's applied before the dialog opens.
        stereoCoordinator.resetStereoModeForNewFile(uri.toString())

        checkFavoriteStatus(uri.toString())

        initFolderPaging(uri)
    }

    /**
     * Resolve the opened URI to a local folder and, if reachable, enumerate neighbours so paging
     * controls can be surfaced. NotLocal sources keep single-file semantics (supportsFolderPaging
     * stays false). The opened file's own [MediaFile] (carrying the external content URI) is kept as
     * the current file - we only borrow the neighbour list's ordering and position.
     */
    private fun initFolderPaging(uri: Uri) {
        viewModelScope.launch {
            val resolution = resolveLocalPathFromUriUseCase(uri)
            Timber.d("S0389: standalone folder paging init, local=${resolution is LocalPathResolution.Local}")
            if (resolution !is LocalPathResolution.Local) {
                pagingManager = null
                updateState { it.copy(supportsFolderPaging = false) }
                return@launch
            }

            val manager = StandaloneFolderPagingManager(
                mediaScanner = localMediaScanner,
                supportedTypes = hostSupportedTypes,
            )
            val resolved = manager.initialize(
                parentFolderPath = resolution.parentFolderPath,
                currentAbsolutePath = resolution.absolutePath,
            )

            if (resolved == null || !manager.isPagingAvailable) {
                pagingManager = null
                updateState { it.copy(supportsFolderPaging = false) }
                return@launch
            }

            pagingManager = manager
            updateState { it.copy(supportsFolderPaging = true) }
        }
    }

    fun pageNext() = applyPaging { it.next() }

    fun pagePrevious() = applyPaging { it.previous() }

    fun pageRandom() = applyPaging { it.random() }

    private inline fun applyPaging(move: (StandaloneFolderPagingManager) -> MediaFile?) {
        val manager = pagingManager ?: return
        val file = move(manager) ?: return
        publishCurrentFile(file)
    }

    /** Swap the displayed file to a folder neighbour; the host re-renders off the state change. */
    private fun publishCurrentFile(file: MediaFile) {
        updateState {
            it.copy(
                mediaFile = file,
                mediaType = file.type,
                errorMessage = null
            )
        }
        stereoCoordinator.resetStereoModeForNewFile(file.path)
        checkFavoriteStatus(file.path)
    }

    fun toggleSlideshow() {
        if (state.value.isSlideshowActive) {
            stopSlideshow()
        } else {
            startSlideshow()
        }
    }

    private fun startSlideshow() {
        val manager = pagingManager ?: return
        if (!manager.isPagingAvailable) return
        updateState { it.copy(isSlideshowActive = true) }
        slideshowJob?.cancel()
        slideshowJob = viewModelScope.launch {
            val intervalMillis = settingsRepository.getSettings().first().slideshowInterval.coerceAtLeast(1) * 1000L
            while (isActive) {
                delay(intervalMillis)
                val next = manager.next() ?: break
                publishCurrentFile(next)
            }
        }
    }

    private fun stopSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = null
        updateState { it.copy(isSlideshowActive = false) }
    }

    fun checkFavoriteStatus(uri: String) {
        viewModelScope.launch {
            _isFavorite.value = favoritesUseCase.isFavoriteSync(uri)
        }
    }

    fun toggleFavorite() {
        val file = state.value.mediaFile ?: return
        viewModelScope.launch {
            favoritesUseCase.toggleFavorite(file, resourceId = 0L)
            _isFavorite.value = !_isFavorite.value
        }
    }

    fun onRenameComplete(newUri: Uri, newName: String) {
        updateState { state ->
            state.copy(mediaFile = state.mediaFile?.copy(
                name = newName,
                path = newUri.toString(),
                contentUri = newUri.toString()
            ))
        }
    }

}

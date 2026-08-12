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
import com.sza.fastmediasorter.domain.usecase.MaterializeUriToFileUseCase
import com.sza.fastmediasorter.domain.usecase.ResolveLocalPathFromUriUseCase
import com.sza.fastmediasorter.domain.usecase.UriMaterialization
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

// S0995: one quarter turn per tap; a full turn wraps the cumulative angle back to 0.
private const val ROTATION_STEP_DEGREES = 90
private const val FULL_ROTATION_DEGREES = 360

@HiltViewModel
class StandalonePlayerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val favoritesUseCase: FavoritesUseCase,
    private val resolveLocalPathFromUriUseCase: ResolveLocalPathFromUriUseCase,
    private val materializeUriToFileUseCase: MaterializeUriToFileUseCase,
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
        val isSlideshowActive: Boolean = false,
        // S0995: cumulative visual frame rotation (0/90/180/270) applied to the current image/video.
        // Session-scoped: never persisted, carried across folder paging, cleared on ViewModel destroy.
        // Distinct from the screen-orientation sensor toggle and from destructive file rotation.
        val sessionRotationAngle: Int = 0
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

    // S0390: the current file resolved to a writable local static-image path, or null when the file
    // is not an editable image (wrong type, or a non-local source with no filesystem path). This is
    // the gate the host reads to surface Group A image actions (crop / crop-to-file / compress).
    private val _editableImageFile = MutableStateFlow<MediaFile?>(null)
    val editableImageFile: StateFlow<MediaFile?> = _editableImageFile.asStateFlow()

    // S0390: screen-rotation sensor toggle. The VM holds no Activity ref, so the host observes this
    // and applies it through ScreenRotationManager (mirrors the in-app ROTATION_TOGGLE command).
    private val _rotationSensorEnabled = MutableStateFlow(false)
    val rotationSensorEnabled: StateFlow<Boolean> = _rotationSensorEnabled.asStateFlow()

    fun toggleRotationSensor() {
        _rotationSensorEnabled.value = !_rotationSensorEnabled.value
    }

    /**
     * S0995: advance the session frame rotation by one clockwise quarter turn (0->90->180->270->0).
     * State-only; the host reads [StandalonePlayerState.sessionRotationAngle] and applies it to the
     * live surface. Not persisted; distinct from [toggleRotationSensor] and destructive file rotation.
     */
    fun rotateSession90() = rotateSessionBy(ROTATION_STEP_DEGREES)

    /** S1364: the counter-clockwise twin of [rotateSession90] (0->270->180->90->0). */
    fun rotateSessionCounter90() = rotateSessionBy(-ROTATION_STEP_DEGREES)

    // Kotlin's % keeps the dividend's sign, so a negative step would store a negative angle and the
    // host applies sessionRotationAngle absolutely. The second modulo folds it back into 0..359.
    private fun rotateSessionBy(stepDegrees: Int) {
        updateState {
            val next = (it.sessionRotationAngle + stepDegrees) % FULL_ROTATION_DEGREES
            it.copy(sessionRotationAngle = (next + FULL_ROTATION_DEGREES) % FULL_ROTATION_DEGREES)
        }
    }

    /**
     * S0410: guarantee the current static image has a local editable path so file-based actions
     * (crop-to-file / compress) can read a source. If the URI already resolved to a local path the
     * gate is already set; otherwise materialize the source URI into a cache file. The action saves
     * its result as a new file - the original (possibly non-writable) URI is never written back.
     */
    suspend fun ensureEditableImage() {
        if (_editableImageFile.value != null) return
        val file = state.value.mediaFile ?: return
        if (state.value.mediaType != MediaType.IMAGE) return
        val uri = Uri.parse(file.contentUri ?: file.path)
        when (val result = materializeUriToFileUseCase(uri, file.name)) {
            is UriMaterialization.Materialized ->
                _editableImageFile.value = file.copy(path = result.absolutePath)
            UriMaterialization.Failed ->
                _messageFlow.tryEmit(context.getString(R.string.error_opening_file_simple))
        }
    }

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
        // S0390: reset editable gate until the URI resolves to a local image path (initFolderPaging).
        _editableImageFile.value = null
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
            // S0390: a local static image becomes editable once its real filesystem path is known.
            _editableImageFile.value = if (resolution is LocalPathResolution.Local &&
                state.value.mediaType == MediaType.IMAGE
            ) {
                state.value.mediaFile?.copy(path = resolution.absolutePath)
            } else {
                null
            }
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
        // S0390: folder neighbours already carry a real filesystem path - editable if a static image.
        _editableImageFile.value =
            if (file.type == MediaType.IMAGE && file.path.startsWith("/")) file else null
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

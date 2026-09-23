package com.sza.fastmediasorter.wear.ui.fdsec

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.files.WearMediaFileStager
import com.sza.fastmediasorter.wear.data.preferences.WearFdSecCredentialRepository
import com.sza.fastmediasorter.wear.domain.files.WearFdSecUseCase
import com.sza.fastmediasorter.wear.domain.model.WearFdSecMode
import com.sza.fastmediasorter.wear.domain.model.WearFdSecResult
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.repository.SelectedMedia
import com.sza.fastmediasorter.wear.domain.repository.SelectedMediaManager
import com.sza.fastmediasorter.wear.domain.usecase.DownloadNetworkFileUseCase
import com.sza.fastmediasorter.wear.ui.common.playerRouteFor
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Locale
import javax.inject.Inject

/**
 * S3383: runs the one FileDO errand the screen was opened for and reports its outcome.
 *
 * The credential reaches the use case as a `CharArray` and is blanked the moment the call returns.
 * Neither it nor the true name sealed in the container is ever logged - the format hides the name on
 * purpose, and a log line would undo that for every container the wearer opens.
 */
@HiltViewModel
class FdSecCredentialViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val fdSec: WearFdSecUseCase,
    private val selectedMedia: SelectedMediaManager,
    private val rememberedCredential: WearFdSecCredentialRepository,
    private val stager: WearMediaFileStager,
    private val playbackSets: PlaybackSetManager,
    private val downloadNetworkFile: DownloadNetworkFileUseCase
) : ViewModel() {

    private val fileId: Long = savedStateHandle.get<Long>(WearRoutes.ARG_FILE_ID) ?: -1L
    private val mode = WearFdSecMode.fromNameOrOpen(savedStateHandle.get<String>(WearRoutes.ARG_FDSEC_MODE))

    private val _uiState = MutableStateFlow(FdSecCredentialUiState(mode = mode))
    val uiState: StateFlow<FdSecCredentialUiState> = _uiState.asStateFlow()

    // The backstop after a power loss: a copy a killed process left behind is nobody's to clean up
    // later, and it is plaintext. Joined before any materialise so the sweep cannot eat a fresh copy.
    private val sweep = viewModelScope.launch { fdSec.discardOpened(context.cacheDir) }

    init {
        val name = selectedMedia.getSelectedFileById(fileId)?.file?.name.orEmpty()
        _uiState.update { it.copy(fileName = name) }
        if (mode == WearFdSecMode.OPEN) {
            tryRemembered()
        }
    }

    fun onRememberChange(value: Boolean) {
        _uiState.update { it.copy(remember = value) }
    }

    fun onCredentialChange(value: String) {
        _uiState.update { it.copy(credential = value, messageRes = null) }
    }

    fun onRepeatedChange(value: String) {
        _uiState.update { it.copy(repeated = value, messageRes = null) }
    }

    fun onConfirm() {
        val state = _uiState.value
        if (!state.canConfirm) {
            return
        }
        run(state.credential.toCharArray(), state.offersRemember && state.remember)
    }

    /**
     * S3397: tries the remembered credential before the wearer is asked for anything. A refusal
     * forgets it at once - the format cannot tell a wrong credential from a foreign or tampered
     * file - and the screen says so before handing the field back.
     */
    private fun tryRemembered() {
        viewModelScope.launch {
            val credential = rememberedCredential.read() ?: return@launch
            // Raised before the target is resolved: fetching a network container takes long enough for
            // a typed confirm to start a second fetch into the same cache file.
            _uiState.update { it.copy(isWorking = true) }
            val target = resolveTarget()
            if (target == null) {
                credential.fill(' ')
                _uiState.update { it.copy(isWorking = false) }
                return@launch
            }
            val result = execute(target, credential)
            if (result == WearFdSecResult.WrongCredentialOrTamper) {
                rememberedCredential.forget()
                _uiState.update {
                    it.copy(isWorking = false, messageRes = R.string.wear_filedo_remembered_forgotten)
                }
            } else {
                publish(result)
            }
        }
    }

    /** Dismissing an outcome message returns the field to the wearer for another attempt. */
    fun onMessageDismissed() {
        _uiState.update { it.copy(messageRes = null, credential = "", repeated = "") }
    }

    private fun run(credential: CharArray, remember: Boolean) {
        _uiState.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val target = resolveTarget()
            if (target == null) {
                credential.fill(' ')
                _uiState.update { it.copy(isWorking = false, messageRes = R.string.wear_filedo_result_failed) }
                return@launch
            }
            val result = execute(target, credential, blank = false)
            // Kept only after it opened the file, so a mistyped credential is never remembered.
            if (remember && result is WearFdSecResult.Restored) {
                rememberedCredential.save(credential)
            }
            credential.fill(' ')
            publish(result)
        }
    }

    /**
     * The file this errand works on, or null when the watch cannot reach one.
     *
     * Four origins: a file with a path of its own, a shared-storage row reached by its path, and - for
     * viewing only - a copy of a row whose path this device will not give out, or of a network entry.
     * Packing and restoring must write beside the original, which a copy in the cache cannot do, so
     * those two never take a copy.
     */
    private suspend fun resolveTarget(): FdSecTarget? {
        val selected = selectedMedia.getSelectedFileById(fileId)
        return when {
            selected == null -> null
            selected.isNetworkSource -> fetchedTarget(selected)
            else -> localTarget(selected.file)
        }
    }

    /**
     * S3407: a network entry has no bytes on the watch, so they are fetched over the entry's own
     * protocol by the download every player already uses, rather than a second route to the share.
     */
    private suspend fun fetchedTarget(selected: SelectedMedia): FdSecTarget? {
        if (mode != WearFdSecMode.OPEN) {
            return null
        }
        return downloadNetworkFile(selected, DownloadNetworkFileUseCase.Kind.CONTAINER)
            .getOrNull()
            ?.let { FdSecTarget(it, fetched = true) }
    }

    private suspend fun localTarget(file: WearMediaFile): FdSecTarget? = withContext(Dispatchers.IO) {
        val own = stager.localFileOf(file)?.takeIf { it.isFile }
        val shared = if (own == null) stager.sharedStorageFileOf(file) else null
        when {
            own != null -> FdSecTarget(own)
            shared != null -> FdSecTarget(shared, inSharedStorage = true)
            mode == WearFdSecMode.OPEN -> stager.stage(file)?.let { FdSecTarget(it, stagedFrom = file) }
            else -> null
        }
    }

    /** Runs this screen's errand; blanks [credential] on return unless the caller still needs it. */
    private suspend fun execute(target: FdSecTarget, credential: CharArray, blank: Boolean = true): WearFdSecResult {
        val result = try {
            // Inside the try, so a screen left while the sweep runs still deletes its copy.
            sweep.join()
            when (mode) {
                WearFdSecMode.ENCRYPT -> fdSec.pack(target.file, credential)
                WearFdSecMode.DECRYPT -> fdSec.restoreBeside(target.file, credential)
                WearFdSecMode.OPEN -> fdSec.materialize(
                    target.file,
                    File(fdSec.openWorkspace(context.cacheDir), NOW.invoke()),
                    credential
                )
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { discardCopy(target) }
        }
        if (blank) {
            credential.fill(' ')
        }
        // The class is safe to log; the credential and the sealed true name never are.
        Timber.i("fdsec: %s ended as %s", mode.name, result::class.java.simpleName)
        if (target.inSharedStorage) {
            writtenBeside(result)?.let(stager::announce)
        }
        return result
    }

    /** A staged or fetched copy is ciphertext, but it is a second copy nobody asked for. */
    private fun discardCopy(target: FdSecTarget) {
        target.stagedFrom?.let { original -> stager.discard(target.file, original) }
        if (target.fetched && !target.file.delete()) {
            Timber.w("fdsec: could not remove a fetched container copy")
        }
    }

    /** What packing or restoring left beside the original. Viewing writes only into the private cache. */
    private fun writtenBeside(result: WearFdSecResult): File? = when {
        result is WearFdSecResult.Packed -> result.container
        result is WearFdSecResult.Restored && mode == WearFdSecMode.DECRYPT -> result.file
        else -> null
    }

    private fun publish(result: WearFdSecResult) {
        if (mode == WearFdSecMode.OPEN && result is WearFdSecResult.Restored) {
            openRecovered(result)
            return
        }
        _uiState.update {
            it.copy(isWorking = false, credential = "", repeated = "", messageRes = messageFor(result))
        }
    }

    /**
     * Hands the recovered file to the watch's ordinary viewer.
     *
     * The container's own id is kept so the viewer resolves it exactly as it resolves a browsed
     * file, and the type is read from the sealed true name because the container's visible name
     * carries no extension at all.
     */
    private fun openRecovered(result: WearFdSecResult.Restored) {
        if (isRefusedType(result.realName)) {
            result.file.parentFile?.deleteRecursively()
            _uiState.update {
                it.copy(isWorking = false, credential = "", messageRes = R.string.wear_filedo_result_not_viewable)
            }
            return
        }
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(result.realName.substringAfterLast('.', "").lowercase(Locale.ROOT))
        val recovered = WearMediaFile(
            id = fileId,
            name = result.realName,
            uri = Uri.fromFile(result.file),
            mimeType = mimeType,
            size = result.realSize,
            dateModified = 0L
        )
        selectedMedia.selectFile(file = recovered, isNetworkSource = false)
        // The image and document viewers read the published set before the selection, and the set
        // still names the list the container was tapped in - with the container itself as current.
        playbackSets.publish(listOf(recovered), startIndex = 0)
        _uiState.update {
            it.copy(
                isWorking = false,
                credential = "",
                playerRoute = playerRouteFor(fileId, mimeType, fileName = result.realName)
            )
        }
    }

    private fun messageFor(result: WearFdSecResult): Int = when (result) {
        is WearFdSecResult.Packed -> R.string.wear_filedo_result_packed
        is WearFdSecResult.Restored -> R.string.wear_filedo_result_restored
        WearFdSecResult.WrongCredentialOrTamper -> R.string.wear_filedo_result_wrong
        WearFdSecResult.Damaged -> R.string.wear_filedo_result_damaged
        WearFdSecResult.Unsupported -> R.string.wear_filedo_result_unsupported
        WearFdSecResult.OutOfMemory -> R.string.wear_filedo_result_memory
        is WearFdSecResult.Failed -> R.string.wear_filedo_result_failed
    }

    private fun isRefusedType(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT) in REFUSED_EXTENSIONS

    private companion object {

        /** Named so the nanosecond read stays out of the property initialisers above it. */
        val NOW: () -> String = { System.nanoTime().toString() }

        /**
         * The phone's list under S3382, kept identical on purpose: the same container opened on
         * either device must be refused by both or by neither.
         *
         * Deliberately wider than what Android executes - it guards what is handed to a viewer, and
         * a type this platform cannot run today may be runnable by an app installed tomorrow.
         */
        val REFUSED_EXTENSIONS = setOf(
            "apk", "apks", "xapk", "dex", "jar", "so", "aar",
            "sh", "bash", "zsh", "bin", "run", "elf",
            "exe", "dll", "msi", "com", "scr", "cpl", "bat", "cmd", "ps1", "psm1", "vbs", "vbe",
            "js", "jse", "wsf", "wsh", "hta", "lnk", "reg", "scf", "url",
            "py", "pyc", "rb", "pl", "php", "jsp", "class"
        )
    }
}

/**
 * The file an errand works on: the row it was copied from when it is a staged copy, whether it was
 * fetched from a network source, and whether it sits in shared storage, where whatever is written
 * beside it has to be announced to MediaStore.
 */
private class FdSecTarget(
    val file: File,
    val stagedFrom: WearMediaFile? = null,
    val fetched: Boolean = false,
    val inSharedStorage: Boolean = false
)

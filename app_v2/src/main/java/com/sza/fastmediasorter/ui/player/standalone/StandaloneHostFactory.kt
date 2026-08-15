package com.sza.fastmediasorter.ui.player.standalone

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.ui.PlayerView
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.domain.usecase.ResolveOpenInFmsTargetUseCase
import com.sza.fastmediasorter.domain.usecase.SaveTextNoteUseCase
import com.sza.fastmediasorter.ui.dialog.FileInfoDialog
import com.sza.fastmediasorter.ui.player.DestinationButtonsManager
import com.sza.fastmediasorter.ui.player.VideoTrackSelectionManager
import com.sza.fastmediasorter.ui.player.helpers.EpubViewerManager
import com.sza.fastmediasorter.ui.player.helpers.NetworkFileManager
import com.sza.fastmediasorter.ui.player.helpers.PdfViewerManager
import com.sza.fastmediasorter.ui.player.helpers.StandaloneFileOperationsHandler
import com.sza.fastmediasorter.ui.player.helpers.StandalonePlayerSettingsManager
import com.sza.fastmediasorter.ui.player.helpers.StandaloneViewManager
import com.sza.fastmediasorter.ui.player.helpers.TextEditorSaveFlow
import com.sza.fastmediasorter.ui.player.helpers.TextViewerManager
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import com.sza.fastmediasorter.ui.share.SendToMenuManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * S1329: owns the six domain dependencies the standalone player family used to field-inject, and hands
 * them to each manager itself, so no host declares a repository or a use case of its own.
 *
 * Every method adapts to its manager exactly as that manager is written today. **No manager constructor
 * signature may change:** nine of them are also built by `PlayerActivity` and
 * `PhotoVideoStandaloneActivity`, the two hosts this ticket defers, so widening one would reach straight
 * into out-of-scope code and past its line budget (strategic ADR-1).
 *
 * Unscoped on purpose - what it builds is Activity-scoped and must not outlive the host that asked.
 */
class StandaloneHostFactory @Inject constructor(
    private val networkClients: StandaloneNetworkClients,
    private val fileOpHandlers: StandaloneFileOpHandlers,
    // S1637: public because PhotoVideoStandaloneActivity reads settings directly for its own UI
    // decisions and builds ImageCropManager itself - neither is a manager this factory constructs.
    val settingsRepository: SettingsRepository,
    private val playbackPositionRepository: PlaybackPositionRepository,
    private val resolveOpenInFmsTarget: ResolveOpenInFmsTargetUseCase,
    val fileOperationUseCase: FileOperationUseCase,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val sendToMenuManager: SendToMenuManager,
    private val saveTextNoteUseCase: SaveTextNoteUseCase,
) {

    /**
     * Both Copy/Move arguments are supplied unconditionally because both hosts migrated in this phase
     * wire them today; the manager itself defaults them to null for hosts that do not. A later phase
     * moving a host that leaves them null must measure that site before reusing this method.
     */
    fun createFileOperationsHandler(
        activity: AppCompatActivity,
        root: View,
        callbacks: StandaloneFileOpsCallbacks,
    ): StandaloneFileOperationsHandler = StandaloneFileOperationsHandler(
        activity = activity,
        root = root,
        getCurrentMediaFile = callbacks.getCurrentMediaFile,
        resolveOpenInFmsTarget = resolveOpenInFmsTarget,
        onRenameComplete = callbacks.onRenameComplete,
        updateAudioMediaItem = callbacks.updateAudioMediaItem,
        batchDeleteLauncher = callbacks.batchDeleteLauncher,
        recoverableDeleteLauncher = callbacks.recoverableDeleteLauncher,
        sendToMenuManager = sendToMenuManager,
        getCurrentSettings = { settingsRepository.getSettings().first() },
        fileOperationUseCase = fileOperationUseCase,
        getDestinationsUseCase = getDestinationsUseCase,
        onPickCustomFolderForCopy = callbacks.onPickCustomFolderForCopy,
    )

    fun createViewManager(
        activity: Activity,
        root: View,
        lifecycleScope: LifecycleCoroutineScope,
    ): StandaloneViewManager = StandaloneViewManager(
        activity = activity,
        root = root,
        lifecycleScope = lifecycleScope,
        smbClient = networkClients.smbClient,
        sftpClient = networkClients.sftpClient,
        ftpClient = networkClients.ftpClient,
        googleDriveClient = networkClients.googleDriveClient,
        dropboxClient = networkClients.dropboxClient,
        oneDriveClient = networkClients.oneDriveClient,
        credentialsRepository = networkClients.credentialsRepository,
        smbFileOperationHandler = fileOpHandlers.smbFileOperationHandler,
        sftpFileOperationHandler = fileOpHandlers.sftpFileOperationHandler,
        ftpFileOperationHandler = fileOpHandlers.ftpFileOperationHandler,
        cloudFileOperationHandler = fileOpHandlers.cloudFileOperationHandler,
        unifiedCache = fileOpHandlers.unifiedCache,
        settingsRepository = settingsRepository,
        playbackPositionRepository = playbackPositionRepository,
    )

    fun createNetworkFileManager(
        context: Context,
        callback: NetworkFileManager.NetworkFileCallback,
    ): NetworkFileManager = NetworkFileManager(
        context = context,
        smbClient = networkClients.smbClient,
        sftpClient = networkClients.sftpClient,
        ftpClient = networkClients.ftpClient,
        googleDriveClient = networkClients.googleDriveClient,
        dropboxClient = networkClients.dropboxClient,
        oneDriveClient = networkClients.oneDriveClient,
        credentialsRepository = networkClients.credentialsRepository,
        smbFileOperationHandler = fileOpHandlers.smbFileOperationHandler,
        sftpFileOperationHandler = fileOpHandlers.sftpFileOperationHandler,
        ftpFileOperationHandler = fileOpHandlers.ftpFileOperationHandler,
        cloudFileOperationHandler = fileOpHandlers.cloudFileOperationHandler,
        unifiedCache = fileOpHandlers.unifiedCache,
        callback = callback,
    )

    fun createDestinationButtons(
        root: View,
        lifecycleScope: LifecycleCoroutineScope,
        callback: DestinationButtonsManager.DestinationButtonsCallback,
        shouldNumberSlots: () -> Boolean,
        slotKeyGlyph: (Int) -> String?,
    ): DestinationButtonsManager = DestinationButtonsManager(
        root = root,
        settingsRepository = settingsRepository,
        getDestinationsUseCase = getDestinationsUseCase,
        lifecycleScope = lifecycleScope,
        callback = callback,
        shouldNumberSlots = shouldNumberSlots,
        slotKeyGlyph = slotKeyGlyph,
    )

    fun createTranslationManager(
        context: Context,
        callback: TranslationManager.TranslationCallback,
    ): TranslationManager = TranslationManager(
        context = context,
        callback = callback,
        settingsRepository = settingsRepository,
    )

    fun createPdfViewerManager(
        root: View,
        networkFileManager: NetworkFileManager,
        coroutineScope: CoroutineScope,
        callback: PdfViewerManager.PdfViewerCallback,
        translationManager: TranslationManager,
        statsSink: StatsSink,
    ): PdfViewerManager = PdfViewerManager(
        root = root,
        networkFileManager = networkFileManager,
        settingsRepository = settingsRepository,
        coroutineScope = coroutineScope,
        callback = callback,
        translationManager = translationManager,
        playbackPositionRepository = playbackPositionRepository,
        statsSink = statsSink,
    )

    /**
     * The loading-indicator coordinator keeps its null default: it is non-null only in the unified
     * player, which this factory does not serve.
     */
    fun createEpubViewerManager(
        root: View,
        networkFileManager: NetworkFileManager,
        coroutineScope: CoroutineScope,
        callback: EpubViewerManager.EpubViewerCallback,
        translationManager: TranslationManager,
    ): EpubViewerManager = EpubViewerManager(
        root = root,
        networkFileManager = networkFileManager,
        settingsRepository = settingsRepository,
        coroutineScope = coroutineScope,
        callback = callback,
        playbackPositionRepository = playbackPositionRepository,
        translationManager = translationManager,
    )

    /**
     * The text lane. `saveFlow` stays a parameter rather than being built here: only a writable local text
     * file gets one, and the host is what knows that. `textNoteStagingRegistry` and
     * `loadingIndicatorCoordinator` keep their null defaults - both are unified-player-only.
     */
    fun createTextViewerManager(
        context: Context,
        root: View,
        networkFileManager: NetworkFileManager,
        coroutineScope: CoroutineScope,
        callback: TextViewerManager.TextViewerCallback,
        translationManager: TranslationManager,
        saveFlow: TextEditorSaveFlow?,
    ): TextViewerManager = TextViewerManager(
        context = context,
        root = root,
        networkFileManager = networkFileManager,
        settingsRepository = settingsRepository,
        coroutineScope = coroutineScope,
        callback = callback,
        translationManager = translationManager,
        saveFlow = saveFlow,
    )

    /**
     * Hands the save flow the operation rather than the use case, which is the seam the flow was written
     * against (its own `saveTextNote` parameter carries that note) - so the host never names the use case.
     */
    fun createTextEditorSaveFlow(
        context: Context,
        scope: CoroutineScope,
    ): TextEditorSaveFlow = TextEditorSaveFlow(
        context = context,
        saveTextNote = saveTextNoteUseCase::invoke,
        scope = scope,
    )

    fun createSettingsManager(
        activity: Activity,
        playerView: PlayerView,
        trackSelectionManager: VideoTrackSelectionManager,
        lifecycleScope: CoroutineScope,
    ): StandalonePlayerSettingsManager = StandalonePlayerSettingsManager(
        activity = activity,
        playerView = playerView,
        settingsRepository = settingsRepository,
        trackSelectionManager = trackSelectionManager,
        lifecycleScope = lifecycleScope,
    )

    /**
     * Resolves the credentials repository here rather than handing the host a `Lazy` to unwrap - the
     * dialog takes the resolved type, and unwrapping is exactly the forwarding this ticket removes.
     */
    fun createFileInfoDialog(
        context: Context,
        mediaFile: MediaFile,
    ): FileInfoDialog = FileInfoDialog(
        context = context,
        mediaFile = mediaFile,
        smbClient = networkClients.smbClient.get(),
        sftpClient = networkClients.sftpClient.get(),
        ftpClient = networkClients.ftpClient.get(),
        credentialsRepository = networkClients.credentialsRepository.get(),
        unifiedCache = fileOpHandlers.unifiedCache.get(),
    )
}

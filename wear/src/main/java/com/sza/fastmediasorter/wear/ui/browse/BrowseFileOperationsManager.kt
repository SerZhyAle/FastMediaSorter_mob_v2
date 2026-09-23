package com.sza.fastmediasorter.wear.ui.browse

import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import com.sza.fastmediasorter.wear.data.repository.WearSendToReceiversRepository
import com.sza.fastmediasorter.wear.domain.files.WearFdSecUseCase
import com.sza.fastmediasorter.wear.domain.files.WearFileCapabilityPolicy
import com.sza.fastmediasorter.wear.domain.files.WearSendToReceiverFilter
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationResult
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearSendToReceiverEntry
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.usecase.PerformWearFileOperationUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val RECEIVER_SUBSCRIPTION_MS = 5_000L

/**
 * S2444: everything the browse screen does *to* its files - which ones are picked, what they permit,
 * and the batch that runs over them.
 *
 * Split out of [BrowseViewModel] because these three dependencies and this set of functions were the
 * one cohesive block in it: the class carried ten constructor parameters and forty functions, and
 * detekt refuses both counts. Nothing here reads the loaded list, the refine state or the navigation
 * arguments, which is what made the seam clean rather than arbitrary.
 *
 * The screen's own context arrives once through [bind] rather than through the constructor, because
 * `viewModelScope` and the published file list do not exist until the ViewModel body has run.
 */
class BrowseFileOperationsManager @Inject constructor(
    private val capabilityPolicy: WearFileCapabilityPolicy,
    private val performFileOperation: PerformWearFileOperationUseCase,
    private val sendToReceiversRepository: WearSendToReceiversRepository,
    private val preferences: WearPreferencesRepository,
    // S3383: asked only what a container is, so this menu and the credential screen cannot end up
    // disagreeing about which files are one.
    private val fdSec: WearFdSecUseCase,
    @ApplicationContext private val context: Context
) {

    private lateinit var scope: CoroutineScope
    private lateinit var displayedFiles: StateFlow<List<WearMediaFile>>
    private var isNetworkSource: () -> Boolean = { false }

    /** S3359: which share the listed files are read from, needed by the two operations onto the watch. */
    private var networkSourceId: () -> String? = { null }
    private var onListInvalidated: () -> Unit = {}

    /**
     * Kept apart from the ui state on purpose: folding it in would re-emit the whole list on every tap
     * and re-run the per-id thumbnail effects the grid keys on.
     */
    private val _selectedFileIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedFileIds: StateFlow<Set<Long>> = _selectedFileIds.asStateFlow()

    private val _operationRun = MutableStateFlow(WearFileOperationRunState())
    val operationRun: StateFlow<WearFileOperationRunState> = _operationRun.asStateFlow()

    /** S2142: owns which write confirmation is waiting and what to retry once it is answered. */
    private val consentManager = MediaStoreConsentManager()

    /** The system confirmation waiting to be shown, or null when nothing is waiting. */
    val consentRequest: StateFlow<IntentSender?> = consentManager.request

    /** Cancelled by the binding scope when the screen goes, so an abandoned batch stops copying. */
    private var operationJob: Job? = null

    private lateinit var _allowedOperations: StateFlow<Set<WearFileOperationKind>>

    /**
     * The operations every selected file permits - an intersection, never a union.
     *
     * A mixed selection is the normal case once "select all" exists, and offering an action only
     * some of its files accept is the trust failure strategic 7 rates first.
     */
    val allowedOperations: StateFlow<Set<WearFileOperationKind>> get() = _allowedOperations

    private lateinit var _sendToReceivers: StateFlow<List<WearSendToReceiverEntry>>

    /**
     * S2142: the receivers this particular selection may be handed to.
     *
     * Narrowed here rather than in the dialog for [allowedOperations]'s reason: a receiver that
     * cannot take what is selected would open onto a refusal, which ADR-3 forbids as firmly for a
     * receiver as for an operation. Two filters, both carried by the record the phone published -
     * [WearSendToReceiverEntry.batchCapable] for a selection of more than one, and
     * [WearSendToReceiverEntry.applicableTypes] for what the files actually are.
     */
    val sendToReceivers: StateFlow<List<WearSendToReceiverEntry>> get() = _sendToReceivers

    /**
     * Hands over the screen context this manager cannot own: the scope its batches run in, the list
     * currently on screen, whether that list came from a network source, and how to ask for a reload.
     *
     * Called exactly once, from the ViewModel's `init`. The two derived flows are built here rather
     * than as constructor-time properties because both combine [displayedFiles], which does not exist
     * before this call.
     */
    fun bind(
        scope: CoroutineScope,
        displayedFiles: StateFlow<List<WearMediaFile>>,
        isNetworkSource: () -> Boolean,
        networkSourceId: () -> String?,
        onListInvalidated: () -> Unit
    ) {
        this.scope = scope
        this.displayedFiles = displayedFiles
        this.isNetworkSource = isNetworkSource
        this.networkSourceId = networkSourceId
        this.onListInvalidated = onListInvalidated

        _allowedOperations = combine(
            displayedFiles,
            _selectedFileIds,
            preferences.fileDoOperationsEnabled
        ) { files, ids, fileDoEnabled ->
            allowedFor(files, ids, fileDoEnabled)
        }.stateIn(scope, SharingStarted.WhileSubscribed(RECEIVER_SUBSCRIPTION_MS), emptySet())

        _sendToReceivers =
            combine(sendToReceiversRepository.observe(), displayedFiles, _selectedFileIds) { receivers, files, ids ->
                WearSendToReceiverFilter.apply(receivers, selectedIn(files, ids))
            }.stateIn(scope, SharingStarted.WhileSubscribed(RECEIVER_SUBSCRIPTION_MS), emptyList())
    }

    /** S3383: whether [file] is a FileDO container, answered by the one place that decides it. */
    fun isContainer(file: WearMediaFile): Boolean = fdSec.isContainer(file.name)

    /** Long press opens selection mode on the pressed file. */
    fun enterSelection(file: WearMediaFile) {
        if (capabilityPolicy.operationsFor(file, isNetworkSource()).isEmpty()) return
        _selectedFileIds.value = setOf(file.id)
    }

    fun toggleSelection(file: WearMediaFile) {
        if (capabilityPolicy.operationsFor(file, isNetworkSource()).isEmpty()) return
        _selectedFileIds.update { current ->
            if (file.id in current) current - file.id else current + file.id
        }
    }

    fun selectAll() {
        _selectedFileIds.value = displayedFiles.value
            .filter { capabilityPolicy.operationsFor(it, isNetworkSource()).isNotEmpty() }
            .map { it.id }
            .toSet()
    }

    fun clearFileSelection() {
        _selectedFileIds.value = emptySet()
    }

    /**
     * What every selected file allows, intersected - an empty set when nothing is selected, so the
     * action chip has nothing to open.
     *
     * A file no operation accepts must never enter the selection either: it would count towards the
     * batch and let the action menu offer work its source cannot perform.
     */
    private fun selectedIn(files: List<WearMediaFile>, ids: Set<Long>): List<WearMediaFile> =
        files.filter { it.id in ids }

    private fun allowedFor(
        files: List<WearMediaFile>,
        ids: Set<Long>,
        fileDoEnabled: Boolean
    ): Set<WearFileOperationKind> {
        val selected = selectedIn(files, ids)
        return if (selected.isEmpty()) {
            emptySet()
        } else {
            selected
                .map { capabilityPolicy.operationsFor(it, isNetworkSource()) }
                .reduce { acc, allowed -> acc intersect allowed }
                // S3383: the FileDO pair is decided here, not in the capability policy. The switch is a
                // preference rather than a property of a file, and only the browse list's navigation
                // graph reaches the credential screen - the player menu and the favourites list draw
                // from the same policy and must not gain an entry that leads nowhere. Both write a new
                // file beside the selected one, so a file that may not be renamed is offered neither.
                .let { allowed ->
                    val single = selected.singleOrNull()
                    val writable = WearFileOperationKind.RENAME in allowed &&
                        single != null && takesContainerBeside(single)
                    allowed + fdSec.offerFor(singleName = single?.name, enabled = fileDoEnabled, writable = writable)
                }
        }
    }

    /**
     * Renaming in place is not enough for the FileDO pair: a shared-storage row may be renamed where
     * scoped storage still refuses a new file with no media type beside it. A file with a path of its
     * own is app-owned and takes anything.
     */
    private fun takesContainerBeside(file: WearMediaFile): Boolean =
        file.uri.scheme != ContentResolver.SCHEME_CONTENT || fdSec.sharedFolderTakesContainer(file.relativePath)

    /** S3383: deletes what a viewed container left in the private cache, once its viewer is gone. */
    fun discardOpenedContainers() {
        scope.launch { fdSec.discardOpened(context.cacheDir) }
    }

    /**
     * Runs [operation] over the current selection, reporting each file as its own result.
     *
     * The run is not collapsed into one verdict: strategic 11 criterion 6 requires the user to read
     * the partial success of a batch, so every emission is accumulated rather than replaced.
     */
    fun runOperation(operation: WearFileOperation) {
        val targets = selectedIn(displayedFiles.value, _selectedFileIds.value)
        when {
            targets.isEmpty() -> Timber.w("Wear file operation requested with an empty selection")
            // S2142: a run already going is left alone rather than cancelled and restarted. A send
            // through the phone has already handed bytes over by its middle, so restarting it is how
            // one file reaches a receiver twice - the risk strategic 7 rates as the likely one here.
            _operationRun.value.running ->
                Timber.i("Wear file operation ignored: a run is already in progress")
            else -> startOperation(targets, operation)
        }
    }

    private fun startOperation(
        targets: List<WearMediaFile>,
        operation: WearFileOperation,
        keptResults: List<WearFileOperationResult> = emptyList()
    ) {
        operationJob?.cancel()
        operationJob = scope.launch {
            _operationRun.value = WearFileOperationRunState(
                running = true,
                total = targets.size,
                // A retry after the owner confirms keeps what the first pass already settled, so a
                // batch that half succeeded does not lose those lines to the second run.
                results = keptResults
            )
            try {
                collectRun(targets, operation)
            } finally {
                // Also on cancellation: without this the progress dialog would keep the screen with
                // running = true forever, and the files never reached would have no answer at all.
                finishRun(targets)
            }
            consentManager.raiseIfBlocked(_operationRun.value.results, targets, operation)
            _selectedFileIds.value = emptySet()
            // Only a run that actually changed the directory invalidates the list on screen; a send
            // that left every file where it was would reload for nothing.
            val changed = _operationRun.value.results.any { it.outcome == WearFileOperationOutcome.SUCCEEDED }
            if (operation.mutatesList() && changed) {
                onListInvalidated()
            }
        }
    }

    /**
     * A failure upstream ends the batch, not the process.
     *
     * The stager reads a MediaStore row through the content resolver, which throws past the
     * [java.io.IOException] it handles when a provider or a grant has gone; unhandled, that killed the
     * app mid-batch and left the progress dialog owning the screen.
     */
    private suspend fun collectRun(targets: List<WearMediaFile>, operation: WearFileOperation) {
        performFileOperation(targets, operation, isNetworkSource(), networkSourceId())
            .catch { throwable ->
                Timber.e(throwable, "Wear file operation failed mid-batch")
                val answered = _operationRun.value.results.map { it.fileName }.toSet()
                targets.filterNot { it.name in answered }.forEach { pending ->
                    emit(WearFileOperationResult(pending.name, WearFileOperationOutcome.FAILED))
                }
            }
            .collect { result ->
                _operationRun.update { current ->
                    current.copy(completed = current.completed + 1, results = current.results + result)
                }
            }
    }

    /**
     * Closes the run, giving every file the batch never reached an explicit CANCELLED line.
     *
     * Silence would otherwise be indistinguishable from success on a screen the user reads once.
     */
    private fun finishRun(targets: List<WearMediaFile>) {
        _operationRun.update { current ->
            val answered = current.results.map { it.fileName }.toSet()
            val cancelled = targets
                .filterNot { it.name in answered }
                .map { WearFileOperationResult(it.name, WearFileOperationOutcome.CANCELLED) }
            current.copy(running = false, results = current.results + cancelled)
        }
    }

    /**
     * The owner has answered the system dialog; a granted one runs the refused files again.
     *
     * A refusal leaves every NEEDS_CONSENT line standing, because that line already reads as "not
     * confirmed, nothing changed" - which is exactly what happened, and what strategic 11 criterion
     * 2 requires the owner to be able to see.
     */
    fun onConsentAnswered(granted: Boolean) {
        val pending = consentManager.consume(granted) ?: return
        val kept = _operationRun.value.results
            .filterNot { it.outcome == WearFileOperationOutcome.NEEDS_CONSENT }
        startOperation(pending.files, pending.operation, keptResults = kept)
    }

    /** Stops a run in flight; [finishRun] then records what it never reached. */
    fun cancelOperation() {
        operationJob?.cancel()
    }

    /** The results stay until the user dismisses them, outliving the reload a run may have caused. */
    fun dismissOperationResults() {
        _operationRun.value = WearFileOperationRunState()
        consentManager.reset()
    }
}

/**
 * What [file] permits, direction included.
 *
 * The screen only ever asks two questions - "may this file be acted on at all" and "what do all the
 * selected ones share" - and both are this one call, so it is named here once rather than as a member
 * per question. S3359 moved the classification inside the policy, which is what keeps a network entry
 * and a phone copy from being asked about as though they were the watch's own files.
 */
private fun WearFileCapabilityPolicy.operationsFor(
    file: WearMediaFile,
    isNetworkSource: Boolean
): Set<WearFileOperationKind> = allowedOperations(file, isNetworkSource)

/**
 * Whether a finished run leaves the list on screen describing files that are no longer there.
 *
 * A move removes the watch copy once the phone confirms, so it invalidates the list exactly as a
 * delete does; a plain send never touches the source.
 */
private fun WearFileOperation.mutatesList(): Boolean = when (this) {
    WearFileOperation.SendToPhone -> false
    WearFileOperation.MoveToPhone -> true
    // A copy to the watch adds a file elsewhere and leaves this listing's own entry untouched; a
    // move takes the entry away, because the source the list is showing is the one being emptied.
    WearFileOperation.CopyToWatch -> false
    WearFileOperation.MoveToWatch -> true
    WearFileOperation.Delete -> true
    is WearFileOperation.Rename -> true
    // Everything it changes happens on the phone; the watch copy it names is still where it was.
    is WearFileOperation.OpenOnPhone -> false
    // Handing a copy to a receiver leaves the original where it is, on either branch.
    is WearFileOperation.SendToReceiver -> false
    // S3383: both write a new file into the listed folder, so the list on screen is stale either
    // way. Neither ever reaches this function through the batch engine - the menu routes them to
    // the credential screen - and the branch exists so that a future caller cannot quietly get the
    // wrong answer instead of a compile error.
    WearFileOperation.EncryptFileDo, WearFileOperation.DecryptFileDo -> true
}

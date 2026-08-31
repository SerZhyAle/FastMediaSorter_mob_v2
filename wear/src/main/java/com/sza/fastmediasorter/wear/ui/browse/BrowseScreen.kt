package com.sza.fastmediasorter.wear.ui.browse

import android.app.Activity
import android.app.RemoteInput
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Alert
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.browse.BrowseCategoryCatalog
import com.sza.fastmediasorter.wear.domain.browse.BrowseRefineState
import com.sza.fastmediasorter.wear.domain.browse.BrowseSortOrder
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearContentType
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationResult
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.ScreenTitle
import com.sza.fastmediasorter.wear.ui.common.WEAR_SEARCH_INPUT_KEY
import com.sza.fastmediasorter.wear.ui.common.WearChoiceDialog
import com.sza.fastmediasorter.wear.ui.common.WearGridScalingParams
import com.sza.fastmediasorter.wear.ui.common.WearRefineControlHeader
import com.sza.fastmediasorter.wear.ui.common.WearRefineHeaderActions
import com.sza.fastmediasorter.wear.ui.common.WearRefineHeaderHeight
import com.sza.fastmediasorter.wear.ui.common.WearRefineHeaderLabels
import com.sza.fastmediasorter.wear.ui.common.WearRefineHeaderState
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearSearchDialog
import com.sza.fastmediasorter.wear.ui.common.WearStateBlock
import com.sza.fastmediasorter.wear.ui.common.WearStateKind
import com.sza.fastmediasorter.wear.ui.common.labelForContentType
import com.sza.fastmediasorter.wear.ui.common.labelForSortOrder
import com.sza.fastmediasorter.wear.ui.common.launchWearSearchInput
import com.sza.fastmediasorter.wear.ui.common.playerRouteFor
import com.sza.fastmediasorter.wear.ui.common.rememberWearRenameInput
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.ui.player.common.VolumeIndicatorBar
import com.sza.fastmediasorter.wear.ui.player.common.VolumeIndicatorViewModel
import com.sza.fastmediasorter.wear.ui.player.common.VolumeReadout
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber

/**
 * Browse screen for displaying media files.
 * Shows a scrollable list of files based on media type (music, videos, photos).
 */
@Composable
fun BrowseScreen(
    navController: NavController,
    backStackEntry: NavBackStackEntry,
    viewModel: BrowseViewModel = hiltViewModel()
) {
    // S2028: read the arguments off this destination's own entry, never off
    // navController.currentBackStackEntry - that property tracks the TOP of the back stack, so
    // every navigation away from Browse (and the pop back into it) recomposes this screen while
    // the top entry belongs to another destination and carries no mediaType. The argument then
    // read as null, the branch below fell back to MUSIC, and the LaunchedEffect keyed on it
    // reloaded the wrong library under the user.
    val mediaTypeArg = backStackEntry.arguments?.getString(WearRoutes.ARG_MEDIA_TYPE)
    val sourceId = backStackEntry.arguments?.getString(WearRoutes.ARG_SOURCE_ID)
    val sourceName = backStackEntry.arguments?.getString(WearRoutes.ARG_SOURCE_NAME)
    Timber.d("S2028: browse args mediaType=$mediaTypeArg sourceId=$sourceId")

    val mediaType = parseMediaType(mediaTypeArg)

    // Initialize ViewModel with navigation args
    LaunchedEffect(mediaTypeArg, sourceId) {
        // S2130: the raw token travels too. Documents, "all" and "recents" carry no MediaType, so
        // collapsing the argument into one would hand the ViewModel a music request for all three.
        viewModel.setNavigationArgs(mediaType, sourceId, sourceName, mediaTypeArg)
        viewModel.loadMediaFiles()
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val fileListViewMode by viewModel.fileListViewMode.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    // Collected beside uiState rather than inside it, so a tap does not re-emit the whole list.
    val selectedIds by viewModel.selectedFileIds.collectAsStateWithLifecycle()
    val allowedOperations by viewModel.allowedOperations.collectAsStateWithLifecycle()
    val operationRun by viewModel.operationRun.collectAsStateWithLifecycle()
    // S2140: its own holder, not a field on BrowseViewModel - the reading belongs to the indicator, so
    // another screen adopting the bar needs this one line and no edit to its state holder.
    val volumeViewModel: VolumeIndicatorViewModel = hiltViewModel()
    val volume by volumeViewModel.readout.collectAsStateWithLifecycle()
    val title = viewModel.getScreenTitle().resolveText()

    var showActions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val requestRename = rememberWearRenameInput { newName ->
        viewModel.runOperation(WearFileOperation.Rename(newName))
    }

    Timber.d("BrowseScreen composing with state: ${uiState.summarize()}")

    // Selection mode owns back first: leaving the screen with a selection still armed would strand
    // the user's choice on a list they can no longer see.
    BackHandler(enabled = selectedIds.isNotEmpty()) {
        showActions = false
        showDeleteConfirm = false
        viewModel.clearFileSelection()
    }

    val listState = rememberScalingLazyListState()

    val refineState by viewModel.refineState.collectAsStateWithLifecycle()

    BrowseScaffold(
        uiState = uiState,
        listState = listState,
        refine = browseRefineUi(viewModel, refineState),
        presentation = BrowseListPresentation(
            title = title,
            thumbnails = thumbnails,
            mediaType = mediaType,
            viewMode = fileListViewMode,
            volume = volume
        ),
        selection = MediaSelectionState(
            selectedIds = selectedIds,
            onSelectAll = viewModel::selectAll,
            onActionsClick = { showActions = true }
        ),
        actions = browseFileActions(
            viewModel = viewModel,
            navController = navController,
            mediaType = mediaType,
            selectedIds = selectedIds,
            running = operationRun.running
        ),
        stateActions = BrowseStateActions(
            onRetry = viewModel::loadMediaFiles,
            onBack = { navController.popBackStack() }
        )
    )

    BrowseDialogsHost(
        state = BrowseDialogsState(
            // Keyed to the selection they act on, following the precedent's
            // `pendingActionSource?.let {}`: back can empty the selection underneath a dialog, and a
            // menu left standing over nothing offers no actions and no way out.
            showActions = showActions && selectedIds.isNotEmpty(),
            showDeleteConfirm = showDeleteConfirm && selectedIds.isNotEmpty(),
            selectedCount = selectedIds.size,
            allowedOperations = allowedOperations,
            run = operationRun
        ),
        viewModel = viewModel,
        onActionsVisibilityChange = { showActions = it },
        onDeleteVisibilityChange = { showDeleteConfirm = it },
        onRequestRename = requestRename
    )

    BrowseRefineDialogsHost(refine = refineState, viewModel = viewModel, viewMode = fileListViewMode)

    MediaStoreConsentPrompt(viewModel = viewModel)
}

/**
 * S2142: shows the system's write confirmation for a MediaStore row the app does not own.
 *
 * The dialog belongs here rather than in `BrowseDialogsHost` because it is not one of the screen's
 * own dialogs: the system owns and draws it, and this side only starts it and reports the answer.
 *
 * [launched] survives Activity recreation, which is the case this guard exists for: the request is
 * deliberately kept in the ViewModel so it outlives the screen, so without the flag a recreation
 * while the system dialog is already up re-enters composition on the same non-null request and
 * stacks a second confirmation on the first. The result callback is re-registered by
 * [rememberLauncherForActivityResult] either way, so the answer still arrives.
 */
@Composable
private fun MediaStoreConsentPrompt(viewModel: BrowseViewModel) {
    val consentRequest by viewModel.consentRequest.collectAsStateWithLifecycle()
    var launched by rememberSaveable { mutableStateOf(false) }
    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onConsentAnswered(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(consentRequest) {
        val request = consentRequest
        when {
            request == null -> launched = false
            !launched -> {
                launched = true
                consentLauncher.launch(IntentSenderRequest.Builder(request).build())
            }
        }
    }
}

/**
 * S2070: the state's kind, and for Success the file count only.
 *
 * Never interpolate the state itself. [BrowseUiState.Success] wraps a list of data classes, so a
 * full `toString()` walks every [WearMediaFile] on every recomposition - and the call site builds
 * that string before any planted `Timber.Tree` gets to filter it by priority.
 */
private fun BrowseUiState.summarize(): String = when (this) {
    is BrowseUiState.Success -> "Success(files=${files.size})"
    else -> javaClass.simpleName
}

/** S2136: binds the refine header to the ViewModel that answers it. */
private fun browseRefineUi(
    viewModel: BrowseViewModel,
    refineState: BrowseRefineState
) = BrowseRefineUi(
    state = refineState,
    // ADR-2: read off the loaded list, not off the route - a category screen lists one type, and
    // its filter icon would open a dialog offering that single type.
    showFilter = viewModel.presentContentTypes().size > 1,
    onSearchClick = {
        // A new attempt retires the previous refusal, so the notice does not outlive the condition
        // it reported.
        viewModel.setSearchInputUnavailable(false)
        viewModel.setShowSearchDialog(true)
    },
    onFilterClick = { viewModel.setShowFilterDialog(true) },
    onSortClick = { viewModel.setShowSortDialog(true) }
)

/** S2136: what the refine header needs from the screen's state, in one carrier. */
private data class BrowseRefineUi(
    val state: BrowseRefineState,
    val showFilter: Boolean,
    val onSearchClick: () -> Unit,
    val onFilterClick: () -> Unit,
    val onSortClick: () -> Unit
)

/**
 * S2136: hands the watch's input result back to the ViewModel as a search query.
 *
 * The fallback over the whole bundle is the streams screen's, kept because some input activities
 * answer under a key of their own rather than the one the request named, and a query that arrived
 * but was read under the wrong key looks exactly like a query the user never typed.
 */
@Composable
private fun rememberBrowseSearchInput(viewModel: BrowseViewModel): () -> Unit {
    val searchHint = stringResource(R.string.wear_browse_search_hint)
    val searchLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val results = result.data?.let { RemoteInput.getResultsFromIntent(it) }
        val query = results?.let { bundle ->
            bundle.getCharSequence(WEAR_SEARCH_INPUT_KEY)?.toString()
                ?: bundle.keySet().firstNotNullOfOrNull { key ->
                    bundle.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }
                }
        }
        if (!query.isNullOrBlank()) {
            viewModel.setSearchQuery(query)
        }
    }
    return {
        launchWearSearchInput(
            searchHint = searchHint,
            onUnavailable = { viewModel.setSearchInputUnavailable(true) },
            launch = { searchLauncher.launch(it) }
        )
    }
}

/** S2136: the three dialogs behind the refine header, each shown by its own flag. */
@Composable
private fun BrowseRefineDialogsHost(
    refine: BrowseRefineState,
    viewModel: BrowseViewModel,
    viewMode: WearViewMode
) {
    // The launcher is remembered here rather than by the screen: it exists only to answer this
    // dialog, and hoisting it made the screen carry a value it never read itself.
    val onLaunchInput = rememberBrowseSearchInput(viewModel)
    if (refine.showSearchDialog) {
        WearSearchDialog(
            title = stringResource(R.string.wear_browse_search),
            inputLabel = stringResource(R.string.wear_browse_search_hint),
            clearLabel = stringResource(R.string.wear_browse_clear_search),
            currentQuery = refine.searchQuery,
            onLaunchInput = onLaunchInput,
            onClear = {
                viewModel.setSearchQuery("")
                viewModel.setShowSearchDialog(false)
            },
            onDismiss = { viewModel.setShowSearchDialog(false) }
        )
    }

    if (refine.showSortDialog) {
        WearChoiceDialog(
            title = stringResource(R.string.wear_browse_sort),
            options = viewModel.availableSortOrders(),
            selected = refine.sortOrder,
            labelOf = { stringResource(labelForSortOrder(it)) },
            onSelected = viewModel::setSortOrder,
            onDismiss = { viewModel.setShowSortDialog(false) },
            viewMode = viewMode
        )
    }

    if (refine.showFilterDialog) {
        BrowseTypeFilterDialog(
            present = viewModel.presentContentTypes(),
            selected = refine.contentTypes,
            onSelected = viewModel::setContentTypes,
            onDismiss = { viewModel.setShowFilterDialog(false) },
            viewMode = viewMode
        )
    }
}

/**
 * S2136: the type filter, offered as one choice rather than a set of toggles.
 *
 * A null option is the "all types" row that clears the set - modelling it as an absent value keeps
 * [WearChoiceDialog] generic instead of teaching it what "everything" means for this screen.
 */
@Composable
private fun BrowseTypeFilterDialog(
    present: List<WearContentType>,
    selected: Set<WearContentType>,
    onSelected: (Set<WearContentType>) -> Unit,
    onDismiss: () -> Unit,
    viewMode: WearViewMode
) {
    val allTypes = stringResource(R.string.wear_browse_filter_type_all)
    WearChoiceDialog(
        title = stringResource(R.string.wear_browse_filter),
        options = listOf(null) + present,
        selected = selected.singleOrNull(),
        labelOf = { type -> if (type == null) allTypes else stringResource(labelForContentType(type)) },
        onSelected = { type -> onSelected(if (type == null) emptySet() else setOf(type)) },
        onDismiss = onDismiss,
        viewMode = viewMode
    )
}

/**
 * The progress of a run, then its per-file answer - one dialog because the second is what the first
 * turns into, and a user who looked away must still find the outcome where the progress was.
 */
@Composable
private fun OperationRunDialog(
    run: WearFileOperationRunState,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    if (run.running) {
        Alert(
            title = {
                Text(
                    text = stringResource(R.string.wear_file_op_progress, run.completed, run.total),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.title3
                )
            }
        ) {
            item {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            // Strategic 3.2 requires the copy to be cancellable, and a batch waiting on an absent
            // phone blocks for ten seconds per file - long enough to own the watch outright.
            item {
                Chip(
                    onClick = onCancel,
                    label = { Text(text = stringResource(R.string.cancel)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    } else {
        Alert(
            title = {
                Text(
                    text = stringResource(R.string.wear_file_op_results_title),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.title3
                )
            }
        ) {
            items(run.results) { result ->
                OperationResultRow(result = result)
            }
            item {
                Chip(
                    onClick = onDismiss,
                    label = { Text(text = stringResource(R.string.done)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors()
                )
            }
        }
    }
}

/** The file, then what happened to it - and the name it actually landed under when that differs. */
@Composable
private fun OperationResultRow(result: WearFileOperationResult) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = result.fileName,
            style = MaterialTheme.typography.body2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val outcomeText = when {
            result.destination != null && result.outcome == WearFileOperationOutcome.SUCCEEDED ->
                stringResource(R.string.wear_file_op_outcome_saved_to, result.destination)
            result.destination != null && result.outcome == WearFileOperationOutcome.QUEUED_ON_PHONE ->
                stringResource(R.string.wear_file_op_outcome_queued_on_phone, result.destination)
            else -> stringResource(result.outcome.messageRes())
        }
        Text(
            text = outcomeText,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant
        )
        result.finalName?.let { finalName ->
            Text(
                text = stringResource(R.string.wear_file_op_renamed_to, finalName),
                style = MaterialTheme.typography.caption2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun WearFileOperationOutcome.messageRes(): Int = when (this) {
    WearFileOperationOutcome.SUCCEEDED -> R.string.wear_file_op_outcome_succeeded
    WearFileOperationOutcome.QUEUED_ON_PHONE -> R.string.wear_file_op_outcome_succeeded
    WearFileOperationOutcome.NO_DESTINATION -> R.string.wear_file_op_outcome_no_destination
    WearFileOperationOutcome.UNCONFIRMED -> R.string.wear_file_op_outcome_unconfirmed
    WearFileOperationOutcome.REFUSED_UNSUPPORTED -> R.string.wear_file_op_outcome_unsupported
    WearFileOperationOutcome.NEEDS_CONSENT -> R.string.wear_file_op_outcome_needs_consent
    WearFileOperationOutcome.REFUSED_TOO_LARGE -> R.string.wear_file_op_outcome_too_large
    WearFileOperationOutcome.PHONE_UNREACHABLE -> R.string.wear_file_op_outcome_phone_unreachable
    WearFileOperationOutcome.OPENED_ON_PHONE -> R.string.wear_open_on_phone_shown
    WearFileOperationOutcome.NOTIFIED_ON_PHONE -> R.string.wear_open_on_phone_notified
    WearFileOperationOutcome.REFUSED_PHONE_NOTIFICATIONS_OFF ->
        R.string.wear_open_on_phone_no_notifications
    WearFileOperationOutcome.FAILED -> R.string.wear_file_op_outcome_failed
    WearFileOperationOutcome.CANCELLED -> R.string.wear_file_op_outcome_cancelled
}

/**
 * S1829: three callers now pass this argument instead of one hard-coded value, so a route that
 * spells the type wrong is a real possibility rather than a theoretical one. The default stays - a
 * browse screen must still open - but it stops being silent: a wrong route used to be
 * indistinguishable from a deliberate music request.
 *
 * S2130: a token the catalog knows but no MediaType covers - documents, "all", "recents" - is not a
 * spelling mistake. The ViewModel routes those three by token and never reads this value for them,
 * so they take the default without the error line that a genuinely unknown token still earns.
 */
private fun parseMediaType(mediaTypeArg: String?): MediaType = when (mediaTypeArg) {
    BrowseCategoryCatalog.TOKEN_MUSIC -> MediaType.MUSIC
    BrowseCategoryCatalog.TOKEN_VIDEOS -> MediaType.VIDEO
    BrowseCategoryCatalog.TOKEN_PHOTOS -> MediaType.PHOTO
    else -> {
        if (BrowseCategoryCatalog.categoryForToken(mediaTypeArg) == null) {
            Timber.e("Browse: unknown media type argument '%s'; falling back to MUSIC", mediaTypeArg)
        }
        MediaType.MUSIC
    }
}

@Composable
private fun ScreenTitle.resolveText(): String = when (this) {
    is ScreenTitle.Text -> value
    is ScreenTitle.Resource -> stringResource(id)
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(wearScreenInsets()),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp)
        )
    }
}

/** The selection travels as one value because its ids and its bulk control are never useful apart. */
internal data class MediaSelectionState(
    val selectedIds: Set<Long>,
    val onSelectAll: () -> Unit,
    val onActionsClick: () -> Unit
)

/** What the list draws: the files, the pictures they resolved to, and the library they came from. */
internal data class MediaListData(
    val files: List<WearMediaFile>,
    val thumbnails: Map<Long, WearThumbnail>,
    val mediaType: MediaType
)

@Composable
private fun MediaListContent(
    title: String,
    data: MediaListData,
    listState: ScalingLazyListState,
    viewMode: WearViewMode,
    selection: MediaSelectionState,
    actions: MediaFileActions,
    volume: VolumeReadout
) {
    // The column count comes from the width this composable actually gets, never from the mode name -
    // the same rule the Resources page applies, so the two lists cannot drift apart.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = GridColumnFit.columnsFor(viewMode, maxWidth.value.toInt())
        val screenInsets = wearScreenInsets()
        val initialCenterIndex = if (columns == 1) 1 else 1
        val autoCentering = remember(columns) { AutoCenteringParams(itemIndex = initialCenterIndex) }
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            autoCentering = autoCentering,
            // S2136: the refine header is laid over this list rather than in it, so the list has to
            // give back the height it covers - otherwise the first row starts under the icons.
            contentPadding = PaddingValues(
                start = screenInsets.calculateLeftPadding(LayoutDirection.Ltr),
                top = screenInsets.calculateTopPadding() + WearRefineHeaderHeight,
                end = screenInsets.calculateRightPadding(LayoutDirection.Ltr),
                bottom = screenInsets.calculateBottomPadding()
            ),
            scalingParams = WearGridScalingParams
        ) {
            item {
                Text(
                    text = title,
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }

            // S2140: unconditional, and second so it is read before the eye reaches the files. This is
            // the last screen before a tap starts playback, which is the moment the warning is for -
            // the category screens above deliberately do not carry it (strategic ADR-3).
            item {
                VolumeIndicatorBar(level = volume.level, max = volume.max)
            }

            // Offered only once a selection exists: on an untouched list they would be controls with
            // nothing to act on.
            if (selection.selectedIds.isNotEmpty()) {
                item {
                    Chip(
                        onClick = selection.onActionsClick,
                        label = { Text(text = stringResource(R.string.wear_file_op_actions)) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.primaryChipColors()
                    )
                }
                item {
                    Chip(
                        onClick = selection.onSelectAll,
                        label = { Text(text = stringResource(R.string.wear_select_all)) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }

            mediaFileItems(
                files = data.files,
                columns = columns,
                thumbnails = data.thumbnails,
                mediaType = data.mediaType,
                selectedIds = selection.selectedIds,
                actions = actions
            )
        }
    }
}

private fun navigateToPlayer(
    navController: NavController,
    file: WearMediaFile,
    mediaType: MediaType
) {
    // The file's own mime type decides; the screen's media type answers only for an unknown one.
    val route = playerRouteFor(file.id, file.mimeType, mediaType)
    Timber.d("S2005: media item tap routes to $route")
    Timber.d("Navigating to: $route for file: ${file.name} (mimeType: ${file.mimeType})")
    navController.navigate(route)
}

/** What the browse dialogs draw. */
private data class BrowseDialogsState(
    val showActions: Boolean,
    val showDeleteConfirm: Boolean,
    val selectedCount: Int,
    val allowedOperations: Set<WearFileOperationKind>,
    val run: WearFileOperationRunState
)

/**
 * Every dialog the browse screen can raise, kept together so the screen itself stays readable.
 *
 * Follows `StreamsDialogsHost`: the host owns which dialog is up and what each answer does, and the
 * screen passes only the state and the two visibility setters it holds.
 */
@Composable
private fun BrowseDialogsHost(
    state: BrowseDialogsState,
    viewModel: BrowseViewModel,
    onActionsVisibilityChange: (Boolean) -> Unit,
    onDeleteVisibilityChange: (Boolean) -> Unit,
    onRequestRename: () -> Unit
) {
    if (state.showActions) {
        FileActionsDialog(
            state = FileActionsDialogState(
                selectedCount = state.selectedCount,
                allowedOperations = state.allowedOperations
            ),
            callbacks = FileActionsCallbacks(
                onSendToPhone = {
                    onActionsVisibilityChange(false)
                    viewModel.runOperation(WearFileOperation.SendToPhone)
                },
                onMoveToPhone = {
                    onActionsVisibilityChange(false)
                    viewModel.runOperation(WearFileOperation.MoveToPhone)
                },
                onRenameRequested = {
                    onActionsVisibilityChange(false)
                    onRequestRename()
                },
                onDeleteRequested = {
                    onActionsVisibilityChange(false)
                    onDeleteVisibilityChange(true)
                }
            )
        )
    }

    if (state.showDeleteConfirm) {
        FileDeleteConfirmDialog(
            selectedCount = state.selectedCount,
            onConfirm = {
                onDeleteVisibilityChange(false)
                viewModel.runOperation(WearFileOperation.Delete)
            },
            onDismiss = { onDeleteVisibilityChange(false) }
        )
    }

    if (!state.run.isIdle) {
        OperationRunDialog(
            run = state.run,
            onCancel = viewModel::cancelOperation,
            onDismiss = viewModel::dismissOperationResults
        )
    }
}

/**
 * What a tap on a file means, which depends on whether a batch is running and whether a selection
 * is already open.
 */
private fun browseFileActions(
    viewModel: BrowseViewModel,
    navController: NavController,
    mediaType: MediaType,
    selectedIds: Set<Long>,
    running: Boolean
): MediaFileActions = MediaFileActions(
    // A run in flight owns the list: letting a tap re-select or open a player while files are being
    // moved would act on rows that are already gone.
    onFileClick = { file ->
        if (running) {
            Timber.d("Browse: tap ignored while a file operation is running")
        } else if (selectedIds.isEmpty()) {
            viewModel.selectFile(file)
            navigateToPlayer(navController, file, mediaType)
        } else {
            viewModel.toggleSelection(file)
        }
    },
    onFileLongClick = { file ->
        if (!running) {
            viewModel.enterSelection(file)
        }
    },
    onThumbnailNeeded = viewModel::thumbnailFor
)

/** The parts of the list's appearance that do not depend on which files the state carries. */
private data class BrowseListPresentation(
    val title: String,
    val thumbnails: Map<Long, WearThumbnail>,
    val mediaType: MediaType,
    val viewMode: WearViewMode,
    val volume: VolumeReadout
)

/** What a stateless branch offers: repeat the load that failed, or leave the screen. */
private data class BrowseStateActions(
    val onRetry: () -> Unit,
    val onBack: () -> Unit
)

/** Picks the branch for the current state and gives only the list branch a position indicator. */
@Composable
private fun BrowseScaffold(
    uiState: BrowseUiState,
    listState: ScalingLazyListState,
    refine: BrowseRefineUi,
    presentation: BrowseListPresentation,
    selection: MediaSelectionState,
    actions: MediaFileActions,
    stateActions: BrowseStateActions
) {
    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        // Only the list branch scrolls, so only it has a position to indicate.
        positionIndicator = if (uiState is BrowseUiState.Success) {
            { PositionIndicator(listState) }
        } else {
            null
        }
    ) {
        // The header sits over the list rather than inside it (strategic 5.3): as a list item it
        // would scroll away, and it has to stay reachable in exactly the states that need it - a
        // search that emptied the list is undone from this row and nowhere else.
        Box(modifier = Modifier.fillMaxSize()) {
            BrowseStateBranch(
                uiState = uiState,
                listState = listState,
                presentation = presentation,
                selection = selection,
                actions = actions,
                stateActions = stateActions
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(wearScreenInsets()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WearRefineControlHeader(
                    state = WearRefineHeaderState(
                        searchActive = refine.state.searchQuery.isNotBlank(),
                        filterActive = refine.state.contentTypes.isNotEmpty(),
                        sortActive = refine.state.sortOrder != BrowseSortOrder.DEFAULT,
                        showFilter = refine.showFilter
                    ),
                    labels = WearRefineHeaderLabels(
                        search = stringResource(R.string.wear_browse_search),
                        filter = stringResource(R.string.wear_browse_filter),
                        sort = stringResource(R.string.wear_browse_sort)
                    ),
                    actions = WearRefineHeaderActions(
                        onSearchClick = refine.onSearchClick,
                        onFilterClick = refine.onFilterClick,
                        onSortClick = refine.onSortClick
                    )
                )

                // The watch refused to offer any input path. Said here rather than in the dialog
                // because the dialog has already closed by the time the refusal comes back, and an
                // unsaid refusal reads as a search that matched everything (S1946).
                if (refine.state.searchInputUnavailable) {
                    Text(
                        text = stringResource(R.string.wear_browse_search_unavailable),
                        style = MaterialTheme.typography.caption2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** Picks the branch for the current state. The header above it is composed in every branch. */
@Composable
private fun BrowseStateBranch(
    uiState: BrowseUiState,
    listState: ScalingLazyListState,
    presentation: BrowseListPresentation,
    selection: MediaSelectionState,
    actions: MediaFileActions,
    stateActions: BrowseStateActions
) {
    when (val state = uiState) {
        is BrowseUiState.Loading -> {
            LoadingContent()
        }
        is BrowseUiState.Success -> {
            MediaListContent(
                title = presentation.title,
                data = MediaListData(
                    files = state.files,
                    thumbnails = presentation.thumbnails,
                    mediaType = presentation.mediaType
                ),
                listState = listState,
                viewMode = presentation.viewMode,
                selection = selection,
                actions = actions,
                volume = presentation.volume
            )
        }
        is BrowseUiState.Empty -> {
            // No retry: the load that produced this emptiness already succeeded.
            WearStateBlock(
                kind = WearStateKind.EMPTY,
                message = state.message.resolveText(),
                onBack = stateActions.onBack
            )
        }
        is BrowseUiState.NoMatches -> {
            // Deliberately not EMPTY and deliberately without Retry: the resource has files, the
            // wearer's own narrowing is hiding them, and reloading would change nothing.
            WearStateBlock(
                kind = WearStateKind.EMPTY,
                message = stringResource(R.string.wear_browse_no_matches),
                onBack = stateActions.onBack
            )
        }
        is BrowseUiState.Error -> {
            WearStateBlock(
                kind = WearStateKind.ERROR,
                message = state.message.resolveText(),
                onRetry = stateActions.onRetry,
                onBack = stateActions.onBack
            )
        }
    }
}

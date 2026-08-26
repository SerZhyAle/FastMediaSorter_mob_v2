package com.sza.fastmediasorter.wear.ui.browse

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
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
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationResult
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.ScreenTitle
import com.sza.fastmediasorter.wear.ui.common.WearGridScalingParams
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearStateBlock
import com.sza.fastmediasorter.wear.ui.common.WearStateKind
import com.sza.fastmediasorter.wear.ui.common.playerRouteFor
import com.sza.fastmediasorter.wear.ui.common.rememberWearRenameInput
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
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
        viewModel.setNavigationArgs(mediaType, sourceId, sourceName)
        viewModel.loadMediaFiles()
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val fileListViewMode by viewModel.fileListViewMode.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()
    // Collected beside uiState rather than inside it, so a tap does not re-emit the whole list.
    val selectedIds by viewModel.selectedFileIds.collectAsStateWithLifecycle()
    val allowedOperations by viewModel.allowedOperations.collectAsStateWithLifecycle()
    val operationRun by viewModel.operationRun.collectAsStateWithLifecycle()
    val title = viewModel.getScreenTitle().resolveText()

    var showActions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val requestRename = rememberWearRenameInput { newName ->
        viewModel.runOperation(WearFileOperation.Rename(newName))
    }

    // S2070: log the state kind and, for Success, the file count only - never interpolate
    // $uiState directly. BrowseUiState.Success wraps a data class list, so a full toString()
    // walks every WearMediaFile in it on every recomposition, and the string is built by this
    // call site before any planted Timber.Tree gets to filter it by priority.
    val uiStateSnapshot = uiState
    val uiStateSummary = when (uiStateSnapshot) {
        is BrowseUiState.Success -> "Success(files=${uiStateSnapshot.files.size})"
        else -> uiStateSnapshot.javaClass.simpleName
    }
    Timber.d("BrowseScreen composing with state: $uiStateSummary")

    // Selection mode owns back first: leaving the screen with a selection still armed would strand
    // the user's choice on a list they can no longer see.
    BackHandler(enabled = selectedIds.isNotEmpty()) {
        showActions = false
        showDeleteConfirm = false
        viewModel.clearFileSelection()
    }

    val listState = rememberScalingLazyListState()

    BrowseScaffold(
        uiState = uiState,
        listState = listState,
        presentation = BrowseListPresentation(
            title = title,
            thumbnails = thumbnails,
            mediaType = mediaType,
            viewMode = fileListViewMode
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
        Text(
            text = stringResource(result.outcome.messageRes()),
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
    WearFileOperationOutcome.REFUSED_UNSUPPORTED -> R.string.wear_file_op_outcome_unsupported
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
 */
private fun parseMediaType(mediaTypeArg: String?): MediaType = when (mediaTypeArg) {
    "music" -> MediaType.MUSIC
    "videos" -> MediaType.VIDEO
    "photos" -> MediaType.PHOTO
    else -> {
        Timber.e("Browse: unknown media type argument '%s'; falling back to MUSIC", mediaTypeArg)
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
    actions: MediaFileActions
) {
    // The column count comes from the width this composable actually gets, never from the mode name -
    // the same rule the Resources page applies, so the two lists cannot drift apart.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = GridColumnFit.columnsFor(viewMode, maxWidth.value.toInt())
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets(),
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
    val viewMode: WearViewMode
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
                    actions = actions
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
}

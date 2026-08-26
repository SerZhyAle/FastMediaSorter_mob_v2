package com.sza.fastmediasorter.wear.ui.browse

import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.wear.input.RemoteInputIntentHelper
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.MediaType
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationResult
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.ScreenTitle
import com.sza.fastmediasorter.wear.ui.common.WearGridScalingParams
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import com.sza.fastmediasorter.wear.util.GridColumnFit
import timber.log.Timber

/** The key the rename input returns its text under. */
private const val KEY_NEW_NAME = "wear_file_op_new_name"

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
    val renameHint = stringResource(R.string.wear_file_op_rename_hint)
    val renameLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val newName = result.data?.let(::newNameFrom)
        if (!newName.isNullOrBlank()) {
            viewModel.runOperation(WearFileOperation.Rename(newName))
        }
    }

    Timber.d("BrowseScreen composing with state: $uiState")

    // Selection mode owns back first: leaving the screen with a selection still armed would strand
    // the user's choice on a list they can no longer see.
    BackHandler(enabled = selectedIds.isNotEmpty()) {
        viewModel.clearFileSelection()
    }

    val listState = rememberScalingLazyListState()

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
                    title = title,
                    data = MediaListData(
                        files = state.files,
                        thumbnails = thumbnails,
                        mediaType = mediaType
                    ),
                    listState = listState,
                    viewMode = fileListViewMode,
                    selection = MediaSelectionState(
                        selectedIds = selectedIds,
                        onSelectAll = viewModel::selectAll,
                        onActionsClick = { showActions = true }
                    ),
                    actions = MediaFileActions(
                        // A run in flight owns the list: letting a tap re-select or open a player
                        // while files are being moved would act on rows that are already gone.
                        onFileClick = { file ->
                            if (operationRun.running) {
                                Timber.d("Browse: tap ignored while a file operation is running")
                            } else if (selectedIds.isEmpty()) {
                                viewModel.selectFile(file)
                                navigateToPlayer(navController, file, mediaType)
                            } else {
                                viewModel.toggleSelection(file)
                            }
                        },
                        onFileLongClick = { file ->
                            if (!operationRun.running) {
                                viewModel.enterSelection(file)
                            }
                        },
                        onThumbnailNeeded = viewModel::thumbnailFor
                    )
                )
            }
            is BrowseUiState.Empty -> {
                EmptyContent(message = state.message.resolveText())
            }
            is BrowseUiState.Error -> {
                ErrorContent(
                    message = state.message.resolveText(),
                    onRetry = { viewModel.loadMediaFiles() }
                )
            }
        }
    }

    if (showActions) {
        FileActionsDialog(
            state = FileActionsDialogState(
                selectedCount = selectedIds.size,
                allowedOperations = allowedOperations
            ),
            callbacks = FileActionsCallbacks(
                onSendToPhone = {
                    showActions = false
                    viewModel.runOperation(WearFileOperation.SendToPhone)
                },
                onMoveToPhone = {
                    showActions = false
                    viewModel.runOperation(WearFileOperation.MoveToPhone)
                },
                onRenameRequested = {
                    showActions = false
                    launchRenameInput(renameHint) { renameLauncher.launch(it) }
                },
                onDeleteRequested = {
                    showActions = false
                    showDeleteConfirm = true
                }
            )
        )
    }

    if (showDeleteConfirm) {
        FileDeleteConfirmDialog(
            selectedCount = selectedIds.size,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.runOperation(WearFileOperation.Delete)
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (!operationRun.isIdle) {
        OperationRunDialog(
            run = operationRun,
            onDismiss = viewModel::dismissOperationResults
        )
    }
}

/**
 * The typed name, keyed answer first.
 *
 * S1946 recorded the failure this order avoids: a watch that returns the text under a key of its own
 * choosing used to read as "the user entered nothing", which here would silently drop a rename.
 */
private fun newNameFrom(data: Intent): String? {
    val results = RemoteInput.getResultsFromIntent(data) ?: return null
    return results.getCharSequence(KEY_NEW_NAME)?.toString()
        ?: results.keySet().firstNotNullOfOrNull { key ->
            results.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }
        }
}

private fun launchRenameInput(hint: String, launch: (Intent) -> Unit) {
    val remoteInput = RemoteInput.Builder(KEY_NEW_NAME)
        .setLabel(hint)
        .build()
    val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
    RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
    try {
        launch(intent)
    } catch (_: ActivityNotFoundException) {
        Timber.w("Wear remote input is unavailable; rename cannot be entered on this watch")
    }
}

/**
 * The progress of a run, then its per-file answer - one dialog because the second is what the first
 * turns into, and a user who looked away must still find the outcome where the progress was.
 */
@Composable
private fun OperationRunDialog(
    run: WearFileOperationRunState,
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

@Composable
private fun EmptyContent(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(wearScreenInsets()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📂",
                style = MaterialTheme.typography.display2
            )
            Text(
                text = message,
                style = MaterialTheme.typography.body1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(wearScreenInsets()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚠️",
                style = MaterialTheme.typography.display2
            )
            Text(
                text = message,
                style = MaterialTheme.typography.body1,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
            Chip(
                onClick = onRetry,
                label = { Text(text = stringResource(R.string.retry)) },
                colors = ChipDefaults.primaryChipColors()
            )
        }
    }
}

private fun navigateToPlayer(
    navController: NavController,
    file: WearMediaFile,
    mediaType: MediaType
) {
    // Determine player based on file's actual mimeType, not the screen's mediaType
    val route = when {
        file.mimeType?.startsWith("image/") == true -> WearRoutes.imageViewer(file.id)
        file.mimeType?.startsWith("video/") == true -> WearRoutes.videoPlayer(file.id)
        file.mimeType?.startsWith("audio/") == true -> WearRoutes.audioPlayer(file.id)
        else -> when (mediaType) {
            MediaType.MUSIC -> WearRoutes.audioPlayer(file.id)
            MediaType.VIDEO -> WearRoutes.videoPlayer(file.id)
            MediaType.PHOTO -> WearRoutes.imageViewer(file.id)
        }
    }
    Timber.d("Navigating to: $route for file: ${file.name} (mimeType: ${file.mimeType})")
    navController.navigate(route)
}

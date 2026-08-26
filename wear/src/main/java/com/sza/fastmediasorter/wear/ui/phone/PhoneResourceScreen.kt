package com.sza.fastmediasorter.wear.ui.phone

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
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
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationOutcome
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceItem
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceResponseStatus
import com.sza.fastmediasorter.wear.domain.model.WearThumbnail
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.ScreenTitle
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
import com.sza.fastmediasorter.wear.ui.browse.FileDeleteConfirmDialog
import com.sza.fastmediasorter.wear.ui.common.WearFileActionsDialog
import com.sza.fastmediasorter.wear.ui.common.WearGridScalingParams
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.WearStateBlock
import com.sza.fastmediasorter.wear.ui.common.WearStateKind
import com.sza.fastmediasorter.wear.ui.common.playerRouteFor
import com.sza.fastmediasorter.wear.ui.common.rememberWearRenameInput
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.util.GridColumnFit
import kotlinx.coroutines.delay

private const val SINGLE_COLUMN = 1

/** Long enough to read one short line on a watch, short enough not to outstay the action. */
private const val NOTICE_VISIBLE_MS = 4_000L

/** The action menu acts on the pressed file alone; the confirmation reuses the selection wording. */
private const val SINGLE_FILE = 1
private val GRID_GAP = GridColumnFit.DEFAULT_GAP_DP.dp
private val ENTRY_ICON_SIZE = 24.dp
private val STATUS_CORNER_RADIUS = 12.dp
private val STATUS_GAP = 8.dp
private val STATUS_PADDING_HORIZONTAL = 12.dp
private val STATUS_PADDING_VERTICAL = 6.dp
private val STATUS_PROGRESS_SIZE = 16.dp

@Composable
fun PhoneResourceScreen(
    navController: NavController,
    viewModel: PhoneResourceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fileListViewMode by viewModel.fileListViewMode.collectAsStateWithLifecycle()
    val thumbnails by viewModel.thumbnails.collectAsStateWithLifecycle()

    // S1846: the tap on a file ends here, once. Navigation is a side effect of an outcome, so it runs
    // in an effect keyed to that outcome and the outcome is consumed straight after - recomposing the
    // list must not push the player a second time.
    val openOutcome by viewModel.openOutcome.collectAsStateWithLifecycle()
    LaunchedEffect(openOutcome) {
        val outcome = openOutcome
        if (outcome is PhoneFileOpenOutcome.Ready) {
            navController.navigate(playerRouteFor(outcome.fileId, outcome.mimeType))
            viewModel.consumeOpenOutcome()
        }
    }

    // Back walks the folder trail first; only the root hands Back back to navigation.
    BackHandler(enabled = true) {
        if (!viewModel.navigateUp()) {
            navController.popBackStack()
        }
    }

    val listState = rememberScalingLazyListState()

    // Held by the screen rather than the ViewModel: which menu is open is view state, and a rotation
    // that dropped it costs nothing, while a ViewModel that carried it would replay it.
    var actionEntry by remember { mutableStateOf<WearPhoneResourceItem?>(null) }
    var deleteEntry by remember { mutableStateOf<WearPhoneResourceItem?>(null) }
    val renameEntry = remember { mutableStateOf<WearPhoneResourceItem?>(null) }
    val requestRename = rememberWearRenameInput { newName ->
        renameEntry.value?.let { viewModel.runOperation(it, WearFileOperation.Rename(newName)) }
    }
    val operationNotice by viewModel.operationNotice.collectAsStateWithLifecycle()

    // The list behind it does not change when an operation lands, so nothing else would ever clear
    // this line - it would sit over the next folder the user walked into.
    LaunchedEffect(operationNotice) {
        if (operationNotice != null) {
            delay(NOTICE_VISIBLE_MS)
            viewModel.consumeOperationNotice()
        }
    }

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        when (val current = state) {
            is PhoneResourceUiState.Loading -> CenteredMessage(
                text = stringResource(R.string.phone_resource_loading),
                showProgress = true
            )

            is PhoneResourceUiState.Content -> Box(modifier = Modifier.fillMaxSize()) {
                PhoneResourceList(
                    items = current.items,
                    listState = listState,
                    viewMode = fileListViewMode,
                    thumbnails = thumbnails,
                    title = current.title,
                    onEntryClick = { entry ->
                        if (entry.isDirectory) {
                            viewModel.openFolder(entry.token, entry.name)
                        } else {
                            viewModel.openFile(entry)
                        }
                    },
                    // A folder has no file to act on, so it keeps the tap it always had.
                    onEntryLongClick = { entry -> if (!entry.isDirectory) actionEntry = entry }
                )

                // S1898: the outcome is anchored to the screen, not to the start of the list. Reaching a
                // file means scrolling to it, so a line drawn as the list's second item reports the result
                // outside the viewport it was meant for - which is why 30 s of waiting and the refusal that
                // followed it both read as nothing happening at all.
                val notice = operationNotice
                val statusRes = notice?.toStatusRes() ?: openOutcome.toStatusRes()
                if (statusRes != null) {
                    PinnedOpenStatus(
                        statusRes = statusRes,
                        showProgress = notice == null && openOutcome == PhoneFileOpenOutcome.Opening,
                        isError = notice == null || notice != WearFileOperationOutcome.SUCCEEDED,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(wearScreenInsets())
                    )
                }
            }

            // No retry: the listing that came back empty already succeeded, so repeating it returns
            // the same empty folder. Until now both cases shared one helper and this screen offered
            // a retry that could not change the answer.
            is PhoneResourceUiState.Empty -> WearStateBlock(
                kind = WearStateKind.EMPTY,
                // A filtered list that says "your phone has nothing to show" would blame the phone for
                // a filter the user chose one screen earlier (S1846).
                message = if (viewModel.mediaType == null) {
                    stringResource(R.string.phone_resource_empty)
                } else {
                    stringResource(R.string.phone_resource_empty_filtered)
                },
                onBack = { navController.popBackStack() }
            )

            is PhoneResourceUiState.Unavailable -> WearStateBlock(
                kind = WearStateKind.UNAVAILABLE,
                message = stringResource(current.reason.toMessageRes()),
                onRetry = viewModel::retry,
                onBack = { navController.popBackStack() }
            )
        }
    }

    actionEntry?.let { entry ->
        val target = viewModel.actionTargetFor(entry)
        if (target == null) {
            // Nothing was ever copied here, so there is nothing on the watch to act on. Saying so is
            // the point of asking first - an empty menu would read as a broken one.
            LaunchedEffect(entry.token) {
                viewModel.reportNothingToActOn()
                actionEntry = null
            }
        } else {
            WearFileActionsDialog(
                file = target,
                allowed = viewModel.allowedOperationsFor(entry),
                onPick = { kind ->
                    actionEntry = null
                    when (kind) {
                        WearFileOperationKind.DELETE -> deleteEntry = entry
                        WearFileOperationKind.RENAME -> {
                            renameEntry.value = entry
                            requestRename()
                        }
                        WearFileOperationKind.SEND_TO_PHONE ->
                            viewModel.runOperation(entry, WearFileOperation.SendToPhone)
                        WearFileOperationKind.MOVE_TO_PHONE ->
                            viewModel.runOperation(entry, WearFileOperation.MoveToPhone)
                    }
                },
                onDismiss = { actionEntry = null }
            )
        }
    }

    deleteEntry?.let { entry ->
        FileDeleteConfirmDialog(
            selectedCount = SINGLE_FILE,
            onConfirm = {
                deleteEntry = null
                viewModel.runOperation(entry, WearFileOperation.Delete)
            },
            onDismiss = { deleteEntry = null }
        )
    }
}

@Composable
private fun PhoneResourceList(
    items: List<WearPhoneResourceItem>,
    listState: ScalingLazyListState,
    viewMode: WearViewMode,
    thumbnails: Map<String, WearThumbnail>,
    title: ScreenTitle,
    onEntryClick: (WearPhoneResourceItem) -> Unit,
    onEntryLongClick: (WearPhoneResourceItem) -> Unit
) {
    // The column count comes from the width this composable actually gets, so this browser answers
    // the geometry question exactly as the general file list does.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = GridColumnFit.columnsFor(viewMode, maxWidth.value.toInt())
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = wearScreenInsets(),
            scalingParams = WearGridScalingParams
        ) {
            item {
                val titleText = when (title) {
                    is ScreenTitle.Text -> title.value
                    is ScreenTitle.Resource -> stringResource(title.id)
                }
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.title3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center
                )
            }

            entryItems(
                items = items,
                columns = columns,
                thumbnails = thumbnails,
                onEntryClick = onEntryClick,
                onEntryLongClick = onEntryLongClick
            )
        }
    }
}

/**
 * S1898: the single place a tap's outcome is reported - the wait first, then whatever it came to, at
 * one coordinate rather than two.
 *
 * Opaque behind the text because the list keeps scrolling underneath it, and deliberately neither
 * clickable nor focusable so the rotating crown keeps addressing the list rather than this row.
 */
@Composable
private fun PinnedOpenStatus(
    @StringRes statusRes: Int,
    showProgress: Boolean,
    modifier: Modifier = Modifier,
    isError: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface, RoundedCornerShape(STATUS_CORNER_RADIUS))
            .padding(horizontal = STATUS_PADDING_HORIZONTAL, vertical = STATUS_PADDING_VERTICAL),
        horizontalArrangement = Arrangement.spacedBy(STATUS_GAP, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showProgress) {
            CircularProgressIndicator(modifier = Modifier.size(STATUS_PROGRESS_SIZE))
        }
        Text(
            text = stringResource(statusRes),
            style = MaterialTheme.typography.caption2,
            // Waiting is not a failure, and neither is a finished operation: the error colour belongs
            // to the outcomes that are one.
            color = if (showProgress || !isError) {
                MaterialTheme.colors.onSurface
            } else {
                MaterialTheme.colors.error
            },
            textAlign = TextAlign.Center
        )
    }
}

/** One column keeps today's chip; more than one draws the cell both watch file lists share. */
private fun ScalingLazyListScope.entryItems(
    items: List<WearPhoneResourceItem>,
    columns: Int,
    thumbnails: Map<String, WearThumbnail>,
    onEntryClick: (WearPhoneResourceItem) -> Unit,
    onEntryLongClick: (WearPhoneResourceItem) -> Unit
) {
    if (columns == SINGLE_COLUMN) {
        items(items) { entry ->
            EntryChip(entry = entry, onEntryClick = onEntryClick)
        }
    } else {
        items(items.chunked(columns)) { rowEntries ->
            EntryRow(
                entries = rowEntries,
                columns = columns,
                thumbnails = thumbnails,
                onEntryClick = onEntryClick,
                onEntryLongClick = onEntryLongClick
            )
        }
    }
}

/** A short row is padded with empty weights so its cells keep the width of a full row's cells. */
@Composable
private fun EntryRow(
    entries: List<WearPhoneResourceItem>,
    columns: Int,
    thumbnails: Map<String, WearThumbnail>,
    onEntryClick: (WearPhoneResourceItem) -> Unit,
    onEntryLongClick: (WearPhoneResourceItem) -> Unit
) {
    val longPressLabel = stringResource(R.string.wear_file_op_actions)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
    ) {
        entries.forEach { entry ->
            ThumbnailCell(
                thumbnail = thumbnails[entry.token] ?: WearThumbnail.Unavailable,
                caption = entry.name,
                onClick = { onEntryClick(entry) },
                // This screen has no multi-select mode to reach the menu a second way, so the gesture
                // is announced instead: without the label TalkBack never offers it at all.
                modifier = Modifier
                    .weight(1f)
                    .semantics { onLongClick(label = longPressLabel, action = null) },
                onLongClick = { onEntryLongClick(entry) }
            ) { glyphModifier ->
                EntryIcon(entry = entry, modifier = glyphModifier)
            }
        }
        repeat(columns - entries.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EntryChip(
    entry: WearPhoneResourceItem,
    onEntryClick: (WearPhoneResourceItem) -> Unit
) {
    Chip(
        onClick = { onEntryClick(entry) },
        label = { Text(text = entry.name) },
        icon = { EntryIcon(entry = entry) },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = entry.name },
        colors = ChipDefaults.primaryChipColors()
    )
}

/**
 * The chip path keeps the fixed 24 dp it always had; the cell path is handed the placeholder
 * modifier instead, so the same glyph is a chip icon in one place and a full-cell glyph in the other.
 */
@Composable
private fun EntryIcon(entry: WearPhoneResourceItem, modifier: Modifier = Modifier.size(ENTRY_ICON_SIZE)) {
    Icon(
        imageVector = if (entry.isDirectory) {
            Icons.Filled.Folder
        } else {
            Icons.AutoMirrored.Filled.InsertDriveFile
        },
        contentDescription = null,
        modifier = modifier
    )
}

@Composable
private fun CenteredMessage(text: String, showProgress: Boolean) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = wearScreenInsets()
    ) {
        if (showProgress) {
            item { CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp)) }
        }
        item {
            Text(
                text = text,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * S1846: the line shown after a tap that did not reach a player, or null when there is nothing to say.
 * `Ready` shows nothing because the screen is already leaving for the player. S1898 moved where it is
 * drawn - see [PinnedOpenStatus] - without changing which outcomes speak.
 */
@StringRes
private fun PhoneFileOpenOutcome?.toStatusRes(): Int? = when (this) {
    PhoneFileOpenOutcome.Opening -> R.string.phone_resource_opening
    PhoneFileOpenOutcome.Unsupported -> R.string.phone_resource_open_unsupported
    is PhoneFileOpenOutcome.Failed -> reason.toMessageRes()
    else -> null
}

/**
 * What a finished file operation says. The wording is the general browser's - one operation reported
 * in two vocabularies would read as two different features.
 */
@StringRes
private fun WearFileOperationOutcome.toStatusRes(): Int = when (this) {
    WearFileOperationOutcome.SUCCEEDED -> R.string.wear_file_op_outcome_succeeded
    WearFileOperationOutcome.REFUSED_UNSUPPORTED -> R.string.wear_file_op_outcome_unsupported
    WearFileOperationOutcome.REFUSED_TOO_LARGE -> R.string.wear_file_op_outcome_too_large
    WearFileOperationOutcome.PHONE_UNREACHABLE -> R.string.wear_file_op_outcome_phone_unreachable
    WearFileOperationOutcome.FAILED -> R.string.wear_file_op_outcome_failed
    WearFileOperationOutcome.CANCELLED -> R.string.wear_file_op_outcome_cancelled
}

private fun WearPhoneResourceResponseStatus?.toMessageRes(): Int = when (this) {
    WearPhoneResourceResponseStatus.SOURCE_UNAVAILABLE -> R.string.phone_resource_source_unavailable
    WearPhoneResourceResponseStatus.ACCESS_DENIED -> R.string.phone_resource_access_denied
    WearPhoneResourceResponseStatus.UNSUPPORTED_MEDIA -> R.string.phone_resource_unsupported
    WearPhoneResourceResponseStatus.TRANSFER_REJECTED -> R.string.phone_resource_transfer_rejected
    // S1897: the phone answered and refused - it just could not find the file. Without its own message
    // this fell to the fallback below and told the user the phone was out of reach, which a device run
    // showed being read as "nothing happened" while the phone had in fact replied in 41 ms.
    WearPhoneResourceResponseStatus.NOT_FOUND -> R.string.phone_resource_not_found
    else -> R.string.phone_resource_unavailable
}

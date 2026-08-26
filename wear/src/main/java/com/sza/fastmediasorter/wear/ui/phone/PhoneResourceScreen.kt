package com.sza.fastmediasorter.wear.ui.phone

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import com.sza.fastmediasorter.wear.ui.browse.FileDeleteConfirmDialog
import com.sza.fastmediasorter.wear.ui.common.ScreenTitle
import com.sza.fastmediasorter.wear.ui.common.ThumbnailCell
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

    val openOutcome by viewModel.openOutcome.collectAsStateWithLifecycle()

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
    val renameEntry = remember { mutableStateOf<WearPhoneResourceItem?>(null) }
    val requestRename = rememberWearRenameInput { newName ->
        renameEntry.value?.let { viewModel.runOperation(it, WearFileOperation.Rename(newName)) }
    }
    val operationNotice by viewModel.operationNotice.collectAsStateWithLifecycle()

    PhoneResourceEffects(
        viewModel = viewModel,
        navController = navController,
        openOutcome = openOutcome,
        operationNotice = operationNotice
    )

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
                        when {
                            entry.isDirectory -> viewModel.openFolder(entry.token, entry.name)
                            // S2092: no player on the watch renders this kind, and the row said so
                            // before the tap. The tap leads to the menu the long press opens, so the
                            // one action that can succeed - asking the phone to show the original -
                            // is one tap away instead of behind a transfer bound to be refused.
                            entry.mimeType == null -> actionEntry = entry
                            else -> viewModel.openFile(entry)
                        }
                    },
                    // A folder has no file to act on, so it keeps the tap it always had.
                    onEntryLongClick = { entry -> if (!entry.isDirectory) actionEntry = entry }
                )

                PinnedOutcome(notice = operationNotice, openOutcome = openOutcome)
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

    PhoneFileDialogs(
        viewModel = viewModel,
        actionEntry = actionEntry,
        onClose = { actionEntry = null },
        onRename = { entry ->
            renameEntry.value = entry
            requestRename()
        }
    )
}

/**
 * The menu the long press opens and the confirmation a delete leads to.
 *
 * Which file the confirmation is about is kept here rather than by the screen: it is answered by the
 * menu and consumed by the confirmation, and nothing outside these two dialogs reads it.
 */
@Composable
private fun PhoneFileDialogs(
    viewModel: PhoneResourceViewModel,
    actionEntry: WearPhoneResourceItem?,
    onClose: () -> Unit,
    onRename: (WearPhoneResourceItem) -> Unit
) {
    var deleteEntry by remember { mutableStateOf<WearPhoneResourceItem?>(null) }

    actionEntry?.let { entry ->
        PhoneFileActionsMenu(
            entry = entry,
            viewModel = viewModel,
            onClose = onClose,
            onDelete = { deleteEntry = entry },
            onRename = { onRename(entry) }
        )
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

/**
 * The two one-shot reactions the screen owes its ViewModel, kept together and away from the layout.
 *
 * S1846: the tap on a file ends here, once. Navigation is a side effect of an outcome, so it runs in an
 * effect keyed to that outcome and the outcome is consumed straight after - recomposing the list must
 * not push the player a second time.
 */
@Composable
private fun PhoneResourceEffects(
    viewModel: PhoneResourceViewModel,
    navController: NavController,
    openOutcome: PhoneFileOpenOutcome?,
    operationNotice: WearFileOperationOutcome?
) {
    LaunchedEffect(openOutcome) {
        if (openOutcome is PhoneFileOpenOutcome.Ready) {
            navController.navigate(playerRouteFor(openOutcome.fileId, openOutcome.mimeType))
            viewModel.consumeOpenOutcome()
        }
    }

    // The list behind it does not change when an operation lands, so nothing else would ever clear this
    // line - it would sit over the next folder the user walked into.
    LaunchedEffect(operationNotice) {
        if (operationNotice != null) {
            delay(NOTICE_VISIBLE_MS)
            viewModel.consumeOperationNotice()
        }
    }
}

/**
 * S1898: the outcome is anchored to the screen, not to the start of the list. Reaching a file means
 * scrolling to it, so a line drawn as the list's second item reports the result outside the viewport it
 * was meant for - which is why 30 s of waiting and the refusal that followed it both read as nothing
 * happening at all.
 */
@Composable
private fun BoxScope.PinnedOutcome(
    notice: WearFileOperationOutcome?,
    openOutcome: PhoneFileOpenOutcome?
) {
    val statusRes = notice?.toStatusRes() ?: openOutcome.toStatusRes() ?: return
    PinnedOpenStatus(
        statusRes = statusRes,
        showProgress = notice == null && openOutcome == PhoneFileOpenOutcome.Opening,
        isError = notice == null || !notice.isSuccess(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(wearScreenInsets())
    )
}

/**
 * The action menu for one phone entry, reached by a long press or by tapping a row the watch cannot
 * render.
 *
 * The screen keeps the "which entry is open" state and this decides what may be done with it, so the
 * two dialogs it can lead to stay owned by the screen that has to close them.
 *
 * S2092: the menu is never empty, so it no longer has a "nothing to act on" branch. An entry this
 * watch never fetched still has the phone's original behind it, and opening that costs no transfer.
 */
@Composable
private fun PhoneFileActionsMenu(
    entry: WearPhoneResourceItem,
    viewModel: PhoneResourceViewModel,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    // Both answers stat the cache directory, so they are read once per pressed entry rather than on
    // every recomposition the open dialog causes - disk work does not belong in a composition pass.
    val target = remember(entry.token) { viewModel.actionTargetFor(entry) }
    val allowed = remember(entry.token) { viewModel.allowedOperationsFor(entry) }
    WearFileActionsDialog(
        file = target,
        allowed = allowed,
        onPick = { kind ->
            onClose()
            when (kind) {
                WearFileOperationKind.DELETE -> onDelete()
                WearFileOperationKind.RENAME -> onRename()
                WearFileOperationKind.SEND_TO_PHONE ->
                    viewModel.runOperation(entry, WearFileOperation.SendToPhone)
                WearFileOperationKind.MOVE_TO_PHONE ->
                    viewModel.runOperation(entry, WearFileOperation.MoveToPhone)
                // The only surface that can ask: the token addressing the phone's own original is the
                // one this list was built from.
                WearFileOperationKind.OPEN_ON_PHONE ->
                    viewModel.runOperation(entry, WearFileOperation.OpenOnPhone(entry.token))
            }
        },
        onDismiss = onClose
    )
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
    WearFileOperationOutcome.OPENED_ON_PHONE -> R.string.wear_open_on_phone_shown
    WearFileOperationOutcome.NOTIFIED_ON_PHONE -> R.string.wear_open_on_phone_notified
    WearFileOperationOutcome.REFUSED_PHONE_NOTIFICATIONS_OFF ->
        R.string.wear_open_on_phone_no_notifications
    WearFileOperationOutcome.FAILED -> R.string.wear_file_op_outcome_failed
    WearFileOperationOutcome.CANCELLED -> R.string.wear_file_op_outcome_cancelled
}

/**
 * Whether the line reports something that worked, so the pinned status is not painted as an error.
 *
 * Both phone answers count: a notification the user still has to tap is the action succeeding on a
 * phone that was not in the foreground, not the action failing.
 */
private fun WearFileOperationOutcome.isSuccess(): Boolean = this == WearFileOperationOutcome.SUCCEEDED ||
    this == WearFileOperationOutcome.OPENED_ON_PHONE ||
    this == WearFileOperationOutcome.NOTIFIED_ON_PHONE

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

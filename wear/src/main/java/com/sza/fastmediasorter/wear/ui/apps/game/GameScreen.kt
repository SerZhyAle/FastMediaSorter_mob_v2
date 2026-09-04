package com.sza.fastmediasorter.wear.ui.apps.game

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.game.GameDirection
import com.sza.fastmediasorter.wear.domain.game.GameStatus
import com.sza.fastmediasorter.wear.ui.common.WearBackAffordance
import com.sza.fastmediasorter.wear.ui.common.WearBackAffordanceRole
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearBackAffordanceInset
import com.sza.fastmediasorter.wear.ui.common.wearMaxSquareSide
import com.sza.fastmediasorter.wear.ui.common.wearRingInset
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import kotlinx.coroutines.delay
import timber.log.Timber
import kotlin.math.abs

/** Below this the gesture was a tap or a tremor, not a swipe, and no move is made. */
private val MIN_SWIPE_TRAVEL = 16.dp

/** Shown while the first board is still being generated, so the ring never reads a level of zero. */
private const val FIRST_LEVEL_DISPLAYED = 1

/** Square screens only: the gap between the counter row and the board it sits above. */
private val SQUARE_ROW_SPACING = 2.dp

/**
 * Pause between finishing a level and the next board appearing, matching the phone's.
 *
 * The pause exists so the player sees that the level was finished: without it the board is replaced
 * in the same frame as the last move and the completion is never shown.
 */
private const val AUTO_ADVANCE_DELAY_MS = 1000L

/**
 * How long the start-of-level arrow stays on the board (S2494).
 *
 * The phone's `GameBoardView.START_HIGHLIGHT_MS`, taken as is: the owner described the hint as
 * lasting half a second and named the phone as the thing to match, and the phone shows it for one.
 */
private const val GUIDE_ARROW_VISIBLE_MS = 1000L

/**
 * The game: a board filling the largest square the glass admits, moved by swiping across it.
 *
 * @param navController the route's host controller. S2158 narrowly reversed the earlier ruling that
 * this screen navigates nowhere, and S2553 finished the reversal: the board is still played by
 * swipes across it, but it now carries the module's standard back affordance beside the long-press
 * menu, so leaving no longer depends on remembering either the menu or the platform's edge swipe.
 */
@Composable
fun GameScreen(
    navController: NavController,
    viewModel: GameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRound = LocalConfiguration.current.isScreenRound
    val levelNumber = uiState.level?.config?.levelNumber ?: FIRST_LEVEL_DISPLAYED
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val menuActions = GameMenuActions(
        onSkipTurn = { viewModel.skipTurn() },
        onRestartLevel = { viewModel.restartLevelNow() },
        onNewGame = { viewModel.startNewGame() },
        onOpenRules = { navController.navigate(WearRoutes.GAME_RULES) },
        onExit = { navController.popBackStack() },
        onDismiss = { menuOpen = false }
    )

    // No job field, no flag, no cancel call: the effect is keyed on the status and the level number,
    // so a status change, an early tap on the chip and leaving the screen each cancel it - all three
    // either move a key or leave the composition. That is the phone's re-check guard without a
    // second piece of state.
    LaunchedEffect(isRound) {
        Timber.d("S2158: game screen laid out, round=%b", isRound)
    }

    val boardKey = uiState.level?.config?.let { config -> config.levelNumber to config.seed }
    val showGuideArrow = rememberGuideArrowVisibility(boardKey)

    LaunchedEffect(uiState.status, levelNumber) {
        if (uiState.status == GameStatus.LEVEL_WON) {
            Timber.d("S2158: level %d won, advancing in %d ms", levelNumber, AUTO_ADVANCE_DELAY_MS)
            delay(AUTO_ADVANCE_DELAY_MS)
            viewModel.restart()
        }
    }

    // Full bleed: the ring is measured against the glass, so the scaffold must not inset it first.
    WearScreenScaffold(contentPadding = PaddingValues(0.dp)) {
        // One box for both shapes since S2553: the two side affordances stand in the same place on
        // either display, so keeping a box per branch would only duplicate their placement.
        Box(modifier = Modifier.fillMaxSize()) {
            if (isRound) {
                GameBoard(
                    uiState = uiState,
                    modifier = Modifier.align(Alignment.Center).size(wearMaxSquareSide()),
                    menuOpen = menuOpen,
                    showGuideArrow = showGuideArrow,
                    onMove = { direction -> viewModel.move(direction) },
                    onOpenMenu = {
                        Timber.d("S2158: in-play menu opened by long press")
                        menuOpen = true
                    }
                )
                GameStatsRing(stats = uiState.stats, levelNumber = levelNumber)
                if (uiState.status != GameStatus.PLAYING) {
                    GameOutcomeChip(
                        status = uiState.status,
                        modifier = Modifier.align(Alignment.Center)
                    ) { viewModel.restart() }
                }
            } else {
                // A square screen has no ring: an inscribed square leaves nothing around it, so the
                // counters keep the row above the board they had before S2158.
                SquareScreenLayout(
                    uiState = uiState,
                    levelNumber = levelNumber,
                    menuOpen = menuOpen,
                    showGuideArrow = showGuideArrow,
                    onMove = { direction -> viewModel.move(direction) },
                    onOpenMenu = {
                        Timber.d("S2158: in-play menu opened by long press")
                        menuOpen = true
                    },
                    onRestart = { viewModel.restart() }
                )
            }
            if (!menuOpen) {
                GameSideAffordances(
                    skipVisible = uiState.status == GameStatus.PLAYING,
                    onBack = {
                        Timber.d("S2553: back affordance tapped on the game screen")
                        navController.popBackStack()
                    },
                    onSkipTurn = {
                        Timber.d("S2553: skip turn tapped beside the board")
                        viewModel.skipTurn()
                    }
                )
            }
            if (menuOpen) {
                GameMenuOverlay(actions = menuActions)
            }
        }
    }
}

/**
 * The back mark and the skip-turn button, flanking the board at the middles of the two side bands.
 *
 * Drawn after the board so a tap on either wins over the board's own long press where the touch
 * targets grow inwards over it. Both are hidden while the menu is open: the menu already carries
 * exit and skip rows, and a control under an overlay answers a tap the player cannot see it accept.
 */
@Composable
private fun BoxScope.GameSideAffordances(
    skipVisible: Boolean,
    onBack: () -> Unit,
    onSkipTurn: () -> Unit
) {
    WearBackAffordance(
        role = WearBackAffordanceRole.Back,
        onClick = onBack,
        modifier = Modifier
            .align(Alignment.CenterStart)
            .padding(start = wearBackAffordanceInset())
    )
    if (skipVisible) {
        GameSkipTurnButton(
            onClick = onSkipTurn,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = wearBackAffordanceInset())
        )
    }
}

/**
 * Whether the start-of-level arrow is currently drawn for the board named by [boardKey].
 *
 * Keyed on the board, not on the state: the level number alone would keep the arrow off after a
 * restart, which draws a new board under the same number, and the whole state would flash it again
 * after every move. A null key is a screen with no board yet, which shows nothing rather than an
 * arrow pointing out of an empty state.
 */
@Composable
private fun rememberGuideArrowVisibility(boardKey: Pair<Int, Long>?): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(boardKey) {
        if (boardKey == null) {
            return@LaunchedEffect
        }
        visible = true
        Timber.d("S2494: guide arrow shown for level %d seed %d", boardKey.first, boardKey.second)
        delay(GUIDE_ARROW_VISIBLE_MS)
        visible = false
    }
    return visible
}

@Composable
private fun SquareScreenLayout(
    uiState: GameUiState,
    levelNumber: Int,
    menuOpen: Boolean,
    showGuideArrow: Boolean,
    onMove: (GameDirection) -> Unit,
    onOpenMenu: () -> Unit,
    onRestart: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(wearRingInset()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SQUARE_ROW_SPACING, Alignment.CenterVertically)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = stringResource(R.string.wear_game_score_description, uiState.stats.score),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.wear_game_level_description, levelNumber),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.wear_game_turns_description, uiState.stats.turns),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
        val side = wearMaxSquareSide()
        GameBoard(
            uiState = uiState,
            modifier = Modifier.weight(1f, fill = false).sizeIn(maxWidth = side, maxHeight = side),
            menuOpen = menuOpen,
            showGuideArrow = showGuideArrow,
            onMove = onMove,
            onOpenMenu = onOpenMenu
        )
        if (uiState.status != GameStatus.PLAYING) {
            GameOutcomeChip(status = uiState.status, modifier = Modifier.fillMaxWidth(), onAct = onRestart)
        }
    }
}

@Composable
private fun GameBoard(
    uiState: GameUiState,
    modifier: Modifier = Modifier,
    menuOpen: Boolean,
    showGuideArrow: Boolean,
    onMove: (GameDirection) -> Unit,
    onOpenMenu: () -> Unit
) {
    val level = uiState.level ?: return
    GameBoardCanvas(
        level = level,
        contentDescription = stringResource(R.string.wear_game_board_description),
        showGuideArrow = showGuideArrow,
        modifier = modifier
            .aspectRatio(1f)
            // Two detectors, two modifiers: sharing one gesture scope would make the long press and
            // the drag compete for the same pointer stream. While the menu covers the board the drag
            // is not applied at all - a board answering a drag under an overlay would move unseen.
            .longPressToOpenMenu(onOpenMenu)
            .then(if (menuOpen) Modifier else Modifier.swipeToMove(onMove))
    )
}

private fun Modifier.longPressToOpenMenu(onOpenMenu: () -> Unit): Modifier =
    pointerInput(onOpenMenu) {
        detectTapGestures(onLongPress = { onOpenMenu() })
    }

@Composable
private fun GameOutcomeChip(status: GameStatus, modifier: Modifier = Modifier, onAct: () -> Unit) {
    val won = status == GameStatus.LEVEL_WON
    val outcome = if (won) R.string.wear_game_level_completed else R.string.wear_game_level_lost
    val action = if (won) R.string.wear_game_continue else R.string.wear_game_restart
    Chip(
        onClick = onAct,
        colors = ChipDefaults.primaryChipColors(),
        label = { Text(text = stringResource(action)) },
        secondaryLabel = { Text(text = stringResource(outcome)) },
        modifier = modifier
    )
}

/**
 * One move per gesture, decided by the axis the finger travelled furthest along.
 *
 * The drag is consumed: without that the platform reads a horizontal swipe as "dismiss this screen"
 * and the player leaves the game instead of moving left. Only drags that started on the board are
 * claimed, so the edge swipe that leaves the game still belongs to the platform.
 */
private fun Modifier.swipeToMove(onMove: (GameDirection) -> Unit): Modifier = pointerInput(onMove) {
    val threshold = MIN_SWIPE_TRAVEL.toPx()
    var travel = Offset.Zero
    detectDragGestures(
        onDragStart = { travel = Offset.Zero },
        onDragCancel = { travel = Offset.Zero },
        onDragEnd = { directionOf(travel, threshold)?.let(onMove) },
        onDrag = { change, amount ->
            change.consume()
            travel += amount
        }
    )
}

private fun directionOf(travel: Offset, threshold: Float): GameDirection? {
    val horizontal = abs(travel.x) >= abs(travel.y)
    val distance = if (horizontal) abs(travel.x) else abs(travel.y)
    return when {
        distance < threshold -> null
        horizontal && travel.x > 0 -> GameDirection.RIGHT
        horizontal -> GameDirection.LEFT
        travel.y > 0 -> GameDirection.DOWN
        else -> GameDirection.UP
    }
}

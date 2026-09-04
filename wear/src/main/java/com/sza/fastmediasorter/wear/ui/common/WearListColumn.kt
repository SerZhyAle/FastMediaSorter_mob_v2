package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.ScalingParams
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.sza.fastmediasorter.wear.util.GridColumnFit
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Creates and remembers a [ScalingLazyListState] for use with [WearListColumn].
 *
 * Contract: [rememberWearListState] and [WearListColumn] are taken together as a pair.
 * The returned state should be passed to [WearListColumn] and, where applicable, forwarded
 * to the screen scaffold (`WearScreenScaffold`) and position indicator unchanged.
 *
 * By default, lists prescroll to the first data row (initialCenterItemIndex = 1), skipping
 * the leading title item so that multi-column grids open displaying 3/6/9 items at once (S2466).
 * Screens without a leading title item (e.g. HomeScreen, StreamsScreen) or fixed control panels
 * explicitly pass [initialCenterItemIndex] = 0.
 *
 * @param positionKey opts this list into position memory (S2543). A key identifies the list's content -
 * its navigation route, plus whatever argument distinguishes what it shows - so the screen reopens on the
 * item that was in the middle of the display when the user left it. Null keeps the plain behaviour above,
 * which is what every dialog, sheet and secondary list of a route uses: they share their route with the
 * screen's own list and a shared key would restore one list's position into another.
 */
@Composable
fun rememberWearListState(
    initialCenterItemIndex: Int = 1,
    positionKey: String? = null
): ScalingLazyListState {
    Timber.d("S2466: rememberWearListState initialItemIndex=%d", initialCenterItemIndex)
    val store = LocalWearListPositions.current
    val saved = remember(positionKey, store) { positionKey?.let { store?.peek(it) } }
    val state = rememberScalingLazyListState(
        initialCenterItemIndex = saved?.index ?: initialCenterItemIndex,
        initialCenterItemScrollOffset = saved?.offset ?: 0
    )
    WearListPositionMemory(state = state, positionKey = positionKey, store = store)
    return state
}

/**
 * Restores the remembered anchor once the list actually has rows, and writes the current one back when
 * the screen leaves composition - which is exactly the "back" the user performs.
 *
 * The restore cannot ride on `initialCenterItemIndex` alone: folder, stream and note lists are filled
 * asynchronously, so on the first frame the list is still empty and any initial index is discarded.
 */
@Composable
private fun WearListPositionMemory(
    state: ScalingLazyListState,
    positionKey: String?,
    store: WearListPositionStore?
) {
    if (positionKey == null || store == null) {
        return
    }

    // Guards the write below. A screen left while its list is still loading has a position that means
    // nothing - saving it would overwrite the anchor the user is coming back for with row zero.
    val settled = remember(positionKey) { mutableStateOf(store.peek(positionKey) == null) }

    LaunchedEffect(positionKey) {
        val saved = store.peek(positionKey) ?: return@LaunchedEffect
        val itemCount = snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > 0 }
        val target = saved.index.coerceIn(0, itemCount - 1)
        state.scrollToItem(target, saved.offset)
        settled.value = true
    }

    DisposableEffect(positionKey) {
        onDispose {
            val index = state.centerItemIndex
            val offset = state.centerItemScrollOffset
            if (settled.value && state.layoutInfo.totalItemsCount > 0) {
                store.save(positionKey, index, offset)
            }
        }
    }
}

/**
 * Default content padding for [WearListColumn], augmenting [wearScreenInsets] with extra
 * bottom space (one min tap target height) so the last row remains easily reachable and clickable
 * on a round display when auto-centering is disabled.
 */
@Composable
private fun wearListDefaultContentPadding(): PaddingValues {
    val insets = wearScreenInsets()
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = insets.calculateStartPadding(layoutDirection),
        top = insets.calculateTopPadding(),
        end = insets.calculateEndPadding(layoutDirection),
        bottom = insets.calculateBottomPadding() + GridColumnFit.DEFAULT_MIN_TARGET_DP.dp
    )
}

/**
 * Common list wrapper for all scrolling lists on Wear OS (S2466).
 *
 * Enforces the module-wide list start rule: lists open with content at the top edge
 * rather than below a blank centering reservation. Both the list wrapper and [rememberWearListState]
 * work together to guarantee this layout behavior without post-frame jumping.
 *
 * @param state the list state, created via [rememberWearListState]. Forward to scaffold and position indicator.
 * @param modifier modifier for the list.
 * @param contentPadding padding around list content; defaults to screen insets plus bottom margin.
 * @param scalingParams scaling behavior at viewport edges; defaults to [WearGridScalingParams].
 * @param verticalArrangement vertical spacing between items.
 * @param centered explicit opt-out; passing true restores [AutoCenteringParams] for fixed control panels.
 * @param content list content DSL.
 */
@Composable
fun WearListColumn(
    state: ScalingLazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = wearListDefaultContentPadding(),
    scalingParams: ScalingParams = WearGridScalingParams,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp),
    centered: Boolean = false,
    content: ScalingLazyListScope.() -> Unit
) {
    ScalingLazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        scalingParams = scalingParams,
        verticalArrangement = verticalArrangement,
        autoCentering = if (centered) AutoCenteringParams() else null,
        content = content
    )
}

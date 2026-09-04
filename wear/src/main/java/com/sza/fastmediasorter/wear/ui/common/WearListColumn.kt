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
 * Where a wear list stands the moment it opens, counted in lazy items (S2466, owner ruling 2026-09-04).
 *
 * The owner's rule is about rows of DATA, not about list items: the SECOND data row sits in the middle
 * of the frame, so the opening frame carries the tail of row one, all of row two and the head of row
 * three - three rows of content - instead of a title with a single row under it. On the usual screen,
 * whose list opens with a title item, that is item index 2.
 */
const val WEAR_LIST_ANCHOR = 2

/** The same second data row on a list that opens straight into data, with no title item above it. */
const val WEAR_LIST_UNTITLED_ANCHOR = 1

/** Opt-out for fixed control panels and single-message screens: open exactly where the layout puts it. */
const val WEAR_LIST_NO_ANCHOR = 0

/**
 * Creates and remembers a [ScalingLazyListState] for use with [WearListColumn].
 *
 * Contract: [rememberWearListState] and [WearListColumn] are taken together as a pair.
 * The returned state should be passed to [WearListColumn] and, where applicable, forwarded
 * to the screen scaffold (`WearScreenScaffold`) and position indicator unchanged.
 *
 * The opening position defaults to [WEAR_LIST_ANCHOR]; a host that starts with data says
 * [WEAR_LIST_UNTITLED_ANCHOR] and one that must not move says [WEAR_LIST_NO_ANCHOR], each with a reason.
 *
 * @param positionKey opts this list into position memory (S2543). A key identifies the list's content -
 * its navigation route, plus whatever argument distinguishes what it shows - so the screen reopens on the
 * item that was in the middle of the display when the user left it. Null keeps the plain behaviour above,
 * which is what every dialog, sheet and secondary list of a route uses: they share their route with the
 * screen's own list and a shared key would restore one list's position into another.
 */
@Composable
fun rememberWearListState(
    initialCenterItemIndex: Int = WEAR_LIST_ANCHOR,
    positionKey: String? = null
): ScalingLazyListState {
    val store = LocalWearListPositions.current
    val saved = remember(positionKey, store) { positionKey?.let { store?.peek(it) } }
    val state = rememberScalingLazyListState(
        initialCenterItemIndex = saved?.index ?: initialCenterItemIndex,
        initialCenterItemScrollOffset = saved?.offset ?: 0
    )
    WearListPositionMemory(state = state, positionKey = positionKey, store = store)
    WearListOpeningAnchor(state = state, index = initialCenterItemIndex, restored = saved != null)
    return state
}

/**
 * Puts the list on its opening anchor once it actually has rows.
 *
 * `initialCenterItemIndex` cannot do this on its own and never did: [WearListColumn] passes
 * `autoCentering = null`, and with no centering reservation the column lays out from the top and the
 * initial index is discarded. Measured on the watch 2026-09-04 - Local logged `initialItemIndex=1`
 * while its title stayed fully visible and row one sat untouched below it - which is why the rule read
 * as "implemented only on the home screen": home has no title, so its unmoved list happened to look
 * right. The anchor is applied the way the remembered position is, by a scroll after the first
 * non-empty layout, because the list is filled asynchronously and has no rows on the first frame.
 *
 * Skipped when a position was restored: that anchor is where the user actually left this list, and it
 * wins over the generic opening rule.
 */
@Composable
private fun WearListOpeningAnchor(state: ScalingLazyListState, index: Int, restored: Boolean) {
    if (restored || index <= WEAR_LIST_NO_ANCHOR) {
        return
    }
    LaunchedEffect(index) {
        // Waits for a list long enough to HOLD the anchor, not merely for a non-empty one. Measured on
        // the watch 2026-09-04: home's first non-empty layout is a single loading row, so a `> 0` wait
        // anchored item 0 of 1 and then never fired again once the sections arrived. A list that never
        // grows past the anchor is a list with nothing to scroll to, and leaving it where it is is right.
        val itemCount = snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > index }
        Timber.d("S2466: list anchored at item %d of %d", index, itemCount)
        state.scrollToItem(index)
    }
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
 * Enforces one half of the module-wide list start rule: no blank centering reservation above the first
 * item (`autoCentering = null`). The other half - where the list then stands - belongs to
 * [rememberWearListState], and neither half is correct alone: the reservation is what the library would
 * otherwise use to honour a start index, so switching it off here is exactly why the state has to place
 * the list itself.
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

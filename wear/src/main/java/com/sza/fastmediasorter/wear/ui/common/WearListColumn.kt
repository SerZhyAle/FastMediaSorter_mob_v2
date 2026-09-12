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
import com.sza.fastmediasorter.wear.ui.player.common.rotaryActionScroll
import com.sza.fastmediasorter.wear.util.GridColumnFit
import kotlinx.coroutines.delay
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
 * Milliseconds to wait after the list first holds the anchor before scrolling. The first non-empty
 * layout can carry a single-column item count before the grid mode recomposition chunks items into
 * rows, and large tiles may not be measured on the frame they are added. Measured 2026-09-11.
 */
private const val ANCHOR_SETTLE_DELAY_MS = 100L

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
        snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > index }
        // Wait for the layout to settle before scrolling. The first non-empty layout can carry a
        // single-column item count before the grid mode recomposition chunks items into rows, and
        // large tiles may not be measured on the frame they are added. Measured 2026-09-11: Local
        // reported 9 items in 1, 2 and 3 columns because the anchor fired before the grid chunking
        // recomposed, and Images in single column landed on data row one because the tiles were not
        // yet measured. A short delay lets both the recomposition and the re-measurement land.
        delay(ANCHOR_SETTLE_DELAY_MS)
        Timber.d("S2466: list anchored at item %d of %d", index, state.layoutInfo.totalItemsCount)
        state.scrollToItem(index)
    }
}

/**
 * Restores the remembered anchor once the list actually has rows, and writes the current one back when
 * the screen leaves composition - which is exactly the "back" the user performs.
 *
 * The restore cannot ride on `initialCenterItemIndex` alone: folder, stream and note lists are filled
 * asynchronously, so on the first frame the list is still empty and any initial index is discarded.
 *
 * The restore waits for a list long enough to HOLD the saved anchor, not merely for a non-empty one
 * (S2816): the first non-empty layout can be scaffolding alone - the Apps screen's title item, the
 * home screen's loading row - and restoring against that layout coerced the saved index to zero,
 * which read on the watch as "the position was never remembered". A list that never grows past the
 * saved anchor is content the position was not saved from: it gets neither the restore nor the
 * write-back, for the same reason the store is not persisted - an index into different content
 * points at a different row.
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
        snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > saved.index }
        Timber.d("S2816: list restored key=%s index=%d offset=%d", positionKey, saved.index, saved.offset)
        state.scrollToItem(saved.index, saved.offset)
        settled.value = true
    }

    DisposableEffect(positionKey) {
        onDispose {
            val index = state.centerItemIndex
            val offset = state.centerItemScrollOffset
            if (settled.value && state.layoutInfo.totalItemsCount > 0) {
                Timber.d("S2816: list saved key=%s index=%d offset=%d", positionKey, index, offset)
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
 * Content padding for a list drawn inside a `Dialog` rather than inside a screen scaffold (S2762).
 *
 * A screen list pays [wearScreenInsets], a single proportional clearance that is true at the vertical
 * middle, where the chord is the full diameter, and too small at either end - the row it leaves is 0.8
 * of the diameter wide, while the chord 23 dp below the top of a 227 dp watch is about 136 dp. Google
 * Play rejected the watch build on 2026-09-08 for `Watch shapes` on two frames that were dialogs, not
 * screens.
 *
 * The answer reuses the shape helpers rather than inventing a seventh: horizontally [wearRingInset],
 * which narrows the row to the inscribed square that the action menus already stand in and that the
 * rejection did not touch; vertically [wearBandEdgeOffset] of that same width, which is [wearChordInset]
 * read backwards and so places the first and last row exactly where the glass is wide enough to hold
 * them. A row passing an edge mid-scroll is left to `WearGridScalingParams` - in a scrolling list every
 * row reaches an edge eventually, and shrinking it there is the only lever that applies.
 */
@Composable
fun wearDialogListContentPadding(): PaddingValues = PaddingValues(
    horizontal = wearRingInset(),
    vertical = wearBandEdgeOffset(wearMaxSquareSide())
)

/**
 * List state for a dialog: opens where the layout puts it, never on [WEAR_LIST_ANCHOR] (S2762).
 *
 * The opening anchor states that the second row of DATA belongs in the middle of the frame, which is a
 * rule about a screen the user navigated to. A dialog's first item is its own title, and scrolling past
 * it drives the title and the first row under the top arc - the `All channels` tile cut flat in the
 * rejected frame - while on a short dialog the same scroll presses the last row into the bottom arc.
 *
 * @param positionKey deliberately absent: a dialog shares its route with the screen behind it, and
 * [rememberWearListState] already records that a shared key would restore one list's position into
 * another.
 */
@Composable
fun rememberWearDialogListState(): ScalingLazyListState =
    rememberWearListState(initialCenterItemIndex = WEAR_LIST_NO_ANCHOR)

/**
 * [WearListColumn] for a dialog: the same list, with the dialog's padding instead of the screen's.
 *
 * Kept as its own entry point rather than as a flag on the screen list so a dialog added later is safe
 * without asking for it, which is how the six list-based dialogs of this module all reached Google Play
 * on the screen padding (S2762).
 */
@Composable
fun WearDialogListColumn(
    state: ScalingLazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = wearDialogListContentPadding(),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp),
    content: ScalingLazyListScope.() -> Unit
) {
    WearListColumn(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        content = content
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
 * @param rotary opt-out for a host that binds rotation to something other than scrolling. Rotary input
 * is wired here rather than per screen because this wrapper is the single point through which the
 * module's lists are built, and the pinned Wear Compose Foundation build wires none into
 * `ScalingLazyColumn` itself - which is why the bezel scrolled two screens out of forty (S2763).
 * @param content list content DSL.
 */
// Eight parameters is one past detekt's threshold, and every one of them has a live caller: `centered`
// is passed by eight screens and `rotary` by the calculator's history page. A Compose wrapper's
// parameters ARE its API, so the alternatives are a second near-identical entry point or a parameter
// object that every existing caller would have to construct - both worse to read than this list.
@Suppress("LongParameterList")
@Composable
fun WearListColumn(
    state: ScalingLazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = wearListDefaultContentPadding(),
    scalingParams: ScalingParams = WearGridScalingParams,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(4.dp),
    centered: Boolean = false,
    rotary: Boolean = true,
    content: ScalingLazyListScope.() -> Unit
) {
    ScalingLazyColumn(
        modifier = if (rotary) modifier.rotaryActionScroll(state) else modifier,
        state = state,
        contentPadding = contentPadding,
        scalingParams = scalingParams,
        verticalArrangement = verticalArrangement,
        autoCentering = if (centered) AutoCenteringParams() else null,
        content = content
    )
}

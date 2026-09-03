package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
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

/**
 * Creates and remembers a [ScalingLazyListState] for use with [WearListColumn].
 *
 * Contract: [rememberWearListState] and [WearListColumn] are taken together as a pair.
 * The returned state should be passed to [WearListColumn] and, where applicable, forwarded
 * to the screen scaffold (`WearScreenScaffold`) and position indicator unchanged.
 */
@Composable
fun rememberWearListState(): ScalingLazyListState = rememberScalingLazyListState(initialCenterItemIndex = 0)

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

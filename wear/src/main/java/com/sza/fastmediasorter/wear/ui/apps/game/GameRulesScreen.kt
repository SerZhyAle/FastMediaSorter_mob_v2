package com.sza.fastmediasorter.wear.ui.apps.game

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import timber.log.Timber

private val TITLE_BOTTOM_PADDING = 8.dp

private val SECTION_TOP_PADDING = 10.dp
private val ITEM_VERTICAL_PADDING = 4.dp
private val ITEM_HORIZONTAL_PADDING = 8.dp

/**
 * S2350: rules screen for the wear mini-game.
 *
 * A standalone destination in the Wear OS navigation graph, so edge swipe automatically dismisses
 * it and returns the player to the game.
 */
@Composable
fun GameRulesScreen(
    listState: ScalingLazyListState = rememberWearListState(initialCenterItemIndex = 0)
) {
    LaunchedEffect(Unit) {
        Timber.d("S2350: game rules screen opened")
    }

    WearScreenScaffold(

        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            centered = true
        ) {
            item {
                Text(
                    text = stringResource(R.string.wear_game_rules_title),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = TITLE_BOTTOM_PADDING),
                    textAlign = TextAlign.Center
                )
            }

            item {
                Text(
                    text = stringResource(R.string.wear_game_rules_intro),
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ITEM_HORIZONTAL_PADDING),
                    textAlign = TextAlign.Center
                )
            }

            item {
                Text(
                    text = stringResource(R.string.wear_game_rules_legend_title),
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SECTION_TOP_PADDING),
                    textAlign = TextAlign.Center
                )
            }

            item { LegendRow(R.string.wear_game_rules_legend_player) }
            item { LegendRow(R.string.wear_game_rules_legend_kryvavitsa) }
            item { LegendRow(R.string.wear_game_rules_legend_shadow) }
            item { LegendRow(R.string.wear_game_rules_legend_exit) }
            item { LegendRow(R.string.wear_game_rules_legend_wall) }
        }
    }
}

@Composable
private fun LegendRow(textRes: Int) {
    val text = stringResource(textRes)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ITEM_HORIZONTAL_PADDING, vertical = ITEM_VERTICAL_PADDING)
            .semantics(mergeDescendants = true) { contentDescription = text }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.caption2,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

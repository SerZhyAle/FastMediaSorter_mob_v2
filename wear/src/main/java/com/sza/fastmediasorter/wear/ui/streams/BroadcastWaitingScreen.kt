package com.sza.fastmediasorter.wear.ui.streams

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastStartState
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import kotlinx.coroutines.launch

/**
 * S4692: one column - the channel's title, where its broadcast stands, and a Play chip once it is on air. The state
 * is collected in the STARTED boundary, so the relay probe of LIVE-BROADCAST item S runs only while this is visible.
 */
@Composable
fun BroadcastWaitingScreen(
    navController: NavController,
    viewModel: BroadcastWaitingViewModel = hiltViewModel(),
) {
    val state by viewModel.startState.collectAsStateWithLifecycle(initialValue = null)
    val title by produceState<String?>(initialValue = null, viewModel) { value = viewModel.title() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val unplayable = stringResource(R.string.wear_stream_broadcast_unplayable)
    val listState = rememberWearListState()
    val onPlay: () -> Unit = {
        scope.launch {
            val target = viewModel.play()
            if (target == null || target.unplayable) {
                Toast.makeText(context, unplayable, Toast.LENGTH_SHORT).show()
            } else {
                val route = if (target.isVideo) {
                    WearRoutes.videoPlayer(target.fileId)
                } else {
                    WearRoutes.audioPlayer(target.fileId)
                }
                navController.navigate(route) {
                    popUpTo(WearRoutes.BROADCAST_WAITING_PATTERN) { inclusive = true }
                }
            }
        }
    }
    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) },
    ) {
        WearListColumn(modifier = Modifier.fillMaxSize(), state = listState, centered = true) {
            title?.let { name ->
                item {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.title3,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            state?.let { current ->
                item { StartStateText(current) }
                if (current == WearBroadcastStartState.Started) {
                    item {
                        Chip(
                            onClick = onPlay,
                            label = { Text(stringResource(R.string.wear_broadcast_play)) },
                            colors = ChipDefaults.primaryChipColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartStateText(state: WearBroadcastStartState) {
    val text = when (state) {
        is WearBroadcastStartState.Waiting -> stringResource(
            R.string.wear_broadcast_waiting_until,
            DateUtils.formatDateTime(
                LocalContext.current,
                state.untilMs,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
            ),
        )
        WearBroadcastStartState.Started -> stringResource(R.string.wear_broadcast_started)
        WearBroadcastStartState.Expired -> stringResource(R.string.wear_broadcast_expired)
    }
    Text(text = text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}

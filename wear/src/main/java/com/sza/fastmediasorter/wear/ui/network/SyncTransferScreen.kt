package com.sza.fastmediasorter.wear.ui.network

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.data.wear.WatchSyncEvents
import com.sza.fastmediasorter.wear.domain.model.ImportResult
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes
import kotlinx.coroutines.flow.merge

/**
 * Full-screen sync transfer animation shown while the watch receives data from the phone.
 * Automatically navigates to SyncResultScreen on completion.
 *
 * @param phoneName Optional name of the sending phone (populated from WearSyncPayload).
 */

@Composable
fun SyncTransferScreen(
    navController: NavController,
    phoneName: String = ""
) {
    LaunchedEffect(Unit) {
        WatchSyncEvents.importResultFlow.collect { result ->
            navController.navigate(WearRoutes.syncResult(result.added, result.updated)) {
                popUpTo(WearRoutes.SYNC_TRANSFER) { inclusive = true }
            }
        }
    }

    WearScreenScaffold {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(80.dp),
                strokeWidth = 4.dp
            )
            Column(
                modifier = Modifier.padding(wearScreenInsets()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(96.dp))
                Text(
                    text = if (phoneName.isNotBlank())
                        stringResource(R.string.wear_sync_receiving_from, phoneName)
                    else
                        stringResource(R.string.wear_sync_receiving),
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

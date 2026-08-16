package com.sza.fastmediasorter.wear.ui.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
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
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceItem
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceResponseStatus
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets

@Composable
fun PhoneResourceScreen(
    navController: NavController,
    viewModel: PhoneResourceViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Back walks the folder trail first; only the root hands Back back to navigation.
    BackHandler(enabled = true) {
        if (!viewModel.navigateUp()) {
            navController.popBackStack()
        }
    }

    val listState = rememberScalingLazyListState()

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        positionIndicator = { PositionIndicator(listState) }
    ) {
        when (val current = state) {
            is PhoneResourceUiState.Loading -> CenteredMessage(
                text = stringResource(R.string.phone_resource_loading),
                showProgress = true
            )

            is PhoneResourceUiState.Content -> PhoneResourceList(
                items = current.items,
                listState = listState,
                onFolderClick = viewModel::openFolder
            )

            is PhoneResourceUiState.Empty -> RetryMessage(
                text = stringResource(R.string.phone_resource_empty),
                onRetry = viewModel::retry
            )

            is PhoneResourceUiState.Unavailable -> RetryMessage(
                text = stringResource(current.reason.toMessageRes()),
                onRetry = viewModel::retry
            )
        }
    }
}

@Composable
private fun PhoneResourceList(
    items: List<WearPhoneResourceItem>,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    onFolderClick: (String) -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = wearScreenInsets()
    ) {
        item {
            Text(
                text = stringResource(R.string.phone_resource_title),
                style = MaterialTheme.typography.title3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                textAlign = TextAlign.Center
            )
        }

        items(items) { entry ->
            Chip(
                onClick = { if (entry.isDirectory) onFolderClick(entry.token) },
                label = { Text(text = entry.name) },
                icon = {
                    Icon(
                        imageVector = if (entry.isDirectory) {
                            Icons.Filled.Folder
                        } else {
                            Icons.AutoMirrored.Filled.InsertDriveFile
                        },
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = entry.name },
                colors = ChipDefaults.primaryChipColors()
            )
        }
    }
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

@Composable
private fun RetryMessage(text: String, onRetry: () -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = wearScreenInsets()
    ) {
        item {
            Text(
                text = text,
                style = MaterialTheme.typography.body2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textAlign = TextAlign.Center
            )
        }
        item {
            Chip(
                onClick = onRetry,
                label = { Text(text = stringResource(R.string.phone_resource_retry)) },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.phone_resource_retry),
                        modifier = Modifier.size(24.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.secondaryChipColors()
            )
        }
    }
}

/**
 * A protocol status never reaches the user: each one is answered with the next step the person can
 * actually take, per `docs/COMMUNICATION_POLICY.md`.
 */
private fun WearPhoneResourceResponseStatus?.toMessageRes(): Int = when (this) {
    WearPhoneResourceResponseStatus.ACCESS_DENIED -> R.string.phone_resource_access_denied
    WearPhoneResourceResponseStatus.UNSUPPORTED_MEDIA -> R.string.phone_resource_unsupported
    WearPhoneResourceResponseStatus.TRANSFER_REJECTED -> R.string.phone_resource_transfer_rejected
    else -> R.string.phone_resource_unavailable
}

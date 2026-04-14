package com.sza.fastmediasorter.ui.settings.fragments

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.ui.settings.WearSyncUiState
import com.sza.fastmediasorter.ui.settings.WearSyncViewModel
import com.sza.fastmediasorter.ui.settings.helpers.BeamAnimationDialog
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WearSyncSettingsFragment : Fragment() {

    private val viewModel: WearSyncViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setContent {
                WearSyncScreen(
                    viewModel = viewModel,
                    onPushClick = { launchBeamDialog() }
                )
            }
        }

    private fun launchBeamDialog() {
        viewModel.startPush()
        if (childFragmentManager.findFragmentByTag("beam_dialog") == null) {
            BeamAnimationDialog().show(childFragmentManager, "beam_dialog")
        }
    }
}

@Composable
private fun WearSyncScreen(
    viewModel: WearSyncViewModel,
    onPushClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val lastSync = viewModel.lastSyncTimestamp

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = stringResource(R.string.wear_companion),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.wear_sync_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        if (lastSync > 0L) {
            Text(
                text = stringResource(
                    R.string.wear_last_synced,
                    DateUtils.getRelativeTimeSpanString(lastSync)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = onPushClick,
            enabled = state !is WearSyncUiState.Sending,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.wear_push_to_watch))
        }
    }
}

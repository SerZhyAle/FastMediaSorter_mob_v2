package com.sza.fastmediasorter.wear.ui.fdsec

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearFdSecMode
import com.sza.fastmediasorter.wear.ui.common.StandardWearToggleChip
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold

/**
 * S3383: where the FileDO credential is typed, for all three errands.
 *
 * A screen of its own rather than a dialog: on a round display a text field with the keyboard over
 * it has room for nothing beside it. `RemoteInput`, which every other text entry on this watch uses,
 * is deliberately NOT used here - it opens the system input screen, where voice dictation sits
 * beside the keyboard and nothing is masked, and a spoken password is exactly what the container
 * format exists to prevent.
 *
 * @param onFinished return to the list that led here, after an errand that wrote something.
 * @param onOpen show the recovered file in the watch's ordinary viewer, replacing this screen.
 */
@Composable
fun FdSecCredentialScreen(
    onFinished: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: FdSecCredentialViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.playerRoute) {
        state.playerRoute?.let(onOpen)
    }

    WearScreenScaffold {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(titleOf(state.mode)),
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center
            )
            Text(
                text = state.fileName,
                style = MaterialTheme.typography.caption2,
                textAlign = TextAlign.Center
            )

            val message = state.messageRes
            if (message != null) {
                Outcome(text = stringResource(message), onDismiss = viewModel::onMessageDismissed)
            } else {
                CredentialFields(state = state, viewModel = viewModel, onFinished = onFinished)
            }
        }
    }
}

@Composable
private fun CredentialFields(
    state: FdSecCredentialUiState,
    viewModel: FdSecCredentialViewModel,
    onFinished: () -> Unit
) {
    MaskedField(
        value = state.credential,
        hint = stringResource(R.string.wear_filedo_password_hint),
        onValueChange = viewModel::onCredentialChange,
        onDone = viewModel::onConfirm
    )
    if (state.asksTwice) {
        MaskedField(
            value = state.repeated,
            hint = stringResource(R.string.wear_filedo_password_repeat_hint),
            onValueChange = viewModel::onRepeatedChange,
            onDone = viewModel::onConfirm
        )
        if (!state.entriesMatch) {
            Note(stringResource(R.string.wear_filedo_password_mismatch))
        }
    }
    // The warning is worded so that it cannot be read as reassurance: the format accepts an empty
    // credential and calls it obfuscation with no secrecy, and the wearer is told exactly that.
    if (state.showsEmptyWarning) {
        Note(stringResource(R.string.wear_filedo_empty_warning))
    }
    if (state.offersRemember) {
        StandardWearToggleChip(
            label = stringResource(R.string.wear_filedo_remember),
            checked = state.remember,
            onCheckedChange = viewModel::onRememberChange
        )
        // FDSEC-BEHAVIOUR section 8: every credential source states its leak where it is offered.
        Note(stringResource(R.string.wear_filedo_remember_leak))
    }
    Chip(
        label = {
            Text(
                if (state.isWorking) {
                    stringResource(R.string.wear_filedo_working)
                } else {
                    stringResource(titleOf(state.mode))
                }
            )
        },
        onClick = viewModel::onConfirm,
        enabled = state.canConfirm,
        colors = ChipDefaults.primaryChipColors(),
        modifier = Modifier.fillMaxWidth()
    )
    Chip(
        label = { Text(stringResource(R.string.cancel)) },
        onClick = onFinished,
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun MaskedField(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit
) {
    Text(text = hint, style = MaterialTheme.typography.caption2)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface)
            .padding(8.dp),
        textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onSurface),
        // No visibility toggle, unlike the network-source password: this one is typed on a wrist in
        // public and a container has no second chance if it is read over a shoulder.
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        maxLines = 1
    )
}

@Composable
private fun Outcome(text: String, onDismiss: () -> Unit) {
    Note(text)
    Chip(
        label = { Text(stringResource(R.string.ok)) },
        onClick = onDismiss,
        colors = ChipDefaults.primaryChipColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun titleOf(mode: WearFdSecMode): Int = when (mode) {
    WearFdSecMode.OPEN -> R.string.wear_filedo_title_open
    WearFdSecMode.ENCRYPT -> R.string.wear_filedo_title_encrypt
    WearFdSecMode.DECRYPT -> R.string.wear_filedo_title_decrypt
}

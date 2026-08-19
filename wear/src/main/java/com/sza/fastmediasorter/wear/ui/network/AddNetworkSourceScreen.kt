package com.sza.fastmediasorter.wear.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.wearScreenInsets
import com.sza.fastmediasorter.wear.ui.network.viewmodel.AddNetworkSourceUiState
import com.sza.fastmediasorter.wear.ui.network.viewmodel.AddNetworkSourceViewModel
import timber.log.Timber

private enum class AddField {
    NAME,
    SERVER,
    PORT,
    USERNAME,
    PASSWORD,
    SHARE_NAME,
    DOMAIN,
    SSH_PRIVATE_KEY
}

@Composable
fun AddNetworkSourceScreen(
    navController: NavController,
    viewModel: AddNetworkSourceViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var editingField by remember { mutableStateOf<AddField?>(null) }
    var editingValue by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val listState = rememberScalingLazyListState()

    Timber.d("AddNetworkSourceScreen composing for protocol ${uiState.protocol}")

    if (editingField != null) {
        EditFieldOverlay(
            field = editingField!!,
            value = editingValue,
            onValueChange = { editingValue = it },
            passwordVisible = passwordVisible,
            onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
            onSave = {
                applyFieldChange(viewModel, editingField!!, editingValue)
                editingField = null
                passwordVisible = false
            },
            onCancel = {
                editingField = null
                passwordVisible = false
            }
        )
    } else {
        WearScreenScaffold(
            contentPadding = PaddingValues(0.dp),
            scrollState = listState,
            positionIndicator = { PositionIndicator(listState) }
        ) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = wearScreenInsets(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.add_network_source),
                        style = MaterialTheme.typography.title2
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.tap_to_edit),
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.primary
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.protocol),
                        style = MaterialTheme.typography.caption1
                    )
                }

                item {
                    ProtocolChip(
                        label = stringResource(R.string.smb_connection),
                        selected = uiState.protocol == NetworkSourceType.SMB,
                        onClick = { viewModel.setProtocol(NetworkSourceType.SMB) }
                    )
                }
                item {
                    ProtocolChip(
                        label = stringResource(R.string.ftp_connection),
                        selected = uiState.protocol == NetworkSourceType.FTP,
                        onClick = { viewModel.setProtocol(NetworkSourceType.FTP) }
                    )
                }
                item {
                    ProtocolChip(
                        label = stringResource(R.string.sftp_connection),
                        selected = uiState.protocol == NetworkSourceType.SFTP,
                        onClick = { viewModel.setProtocol(NetworkSourceType.SFTP) }
                    )
                }

                item {
                    EditableFieldChip(
                        label = stringResource(R.string.name_label),
                        value = uiState.name,
                        fallback = stringResource(R.string.optional_value),
                        onClick = {
                            editingField = AddField.NAME
                            editingValue = uiState.name
                        }
                    )
                }

                item {
                    EditableFieldChip(
                        label = stringResource(R.string.server_address),
                        value = uiState.server,
                        fallback = stringResource(R.string.required_value),
                        onClick = {
                            editingField = AddField.SERVER
                            editingValue = uiState.server
                        }
                    )
                }

                item {
                    EditableFieldChip(
                        label = stringResource(R.string.port),
                        value = uiState.port.takeIf { it > 0 }?.toString().orEmpty(),
                        fallback = stringResource(R.string.required_value),
                        onClick = {
                            editingField = AddField.PORT
                            editingValue = uiState.port.takeIf { it > 0 }?.toString().orEmpty()
                        }
                    )
                }

                if (uiState.protocol == NetworkSourceType.SMB) {
                    item {
                        EditableFieldChip(
                            label = stringResource(R.string.share_name),
                            value = uiState.shareName,
                            fallback = stringResource(R.string.required_value),
                            onClick = {
                                editingField = AddField.SHARE_NAME
                                editingValue = uiState.shareName
                            }
                        )
                    }
                    item {
                        EditableFieldChip(
                            label = stringResource(R.string.domain),
                            value = uiState.domain,
                            fallback = stringResource(R.string.optional_value),
                            onClick = {
                                editingField = AddField.DOMAIN
                                editingValue = uiState.domain
                            }
                        )
                    }
                }

                item {
                    EditableFieldChip(
                        label = stringResource(R.string.username),
                        value = uiState.username,
                        fallback = stringResource(R.string.optional_value),
                        onClick = {
                            editingField = AddField.USERNAME
                            editingValue = uiState.username
                        }
                    )
                }

                item {
                    EditableFieldChip(
                        label = stringResource(R.string.password),
                        value = if (uiState.password.isBlank()) "" else stringResource(R.string.password_masked),
                        fallback = stringResource(R.string.optional_value),
                        onClick = {
                            editingField = AddField.PASSWORD
                            editingValue = uiState.password
                        }
                    )
                }

                if (uiState.protocol == NetworkSourceType.SFTP) {
                    item {
                        Chip(
                            label = {
                                Text(
                                    text = buildString {
                                        append(stringResource(R.string.use_ssh_key))
                                        append("\n")
                                        append(if (uiState.useSshKey) stringResource(R.string.enabled) else stringResource(R.string.disabled))
                                    },
                                    style = MaterialTheme.typography.caption1
                                )
                            },
                            onClick = { viewModel.setUseSshKey(!uiState.useSshKey) },
                            colors = if (uiState.useSshKey) {
                                ChipDefaults.primaryChipColors()
                            } else {
                                ChipDefaults.outlinedChipColors()
                            }
                        )
                    }

                    if (uiState.useSshKey) {
                        item {
                            EditableFieldChip(
                                label = stringResource(R.string.ssh_private_key),
                                value = if (uiState.sshPrivateKey.isBlank()) "" else stringResource(R.string.ssh_key_configured),
                                fallback = stringResource(R.string.required_value),
                                onClick = {
                                    editingField = AddField.SSH_PRIVATE_KEY
                                    editingValue = uiState.sshPrivateKey
                                }
                            )
                        }
                    }
                }

                if (uiState.statusMessage.isNotEmpty()) {
                    item {
                        Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.caption1,
                            color = if (uiState.isError) MaterialTheme.colors.error else MaterialTheme.colors.primary
                        )
                    }
                }

                item {
                    Chip(
                        label = {
                            Text(
                                text = if (uiState.isLoading) stringResource(R.string.testing_connection) else stringResource(R.string.test_connection)
                            )
                        },
                        onClick = { viewModel.testConnection() },
                        enabled = !uiState.isLoading && canSubmit(uiState),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }

                item {
                    Chip(
                        label = {
                            Text(
                                text = if (uiState.isLoading) stringResource(R.string.saving_connection) else stringResource(R.string.save)
                            )
                        },
                        onClick = {
                            viewModel.saveSource {
                                navController.popBackStack()
                            }
                        },
                        enabled = !uiState.isLoading && canSubmit(uiState),
                        colors = ChipDefaults.primaryChipColors()
                    )
                }
            }
        }
    }
}

@Composable
private fun EditFieldOverlay(
    field: AddField,
    value: String,
    onValueChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibilityToggle: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    WearScreenScaffold {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = fieldTitle(field),
                    style = MaterialTheme.typography.title3
                )

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colors.surface)
                        .padding(8.dp),
                    textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onSurface),
                    visualTransformation = if (field == AddField.PASSWORD && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (field == AddField.PORT) KeyboardType.Number else KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                    maxLines = if (field == AddField.SSH_PRIVATE_KEY) 5 else 1
                )

                if (field == AddField.PASSWORD) {
                    Chip(
                        label = {
                            Text(
                                if (passwordVisible) stringResource(R.string.hide_password) else stringResource(R.string.show_password)
                            )
                        },
                        onClick = onPasswordVisibilityToggle,
                        colors = ChipDefaults.outlinedChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Chip(
                    label = { Text(stringResource(R.string.done)) },
                    onClick = onSave,
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Chip(
                    label = { Text(stringResource(R.string.cancel)) },
                    onClick = onCancel,
                    colors = ChipDefaults.outlinedChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun EditableFieldChip(
    label: String,
    value: String,
    fallback: String,
    onClick: () -> Unit
) {
    Chip(
        label = {
            Text(
                text = buildString {
                    append(label)
                    append("\n")
                    append(value.ifBlank { fallback })
                },
                style = MaterialTheme.typography.caption1
            )
        },
        onClick = onClick,
        colors = ChipDefaults.outlinedChipColors()
    )
}

@Composable
private fun ProtocolChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Chip(
        label = { Text(text = label) },
        onClick = onClick,
        colors = if (selected) ChipDefaults.primaryChipColors() else ChipDefaults.outlinedChipColors()
    )
}

private fun applyFieldChange(
    viewModel: AddNetworkSourceViewModel,
    field: AddField,
    value: String
) {
    when (field) {
        AddField.NAME -> viewModel.setName(value)
        AddField.SERVER -> viewModel.setServer(value)
        AddField.PORT -> viewModel.setPort(value)
        AddField.USERNAME -> viewModel.setUsername(value)
        AddField.PASSWORD -> viewModel.setPassword(value)
        AddField.SHARE_NAME -> viewModel.setShareName(value)
        AddField.DOMAIN -> viewModel.setDomain(value)
        AddField.SSH_PRIVATE_KEY -> viewModel.setSshPrivateKey(value)
    }
}

@Composable
private fun fieldTitle(field: AddField): String {
    return when (field) {
        AddField.NAME -> stringResource(R.string.name_label)
        AddField.SERVER -> stringResource(R.string.server_address)
        AddField.PORT -> stringResource(R.string.port)
        AddField.USERNAME -> stringResource(R.string.username)
        AddField.PASSWORD -> stringResource(R.string.password)
        AddField.SHARE_NAME -> stringResource(R.string.share_name)
        AddField.DOMAIN -> stringResource(R.string.domain)
        AddField.SSH_PRIVATE_KEY -> stringResource(R.string.ssh_private_key)
    }
}

private fun canSubmit(state: AddNetworkSourceUiState): Boolean {
    if (state.server.isBlank() || state.port <= 0) return false
    if (state.protocol == NetworkSourceType.SMB && state.shareName.isBlank()) return false
    if (state.protocol == NetworkSourceType.SFTP && state.useSshKey && state.sshPrivateKey.isBlank()) return false
    return true
}

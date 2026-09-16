package com.sza.fastmediasorter.wear.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.NetworkSourceType
import com.sza.fastmediasorter.wear.domain.model.WearViewMode
import com.sza.fastmediasorter.wear.ui.common.WearChoiceGridFit
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.common.wearChoiceRows
import com.sza.fastmediasorter.wear.ui.network.viewmodel.AddNetworkSourceUiState
import com.sza.fastmediasorter.wear.ui.network.viewmodel.AddNetworkSourceViewModel
import timber.log.Timber

private const val FIELD_CELL_MAX_LINES = 2

/** The protocols the watch offers to type in by hand. */
private val PROTOCOL_OPTIONS = listOf(
    NetworkSourceType.SMB,
    NetworkSourceType.FTP,
    NetworkSourceType.SFTP
)

private enum class AddField {
    NAME,
    SERVER,
    PORT,
    USERNAME,
    PASSWORD,
    SHARE_NAME,
    DOMAIN,
    BASE_PATH,
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
    val listState = rememberWearListState()

    // S2486: the gate's second boundary. Both routes to this screen stay registered so a back-stack entry
    // saved by an older build still resolves, which means the flavor that withholds credential entry has to
    // refuse here rather than only at the chip that no longer exists.
    LaunchedEffect(viewModel.offersCredentialEntry) {
        if (!viewModel.offersCredentialEntry) {
            navController.popBackStack()
        }
    }
    if (!viewModel.offersCredentialEntry) {
        return
    }

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
            // S3198: one column whatever view mode the user chose elsewhere (owner ruling 2026-09-17,
            // reversing S2486 for this screen). A form is filled field by field, and narrow cells at the
            // edge of the round glass did not open on the first tap.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val availableWidthDp = maxWidth.value.toInt()
                WearListColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState
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

                    // Routed through WearChoiceGrid in its LIST shape for the check glyph and the `selected`
                    // semantics flag, so the choice is never carried by colour alone.
                    wearChoiceRows(
                        options = PROTOCOL_OPTIONS,
                        selected = uiState.protocol,
                        labelOf = { protocol -> protocolLabel(protocol) },
                        onSelected = { protocol -> viewModel.setProtocol(protocol) },
                        gridFit = WearChoiceGridFit(
                            viewMode = WearViewMode.LIST,
                            availableWidthDp = availableWidthDp
                        )
                    )

                    // A row keeps its label AND its value, so the form shows at a glance which fields are
                    // filled; the value is secondary and may ellipsize.
                    items(connectionFields(uiState)) { field ->
                        EditableFieldChip(
                            label = fieldTitle(field),
                            value = editableDisplayOf(uiState, field),
                            fallback = stringResource(fieldFallbackRes(field)),
                            onClick = {
                                editingField = field
                                editingValue = editableValueOf(uiState, field)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (uiState.protocol == NetworkSourceType.SFTP) {
                        // A toggle, not a field - it changes what the form asks for rather than holding an
                        // answer, so it stays a full-width row of its own.
                        item {
                            Chip(
                                label = {
                                    Text(
                                        text = buildString {
                                            append(stringResource(R.string.use_ssh_key))
                                            append("\n")
                                            append(
                                                if (uiState.useSshKey) {
                                                    stringResource(R.string.enabled)
                                                } else {
                                                    stringResource(R.string.disabled)
                                                }
                                            )
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
                                    value = editableDisplayOf(uiState, AddField.SSH_PRIVATE_KEY),
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
                                color = if (uiState.isError) {
                                    MaterialTheme.colors.error
                                } else {
                                    MaterialTheme.colors.primary
                                }
                            )
                        }
                    }

                    // Both actions stay full width and never share a row. They are not an enumeration to
                    // pick from, and two equal adjacent chips on a round screen are the classic mis-tap -
                    // here that would mean saving a connection the user meant only to test.
                    item {
                        Chip(
                            label = {
                                Text(
                                    text = if (uiState.isLoading) {
                                        stringResource(R.string.testing_connection)
                                    } else {
                                        stringResource(R.string.test_connection)
                                    }
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
                                    text = if (uiState.isLoading) {
                                        stringResource(R.string.saving_connection)
                                    } else {
                                        stringResource(R.string.save)
                                    }
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
                                if (passwordVisible) {
                                    stringResource(
                                        R.string.hide_password
                                    )
                                } else {
                                    stringResource(R.string.show_password)
                                }
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Chip(
        label = {
            Text(
                text = buildString {
                    append(label)
                    append("\n")
                    append(value.ifBlank { fallback })
                },
                style = MaterialTheme.typography.caption1,
                maxLines = FIELD_CELL_MAX_LINES,
                overflow = TextOverflow.Ellipsis
            )
        },
        onClick = onClick,
        colors = ChipDefaults.outlinedChipColors(),
        modifier = modifier
    )
}

/**
 * The fields the current protocol asks for, in the order they are filled in. The SSH key is deliberately
 * absent: it is emitted after the toggle that enables it, so it can never be listed before its own switch.
 */
private fun connectionFields(state: AddNetworkSourceUiState): List<AddField> = buildList {
    add(AddField.NAME)
    add(AddField.SERVER)
    add(AddField.PORT)
    if (state.protocol == NetworkSourceType.SMB) {
        add(AddField.SHARE_NAME)
        add(AddField.DOMAIN)
    }
    add(AddField.BASE_PATH)
    add(AddField.USERNAME)
    add(AddField.PASSWORD)
}

/** What the cell shows. The password never appears here, masked or otherwise - only that one is set. */
@Composable
private fun editableDisplayOf(state: AddNetworkSourceUiState, field: AddField): String = when (field) {
    AddField.PASSWORD -> if (state.password.isBlank()) "" else stringResource(R.string.password_masked)
    AddField.SSH_PRIVATE_KEY ->
        if (state.sshPrivateKey.isBlank()) "" else stringResource(R.string.ssh_key_configured)
    else -> editableValueOf(state, field)
}

/** What the edit overlay opens with - the raw value, including the password the cell only hints at. */
private fun editableValueOf(state: AddNetworkSourceUiState, field: AddField): String = when (field) {
    AddField.NAME -> state.name
    AddField.SERVER -> state.server
    AddField.PORT -> state.port.takeIf { it > 0 }?.toString().orEmpty()
    AddField.USERNAME -> state.username
    AddField.PASSWORD -> state.password
    AddField.SHARE_NAME -> state.shareName
    AddField.DOMAIN -> state.domain
    AddField.BASE_PATH -> state.basePath
    AddField.SSH_PRIVATE_KEY -> state.sshPrivateKey
}

/** Whether an empty cell reads as "required" or "optional" - unchanged from the single-column form. */
private fun fieldFallbackRes(field: AddField): Int = when (field) {
    AddField.SERVER, AddField.PORT, AddField.SHARE_NAME, AddField.SSH_PRIVATE_KEY -> R.string.required_value
    else -> R.string.optional_value
}

@Composable
private fun protocolLabel(protocol: NetworkSourceType): String = when (protocol) {
    NetworkSourceType.SMB -> stringResource(R.string.smb_connection)
    NetworkSourceType.FTP -> stringResource(R.string.ftp_connection)
    NetworkSourceType.SFTP -> stringResource(R.string.sftp_connection)
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
        AddField.BASE_PATH -> viewModel.setBasePath(value)
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
        AddField.BASE_PATH -> stringResource(R.string.base_path)
        AddField.SSH_PRIVATE_KEY -> stringResource(R.string.ssh_private_key)
    }
}

private fun canSubmit(state: AddNetworkSourceUiState): Boolean {
    if (state.server.isBlank() || state.port <= 0) return false
    if (state.protocol == NetworkSourceType.SMB && state.shareName.isBlank()) return false
    if (state.protocol == NetworkSourceType.SFTP && state.useSshKey && state.sshPrivateKey.isBlank()) return false
    return true
}

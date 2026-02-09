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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.MaterialTheme
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.ui.network.viewmodel.SmbConnectionViewModel
import timber.log.Timber

@Composable
fun AddSmbScreen(
    navController: NavController,
    viewModel: SmbConnectionViewModel = hiltViewModel()
) {
    Timber.d("AddSmbScreen composing")
    
    val uiState by viewModel.uiState.collectAsState()
    var editingField by remember { mutableStateOf<String?>(null) }
    var editingValue by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    if (editingField != null) {
        // Simple text input dialog
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
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
                    text = when (editingField) {
                        "server" -> "Server IP"
                        "share" -> "Share Name"
                        "user" -> "Username"
                        "pass" -> "Password"
                        else -> "Input"
                    },
                    style = MaterialTheme.typography.title3
                )
                
                // Text input field
                BasicTextField(
                    value = editingValue,
                    onValueChange = { editingValue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colors.surface)
                        .padding(8.dp),
                    textStyle = MaterialTheme.typography.body1.copy(color = MaterialTheme.colors.onSurface),
                    visualTransformation = if (editingField == "pass" && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (editingField == "server") KeyboardType.Number else KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            when (editingField) {
                                "server" -> viewModel.setServer(editingValue)
                                "share" -> viewModel.setShareName(editingValue)
                                "user" -> viewModel.setUsername(editingValue)
                                "pass" -> viewModel.setPassword(editingValue)
                            }
                            editingField = null
                            passwordVisible = false
                        }
                    )
                )
                
                // Show/Hide password button (only for password field)
                if (editingField == "pass") {
                    Chip(
                        label = { Text(if (passwordVisible) "🙈 Hide" else "👁️ Show") },
                        onClick = { passwordVisible = !passwordVisible },
                        colors = ChipDefaults.outlinedChipColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                // Buttons
                Chip(
                    label = { Text("✓ Save") },
                    onClick = {
                        when (editingField) {
                            "server" -> viewModel.setServer(editingValue)
                            "share" -> viewModel.setShareName(editingValue)
                            "user" -> viewModel.setUsername(editingValue)
                            "pass" -> viewModel.setPassword(editingValue)
                        }
                        editingField = null
                    },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Chip(
                    label = { Text("✕ Cancel") },
                    onClick = { editingField = null },
                    colors = ChipDefaults.outlinedChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.add_smb_connection),
                    style = MaterialTheme.typography.title2
                )
            }
            
            item {
                Text(
                    text = "Tap to edit (keyboard supported)",
                    style = MaterialTheme.typography.caption1,
                    color = MaterialTheme.colors.primary
                )
            }
            
            // Server field
            item {
                Chip(
                    label = {
                        Text(
                            text = "📍 Server\n${if (uiState.server.isEmpty()) "(required)" else uiState.server}",
                            style = MaterialTheme.typography.caption1
                        )
                    },
                    onClick = {
                        editingField = "server"
                        editingValue = uiState.server
                    },
                    colors = ChipDefaults.outlinedChipColors()
                )
            }
            
            // Share field
            item {
                Chip(
                    label = {
                        Text(
                            text = "📁 Share\n${if (uiState.shareName.isEmpty()) "(required)" else uiState.shareName}",
                            style = MaterialTheme.typography.caption1
                        )
                    },
                    onClick = {
                        editingField = "share"
                        editingValue = uiState.shareName
                    },
                    colors = ChipDefaults.outlinedChipColors()
                )
            }
            
            // User field
            item {
                Chip(
                    label = {
                        Text(
                            text = "👤 User\n${if (uiState.username.isEmpty()) "(optional)" else uiState.username}",
                            style = MaterialTheme.typography.caption1
                        )
                    },
                    onClick = {
                        editingField = "user"
                        editingValue = uiState.username
                    },
                    colors = ChipDefaults.outlinedChipColors()
                )
            }
            
            // Password field
            item {
                Chip(
                    label = {
                        Text(
                            text = "🔐 Password\n${if (uiState.password.isEmpty()) "(optional)" else "●●●●"}",
                            style = MaterialTheme.typography.caption1
                        )
                    },
                    onClick = {
                        editingField = "pass"
                        editingValue = uiState.password
                    },
                    colors = ChipDefaults.outlinedChipColors()
                )
            }
            
            // Status message
            if (uiState.statusMessage.isNotEmpty()) {
                item {
                    Text(
                        text = uiState.statusMessage,
                        style = MaterialTheme.typography.caption1,
                        color = if (uiState.isError) MaterialTheme.colors.error else MaterialTheme.colors.primary
                    )
                }
            }
            
            // Test Connection button
            item {
                Chip(
                    label = { Text(if (uiState.isLoading) "Testing..." else "Test →") },
                    onClick = { viewModel.testConnection() },
                    enabled = !uiState.isLoading && uiState.server.isNotEmpty() && uiState.shareName.isNotEmpty(),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
            
            // Save button
            item {
                Chip(
                    label = { Text(if (uiState.isLoading) "Saving..." else "Save ✓") },
                    onClick = {
                        viewModel.saveConnection {
                            navController.popBackStack()
                        }
                    },
                    enabled = !uiState.isLoading && uiState.server.isNotEmpty() && uiState.shareName.isNotEmpty() && !uiState.isError,
                    colors = ChipDefaults.primaryChipColors()
                )
            }
        }
    }
}

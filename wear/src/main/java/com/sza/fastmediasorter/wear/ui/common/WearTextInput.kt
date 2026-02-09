package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

/**
 * Wear OS compatible input field with simple text editing.
 * Provides a basic character input UI suitable for Wear OS screens.
 * 
 * Features:
 * - Display current value
 * - Simple backspace/clear functionality
 * - Character selection via scrollable list
 * - Password masking support
 */
@Composable
fun WearTextInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showEditDialog by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Chip(
            label = {
                Text(
                    text = "$label:\n${if (isPassword && value.isNotEmpty()) "●".repeat(value.length) else if (value.isEmpty()) "(tap to edit)" else value}",
                    style = MaterialTheme.typography.caption1,
                    maxLines = 2
                )
            },
            onClick = { showEditDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ChipDefaults.outlinedChipColors()
        )
    }
    
    if (showEditDialog) {
        SimpleCharacterPickerDialog(
            initialValue = value,
            isPassword = isPassword,
            onSave = { newValue ->
                onValueChange(newValue)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }
}

/**
 * Simple character picker dialog for Wear OS text input.
 * Optimized for entering server IPs and hostnames.
 */
@Composable
private fun SimpleCharacterPickerDialog(
    initialValue: String,
    isPassword: Boolean = false,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputValue by remember { mutableStateOf(initialValue) }
    var selectedCharSet by remember { mutableStateOf(CharacterSets.LOWERCASE) }
    
    // Using a simple Box-based dialog instead of Dialog composable to avoid animation issues
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(8.dp)
                .background(MaterialTheme.colors.surface),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Current input display
            Text(
                text = "Input:",
                style = MaterialTheme.typography.caption1
            )
            Text(
                text = if (isPassword && inputValue.isNotEmpty()) "●".repeat(inputValue.length) else inputValue.ifEmpty { "(empty)" },
                style = MaterialTheme.typography.body1,
                modifier = Modifier
                    .background(MaterialTheme.colors.background)
                    .fillMaxWidth()
                    .padding(4.dp)
            )
            
            // Quick presets for common server values
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                // Common IP octet quick-add buttons
                listOf("192.", "10.", "172.", "127.").forEach { preset ->
                    Chip(
                        label = { Text(preset, style = MaterialTheme.typography.caption2) },
                        onClick = { inputValue += preset },
                        colors = ChipDefaults.outlinedChipColors(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Character set selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Chip(
                    label = { Text("0-9", style = MaterialTheme.typography.caption2) },
                    onClick = { selectedCharSet = CharacterSets.NUMBERS },
                    colors = if (selectedCharSet == CharacterSets.NUMBERS) 
                        ChipDefaults.primaryChipColors() 
                    else 
                        ChipDefaults.outlinedChipColors(),
                    modifier = Modifier.weight(1f)
                )
                Chip(
                    label = { Text("a-z", style = MaterialTheme.typography.caption2) },
                    onClick = { selectedCharSet = CharacterSets.LOWERCASE },
                    colors = if (selectedCharSet == CharacterSets.LOWERCASE) 
                        ChipDefaults.primaryChipColors() 
                    else 
                        ChipDefaults.outlinedChipColors(),
                    modifier = Modifier.weight(1f)
                )
                Chip(
                    label = { Text("Sym", style = MaterialTheme.typography.caption2) },
                    onClick = { selectedCharSet = CharacterSets.SYMBOLS },
                    colors = if (selectedCharSet == CharacterSets.SYMBOLS) 
                        ChipDefaults.primaryChipColors() 
                    else 
                        ChipDefaults.outlinedChipColors(),
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Characters grid
            val chars = when (selectedCharSet) {
                CharacterSets.LOWERCASE -> "abcdefghijklmnopqrstuvwxyz"
                CharacterSets.UPPERCASE -> "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                CharacterSets.NUMBERS -> "0123456789"
                CharacterSets.SYMBOLS -> ".-@_:./:*"
            }
            
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                chars.chunked(5).forEach { charRow ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        charRow.forEach { char ->
                            Chip(
                                label = { Text(char.toString(), style = MaterialTheme.typography.caption2) },
                                onClick = { inputValue += char },
                                colors = ChipDefaults.secondaryChipColors(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            
            // Control buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Chip(
                    label = { Text("⌫", style = MaterialTheme.typography.body1) },
                    onClick = { if (inputValue.isNotEmpty()) inputValue = inputValue.dropLast(1) },
                    colors = ChipDefaults.outlinedChipColors(),
                    modifier = Modifier.weight(1f)
                )
                Chip(
                    label = { Text("Clr", style = MaterialTheme.typography.caption2) },
                    onClick = { inputValue = "" },
                    colors = ChipDefaults.outlinedChipColors(),
                    modifier = Modifier.weight(1f)
                )
                Chip(
                    label = { Text("✓", style = MaterialTheme.typography.body1) },
                    onClick = { onSave(inputValue) },
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Cancel button
            Chip(
                label = { Text("Cancel", style = MaterialTheme.typography.caption2) },
                onClick = onDismiss,
                colors = ChipDefaults.outlinedChipColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private enum class CharacterSets {
    LOWERCASE, UPPERCASE, NUMBERS, SYMBOLS
}

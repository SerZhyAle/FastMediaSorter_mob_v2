package com.sza.fastmediasorter.wear.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import timber.log.Timber

/**
 * Reusable stepper cell for Wear OS settings screens (e.g. slideshow interval, player panel auto-hide).
 * Decrease/increase buttons sit side by side, and the value label is below them at full width - the
 * narrowest point of a round display is the middle, so the label takes the widest row instead.
 */
@Composable
fun WearSettingsStepperCell(
    values: IntArray,
    currentValue: Int,
    labelText: String,
    decreaseDescription: String,
    increaseDescription: String,
    onValueChanged: (Int) -> Unit
) {
    val currentIndex = values.indexOfFirst { it == currentValue }.coerceAtLeast(0)
    Timber.d("S2867: stepper cell rendered with label below buttons")

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RectangularButton(
                onClick = { if (currentIndex > 0) onValueChanged(values[currentIndex - 1]) },
                enabled = currentIndex > 0,
                modifier = Modifier.size(36.dp).semantics { contentDescription = decreaseDescription },
                colors = ButtonDefaults.secondaryButtonColors()
            ) { Text(text = "−", style = MaterialTheme.typography.button) }
            RectangularButton(
                onClick = {
                    if (currentIndex < values.lastIndex) {
                        onValueChanged(values[currentIndex + 1])
                    }
                },
                enabled = currentIndex < values.lastIndex,
                modifier = Modifier.size(36.dp).semantics { contentDescription = increaseDescription },
                colors = ButtonDefaults.secondaryButtonColors()
            ) { Text(text = "+", style = MaterialTheme.typography.button) }
        }
        Text(
            text = labelText,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                .semantics { contentDescription = labelText },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.caption1
        )
    }
}

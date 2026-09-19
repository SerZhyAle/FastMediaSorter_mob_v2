package com.sza.fastmediasorter.wear.ui.apps.bloodpressure.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.ui.common.RectangularButton

private val BUTTON_SIZE = 36.dp
private val STEPPER_FIELD_WIDTH = 56.dp
private val STEPPER_CORNER_RADIUS = 6.dp
private val STEPPER_FIELD_PADDING = 6.dp
private const val STEPPER_DELTA = 5

/**
 * S3012: one pressure value with -5 / +5 buttons and a typed field. S3113 moved it here unchanged from the
 * blood-pressure screen, because typing a value is now a calibration step and nothing else.
 */
@Composable
internal fun PressureStepperRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onAdjust: (Int) -> Unit,
    descPair: Pair<String, String>,
    imeAction: ImeAction
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StepButton(text = "−5", description = descPair.first, onClick = { onAdjust(-STEPPER_DELTA) })
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .size(width = STEPPER_FIELD_WIDTH, height = BUTTON_SIZE)
                    .clip(RoundedCornerShape(STEPPER_CORNER_RADIUS))
                    .background(MaterialTheme.colors.surface)
                    .padding(vertical = STEPPER_FIELD_PADDING),
                textStyle = MaterialTheme.typography.title3.copy(
                    color = MaterialTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = imeAction
                ),
                singleLine = true
            )
            StepButton(text = "+5", description = descPair.second, onClick = { onAdjust(STEPPER_DELTA) })
        }
    }
}

@Composable
private fun StepButton(text: String, description: String, onClick: () -> Unit) {
    RectangularButton(
        onClick = onClick,
        modifier = Modifier
            .size(BUTTON_SIZE)
            .semantics { contentDescription = description },
        colors = ButtonDefaults.secondaryButtonColors()
    ) {
        Text(text = text, style = MaterialTheme.typography.button)
    }
}

package com.sza.fastmediasorter.wear.ui.apps.bloodpressure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.BloodPressureCategory
import com.sza.fastmediasorter.wear.domain.model.BloodPressureHistoryEntry
import com.sza.fastmediasorter.wear.ui.common.RectangularButton
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.navigation.WearRoutes

private val TITLE_BOTTOM_PADDING = 6.dp
private val SECTION_VERTICAL_PADDING = 4.dp
private val BUTTON_SIZE = 36.dp
private val STEPPER_FIELD_WIDTH = 56.dp
private val STEPPER_CORNER_RADIUS = 6.dp
private val STEPPER_FIELD_PADDING = 6.dp
private const val STEPPER_DELTA = 5
private const val BADGE_ALPHA = 0.2f

/**
 * S3012: The blood pressure input and monitoring screen.
 *
 * Provides interactive stepper controls (+/-) and text entry for systolic/diastolic values,
 * real-time category classification (AHA/WHO guidelines), last reading status card,
 * and navigation to history & analytics.
 */
@Composable
fun BloodPressureScreen(
    viewModel: BloodPressureViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState(positionKey = WearRoutes.BLOOD_PRESSURE),
    onHistoryClick: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            centered = true
        ) {
            item { ScreenTitle() }

            state.lastReading?.let { reading ->
                item {
                    LastReadingCard(
                        reading = reading,
                        category = state.lastReadingCategory
                    )
                }
            }

            state.currentCategory?.let { category ->
                item { CategoryBadge(category = category) }
            }

            item {
                PressureStepperRow(
                    label = stringResource(R.string.blood_pressure_systolic),
                    value = state.systolicInput,
                    onValueChange = viewModel::onSystolicChanged,
                    onAdjust = viewModel::adjustSystolic,
                    descPair = Pair(
                        stringResource(R.string.blood_pressure_decrease_systolic),
                        stringResource(R.string.blood_pressure_increase_systolic)
                    ),
                    imeAction = ImeAction.Next
                )
            }

            item {
                PressureStepperRow(
                    label = stringResource(R.string.blood_pressure_diastolic),
                    value = state.diastolicInput,
                    onValueChange = viewModel::onDiastolicChanged,
                    onAdjust = viewModel::adjustDiastolic,
                    descPair = Pair(
                        stringResource(R.string.blood_pressure_decrease_diastolic),
                        stringResource(R.string.blood_pressure_increase_diastolic)
                    ),
                    imeAction = ImeAction.Done
                )
            }

            state.errorMessageRes?.let { res ->
                item { ErrorText(res) }
            }

            if (state.canSave) {
                item { SaveChip(onClick = { viewModel.save() }) }
            }

            if (onHistoryClick != null) {
                item { HistoryChip(onClick = onHistoryClick) }
            }
        }
    }
}

@Composable
private fun ScreenTitle() {
    Text(
        text = stringResource(R.string.blood_pressure_title),
        style = MaterialTheme.typography.title2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = TITLE_BOTTOM_PADDING),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun LastReadingCard(
    reading: BloodPressureHistoryEntry,
    category: BloodPressureCategory?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = SECTION_VERTICAL_PADDING)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colors.surface)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.blood_pressure_last_reading, reading.systolic, reading.diastolic),
            style = MaterialTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colors.onSurface
        )
        category?.let { cat ->
            Text(
                text = stringResource(cat.labelRes),
                style = MaterialTheme.typography.caption2.copy(fontWeight = FontWeight.SemiBold),
                color = cat.color,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun CategoryBadge(category: BloodPressureCategory) {
    Box(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(category.color.copy(alpha = BADGE_ALPHA))
            .padding(horizontal = 12.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(category.labelRes),
            style = MaterialTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
            color = category.color,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PressureStepperRow(
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
            .padding(horizontal = 4.dp, vertical = SECTION_VERTICAL_PADDING),
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
            RectangularButton(
                onClick = { onAdjust(-STEPPER_DELTA) },
                modifier = Modifier
                    .size(BUTTON_SIZE)
                    .semantics { contentDescription = descPair.first },
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text(text = "−5", style = MaterialTheme.typography.button)
            }

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

            RectangularButton(
                onClick = { onAdjust(STEPPER_DELTA) },
                modifier = Modifier
                    .size(BUTTON_SIZE)
                    .semantics { contentDescription = descPair.second },
                colors = ButtonDefaults.secondaryButtonColors()
            ) {
                Text(text = "+5", style = MaterialTheme.typography.button)
            }
        }
    }
}

@Composable
private fun ErrorText(stringRes: Int) {
    Text(
        text = stringResource(stringRes),
        style = MaterialTheme.typography.body2,
        color = MaterialTheme.colors.error,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = SECTION_VERTICAL_PADDING),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SaveChip(onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.blood_pressure_save)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        colors = ChipDefaults.primaryChipColors()
    )
}

@Composable
private fun HistoryChip(onClick: () -> Unit) {
    CompactChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.blood_pressure_btn_history_analytics)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        colors = ChipDefaults.secondaryChipColors()
    )
}

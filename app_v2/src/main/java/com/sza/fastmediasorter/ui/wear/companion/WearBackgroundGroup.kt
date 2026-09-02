package com.sza.fastmediasorter.ui.wear.companion

import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.ui.settings.WearBackgroundDeliveryState
import com.sza.fastmediasorter.ui.settings.WearBackgroundPreview
import com.sza.fastmediasorter.ui.settings.WearSyncViewModel
import java.io.File

private val PREVIEW_EDGE = 120.dp

private val BACKGROUND_MODES = listOf(
    WearSettingsPayload.BACKGROUND_MODE_BRANDED_ANIMATION to R.string.wear_background_mode_animation,
    WearSettingsPayload.BACKGROUND_MODE_IMAGE to R.string.wear_background_mode_image
)

private val PICKED_IMAGE_TYPES = arrayOf("image/*")

/**
 * S2000: what the watch draws behind its screens, as one group of the companion window.
 *
 * The picker, the preview and the delivery line appear only under the image option, so choosing the
 * branded animation leaves the group a single control (strategic §3.3.6, §3.3.7). The two options
 * are told apart by their labels rather than by the preview, because a thumbnail is not a label for
 * a screen reader (strategic §3.2 "Доступность").
 */
@Composable
fun WearBackgroundGroup(
    viewModel: WearSyncViewModel,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val mode by viewModel.backgroundMode.collectAsState()
    val preview by viewModel.backgroundPreview.collectAsState()
    val delivery by viewModel.backgroundDelivery.collectAsState()

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::sendBackgroundImage)
    }

    WearCompanionGroup(
        title = stringResource(R.string.wear_background_section_title),
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        Spacer(Modifier.height(SPACING_SMALL))
        BackgroundModeRow(selected = mode, onSelect = viewModel::updateBackgroundMode)

        if (mode == WearSettingsPayload.BACKGROUND_MODE_IMAGE) {
            Spacer(Modifier.height(SPACING_SMALL))
            OutlinedButton(
                onClick = { pickImage.launch(PICKED_IMAGE_TYPES) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wearBackgroundPickImage")
            ) {
                Text(stringResource(R.string.wear_background_pick_image))
            }
            preview?.let {
                Spacer(Modifier.height(SPACING_SMALL))
                BackgroundPreview(preview = it)
            }
            DeliveryLine(delivery = delivery)
        }
    }
}

@Composable
private fun BackgroundModeRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL)
    ) {
        BACKGROUND_MODES.forEach { (value, labelRes) ->
            val chipLabel = stringResource(labelRes)
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(chipLabel) },
                // S2091: a chip's own label does not reach the accessibility node, so the two options
                // dump as anonymous checkboxes and the screen reader announces neither.
                modifier = Modifier
                    .testTag("wearBackgroundMode_" + value)
                    .semantics { contentDescription = chipLabel }
            )
        }
    }
}

/**
 * Drawn through the module's own image loader rather than decoded here, and keyed by the frame's
 * stamp: every delivery overwrites the one path, so a loader keyed by path alone would keep showing
 * the picture before last.
 */
@Composable
private fun BackgroundPreview(preview: WearBackgroundPreview) {
    val description = stringResource(R.string.wear_background_preview)
    AndroidView(
        modifier = Modifier.size(PREVIEW_EDGE),
        factory = { context ->
            ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        },
        update = { view ->
            view.contentDescription = description
            Glide.with(view)
                .load(File(preview.path))
                .signature(ObjectKey(preview.stamp))
                .into(view)
        }
    )
}

/**
 * Silence would read as success, and the picture is the one part of this window that travels a
 * channel able to refuse it (strategic §2.8).
 */
@Composable
private fun DeliveryLine(delivery: WearBackgroundDeliveryState) {
    val messageRes = when (delivery) {
        WearBackgroundDeliveryState.Idle -> null
        WearBackgroundDeliveryState.Sending -> R.string.wear_background_sending
        WearBackgroundDeliveryState.Sent -> R.string.wear_background_sent
        WearBackgroundDeliveryState.WatchUnreachable -> R.string.wear_background_watch_unreachable
        WearBackgroundDeliveryState.Failed -> R.string.wear_background_failed
    } ?: return

    val failed = delivery is WearBackgroundDeliveryState.WatchUnreachable ||
        delivery is WearBackgroundDeliveryState.Failed
    Spacer(Modifier.height(SPACING_TINY))
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodySmall,
        color = if (failed) MaterialTheme.colorScheme.error else Color.Unspecified
    )
}

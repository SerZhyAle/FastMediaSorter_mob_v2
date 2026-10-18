package com.sza.fastmediasorter.wear.ui.common

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Dialog
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.model.WearOpenInRole
import com.sza.fastmediasorter.wear.domain.model.WearOpenInTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

private val TITLE_GAP = 8.dp
private val ROW_ICON_SIZE = 24.dp
private val LOADING_SIZE = 24.dp

/**
 * S4381: the applications an object can be opened in, drawn over the Clipboard screen (strategic ADR-2).
 *
 * A dialog and not a screen for the reason [ReceiverListDialog] gives: the wearer reaches it from a chip,
 * dismisses it by swiping and lands back on the screen, so no navigation point is added. An empty list says so in
 * words instead of showing a dead dialog (criterion 10).
 */
@Composable
internal fun OpenInDialog(
    state: OpenInUiState,
    onPick: (WearOpenInTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(showDialog = true, onDismissRequest = onDismiss) {
        val listState = rememberWearDialogListState()
        // A dialog has no Scaffold to hand the indicator to, so it draws its own over the list (S2754).
        Box(modifier = Modifier.fillMaxSize()) {
            WearDialogListColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                item {
                    Text(
                        text = stringResource(R.string.wear_open_in_title),
                        style = MaterialTheme.typography.title3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = TITLE_GAP),
                    )
                }
                when (state) {
                    OpenInUiState.Loading -> item {
                        CircularProgressIndicator(modifier = Modifier.size(LOADING_SIZE))
                    }
                    OpenInUiState.None -> item {
                        Text(
                            text = stringResource(R.string.wear_open_in_none),
                            style = MaterialTheme.typography.caption1,
                            color = MaterialTheme.colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is OpenInUiState.Choose -> items(state.targets, key = { it.packageName }) { target ->
                        OpenInRow(target = target, onClick = { onPick(target) })
                    }
                }
            }
            PositionIndicator(listState)
        }
    }
}

/** One application, one tap target, one spoken label: the name followed by its role, never the role alone. */
@Composable
private fun OpenInRow(target: WearOpenInTarget, onClick: () -> Unit) {
    val roleLabel = roleLabelRes(target.role)?.let { stringResource(it) }
    val spoken = if (roleLabel == null) target.label else "${target.label}, $roleLabel"
    val iconSizePx = with(LocalDensity.current) { ROW_ICON_SIZE.roundToPx() }
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, target.packageName, iconSizePx) {
        value = withContext(Dispatchers.Default) { loadIcon(context, target.packageName, iconSizePx) }
    }
    Chip(
        onClick = onClick,
        label = { Text(text = target.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = roleSlot(roleLabel),
        icon = iconSlot(icon),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = spoken },
        colors = ChipDefaults.secondaryChipColors(),
    )
}

private fun roleSlot(role: String?): (@Composable RowScope.() -> Unit)? {
    if (role == null) {
        return null
    }
    return { Text(text = role, maxLines = 1) }
}

private fun iconSlot(bitmap: ImageBitmap?): (@Composable BoxScope.() -> Unit)? {
    if (bitmap == null) {
        return null
    }
    return { Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(ROW_ICON_SIZE)) }
}

@StringRes
private fun roleLabelRes(role: WearOpenInRole): Int? = when (role) {
    WearOpenInRole.LAST_USED -> R.string.wear_open_in_role_last_used
    WearOpenInRole.BROWSER -> R.string.wear_open_in_role_browser
    WearOpenInRole.MAPS -> R.string.wear_open_in_role_maps
    WearOpenInRole.OTHER -> null
}

/** The application's own icon at row size; null when the package vanished or the icon cannot be drawn. */
private fun loadIcon(context: Context, packageName: String, sizePx: Int): ImageBitmap? = try {
    val drawable = context.packageManager.getApplicationIcon(packageName)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, sizePx, sizePx)
    drawable.draw(Canvas(bitmap))
    bitmap.asImageBitmap()
} catch (e: PackageManager.NameNotFoundException) {
    Timber.d(e, "Open in: no icon for a package that is gone")
    null
}

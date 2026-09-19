package com.sza.fastmediasorter.wear.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.domain.permission.WearPermissionGroup
import com.sza.fastmediasorter.wear.ui.common.WearListColumn
import com.sza.fastmediasorter.wear.ui.common.WearScreenScaffold
import com.sza.fastmediasorter.wear.ui.common.findActivity
import com.sza.fastmediasorter.wear.ui.common.rememberWearListState
import com.sza.fastmediasorter.wear.ui.testing.WearTestTags
import timber.log.Timber

private val ROW_ICON_SIZE = 24.dp

/**
 * S3226: the way into the permissions of an install that is already past its first run.
 *
 * Every row says what the permission is for before it asks for it, because by this point the user came
 * here on purpose and the system dialog alone never explains why the watch wants a microphone.
 *
 * A refusal that the platform will no longer show a dialog for is not reported as a second refusal: the
 * request comes back denied with no rationale to show, which is the one state only the system settings
 * can leave, so the screen opens App Info instead of asking again and getting the same silence.
 */
@Composable
fun PermissionsSettingsScreen(
    viewModel: PermissionsSettingsViewModel = hiltViewModel(),
    listState: ScalingLazyListState = rememberWearListState(positionKey = SettingsRoutes.PERMISSIONS)
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The answer can change in another window - App Info, or the system dialog itself - so it is read
    // again on every return rather than kept from the last composition.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        Timber.i("Permissions settings answer: %s", results)
        viewModel.refresh()
        if (results.values.any { !it } && !context.canStillAsk(results.keys)) {
            context.openAppInfo()
        }
    }

    WearScreenScaffold(
        contentPadding = PaddingValues(0.dp),
        scrollState = listState,
        positionIndicator = { PositionIndicator(listState) }
    ) {
        WearListColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            centered = true
        ) {
            item {
                Text(
                    text = stringResource(R.string.wear_settings_permissions_title),
                    style = MaterialTheme.typography.title2,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
            items(rows) { row ->
                PermissionRow(
                    row = row,
                    onGrant = { launcher.launch(row.permissions.toTypedArray()) }
                )
            }
            item {
                Chip(
                    onClick = { context.openAppInfo() },
                    label = {
                        Text(
                            text = stringResource(R.string.wear_permissions_open_system_settings),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(row: WearPermissionRow, onGrant: () -> Unit) {
    val look = row.group.look()
    // A Column, not two siblings: one list item is a single slot, and the explanation would otherwise
    // be drawn over the chip instead of under it.
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Chip(
            // A granted row keeps its chip so the list reads as one column of rows, but the chip has
            // nothing left to do: the way back out of a grant is the system settings, not this screen.
            onClick = { if (!row.granted) onGrant() },
            enabled = !row.granted,
            icon = {
                Icon(
                    imageVector = look.icon,
                    contentDescription = null,
                    modifier = Modifier.size(ROW_ICON_SIZE)
                )
            },
            label = {
                Text(text = stringResource(look.title), modifier = Modifier.fillMaxWidth())
            },
            secondaryLabel = {
                Text(
                    text = stringResource(
                        if (row.granted) R.string.wear_permissions_granted else R.string.wear_permissions_grant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            colors = ChipDefaults.secondaryChipColors(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WearTestTags.permissionRow(row.group.name))
        )
        Text(
            text = stringResource(look.reason),
            style = MaterialTheme.typography.caption3,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colors.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

private data class PermissionRowLook(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val reason: Int,
)

private fun WearPermissionGroup.look(): PermissionRowLook = when (this) {
    WearPermissionGroup.MEDIA -> PermissionRowLook(
        Icons.Default.FolderOpen,
        R.string.wear_permissions_media_title,
        R.string.wear_permissions_media_desc
    )
    WearPermissionGroup.MICROPHONE -> PermissionRowLook(
        Icons.Default.Mic,
        R.string.wear_permissions_audio_title,
        R.string.wear_permissions_audio_desc
    )
    WearPermissionGroup.SENSORS -> PermissionRowLook(
        Icons.Default.Favorite,
        R.string.wear_permissions_sensors_title,
        R.string.wear_permissions_sensors_desc
    )
}

/**
 * True while the platform is still willing to show a dialog for any of [permissions].
 *
 * Without an Activity there is nothing to ask, so the answer is false and the caller falls back to the
 * system settings, which work either way.
 */
private fun Context.canStillAsk(permissions: Set<String>): Boolean {
    val activity: Activity = findActivity() ?: return false
    return permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
}

private fun Context.openAppInfo() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Not swallowed: a watch without an App Info screen leaves the user the system permissions
        // list, and the line says which watch that was.
        Timber.w(e, "No App Info screen on this watch")
    }
}

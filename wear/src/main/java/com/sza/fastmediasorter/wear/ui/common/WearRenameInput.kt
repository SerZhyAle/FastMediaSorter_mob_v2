package com.sza.fastmediasorter.wear.ui.common

import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.input.RemoteInputIntentHelper
import com.sza.fastmediasorter.wear.R
import timber.log.Timber

/** The key the rename input returns its text under. */
private const val KEY_NEW_NAME = "wear_file_op_new_name"

/**
 * Opens the watch's own text entry and reports the typed name, or nothing when the user entered none.
 *
 * Shared rather than repeated per screen: three file surfaces now offer Rename, and copying the
 * `RemoteInput` plumbing onto each is how one watch's key quirk (S1946, below) would end up fixed in
 * one copy and left broken in the others.
 */
@Composable
fun rememberWearRenameInput(onName: (String) -> Unit): (String?) -> Unit {
    val hint = stringResource(R.string.wear_file_op_rename_hint)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val newName = result.data?.let(::newNameFrom)
        if (!newName.isNullOrBlank()) {
            onName(newName)
        }
    }
    return { initialName -> launchRenameInput(hint, initialName) { launcher.launch(it) } }
}

/**
 * The typed name, keyed answer first.
 *
 * S1946 recorded the failure this order avoids: a watch that returns the text under a key of its own
 * choosing used to read as "the user entered nothing", which here would silently drop a rename.
 */
private fun newNameFrom(data: Intent): String? {
    val results = RemoteInput.getResultsFromIntent(data) ?: return null
    return results.getCharSequence(KEY_NEW_NAME)?.toString()
        ?: results.keySet().firstNotNullOfOrNull { key ->
            results.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }
        }
}

private fun launchRenameInput(hint: String, initialName: String?, launch: (Intent) -> Unit) {
    val remoteInput = RemoteInput.Builder(KEY_NEW_NAME)
        .setLabel(hint)
        .build()
    val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
    RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
    if (!initialName.isNullOrBlank()) {
        val bundle = android.os.Bundle().apply {
            putCharSequence(KEY_NEW_NAME, initialName)
        }
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
        intent.putExtra(Intent.EXTRA_TEXT, initialName)
    }
    try {
        launch(intent)
    } catch (_: ActivityNotFoundException) {
        Timber.w("Wear remote input is unavailable; rename cannot be entered on this watch")
    }
}

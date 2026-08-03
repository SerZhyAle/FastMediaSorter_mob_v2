package com.sza.fastmediasorter.ui.launcher.helpers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactChannel
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactTarget
import com.sza.fastmediasorter.domain.usecase.launcher.PickContactShortcutUseCase
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S1176: drives "put a person on the desktop" - ask what the cell should do, hand off to the system
 * picker for that action, and report back the snapshot to pin. The Activity only says where the cell
 * goes; none of this flow lives there (Rule 3).
 *
 * **The action is asked before the contact, not after.** It decides which system picker opens: calling
 * uses the phone-number picker so the user pins the exact number they meant, while the other two use
 * the contact picker. Asking afterwards would mean guessing a number on their behalf.
 *
 * S1195: the two operations arrive as functions rather than the use case, so the host Activity routes
 * them through its ViewModel instead of injecting a domain type (CLAUDE.md Rule 3). They stay lazy for
 * the original reason: this manager is built in an Activity field initialiser - the only point early
 * enough to register an activity-result contract - so nothing may be dereferenced at construction.
 */
class LauncherContactPickManager(
    private val activity: FragmentActivity,
    private val pickIntent: (LauncherContactAction) -> Intent,
    private val resolvePick: suspend (LauncherContactAction, Uri) -> PickContactShortcutUseCase.Outcome,
    private val onTargetPicked: (LauncherContactTarget) -> Unit,
) {

    /**
     * Which action the in-flight pick is for. Held in memory like the add-flow's target square next to
     * it: one flow runs at a time and every step is modal, so there is nothing to reconcile.
     */
    private var pendingAction: LauncherContactAction? = null

    private val systemPicker = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> onPickResult(result) }

    /** Entry point: the user chose "Contact" on the empty cell they tapped. */
    fun start() {
        val options = LauncherContactAction.entries.map { action ->
            SearchableOptionPickerDialog.Option(id = action.name, label = activity.getString(labelOf(action)))
        }
        showPicker(R.string.launcher_contact_action_title, options, TAG_ACTION, KEY_ACTION) { pickedId ->
            LauncherContactAction.entries
                .firstOrNull { it.name == pickedId }
                ?.let(::launchSystemPicker)
        }
    }

    private fun launchSystemPicker(action: LauncherContactAction) {
        pendingAction = action
        try {
            systemPicker.launch(pickIntent(action))
        } catch (error: ActivityNotFoundException) {
            // A device with no contacts app at all: nothing to recover, so say so and drop the flow.
            Timber.i(error, "Launcher contacts: no picker for action %s", action.name)
            pendingAction = null
            toast(R.string.launcher_contact_no_app)
        }
    }

    private fun onPickResult(result: ActivityResult) {
        val action = pendingAction
        pendingAction = null
        val picked = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || picked == null) return
        if (action == null) {
            // The process was killed while the system picker was in front. The result registry restores
            // the result, but nothing restores which action it was for - nor which cell was tapped, since
            // the host's pending square died with it. Say the pick was lost rather than silently placing
            // a cell in the wrong square or doing nothing at all.
            Timber.i("Launcher contacts: pick result arrived with no in-flight action")
            toast(R.string.launcher_contact_read_failed)
            return
        }
        activity.lifecycleScope.launch {
            val result = resolvePick(action, picked)
            // Outcome kind only - never the person. For MESSAGE it doubles as the grant-reach signal:
            // anything but Unavailable means the read into the contact's own rows went through.
            Timber.d("S1176: pick action=%s outcome=%s", action.name, result.javaClass.simpleName)
            when (val outcome = result) {
                is PickContactShortcutUseCase.Outcome.Ready -> onTargetPicked(outcome.target)
                is PickContactShortcutUseCase.Outcome.ChooseChannel -> chooseChannel(outcome.channels)
                PickContactShortcutUseCase.Outcome.Unavailable -> toast(unavailableMessage(action))
            }
        }
    }

    private fun chooseChannel(channels: List<LauncherContactChannel>) {
        val options = channels.map { channel ->
            SearchableOptionPickerDialog.Option(
                id = channel.target.messageDataId.toString(),
                label = channel.label,
            )
        }
        showPicker(R.string.launcher_contact_channel_title, options, TAG_CHANNEL, KEY_CHANNEL) { pickedId ->
            channels.firstOrNull { it.target.messageDataId.toString() == pickedId }
                ?.let { onTargetPicked(it.target) }
        }
    }

    private fun showPicker(
        @StringRes titleRes: Int,
        options: List<SearchableOptionPickerDialog.Option>,
        tag: String,
        requestKey: String,
        handlePick: (String) -> Unit,
    ) {
        val manager = activity.supportFragmentManager
        // Ahead of the duplicate-open guard on purpose: a picker still up from a rebind has to find a
        // live listener too, and its pick arrives through the FragmentManager, not the dialog instance.
        manager.setFragmentResultListener(requestKey, activity) { _, bundle ->
            Timber.d("S1331: contact picker result key=%s", requestKey)
            bundle.getString(SearchableOptionPickerDialog.RESULT_OPTION_ID)?.let(handlePick)
        }
        // A dialog left up on a rebind must not be duplicated by a second tap - same guard the rest of
        // the add-flow uses.
        if (manager.findFragmentByTag(tag) != null) return
        SearchableOptionPickerDialog.newInstance(
            title = activity.getString(titleRes),
            options = options,
            selectedId = null,
            includeResetRow = false,
            requestKey = requestKey,
        ).show(manager, tag)
    }

    @StringRes
    private fun labelOf(action: LauncherContactAction): Int = when (action) {
        LauncherContactAction.PROFILE -> R.string.launcher_contact_action_profile
        LauncherContactAction.DIAL -> R.string.launcher_contact_action_dial
        LauncherContactAction.MESSAGE -> R.string.launcher_contact_action_message
    }

    /** Per-action wording: "this contact has no number" is a different fact from "cannot read it". */
    @StringRes
    private fun unavailableMessage(action: LauncherContactAction): Int = when (action) {
        LauncherContactAction.PROFILE -> R.string.launcher_contact_read_failed
        LauncherContactAction.DIAL -> R.string.launcher_contact_no_number
        LauncherContactAction.MESSAGE -> R.string.launcher_contact_no_channel
    }

    private fun toast(@StringRes messageRes: Int) {
        Toast.makeText(activity, messageRes, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val TAG_ACTION = "LauncherContactAction"
        const val TAG_CHANNEL = "LauncherContactChannel"

        // One key per picker this manager can open, so the action pick never lands in the channel step.
        const val KEY_ACTION = "launcher_contact_action_pick"
        const val KEY_CHANNEL = "launcher_contact_channel_pick"
    }
}

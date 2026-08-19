package com.sza.fastmediasorter.ui.launcher.helpers

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.PermissionTask
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactChannel
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactTarget
import com.sza.fastmediasorter.domain.usecase.launcher.PickContactShortcutUseCase
import com.sza.fastmediasorter.ui.common.permissions.canRequestPermission
import com.sza.fastmediasorter.ui.common.permissions.markPermissionRequested
import com.sza.fastmediasorter.ui.common.permissions.permissionRationale
import com.sza.fastmediasorter.ui.dialog.SearchableOptionPickerDialog
import com.sza.fastmediasorter.ui.launcher.picker.LauncherPhoneNumberDialogFragment
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S1176: drives "put a person on the desktop" - hand off to the system picker for the chosen action
 * and report back the snapshot to pin. The Activity only says where the cell goes; none of this flow
 * lives there (Rule 3).
 *
 * **The action arrives already chosen, and it decides which system picker opens.** S0428 turned it
 * into four rows of the editor's own first list, so nothing here asks for it. Calling and texting use
 * the phone-number picker, so the user pins the exact number they meant rather than one the app would
 * have guessed; the other two use the contact picker.
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

    /**
     * S1206: the pick waiting on the contacts answer. Held like [pendingAction] above and
     * `LauncherSensorPermissionManager.pendingPlacement`, for the same reason - one modal flow at a time.
     */
    private var pendingPick: (() -> Unit)? = null

    private val systemPicker = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> onPickResult(result) }

    private val contactsPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        val pending = pendingPick
        pendingPick = null
        pending?.invoke()
    }

    /**
     * Entry point: the user chose one of the four contact rows on the empty cell they tapped.
     *
     * S1206: the contacts permission is asked for here, at the moment the person is pinned, because
     * that is where its effect is visible. The answer never decides whether the cell appears - a
     * refusal simply leaves it working from the snapshot, which is what it has always done.
     */
    fun start(action: LauncherContactAction) {
        val granted = ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        val askable = activity.canRequestPermission(Manifest.permission.READ_CONTACTS)
        if (askable) {
            explainThenAsk(action)
        } else {
            pick(action)
        }
    }

    private fun explainThenAsk(action: LauncherContactAction) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.permissions_required_title)
            .setMessage(
                activity.permissionRationale(
                    Manifest.permission.READ_CONTACTS,
                    PermissionTask.CONTACT_CELL_PINNING,
                ),
            )
            .setPositiveButton(R.string.grant_permissions) { _, _ ->
                pendingPick = { pick(action) }
                activity.markPermissionRequested(Manifest.permission.READ_CONTACTS)
                contactsPermission.launch(Manifest.permission.READ_CONTACTS)
            }
            .setNegativeButton(R.string.continue_anyway) { _, _ -> pick(action) }
            // Back or a tap outside is an answer too, and it must not swallow the pin the user asked for.
            .setOnCancelListener { pick(action) }
            .show()
    }

    private fun pick(action: LauncherContactAction) {
        when (action) {
            LauncherContactAction.DIAL, LauncherContactAction.SMS -> askNumberSource(action)
            LauncherContactAction.PROFILE, LauncherContactAction.MESSAGE -> launchSystemPicker(action)
        }
    }

    /**
     * S0428: the address book is the usual source of a number, not the only one. A number that was
     * never saved, or a device carrying no contacts at all, would otherwise dead-end on a toast.
     */
    private fun askNumberSource(action: LauncherContactAction) {
        val options = listOf(
            SearchableOptionPickerDialog.Option(
                id = SOURCE_PICK,
                label = activity.getString(R.string.launcher_contact_source_pick),
            ),
            SearchableOptionPickerDialog.Option(
                id = SOURCE_MANUAL,
                label = activity.getString(R.string.launcher_contact_source_manual),
            ),
        )
        showPicker(R.string.launcher_contact_source_title, options, TAG_SOURCE, KEY_SOURCE) { pickedId ->
            when (pickedId) {
                SOURCE_PICK -> launchSystemPicker(action)
                SOURCE_MANUAL -> askNumber(action)
            }
        }
    }

    /**
     * The typed number is its own caption: there is no name to show, and a cell labelled with the
     * number is what the user just told us they wanted on the desktop.
     */
    private fun askNumber(action: LauncherContactAction) {
        val manager = activity.supportFragmentManager
        manager.setFragmentResultListener(KEY_NUMBER, activity) { _, bundle ->
            bundle.getString(LauncherPhoneNumberDialogFragment.RESULT_NUMBER)?.let { number ->
                // Length only - the number itself is user data and never reaches the log.
                onTargetPicked(
                    LauncherContactTarget(
                        action = action,
                        phoneNumber = number,
                        displayName = number,
                    ),
                )
            }
        }
        if (manager.findFragmentByTag(LauncherPhoneNumberDialogFragment.TAG) != null) return
        LauncherPhoneNumberDialogFragment.newInstance(KEY_NUMBER)
            .show(manager, LauncherPhoneNumberDialogFragment.TAG)
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
            when (val outcome = result) {
                is PickContactShortcutUseCase.Outcome.Ready -> onTargetPicked(outcome.target)
                is PickContactShortcutUseCase.Outcome.ChooseChannel -> chooseChannel(outcome.channels)
                PickContactShortcutUseCase.Outcome.Unavailable -> toast(unavailableMessage(action))
            }
        }
    }

    private fun chooseChannel(channels: List<LauncherContactChannel>) {
        val pm = activity.packageManager
        val options = channels.map { channel ->
            val appIcon = runCatching {
                pm.getApplicationIcon(channel.target.messagePackage)
            }.getOrNull()
            SearchableOptionPickerDialog.Option(
                id = channel.target.messageDataId.toString(),
                label = channel.label,
                leading = appIcon?.let { SearchableOptionPickerDialog.LeadingVisual.IconDrawable(it) },
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

    /** Per-action wording: "this contact has no number" is a different fact from "cannot read it". */
    @StringRes
    private fun unavailableMessage(action: LauncherContactAction): Int = when (action) {
        LauncherContactAction.PROFILE -> R.string.launcher_contact_read_failed
        LauncherContactAction.DIAL, LauncherContactAction.SMS -> R.string.launcher_contact_no_number
        LauncherContactAction.MESSAGE -> R.string.launcher_contact_no_channel
    }

    private fun toast(@StringRes messageRes: Int) {
        Toast.makeText(activity, messageRes, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val TAG_CHANNEL = "LauncherContactChannel"
        const val TAG_SOURCE = "LauncherContactNumberSource"

        // One key per dialog this manager can open, so a pick never lands in another step's listener.
        const val KEY_CHANNEL = "launcher_contact_channel_pick"
        const val KEY_SOURCE = "launcher_contact_source_pick"
        const val KEY_NUMBER = "launcher_contact_number_entry"

        const val SOURCE_PICK = "pick"
        const val SOURCE_MANUAL = "manual"
    }
}

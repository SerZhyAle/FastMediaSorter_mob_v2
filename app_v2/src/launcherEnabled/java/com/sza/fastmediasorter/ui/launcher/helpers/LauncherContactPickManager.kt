package com.sza.fastmediasorter.ui.launcher.helpers

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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
import kotlinx.coroutines.CancellationException
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
 *
 * S2099: which action the in-flight pick is for lives in `LauncherHomeViewModel.pendingContactAction`,
 * not on this class - that field initialiser rebuilds this manager on every process restart, so a
 * field here lost the action whenever the OS killed the process with the system picker in front, and
 * the restored result aborted on a toast instead of placing the cell. It arrives as a read/write pair
 * rather than as the ViewModel, keeping the separation the paragraph above sets up.
 *
 * S2102: every result listener is registered once by [registerContactPickListeners] at screen setup,
 * and no handler closes over anything - each reads its context from [LauncherContactStepState]. Before
 * this, a listener was installed inside the method that opened its dialog, so the rebuilt manager
 * claimed none of the three keys after a process kill. The same ticket replaced the closure that used
 * to be re-run after the contacts answer, which the rebuild lost for the same reason.
 */
class LauncherContactPickManager(
    private val activity: FragmentActivity,
    private val pickIntent: (LauncherContactAction) -> Intent,
    private val resolvePick: suspend (LauncherContactAction, Uri) -> PickContactShortcutUseCase.Outcome,
    private val onTargetPicked: (LauncherContactTarget) -> Unit,
    private val readPendingAction: () -> LauncherContactAction?,
    private val writePendingAction: (LauncherContactAction?) -> Unit,
    private val stepState: LauncherContactStepState,
) {

    private val systemPicker = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> onPickResult(result) }

    /**
     * S2102: the answer is resumed from the durable step, not from a closure. `ActivityResultRegistry`
     * restores this request across process death, so this callback does fire on a rebuilt manager - it
     * simply had nothing to resume, and the flow ended silently after the permission dialog.
     */
    private val contactsPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        val action = stepState.readStep()
        if (action == null) {
            Timber.d("S2102: contacts answer arrived with no pending step")
        } else {
            Timber.d("S2102: contacts answer resumed step ${action.name}")
            pick(action)
        }
    }

    /**
     * S2102: claims all three internal result keys for the Activity's lifetime, called once from
     * `setupViews` beside `LauncherAddFlowManager.registerAddFlowListeners()`.
     *
     * A listener registered here survives what one registered at show time cannot: the FragmentManager
     * restores a dialog without re-running the method that opened it, and the manager that method
     * belonged to is a fresh instance by then.
     */
    fun registerContactPickListeners() {
        val manager = activity.supportFragmentManager
        manager.setFragmentResultListener(KEY_SOURCE, activity) { _, bundle ->
            onSourcePicked(bundle.getString(SearchableOptionPickerDialog.RESULT_OPTION_ID))
        }
        manager.setFragmentResultListener(KEY_NUMBER, activity) { _, bundle ->
            onNumberEntered(bundle.getString(LauncherPhoneNumberDialogFragment.RESULT_NUMBER))
        }
        manager.setFragmentResultListener(KEY_CHANNEL, activity) { _, bundle ->
            onChannelPicked(bundle.getString(SearchableOptionPickerDialog.RESULT_OPTION_ID))
        }
    }

    /**
     * S2102: re-opens the picker step whose dialog closed itself, and starts watching for the user
     * walking away from one. Called from `setupViews` immediately after
     * [registerContactPickListeners], so a re-opened picker delivers to a listener that already exists.
     *
     * `SearchableOptionPickerDialog` keeps its rows in a plain field - an option may carry a `Drawable`,
     * which no bundle takes - so a restored instance has nothing to show and dismisses itself in
     * `onStart`. Its pick is therefore never made, let alone lost, and no listener can rescue it. The
     * durable step is what rebuilds the same question instead.
     *
     * At most one step is re-opened, because the branch shows one dialog at a time. A cold start reads
     * empty state and does nothing.
     */
    fun restorePendingPicker() {
        // Deferred to a resume, never run inline. `setupViews` is posted by BaseActivity, and after a
        // real process kill the system recreates this Activity while another app is still in front - it
        // then reaches onSaveInstanceState before the posted call runs, and showing a dialog there
        // throws `Can not perform this action after onSaveInstanceState`. Measured on device 2026-08-27:
        // the restore fired correctly and took the whole app down one line later.
        //
        // ON_RESUME is also what makes the ordering below sound: the restored dialog's own self-dismissal
        // is enqueued in its onStart, which always precedes it, so the flush sees a dismissal that has
        // definitely been requested. An already-resumed Activity gets the event immediately, because
        // LifecycleRegistry brings a new observer up to the current state.
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    owner.lifecycle.removeObserver(this)
                    reopenPendingPicker()
                }
            },
        )
    }

    private fun reopenPendingPicker() {
        val manager = activity.supportFragmentManager
        // Without this flush findFragmentByTag still answers with the dying instance and the
        // duplicate-open guard would skip the re-open - leaving the flow as dead as before the fix.
        manager.executePendingTransactions()
        if (!restoreChannelPicker(manager)) {
            restoreSourcePicker(manager)
        }
        // Registered last, so the self-dismissal flushed above is never read as the user leaving.
        observeDialogDismissals()
    }

    /** Whether the channel choice was the pending step, whether or not it needed re-opening. */
    private fun restoreChannelPicker(manager: FragmentManager): Boolean {
        val channels = stepState.readChannels()
        if (channels != null && manager.findFragmentByTag(TAG_CHANNEL) == null) {
            Timber.d("S2102: reopening channel picker count=${channels.size}")
            showPicker(
                R.string.launcher_contact_channel_title,
                channelOptions(channels),
                TAG_CHANNEL,
                KEY_CHANNEL,
            )
        }
        return channels != null
    }

    private fun restoreSourcePicker(manager: FragmentManager) {
        val action = stepState.readStep()
        val isNumberBranch = action == LauncherContactAction.DIAL || action == LauncherContactAction.SMS
        // The manual-entry dialog restores itself perfectly well, so a source picker re-opened on top of
        // it would ask again a question the user has already answered and moved past.
        val nothingUp = manager.findFragmentByTag(TAG_SOURCE) == null &&
            manager.findFragmentByTag(LauncherPhoneNumberDialogFragment.TAG) == null
        if (isNumberBranch && nothingUp) {
            Timber.d("S2102: reopening source picker step=${action?.name}")
            askNumberSource()
        }
    }

    /**
     * S2102: clears a step the user walked away from, which is the only thing standing behind strategic
     * §7 risk 1 - neither picker reports a cancellation, so there is no callback to hang this on.
     *
     * Registered here rather than in [registerContactPickListeners] on purpose: [restorePendingPicker]
     * has already flushed the restored dialogs' own self-dismissal by this line, so a removal seen from
     * now on is the user's. `isRemoving` separates that from a configuration change, where the fragment
     * is saved and re-added rather than removed.
     *
     * A delivered pick reaches its handler before the dialog dismisses - `setFragmentResult` is
     * synchronous while the Activity is STARTED - so each branch below only has to avoid clearing a step
     * its successor is still using.
     */
    private fun observeDialogDismissals() {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
                    if (!f.isRemoving) return
                    when (f.tag) {
                        // The manual branch has already opened the number dialog by now, and that step
                        // still needs the action; the system-picker branch cleared the step itself.
                        TAG_SOURCE ->
                            if (fm.findFragmentByTag(LauncherPhoneNumberDialogFragment.TAG) == null) {
                                stepState.writeStep(null)
                            }

                        LauncherPhoneNumberDialogFragment.TAG -> stepState.writeStep(null)
                        TAG_CHANNEL -> stepState.writeChannels(null)
                    }
                }
            },
            false,
        )
    }

    /**
     * Entry point: the user chose one of the four contact rows on the empty cell they tapped.
     *
     * S1206: the contacts permission is asked for here, at the moment the person is pinned, because
     * that is where its effect is visible. The answer never decides whether the cell appears - a
     * refusal simply leaves it working from the snapshot, which is what it has always done.
     */
    fun start(action: LauncherContactAction) {
        // Already-granted is folded into canRequestPermission, so a second check here decided nothing.
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
                // S2102: written before the launch, not inside a later callback - this rationale dialog
                // is a plain AlertDialog and simply vanishes on a process kill, while the permission
                // request behind it is restored and its answer still arrives.
                stepState.writeStep(action)
                activity.markPermissionRequested(Manifest.permission.READ_CONTACTS)
                contactsPermission.launch(Manifest.permission.READ_CONTACTS)
            }
            .setNegativeButton(R.string.continue_anyway) { _, _ -> pick(action) }
            // Back or a tap outside is an answer too, and it must not swallow the pin the user asked for.
            .setOnCancelListener { pick(action) }
            .show()
    }

    private fun pick(action: LauncherContactAction) {
        // S2102: the step is stamped at every entry to the branch, which is also what bounds a stale
        // value - neither picker reports a cancellation, so overwriting here is the only guard a
        // dismissed dialog leaves available.
        stepState.writeStep(action)
        when (action) {
            LauncherContactAction.DIAL, LauncherContactAction.SMS -> askNumberSource()
            LauncherContactAction.PROFILE, LauncherContactAction.MESSAGE -> launchSystemPicker(action)
        }
    }

    /**
     * S0428: the address book is the usual source of a number, not the only one. A number that was
     * never saved, or a device carrying no contacts at all, would otherwise dead-end on a toast.
     */
    private fun askNumberSource() {
        showPicker(R.string.launcher_contact_source_title, sourceOptions(), TAG_SOURCE, KEY_SOURCE)
    }

    /** Split out so the restore path can rebuild the same two rows without re-entering the flow. */
    private fun sourceOptions(): List<SearchableOptionPickerDialog.Option> = listOf(
        SearchableOptionPickerDialog.Option(
            id = SOURCE_PICK,
            label = activity.getString(R.string.launcher_contact_source_pick),
        ),
        SearchableOptionPickerDialog.Option(
            id = SOURCE_MANUAL,
            label = activity.getString(R.string.launcher_contact_source_manual),
        ),
    )

    private fun onSourcePicked(pickedId: String?) {
        val action = stepState.readStep()
        if (pickedId == null || action == null) {
            Timber.d("S2102: source pick dropped hasId=${pickedId != null} step=${action?.name}")
            return
        }
        Timber.d("S2102: source pick $pickedId for step ${action.name}")
        when (pickedId) {
            SOURCE_PICK -> launchSystemPicker(action)
            SOURCE_MANUAL -> askNumber()
        }
    }

    private fun askNumber() {
        val manager = activity.supportFragmentManager
        if (manager.findFragmentByTag(LauncherPhoneNumberDialogFragment.TAG) != null) return
        LauncherPhoneNumberDialogFragment.newInstance(KEY_NUMBER)
            .show(manager, LauncherPhoneNumberDialogFragment.TAG)
    }

    /**
     * The typed number is its own caption: there is no name to show, and a cell labelled with the
     * number is what the user just told us they wanted on the desktop.
     *
     * S2102: this is the one dialog of the three that genuinely restores itself and delivers, so before
     * the permanent listener above existed the user retyped nothing, tapped confirm, and the result sat
     * in the FragmentManager with nobody to claim it.
     */
    private fun onNumberEntered(number: String?) {
        val action = stepState.readStep()
        if (number == null || action == null) {
            Timber.d("S2102: number entry dropped hasNumber=${number != null} step=${action?.name}")
            return
        }
        Timber.d("S2102: number entry accepted step=${action.name} len=${number.length}")
        stepState.writeStep(null)
        // The number itself is user data and never reaches the log.
        onTargetPicked(
            LauncherContactTarget(
                action = action,
                phoneNumber = number,
                displayName = number,
            ),
        )
    }

    private fun launchSystemPicker(action: LauncherContactAction) {
        // S2102: the branch step hands the flow to the system picker here. Clearing it keeps exactly one
        // durable value naming this leg - two that disagreed after a restore would be unresolvable.
        stepState.writeStep(null)
        writePendingAction(action)
        Timber.d("S2099: stored pending contact action ${action.name}")
        try {
            systemPicker.launch(pickIntent(action))
        } catch (error: ActivityNotFoundException) {
            // A device with no contacts app at all: nothing to recover, so say so and drop the flow.
            Timber.i(error, "Launcher contacts: no picker for action %s", action.name)
            writePendingAction(null)
            toast(R.string.launcher_contact_no_app)
        }
    }

    private fun onPickResult(result: ActivityResult) {
        val action = readPendingAction()
        Timber.d("S2099: pick result restored action ${action?.name}")
        writePendingAction(null)
        val picked = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || picked == null) {
            // The one exit that says nothing to the user, so it has to say something to the log:
            // a cancelled picker and a picker that answered without a URI look identical from the grid.
            Timber.d("S2107: pick dropped code=${result.resultCode} hasUri=${picked != null}")
            return
        }
        if (action == null) {
            // S2099 made the action outlive a process kill, so reaching here means no pick was ever in
            // flight - a result delivered to a registry key nothing claimed. Say it was lost rather than
            // silently placing a cell in a square nobody pointed at.
            Timber.i("Launcher contacts: pick result arrived with no in-flight action")
            toast(R.string.launcher_contact_read_failed)
            return
        }
        activity.lifecycleScope.launch { resolveAndPlace(action, picked) }
    }

    /**
     * S2107: the whole post-pick chain, with every exit logged and nothing swallowed.
     *
     * It used to be the unguarded body of the `launch` above, which is why the DIAL flow could finish a
     * pick and place no cell with no trace at all: a provider that threw, a scope cancelled with the
     * Activity, and an outcome that reached the user as a toast nobody saw all looked the same from
     * logcat - a single S2099 line and then silence.
     */
    // The broad catch is the point, not an oversight: this is the last frame before an uncaught throw
    // would take the whole coroutine down silently, and a contacts provider is third-party code that may
    // raise anything at all. Narrowing it to the exceptions seen so far would restore exactly the blind
    // spot the ticket exists to close. Cancellation is re-thrown above so the scope still unwinds.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun resolveAndPlace(action: LauncherContactAction, picked: Uri) {
        try {
            // The authority, never the record: which provider answered the pick is the one fact that
            // separates "read the wrong URI" from "read it and got nothing", and it names no person.
            Timber.d("S2107: resolving pick action=${action.name} authority=${picked.authority}")
            val outcome = resolvePick(action, picked)
            Timber.d("S2107: pick outcome ${outcome::class.simpleName}")
            dispatchOutcome(action, outcome)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Timber.w(error, "Launcher contacts: pick resolution failed")
        }
    }

    /**
     * Outcome kind only - never the person. For MESSAGE it doubles as the grant-reach signal: anything
     * but Unavailable means the read into the contact's own rows went through.
     */
    private fun dispatchOutcome(
        action: LauncherContactAction,
        outcome: PickContactShortcutUseCase.Outcome,
    ) {
        when (outcome) {
            is PickContactShortcutUseCase.Outcome.Ready -> {
                Timber.d("S2107: handing target to add flow, action=${outcome.target.action.name}")
                onTargetPicked(outcome.target)
            }

            is PickContactShortcutUseCase.Outcome.ChooseChannel -> {
                Timber.d("S2107: choosing channel, count=${outcome.channels.size}")
                chooseChannel(outcome.channels)
            }

            PickContactShortcutUseCase.Outcome.Unavailable -> {
                Timber.d("S2107: pick unavailable for action=${action.name}")
                toast(unavailableMessage(action))
            }
        }
    }

    private fun chooseChannel(channels: List<LauncherContactChannel>) {
        // S2102: stored before the picker opens. The list came from a contacts read the restored process
        // never repeats, and it is the only thing mapping the picked row back to a placeable target.
        stepState.writeChannels(channels)
        showPicker(R.string.launcher_contact_channel_title, channelOptions(channels), TAG_CHANNEL, KEY_CHANNEL)
    }

    /**
     * The icon is fetched rather than stored, here and on the restore path alike: a `Drawable` cannot go
     * into saved state, and the package that registered the row is enough to ask for it again.
     */
    private fun channelOptions(
        channels: List<LauncherContactChannel>,
    ): List<SearchableOptionPickerDialog.Option> {
        val pm = activity.packageManager
        return channels.map { channel ->
            val appIcon = runCatching {
                pm.getApplicationIcon(channel.target.messagePackage)
            }.getOrNull()
            SearchableOptionPickerDialog.Option(
                id = channel.target.messageDataId.toString(),
                label = channel.label,
                leading = appIcon?.let { SearchableOptionPickerDialog.LeadingVisual.IconDrawable(it) },
            )
        }
    }

    private fun onChannelPicked(pickedId: String?) {
        val channels = stepState.readChannels()
        val target = channels?.firstOrNull { it.target.messageDataId.toString() == pickedId }?.target
        if (target == null) {
            Timber.d("S2102: channel pick unmatched stored=${channels?.size ?: -1}")
            return
        }
        Timber.d("S2102: channel pick matched dataId=${target.messageDataId}")
        stepState.writeChannels(null)
        onTargetPicked(target)
    }

    private fun showPicker(
        @StringRes titleRes: Int,
        options: List<SearchableOptionPickerDialog.Option>,
        tag: String,
        requestKey: String,
    ) {
        val manager = activity.supportFragmentManager
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

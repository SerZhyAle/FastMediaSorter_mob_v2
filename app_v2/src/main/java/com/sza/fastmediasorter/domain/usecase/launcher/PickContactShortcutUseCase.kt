package com.sza.fastmediasorter.domain.usecase.launcher

import android.content.Intent
import android.net.Uri
import com.sza.fastmediasorter.data.launcher.ContactSnapshotDataSource
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactAction
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactChannel
import com.sza.fastmediasorter.domain.model.launcher.LauncherContactTarget
import javax.inject.Inject

/**
 * S1176: turns "the user picked this record for this action" into the cell target to pin, or into the
 * one question that still has to be asked.
 *
 * **Placement is deliberately not here.** The desktop add-flow starts from a tap on an empty cell and
 * carries that square through every picker, so a contact lands exactly where the user pointed - the
 * same rule the seven existing kinds follow. S1170's free-slot search belongs to the Settings entry
 * point, which has no grid on screen to point at; using it here would move the new cell somewhere the
 * user did not tap.
 *
 * The four actions differ only in how much is still unknown after the pick: a profile and either
 * number-based target are complete, while a message may have several channels and needs one more
 * choice.
 */
class PickContactShortcutUseCase @Inject constructor(
    private val contacts: ContactSnapshotDataSource,
) {

    /** What is left to do after reading the picked record. */
    sealed interface Outcome {
        /** The snapshot is complete - pin it. */
        data class Ready(val target: LauncherContactTarget) : Outcome

        /** Several messaging apps address this contact; the user picks which one the cell opens. */
        data class ChooseChannel(val channels: List<LauncherContactChannel>) : Outcome

        /**
         * This contact cannot serve this action - no number, no messaging channel, or the record could
         * not be read at all. One outcome for all three: from the user's side they are the same answer,
         * and the caller phrases it per action.
         */
        data object Unavailable : Outcome
    }

    /** The system picker to launch for [action]; its result is what [invoke] consumes. */
    fun pickIntent(action: LauncherContactAction): Intent = contacts.pickIntent(action)

    suspend operator fun invoke(action: LauncherContactAction, picked: Uri): Outcome = when (action) {
        LauncherContactAction.PROFILE -> contacts.readProfile(picked).asOutcome()

        LauncherContactAction.DIAL, LauncherContactAction.SMS ->
            contacts.readPhoneTarget(picked, action).asOutcome()

        LauncherContactAction.MESSAGE -> channelOutcome(contacts.readMessageChannels(picked))
    }

    private fun LauncherContactTarget?.asOutcome(): Outcome =
        this?.let { Outcome.Ready(it) } ?: Outcome.Unavailable

    /** A single channel is not a choice - pin it and spare the user a one-row dialog. */
    private fun channelOutcome(channels: List<LauncherContactChannel>): Outcome = when (channels.size) {
        0 -> Outcome.Unavailable
        1 -> Outcome.Ready(channels.first().target)
        else -> Outcome.ChooseChannel(channels)
    }
}

package com.sza.fastmediasorter.ui.broadcast.helpers

import android.view.LayoutInflater
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.broadcast.BROADCAST_SOURCE_FILE
import com.sza.fastmediasorter.broadcast.BroadcastMode
import com.sza.fastmediasorter.broadcast.BroadcastState
import com.sza.fastmediasorter.databinding.ActivityBroadcastControlBinding
import com.sza.fastmediasorter.databinding.ItemPreparedBroadcastLinkBinding
import com.sza.fastmediasorter.domain.model.ExchangeBroadcastPublishState
import com.sza.fastmediasorter.domain.model.PreparedBroadcastLink
import com.sza.fastmediasorter.domain.model.PreparedBroadcastStartOutcome
import com.sza.fastmediasorter.domain.usecase.broadcast.CheckPreparedBroadcastStartUseCase
import com.sza.fastmediasorter.domain.usecase.broadcast.ManagePreparedBroadcastLinksUseCase
import com.sza.fastmediasorter.domain.usecase.broadcast.ObserveExchangeBroadcastPublishStateUseCase
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/**
 * S4247: the "Link in advance" button and the live prepared links of the broadcast screen before the start (owner
 * decision В5), with Share and Revoke; Revoke confirms through the DialogDestructive role of the destructive alert
 * overlay. On air both are gone, and the screen tells once per broadcast when the prepared id was taken by another
 * device (item G) or a file start did not use the handed-out link. Owned by the screen's singleton coordinator, so
 * it keeps no view or activity: every collection runs on the activity's own lifecycle.
 */
class BroadcastPreparedLinksManager @Inject constructor(
    private val manageLinks: ManagePreparedBroadcastLinksUseCase,
    private val checkStart: CheckPreparedBroadcastStartUseCase,
    private val observePublishState: ObserveExchangeBroadcastPublishStateUseCase,
    private val prepareDialog: BroadcastPrepareLinkDialogManager,
    private val shareDialog: BroadcastPreparedShareDialogManager,
) {

    private var links: List<PreparedBroadcastLink> = emptyList()

    // The live session on screen, keyed by its start time; null before the start.
    private var liveStartedAt: Long? = null
    private var liveSource: String? = null

    // The sessions already told, so a rotation or a re-render does not repeat the notice.
    private var collisionToldFor: Long? = null
    private var notUsedToldFor: Long? = null

    fun setup(activity: AppCompatActivity, binding: ActivityBroadcastControlBinding) {
        shareDialog.setup(activity)
        binding.btnPrepareBroadcastLink.setOnClickListener { openPrepareForm(activity, binding) }
        activity.collectOnLifecycle(manageLinks.links) { live ->
            links = live
            if (liveStartedAt == null) renderList(activity, binding)
        }
        val outcomes = combine(manageLinks.startOutcome, observePublishState()) { outcome, publish ->
            outcome to publish
        }
        activity.collectOnLifecycle(outcomes) { (outcome, publish) -> tell(activity, binding, outcome, publish) }
    }

    fun renderPreStream(activity: AppCompatActivity, binding: ActivityBroadcastControlBinding) {
        liveStartedAt = null
        liveSource = null
        binding.btnPrepareBroadcastLink.visibility = View.VISIBLE
        renderList(activity, binding)
    }

    fun renderLive(activity: AppCompatActivity, binding: ActivityBroadcastControlBinding, state: BroadcastState.Live) {
        liveStartedAt = state.startedAtElapsedRealtimeMs
        liveSource = state.source
        binding.btnPrepareBroadcastLink.visibility = View.GONE
        binding.layoutPreparedLinks.visibility = View.GONE
        tell(activity, binding, manageLinks.startOutcome.value, observePublishState().value)
    }

    fun onDetach() {
        shareDialog.onDetach()
    }

    private fun openPrepareForm(activity: AppCompatActivity, binding: ActivityBroadcastControlBinding) {
        prepareDialog.show(activity) { mode, days, choice, customPassword, guests ->
            activity.lifecycleScope.launch {
                val outcome = manageLinks.prepare(mode, days, choice, customPassword, guests)
                Timber.d("S4247: prepare link mode=$mode days=$days outcome=${outcome::class.simpleName}")
                messageFor(outcome)?.let { Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show() }
                if (outcome is ManagePreparedBroadcastLinksUseCase.Outcome.Prepared) {
                    shareDialog.show(activity, outcome.link)
                }
            }
        }
    }

    @StringRes
    private fun messageFor(outcome: ManagePreparedBroadcastLinksUseCase.Outcome): Int? = when (outcome) {
        ManagePreparedBroadcastLinksUseCase.Outcome.NoOutgoingEntry -> R.string.broadcast_prepared_no_server
        ManagePreparedBroadcastLinksUseCase.Outcome.LimitReached -> R.string.broadcast_prepared_limit
        ManagePreparedBroadcastLinksUseCase.Outcome.InvalidPassword -> R.string.broadcast_link_password_error_empty
        ManagePreparedBroadcastLinksUseCase.Outcome.NotStored -> R.string.broadcast_link_password_store_failed
        is ManagePreparedBroadcastLinksUseCase.Outcome.Prepared -> null
    }

    private fun renderList(activity: AppCompatActivity, binding: ActivityBroadcastControlBinding) {
        val container = binding.containerPreparedLinks
        container.removeAllViews()
        binding.layoutPreparedLinks.visibility = if (links.isEmpty()) View.GONE else View.VISIBLE
        val inflater = LayoutInflater.from(activity)
        val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
        links.forEach { link ->
            val row = ItemPreparedBroadcastLinkBinding.inflate(inflater, container, false)
            row.tvPreparedLinkSummary.text = activity.getString(
                R.string.broadcast_prepared_row,
                activity.getString(modeLabel(link.mode)),
                dateFormat.format(Date(link.untilMs)),
            )
            row.btnPreparedLinkShare.setOnClickListener { shareDialog.show(activity, link) }
            row.btnPreparedLinkRevoke.setOnClickListener { confirmRevoke(activity, link) }
            container.addView(row.root)
        }
    }

    private fun confirmRevoke(activity: AppCompatActivity, link: PreparedBroadcastLink) {
        MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive)
            .setTitle(R.string.broadcast_prepared_revoke_title)
            .setMessage(R.string.broadcast_prepared_revoke_message)
            .setPositiveButton(R.string.broadcast_prepared_revoke) { _, _ ->
                Timber.d("S4247: prepared link revoked $link")
                activity.lifecycleScope.launch { manageLinks.revoke(link.broadcastId) }
            }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(activity)
    }

    /**
     * The "not used" notice waits for the server's `broadcast-started`: by then the announcement has already decided
     * whether the start consumed a prepared link, so a link still live for this server was not used.
     */
    private fun tell(
        activity: AppCompatActivity,
        binding: ActivityBroadcastControlBinding,
        outcome: PreparedBroadcastStartOutcome,
        publish: ExchangeBroadcastPublishState,
    ) {
        val session = liveStartedAt
        when {
            session == null -> Unit
            outcome == PreparedBroadcastStartOutcome.Collided -> tellCollision(binding, session)
            mayHaveSkippedLink(session, outcome, publish) -> tellIfNotUsed(activity, binding, session)
        }
    }

    private fun tellCollision(binding: ActivityBroadcastControlBinding, session: Long) {
        if (collisionToldFor == session) return
        collisionToldFor = session
        Timber.d("S4247: id-collision notice shown")
        Timber.w("Broadcast went on air under a new id: the prepared one was taken")
        Snackbar.make(binding.root, R.string.broadcast_prepared_id_taken, Snackbar.LENGTH_LONG).show()
    }

    private fun mayHaveSkippedLink(
        session: Long,
        outcome: PreparedBroadcastStartOutcome,
        publish: ExchangeBroadcastPublishState,
    ): Boolean = liveSource == BROADCAST_SOURCE_FILE &&
        publish is ExchangeBroadcastPublishState.OnServer &&
        outcome == PreparedBroadcastStartOutcome.None &&
        notUsedToldFor != session

    private fun tellIfNotUsed(activity: AppCompatActivity, binding: ActivityBroadcastControlBinding, session: Long) {
        activity.lifecycleScope.launch {
            val stillLive = checkStart(null) == CheckPreparedBroadcastStartUseCase.Verdict.Matches
            if (stillLive && notUsedToldFor != session && liveStartedAt == session) {
                notUsedToldFor = session
                Timber.d("S4247: file start did not use the prepared link")
                Snackbar.make(binding.root, R.string.broadcast_prepared_not_used, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    @StringRes
    private fun modeLabel(mode: String): Int = when (mode) {
        BroadcastMode.VIDEO_AUDIO.name -> R.string.broadcast_entry_mode_video_audio
        BroadcastMode.VIDEO_ONLY.name -> R.string.broadcast_entry_mode_video_only
        else -> R.string.broadcast_entry_mode_audio_only
    }
}

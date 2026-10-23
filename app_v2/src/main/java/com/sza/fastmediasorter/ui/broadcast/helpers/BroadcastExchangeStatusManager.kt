package com.sza.fastmediasorter.ui.broadcast.helpers

import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ExchangeBroadcastPublishState
import com.sza.fastmediasorter.domain.model.ExchangeBroadcastPublishState.LocalOnly
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import com.sza.fastmediasorter.ui.exchange.MyDevicesActivity
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeMessageText
import com.sza.fastmediasorter.util.showBoundTo
import timber.log.Timber

/**
 * Renders the exchange server line of the broadcast screen (contract DEVICE-EXCHANGE item DU). The state is in the
 * words of one `TextView`, so TalkBack reads it as one node and nothing rests on color alone; a failure shows the
 * row of item CZ and opens its advice and code on a tap (item DA). No text carries a secret.
 */
class BroadcastExchangeStatusManager {

    fun render(activity: AppCompatActivity, view: TextView, state: ExchangeBroadcastPublishState) {
        Timber.d("S4419: broadcast screen exchange line renders ${state::class.simpleName}")
        val action: (() -> Unit)? = when (state) {
            ExchangeBroadcastPublishState.Idle -> null
            is ExchangeBroadcastPublishState.Announcing -> {
                view.text = activity.getString(R.string.broadcast_exchange_line_announcing, state.server)
                null
            }
            is ExchangeBroadcastPublishState.OnServer -> {
                val line = if (state.relayed) {
                    R.string.broadcast_exchange_line_relayed
                } else {
                    R.string.broadcast_exchange_line_on_server
                }
                view.text = activity.getString(line, state.server)
                null
            }
            is ExchangeBroadcastPublishState.Failed -> {
                val what = activity.getString(ExchangeMessageText.what(state.message))
                view.text = activity.getString(R.string.broadcast_exchange_line_failed, state.server, what)
                val details: () -> Unit = { showDetails(activity, state.message) }
                details
            }
            is LocalOnly -> {
                val line = if (state.reason == LocalOnly.Reason.NO_ENTRY) {
                    R.string.broadcast_exchange_line_local_only
                } else {
                    R.string.broadcast_exchange_line_guest_only
                }
                view.text = activity.getString(line)
                val addServer: () -> Unit = { MyDevicesActivity.start(activity) }
                addServer
            }
        }
        view.visibility = if (state == ExchangeBroadcastPublishState.Idle) View.GONE else View.VISIBLE
        // setOnClickListener turns clickable on, so the flags are set after it.
        view.setOnClickListener(action?.let { run -> View.OnClickListener { run() } })
        view.isClickable = action != null
        view.isFocusable = action != null
    }

    private fun showDetails(activity: AppCompatActivity, message: ExchangeMessage) {
        val body = ExchangeMessageText.twoParts(activity, message) + "\n\n" +
            activity.getString(R.string.exchange_failure_details_code, message.code)
        MaterialAlertDialogBuilder(activity)
            .setMessage(body)
            .setPositiveButton(R.string.ok, null)
            .showBoundTo(activity)
    }
}

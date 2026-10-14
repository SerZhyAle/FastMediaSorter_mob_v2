package com.sza.fastmediasorter.wear.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sza.fastmediasorter.wear.service.helpers.WearExchangeCastCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Delivers the owner's Listen or Decline on a cast offer notification to the coordinator. */
@AndroidEntryPoint
class WearExchangeCastActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var coordinator: WearExchangeCastCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        // No super.onReceive: BroadcastReceiver declares it abstract, and the Hilt Gradle plugin's bytecode
        // transform is what injects the field above.
        val broadcastId = intent.getStringExtra(EXTRA_BROADCAST_ID)
        if (intent.action == ACTION_ANSWER && broadcastId != null) {
            coordinator.onUserAnswer(broadcastId, intent.getBooleanExtra(EXTRA_ACCEPTED, false))
        }
    }

    companion object {
        private const val ACTION_ANSWER = "com.sza.fastmediasorter.wear.action.EXCHANGE_CAST_ANSWER"
        private const val EXTRA_BROADCAST_ID = "com.sza.fastmediasorter.wear.extra.EXCHANGE_BROADCAST_ID"
        private const val EXTRA_ACCEPTED = "com.sza.fastmediasorter.wear.extra.EXCHANGE_CAST_ACCEPTED"

        fun answerIntent(context: Context, broadcastId: String, accepted: Boolean): Intent =
            Intent(context, WearExchangeCastActionReceiver::class.java)
                .setAction(ACTION_ANSWER)
                .putExtra(EXTRA_BROADCAST_ID, broadcastId)
                .putExtra(EXTRA_ACCEPTED, accepted)
    }
}

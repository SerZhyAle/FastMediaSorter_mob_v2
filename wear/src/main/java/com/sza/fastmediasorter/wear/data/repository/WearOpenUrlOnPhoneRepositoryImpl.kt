package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.concurrent.futures.await
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.wear.domain.model.WearOpenUrlOnPhoneOutcome
import com.sza.fastmediasorter.wear.domain.repository.WearOpenUrlOnPhoneRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2496: opens an address on the paired phone through the Wear OS companion.
 *
 * The companion, not our phone module, is the executor - so the link works on a phone that never
 * installed FastMediaSorter, which is the whole reason this sits beside the S2004 bridge instead of
 * reusing it.
 */
@Singleton
class WearOpenUrlOnPhoneRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : WearOpenUrlOnPhoneRepository {

    override suspend fun openOnPhone(url: String): WearOpenUrlOnPhoneOutcome =
        withContext(Dispatchers.IO) {
            val nodes = connectedNodes()
            if (nodes.isEmpty()) {
                WearOpenUrlOnPhoneOutcome.NO_CONNECTED_PHONE
            } else {
                sendToAny(nodes, url)
            }
        }

    private suspend fun connectedNodes(): List<Node> = runCatching {
        Wearable.getNodeClient(context).connectedNodes.await()
    }.onFailure { Timber.w(it, "Open URL on phone: connected node lookup failed") }
        .getOrDefault(emptyList())

    private suspend fun sendToAny(nodes: List<Node>, url: String): WearOpenUrlOnPhoneOutcome {
        val helper = RemoteActivityHelper(context)
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse(url))
        // map before any(): a watch paired with more than one phone should be offered to every one of
        // them rather than only the first that accepts, which short-circuiting would do.
        val results = nodes.map { node ->
            runCatching { helper.startRemoteActivity(intent, node.id).await() }
                .onFailure { Timber.w(it, "Open URL on phone: node %s refused", node.id) }
                .isSuccess
        }
        return if (results.any { it }) {
            WearOpenUrlOnPhoneOutcome.OPENED
        } else {
            WearOpenUrlOnPhoneOutcome.FAILED
        }
    }
}

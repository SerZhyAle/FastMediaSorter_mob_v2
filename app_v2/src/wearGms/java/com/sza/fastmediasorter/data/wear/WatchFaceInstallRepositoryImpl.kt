package com.sza.fastmediasorter.data.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.concurrent.futures.await
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.WatchFaceLinks
import com.sza.fastmediasorter.domain.model.WatchFaceOpenResult
import com.sza.fastmediasorter.domain.repository.WatchFaceInstallRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4009: starts the watch face's store page on the paired watch - the documented cross-device
 * install route, since Play refuses one bundle carrying both the face and Wear logic.
 *
 * The node lookup deliberately bypasses the Wear Companion switch that gates
 * [WearableDataLayerRepositoryImpl.getConnectedNodes]: the face is a store install, not content
 * shared with the watch, so a user with the companion off may still want it.
 */
@Singleton
class WatchFaceInstallRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : WatchFaceInstallRepository {

    override suspend fun openListingOnWatch(): WatchFaceOpenResult = withContext(Dispatchers.IO) {
        val node = connectedNodes().firstOrNull()
        if (node == null) {
            Timber.i("Watch face install: no connected watch")
            WatchFaceOpenResult.NoWatch
        } else {
            startListingOn(node)
        }
    }

    private suspend fun connectedNodes(): List<Node> = runCatching {
        Wearable.getNodeClient(context).connectedNodes.await()
    }.onFailure { it.rethrowIfCancellation() }
        .onFailure {
            // Play Services Wearable missing or the pairing gone - an unreachable bridge is no watch.
            Timber.i(it, "Watch face install: connected-nodes query failed, treating the watch as absent")
        }.getOrDefault(emptyList())

    private suspend fun startListingOn(node: Node): WatchFaceOpenResult {
        val intent = Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse(WatchFaceLinks.MARKET_URL))
        return runCatching {
            RemoteActivityHelper(context, Dispatchers.IO.asExecutor())
                .startRemoteActivity(intent, node.id)
                .await()
        }.onFailure { it.rethrowIfCancellation() }.fold(
            onSuccess = {
                Timber.i("Watch face install: store page started on the watch")
                WatchFaceOpenResult.OpenedOnWatch(node.displayName)
            },
            onFailure = { e -> failureResult(e) }
        )
    }

    private fun failureResult(e: Throwable): WatchFaceOpenResult =
        if (e is RemoteActivityHelper.RemoteIntentException) {
            Timber.i(e, "Watch face install: the watch could not open the store page")
            WatchFaceOpenResult.WatchStoreUnavailable
        } else {
            Timber.w(e, "Watch face install: remote start failed")
            WatchFaceOpenResult.Failed
        }
}

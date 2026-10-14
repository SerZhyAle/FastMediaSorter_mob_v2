package com.sza.fastmediasorter.wear.data.exchange

import android.content.Context
import android.net.Network
import android.net.Uri
import com.sza.fastmediasorter.wear.data.network.WearTransferNetworkManager
import com.sza.fastmediasorter.wear.domain.files.WearServerClipboardChooser
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardEntryFailure
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardFailure
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardOffer
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardReceiveReport
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSendReport
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardSink
import com.sza.fastmediasorter.wear.domain.model.WearServerClipboardValue
import com.sza.fastmediasorter.wear.domain.repository.WearServerClipboardRepository
import com.sza.fastmediasorter.wear.util.CloseableHandOff
import com.sza.fastmediasorter.wear.util.handingOffCloseable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4227: the two clipboard actions of contract DEVICE-EXCHANGE 22 item CA over every entry the watch holds.
 *
 * One action holds the watch's Wi-Fi or LTE once for all its entries ([WearTransferNetworkManager]). A send walks
 * the entries in their stored order, each on its own data stream, and succeeds when one stored the value. A get
 * asks every entry for its state at once, each bounded, so one silent server never holds the action, and fetches
 * from the entry [WearServerClipboardChooser] picks.
 */
@Singleton
class WearServerClipboardRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: WearExchangeEntryStore,
    private val client: WearServerClipboardClient,
    private val network: WearTransferNetworkManager,
) : WearServerClipboardRepository {

    override val hasEligibleEntry: Flow<Boolean> = store.entries
        .map { entries -> entries.any { it.clipboardEligible } }
        .distinctUntilChanged()

    override suspend fun latestState(): WearServerClipboardValue? {
        val entries = eligibleEntries()
        val chosen = if (entries.isEmpty()) {
            null
        } else {
            network.withTransferNetwork { granted -> askAll(entries, granted).chosen }
        }
        return chosen?.second
    }

    override suspend fun send(
        offer: WearServerClipboardOffer,
        source: Uri,
        onProgress: (sent: Long, total: Long) -> Unit,
    ): WearServerClipboardSendReport {
        val entries = eligibleEntries()
        return when {
            entries.isEmpty() -> WearServerClipboardSendReport(emptyList(), emptyList(), noEntry = true)
            else -> network.withTransferNetwork { granted -> sendToAll(entries, offer, source, granted, onProgress) }
                ?: WearServerClipboardSendReport(emptyList(), emptyList(), noNetwork = true)
        }
    }

    override suspend fun receive(
        destination: suspend (WearServerClipboardValue) -> WearServerClipboardSink?,
        onProgress: (received: Long, total: Long) -> Unit,
    ): WearServerClipboardReceiveReport {
        val entries = eligibleEntries()
        return when {
            entries.isEmpty() -> WearServerClipboardReceiveReport.NoEntry
            else -> network.withTransferNetwork { granted -> fetchLatest(entries, granted, destination, onProgress) }
                ?: WearServerClipboardReceiveReport.NoNetwork
        }
    }

    private suspend fun eligibleEntries(): List<WearExchangeEntry> = store.all().filter { it.clipboardEligible }

    private suspend fun sendToAll(
        entries: List<WearExchangeEntry>,
        offer: WearServerClipboardOffer,
        source: Uri,
        granted: Network,
        onProgress: (Long, Long) -> Unit,
    ): WearServerClipboardSendReport {
        val delivered = mutableListOf<String>()
        val failed = mutableListOf<WearServerClipboardEntryFailure>()
        for (entry in entries) {
            val result = openSource(source)?.use { input ->
                client.put(entry, offer, input, granted) { sent -> onProgress(sent, offer.size) }
            } ?: WearServerClipboardPut.Failed(WearServerClipboardFailure.SOURCE_UNREADABLE)
            when (result) {
                WearServerClipboardPut.Stored -> delivered += entry.label
                is WearServerClipboardPut.Failed ->
                    failed += WearServerClipboardEntryFailure(entry.label, result.failure)
            }
        }
        Timber.i("Clipboard send: %d stored, %d failed", delivered.size, failed.size)
        return WearServerClipboardSendReport(delivered, failed)
    }

    private suspend fun openSource(source: Uri): InputStream? = handingOffCloseable { handOff ->
        withContext(Dispatchers.IO) { openTracked(source, handOff) }
    }

    private fun openTracked(source: Uri, handOff: CloseableHandOff): InputStream? =
        try {
            context.contentResolver.openInputStream(source)?.let(handOff::track)
        } catch (e: FileNotFoundException) {
            Timber.w("Clipboard send: the file is gone (%s)", e.javaClass.simpleName)
            null
        } catch (e: SecurityException) {
            Timber.w("Clipboard send: the file may not be read (%s)", e.javaClass.simpleName)
            null
        }

    private suspend fun fetchLatest(
        entries: List<WearExchangeEntry>,
        granted: Network,
        destination: suspend (WearServerClipboardValue) -> WearServerClipboardSink?,
        onProgress: (Long, Long) -> Unit,
    ): WearServerClipboardReceiveReport {
        val survey = askAll(entries, granted)
        val chosen = survey.chosen
        return when {
            chosen != null -> fetch(chosen.first, chosen.second, survey.skipped, granted, destination, onProgress)
            survey.answered -> WearServerClipboardReceiveReport.Empty(survey.skipped)
            else -> WearServerClipboardReceiveReport.Failed(survey.skipped)
        }
    }

    private suspend fun fetch(
        entry: WearExchangeEntry,
        value: WearServerClipboardValue,
        skipped: List<WearServerClipboardEntryFailure>,
        granted: Network,
        destination: suspend (WearServerClipboardValue) -> WearServerClipboardSink?,
        onProgress: (Long, Long) -> Unit,
    ): WearServerClipboardReceiveReport {
        var sink: WearServerClipboardSink? = null
        var placed = false
        try {
            val result = client.get(
                entry = entry,
                clipId = value.clipId,
                network = granted,
                prepareSink = { described -> destination(described)?.also { sink = it }?.output },
                onProgress = { received -> onProgress(received, value.size) },
            )
            val saved = (result as? WearServerClipboardGet.Received)?.let { sink?.commit() }
            placed = saved != null
            return when {
                saved != null -> WearServerClipboardReceiveReport.Received(result.value, saved, entry.label, skipped)
                result is WearServerClipboardGet.Declined -> WearServerClipboardReceiveReport.Declined(result.value)
                result is WearServerClipboardGet.Empty -> WearServerClipboardReceiveReport.Empty(skipped)
                else -> WearServerClipboardReceiveReport.Failed(skipped + failureOf(entry, result))
            }
        } finally {
            if (!placed) sink?.discard()
        }
    }

    private fun failureOf(entry: WearExchangeEntry, result: WearServerClipboardGet) = WearServerClipboardEntryFailure(
        entry.label,
        (result as? WearServerClipboardGet.Failed)?.failure ?: WearServerClipboardFailure.SAVE_FAILED,
    )

    private suspend fun askAll(entries: List<WearExchangeEntry>, granted: Network): Survey = coroutineScope {
        val answers = entries.map { entry ->
            async {
                withTimeoutOrNull(INFO_TIMEOUT_MS) { client.info(entry, granted) }
                    ?: WearServerClipboardInfo.Failed(WearServerClipboardFailure.UNANSWERED)
            }
        }.awaitAll()
        val values = answers.map { (it as? WearServerClipboardInfo.Answered)?.value }
        val skipped = answers.mapIndexedNotNull { index, answer ->
            (answer as? WearServerClipboardInfo.Failed)?.let { failed ->
                WearServerClipboardEntryFailure(entries[index].label, failed.failure)
            }
        }
        val chosenIndex = WearServerClipboardChooser.choose(values)
        Survey(
            chosen = chosenIndex?.let { index -> values[index]?.let { entries[index] to it } },
            answered = answers.any { it is WearServerClipboardInfo.Answered },
            skipped = skipped,
        )
    }

    private class Survey(
        val chosen: Pair<WearExchangeEntry, WearServerClipboardValue>?,
        val answered: Boolean,
        val skipped: List<WearServerClipboardEntryFailure>,
    )

    private companion object {
        // Contract 22 item CA: an entry that does not answer is skipped and named, never waited for.
        const val INFO_TIMEOUT_MS = 15_000L
    }
}

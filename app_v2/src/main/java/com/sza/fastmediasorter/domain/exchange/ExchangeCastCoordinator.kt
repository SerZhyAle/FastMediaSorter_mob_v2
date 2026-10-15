package com.sza.fastmediasorter.domain.exchange

import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.notification.ExchangeCastNotifier
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorParser
import com.sza.fastmediasorter.data.broadcast.exchange.ExchangeBroadcastAnnouncer
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeCastCodec
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeDirectory
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.sftp.anywhere.SftpServerTunnelManager
import com.sza.fastmediasorter.data.repository.DeviceExchangeAccountRepository
import com.sza.fastmediasorter.data.repository.ExchangeSealedContentOpener
import com.sza.fastmediasorter.domain.model.ExchangeCastAction
import com.sza.fastmediasorter.domain.model.ExchangeCastOffer
import com.sza.fastmediasorter.domain.model.ExchangeCastReceiverMachine
import com.sza.fastmediasorter.domain.model.ExchangeCastResult
import com.sza.fastmediasorter.domain.model.ExchangeCastRules
import com.sza.fastmediasorter.domain.model.ExchangeCastTarget
import com.sza.fastmediasorter.domain.model.ExchangeEndpointPair
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.streams.ImportStreamBroadcastUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cast through the exchange (contract DEVICE-EXCHANGE 7.8, S4116 phase 07), both halves, on the outgoing entry's
 * one control stream ([SftpServerTunnelManager]). As a receiver it holds that stream while streams are on and an
 * outgoing entry exists, imports every offered broadcast as a live channel (9.5), and asks through a notification
 * unless the user chose "play at once". As a sender it lists the receivers on the same stream, so opening the
 * list never displaces the broadcast's control stream (7.3), and sends one `cast` per chosen receiver. Receiving
 * follows the outgoing entry whatever its kind; sending only one that may produce (19 item AO).
 */
// Both halves of cast meet here on purpose: they share the one control stream and its single collector.
@Suppress("LongParameterList")
@Singleton
class ExchangeCastCoordinator @Inject constructor(
    private val tunnel: SftpServerTunnelManager,
    private val announcer: ExchangeBroadcastAnnouncer,
    private val accounts: DeviceExchangeAccountRepository,
    private val settings: SettingsRepository,
    private val capabilities: CapabilityAvailability,
    private val parser: BroadcastDescriptorParser,
    private val importBroadcast: ImportStreamBroadcastUseCase,
    private val notifier: ExchangeCastNotifier,
    private val sealed: ExchangeSealedContentOpener,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val started = AtomicBoolean(false)
    private val machine = ExchangeCastReceiverMachine()
    private val machineLock = Mutex()

    /**
     * A broadcast can be cast only through an outgoing entry, the one it is announced on (item AC), and never a
     * `guest` one, whose `cast` the server refuses (19 item AO).
     */
    val castAvailable: Flow<Boolean> = accounts.producer.map { it != null }.distinctUntilChanged()

    /** Every `cast-result` this device receives as a broadcaster (item R). */
    val results: Flow<ExchangeCastResult> = tunnel.controlEvents.mapNotNull(DeviceExchangeCastCodec::result)

    /** Idempotent: a second call adds no second collector. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(accounts.outgoing, settings.getSettings()) { outgoing, app ->
                outgoing != null && app.enableStreams && capabilities.isStreamsAvailable()
            }.distinctUntilChanged().collect(tunnel::setReceiving)
        }
        scope.launch(ioDispatcher) {
            tunnel.controlEvents.collect { envelope ->
                DeviceExchangeCastCodec.offer(envelope)?.let { onOffer(it) }
                DeviceExchangeCastCodec.stoppedBroadcastId(envelope)?.let { id ->
                    perform(machineLock.withLock { machine.onStopped(id) }, null)
                }
            }
        }
    }

    /** The user's answer from the notification; never throws, an offer withdrawn meanwhile sends nothing. */
    suspend fun answer(broadcastId: String, accepted: Boolean) = withContext(ioDispatcher) {
        perform(machineLock.withLock { machine.onUserAnswer(broadcastId, accepted) }, null)
    }

    /**
     * The receivers that can play the announced broadcast (amendment E), read with one `list` on the live control
     * stream; null when nothing is announced or no control stream answered in time.
     */
    suspend fun castTargets(): List<ExchangeCastTarget>? = withContext(ioDispatcher) {
        val descriptor = announcer.announcedDescriptor
        val own = accounts.producer.first()?.deviceId
        if (descriptor == null || own == null) return@withContext null
        withTimeoutOrNull(LIST_TIMEOUT_MS) { listOnControl() }
            ?.let { ExchangeCastRules.castTargets(it.devices, own, endpointPairs(descriptor)) }
    }

    // The answer arrives on a shared flow without replay, so the collector is in place before `list` is written.
    private suspend fun listOnControl(): DeviceExchangeDirectory? = coroutineScope {
        val answer = async(start = CoroutineStart.UNDISPATCHED) {
            tunnel.controlEvents.first { it.type == DeviceExchangeEnvelope.TYPE_DIRECTORY }
        }
        if (tunnel.sendControl(DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_LIST))) {
            DeviceExchangeDirectory.from(answer.await())
        } else {
            answer.cancel()
            null
        }
    }

    /** Sends one `cast` of the announced broadcast per receiver (7.8); the number actually written. */
    suspend fun cast(receiverDeviceIds: List<String>): Int = withContext(ioDispatcher) {
        val broadcastId = announcer.announcedBroadcastId ?: return@withContext 0
        receiverDeviceIds.count { tunnel.sendControl(DeviceExchangeCastCodec.cast(broadcastId, it)) }
    }

    private suspend fun onOffer(received: ExchangeCastOffer) {
        // 27 item DK: the offered record's box is opened into its endpoints before anything imports it.
        val record = accounts.outgoingEntry()?.let { sealed.openCast(it, received.record) }
            ?: received.record.copy(sealed = null)
        val offer = received.copy(record = record)
        val dto = (parser.parseDetailed(offer.record.descriptorJson) as? BroadcastDescriptorParser.ParseOutcome.Valid)
            ?.dto
        if (dto != null) importBroadcast(offer.record.descriptorJson)
        val playAtOnce = settings.getSettings().first().streamsCastPlayAtOnce
        val actions = machineLock.withLock { machine.onOffer(offer, playable = dto != null, playAtOnce = playAtOnce) }
        perform(actions, dto?.url)
    }

    private suspend fun perform(actions: List<ExchangeCastAction>, playUrl: String?) {
        actions.forEach { action ->
            when (action) {
                is ExchangeCastAction.Answer -> {
                    val sent = tunnel.sendControl(
                        DeviceExchangeCastCodec.answer(action.broadcastId, action.accepted, action.reason)
                    )
                    if (!sent) Timber.i("ExchangeCastCoordinator: cast answer not sent, no control stream")
                }
                is ExchangeCastAction.Ask -> playUrl?.let { url -> notify(action.offer, url, asking = true) }
                is ExchangeCastAction.Play -> playUrl?.let { url -> notify(action.offer, url, asking = false) }
                is ExchangeCastAction.Dismiss -> notifier.dismiss(action.broadcastId)
            }
        }
    }

    private fun notify(offer: ExchangeCastOffer, url: String, asking: Boolean) {
        val title = offer.record.title
        val from = offer.fromDeviceId
        val shown = if (asking) {
            notifier.ask(offer.broadcastId, from, title, url)
        } else {
            notifier.accepted(offer.broadcastId, from, title, url)
        }
        if (!shown) Timber.i("ExchangeCastCoordinator: the cast could not be shown")
    }

    private fun endpointPairs(descriptor: BroadcastDescriptorDto): List<ExchangeEndpointPair> =
        descriptor.getEffectiveEndpoints().map { ExchangeEndpointPair(it.mode ?: descriptor.mode, it.transport) }

    private companion object {
        const val LIST_TIMEOUT_MS = 10_000L
    }
}

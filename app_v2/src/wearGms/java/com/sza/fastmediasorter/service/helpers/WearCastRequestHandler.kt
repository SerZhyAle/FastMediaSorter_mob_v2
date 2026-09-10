package com.sza.fastmediasorter.service.helpers

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.sza.fastmediasorter.core.cast.ActiveCastControllerHolder
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.domain.model.WearCastAck
import com.sza.fastmediasorter.domain.model.WearCastOutcome
import com.sza.fastmediasorter.domain.model.WearCastRequest
import com.sza.fastmediasorter.domain.model.WearCastState
import com.sza.fastmediasorter.domain.model.WearCastStopRequest
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.domain.usecase.wear.CastFromWatchRequestUseCase
import com.sza.fastmediasorter.service.WearDataLayerPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** S2531: decodes one cast request off the watch bridge, runs it, and answers on both cast paths. */
class WearCastRequestHandler @Inject constructor(
    private val castFromWatchRequestUseCase: CastFromWatchRequestUseCase,
    private val activeCastControllerHolder: ActiveCastControllerHolder,
    private val wearableDataLayerRepository: WearableDataLayerRepository,
    private val gson: Gson,
    @ApplicationScope private val applicationScope: CoroutineScope
) {

    /**
     * The seam is asked to stop even when it reports no session: the watch's picture of the session is
     * one report old, and a stop that arrives just after the phone dropped the session should read as
     * done rather than as a refusal the user has to interpret.
     */
    fun handleStop(nodeId: String, data: ByteArray) {
        applicationScope.launch {
            val requestId = runCatching {
                gson.fromJson(data.decodeToString(), WearCastStopRequest::class.java).requestId
            }.getOrNull().orEmpty()
            val controller = activeCastControllerHolder.current()
            val outcome = if (controller == null) {
                WearCastOutcome.CAST_UNAVAILABLE
            } else {
                controller.stopCasting()
                WearCastOutcome.STOPPED
            }
            answer(nodeId, WearCastAck(requestId = requestId, outcome = outcome))
            publishState(nodeId, displayName = null)
        }
    }

    fun handle(nodeId: String, data: ByteArray) {
        Timber.d("S2531: phone received a cast request from the watch")
        applicationScope.launch {
            val request = parse(data)
            val ack = if (request == null) {
                WearCastAck(requestId = "", outcome = WearCastOutcome.UNSUPPORTED_CONTENT)
            } else {
                castFromWatchRequestUseCase(request)
            }
            answer(nodeId, ack)
            publishState(nodeId, request?.displayName?.takeIf { ack.outcome == WearCastOutcome.CASTING })
        }
    }

    /**
     * A request that will not parse still gets an answer: the watch has no other way to tell a refusal
     * from a phone that never heard it, and silence would leave a spinner running until it times out.
     */
    private fun parse(data: ByteArray): WearCastRequest? = try {
        gson.fromJson(data.decodeToString(), WearCastRequest::class.java)
    } catch (e: JsonSyntaxException) {
        Timber.w(e, "Cast from watch: request could not be read")
        null
    }

    private suspend fun answer(nodeId: String, ack: WearCastAck) {
        send(nodeId, WearDataLayerPaths.CAST_ACK, gson.toJson(ack), "acknowledgement")
    }

    /**
     * [WearCastState.deviceName] stays null because the seam publishes no receiver name; the watch
     * already treats it as optional rather than filling it with a placeholder of its own.
     */
    private suspend fun publishState(nodeId: String, displayName: String?) {
        val state = WearCastState(
            isCasting = activeCastControllerHolder.current()?.isCasting == true,
            deviceName = null,
            displayName = displayName
        )
        send(nodeId, WearDataLayerPaths.CAST_STATE, gson.toJson(state), "session state")
    }

    private suspend fun send(nodeId: String, path: String, json: String, what: String) {
        runCatching {
            wearableDataLayerRepository.sendMessage(nodeId, path, json.toByteArray(Charsets.UTF_8))
        }.onFailure { Timber.w(it, "Cast from watch: %s could not be sent", what) }
    }
}

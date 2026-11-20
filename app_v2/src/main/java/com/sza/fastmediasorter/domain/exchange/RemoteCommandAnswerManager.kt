package com.sza.fastmediasorter.domain.exchange

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.exchange.ExchangeSealing
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eOpenResult
import com.sza.fastmediasorter.data.security.e2e.E2eRandom
import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.domain.remotecontrol.DecideRemoteCommandUseCase
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandAnswer
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandLogEntry
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandRequest
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControlRepository
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControllerDirectory
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControllerMembership
import com.sza.fastmediasorter.domain.remotecontrol.RemoteDecision
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/** The sealed answer to put on the wire, and the work that waits until it is there. */
class RemoteCommandReply(val envelope: DeviceExchangeEnvelope, val afterAnswer: (suspend () -> Unit)?)

/**
 * One `command-request` frame (DEVICE-EXCHANGE 1.18 item GC) from box to sealed answer. The box is opened under
 * `command:<requestId>` of the frame it came in; the controller is the opened `from`, never the clear `fromDeviceId`,
 * which only names the sender of a box that did not open (REMOTE-CONTROL rule 4).
 */
@Singleton
class RemoteCommandAnswerManager internal constructor(
    private val decide: DecideRemoteCommandUseCase,
    private val repository: RemoteControlRepository,
    private val random: E2eRandom,
    private val now: () -> Long,
) {

    @Inject
    constructor(decide: DecideRemoteCommandUseCase, repository: RemoteControlRepository) :
        this(decide, repository, E2eRandom.secure, System::currentTimeMillis)

    /**
     * [sealing] is read anew for every check, so a controller removed from the account between two requests is
     * judged by the trust kept now. Null when the box did not open: nothing is sent and the log says `sealed-failed`.
     */
    suspend fun answer(envelope: DeviceExchangeEnvelope, sealing: suspend () -> ExchangeSealing): RemoteCommandReply? {
        val requestId = envelope.string(KEY_REQUEST_ID)?.takeIf(REQUEST_ID::matches)
        val current = sealing()
        val self = current.self
        val opened = if (requestId != null && self != null) {
            val context = E2eSealedBox.commandContext(requestId)
            E2eSealedBox.open(envelope.members.get(KEY_BOX), context, self, current::trusts) as? E2eOpenResult.Opened
        } else {
            null
        }
        if (opened == null || requestId == null || self == null) {
            repository.appendLog(sealedFailed(envelope.string(KEY_FROM_DEVICE_ID)))
            return null
        }
        val decision = decision(requestId, opened, sealing)
        val box = E2eSealedBox.seal(
            answerPayload(decision.answer),
            E2eSealedBox.commandResultContext(requestId),
            self,
            listOf(E2eRecipient(opened.from, opened.fromKey)),
            random,
        )
        val result = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_COMMAND_RESULT) {
            addProperty(KEY_TO_DEVICE_ID, opened.from)
            addProperty(KEY_REQUEST_ID, requestId)
            add(KEY_BOX, box)
        }
        return RemoteCommandReply(result, decision.afterAnswer)
    }

    private suspend fun decision(
        requestId: String,
        opened: E2eOpenResult.Opened,
        sealing: suspend () -> ExchangeSealing,
    ): RemoteDecision {
        val payload = opened.payload
        val version = payload.get(KEY_VERSION)?.takeIf(::isWholeNumber)?.asInt
        val request = readRequest(requestId, opened.from, payload)
        val directory = RemoteControllerDirectory { deviceId ->
            if (sealing().trusts(deviceId, opened.fromKey)) {
                RemoteControllerMembership.PRESENT
            } else {
                RemoteControllerMembership.ABSENT
            }
        }
        return when {
            version != null && version > PAYLOAD_VERSION -> unread(opened.from, RemoteCommandAnswer.schema())
            request == null -> unread(opened.from, RemoteCommandAnswer.badArgument())
            else -> decide(request, directory)
        }
    }

    // A payload the decider cannot read is still refused alike to every controller the switch shuts out (rule 8).
    private suspend fun unread(controllerId: String, refusal: RemoteCommandAnswer): RemoteDecision {
        val grant = if (repository.listening.first()) {
            repository.grants.first().firstOrNull { it.deviceId == controllerId }
        } else {
            null
        }
        val answer = if (grant == null) RemoteCommandAnswer.notPermitted() else refusal
        repository.appendLog(
            RemoteCommandLogEntry(
                timeMillis = now(),
                controllerName = grant?.deviceName,
                deviceId = controllerId,
                verified = true,
                verb = null,
                riskClass = null,
                status = answer.status,
                reason = answer.reason,
            ),
        )
        return RemoteDecision(answer)
    }

    private fun sealedFailed(clearFrom: String?) = RemoteCommandLogEntry(
        timeMillis = now(),
        controllerName = null,
        deviceId = clearFrom?.take(MAX_LOGGED_ID_LENGTH).orEmpty(),
        verified = false,
        verb = null,
        riskClass = null,
        status = RemoteCommandLogEntry.STATUS_SEALED_FAILED,
        reason = null,
    )

    companion object {
        const val KEY_REQUEST_ID = "requestId"
        const val KEY_BOX = "box"
        const val KEY_FROM_DEVICE_ID = "fromDeviceId"
        const val KEY_TO_DEVICE_ID = "toDeviceId"
        const val PAYLOAD_VERSION = 1

        /** REMOTE-CONTROL rule 5 and DEVICE-EXCHANGE item GD: the clear payload of a box. */
        const val MAX_PAYLOAD_BYTES = 65_536
        const val MIN_TTL_SECONDS = 10
        const val MAX_TTL_SECONDS = 600
        const val DEFAULT_TTL_SECONDS = 120

        private const val KEY_VERSION = "v"
        private const val KEY_VERB = "verb"
        private const val KEY_ARGS = "args"
        private const val KEY_ISSUED_AT = "issuedAt"
        private const val KEY_TTL_SECONDS = "ttlSeconds"
        private const val MAX_LOGGED_ID_LENGTH = 64
        private val REQUEST_ID = Regex("[A-Za-z0-9_-]{1,64}")

        /** Null for any member missing, mistyped or out of range; an unknown member is the decider's to refuse. */
        internal fun readRequest(requestId: String, controllerId: String, payload: JsonObject): RemoteCommandRequest? {
            val version = payload.get(KEY_VERSION)?.takeIf(::isWholeNumber)?.asInt
            val verb = payload.get(KEY_VERB)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            val args = payload.get(KEY_ARGS)?.let { if (it.isJsonObject) it.asJsonObject else null } ?: JsonObject()
            val ttl = payload.get(KEY_TTL_SECONDS)?.let { if (isWholeNumber(it)) it.asInt else null }
                ?: DEFAULT_TTL_SECONDS.takeIf { !payload.has(KEY_TTL_SECONDS) }
            val issuedAt = payload.get(KEY_ISSUED_AT)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString?.let(::parseInstant)
            val argsReadable = !payload.has(KEY_ARGS) || payload.get(KEY_ARGS).isJsonObject
            val window = ttl?.takeIf { it in MIN_TTL_SECONDS..MAX_TTL_SECONDS }
                ?.takeIf { version == PAYLOAD_VERSION && argsReadable }
            return if (verb != null && issuedAt != null && window != null) {
                RemoteCommandRequest(requestId, controllerId, verb, args, issuedAt, window)
            } else {
                null
            }
        }

        /** The answer of REMOTE-CONTROL rule 5; one whose `data` would pass the bound goes without it. */
        internal fun answerPayload(answer: RemoteCommandAnswer): JsonObject {
            val full = payload(answer, answer.data, truncated = false)
            return if (E2eBytes.utf8(full.toString()).size <= MAX_PAYLOAD_BYTES) {
                full
            } else {
                payload(answer, null, truncated = true)
            }
        }

        private fun payload(answer: RemoteCommandAnswer, data: JsonObject?, truncated: Boolean) = JsonObject().apply {
            addProperty(KEY_VERSION, PAYLOAD_VERSION)
            addProperty("status", answer.status)
            addProperty("exit", answer.exit)
            answer.reason?.let { addProperty("reason", it) }
            addProperty("message", answer.message)
            data?.let { add("data", it) }
            addProperty("truncated", truncated)
        }

        private fun isWholeNumber(value: JsonElement): Boolean =
            value.isJsonPrimitive && value.asJsonPrimitive.isNumber &&
                value.asDouble.let { it == Math.floor(it) && it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble() }

        private fun parseInstant(text: String): Long? = try {
            Instant.parse(text).toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }
}

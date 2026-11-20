package com.sza.fastmediasorter.domain.remotecontrol

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

/** The answer to send, and the work that waits until it is on the wire. */
data class RemoteDecision(
    val answer: RemoteCommandAnswer,
    val replayed: Boolean = false,
    val afterAnswer: (suspend () -> Unit)? = null,
)

/**
 * Decides one opened request on this device (REMOTE-CONTROL rules 8 to 13). Requests are handled one at a time
 * in arrival order, so a request queued behind a consent prompt is judged for time when its turn comes.
 * The controller is [RemoteCommandRequest.fromDeviceId] as read from the opened box, never the frame's clear id.
 */
@Singleton
class DecideRemoteCommandUseCase internal constructor(
    private val repository: RemoteControlRepository,
    private val handlers: Set<RemoteVerbHandler>,
    private val prompter: RemoteConsentPrompter,
    private val now: () -> Long,
) {

    @Inject
    constructor(
        repository: RemoteControlRepository,
        handlers: Set<@JvmSuppressWildcards RemoteVerbHandler>,
        prompter: RemoteConsentPrompter,
    ) : this(repository, handlers, prompter, System::currentTimeMillis)

    private val turn = Mutex()

    suspend operator fun invoke(request: RemoteCommandRequest, directory: RemoteControllerDirectory): RemoteDecision =
        turn.withLock { decide(request, directory) }

    private suspend fun decide(request: RemoteCommandRequest, directory: RemoteControllerDirectory): RemoteDecision {
        val grant = currentGrant(request.fromDeviceId)
        val handler = handlers.firstOrNull { it.spec.name == request.verb }
        val refusal = refusal(request, grant, handler, directory)
        val decision = if (refusal != null || grant == null || handler == null) {
            RemoteDecision(refusal ?: RemoteCommandAnswer.notPermitted())
        } else {
            stored(request) ?: answer(request, grant, handler)
        }
        if (!decision.replayed) {
            repository.recordAnswered(request.fromDeviceId, request.requestId, decision.answer, keepUntil(request))
        }
        repository.appendLog(logEntry(request, grant, handler, decision.answer))
        return decision
    }

    /** Null while the switch is off, so an off target refuses every controller alike (rule 8). */
    private suspend fun currentGrant(deviceId: String): RemoteControllerGrant? =
        if (repository.listening.first()) repository.grants.first().firstOrNull { it.deviceId == deviceId } else null

    private suspend fun refusal(
        request: RemoteCommandRequest,
        grant: RemoteControllerGrant?,
        handler: RemoteVerbHandler?,
        directory: RemoteControllerDirectory,
    ): RemoteCommandAnswer? = when {
        grant == null -> RemoteCommandAnswer.notPermitted()
        else -> membershipRefusal(grant, directory) ?: verbRefusal(request, grant, handler)
    }

    /** A controller revoked from the account loses its grants here, without the person touching the target (R10). */
    private suspend fun membershipRefusal(
        grant: RemoteControllerGrant,
        directory: RemoteControllerDirectory,
    ): RemoteCommandAnswer? = when (directory.membership(grant.deviceId)) {
        RemoteControllerMembership.PRESENT -> null
        RemoteControllerMembership.ABSENT -> {
            repository.revoke(grant.deviceId)
            RemoteCommandAnswer.notPermitted()
        }
        RemoteControllerMembership.UNKNOWN -> RemoteCommandAnswer.busy()
    }

    private fun verbRefusal(
        request: RemoteCommandRequest,
        grant: RemoteControllerGrant,
        handler: RemoteVerbHandler?,
    ): RemoteCommandAnswer? = when {
        handler == null -> RemoteCommandAnswer.unknownVerb()
        grant.modeFor(handler.spec.riskClass) == null -> RemoteCommandAnswer.notPermitted()
        isExpired(request) -> RemoteCommandAnswer.expired()
        else -> null
    }

    private suspend fun stored(request: RemoteCommandRequest): RemoteDecision? =
        repository.findAnswered(request.fromDeviceId, request.requestId)?.let { RemoteDecision(it, replayed = true) }

    private suspend fun answer(
        request: RemoteCommandRequest,
        grant: RemoteControllerGrant,
        handler: RemoteVerbHandler,
    ): RemoteDecision {
        val asks = grant.modeFor(handler.spec.riskClass) != RemoteConsentMode.ALWAYS ||
            handler.spec.confirm == RemoteConfirm.DEVICE
        return when {
            !argumentsValid(handler.spec.args, request.args) -> RemoteDecision(RemoteCommandAnswer.badArgument())
            asks && !prompter.ask(grant.deviceName, handler, request.args) ->
                RemoteDecision(RemoteCommandAnswer.declined())
            asks -> execute(handler, request.args).also { prompter.ran() }
            else -> execute(handler, request.args)
        }
    }

    // A verb that throws must still be answered and logged; the queue serves the next request either way.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun execute(handler: RemoteVerbHandler, args: JsonObject): RemoteDecision {
        val answer = try {
            handler.run(args)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.w(error, "Remote verb %s failed", handler.spec.name)
            RemoteCommandAnswer(
                RemoteCommandAnswer.STATUS_FAILED,
                RemoteCommandAnswer.EXIT_FAILED,
                message = "The verb failed on the target.",
            )
        }
        return RemoteDecision(answer, afterAnswer = { handler.afterAnswer(answer) })
    }

    private fun isExpired(request: RemoteCommandRequest): Boolean {
        val clock = now()
        return keepUntil(request) < clock || request.issuedAtMillis > clock + CLOCK_SKEW_MS
    }

    /** The ring keeps an answer exactly as long as the time check would still let the box through (rule 12). */
    private fun keepUntil(request: RemoteCommandRequest): Long =
        request.issuedAtMillis + request.ttlSeconds * MILLIS_PER_SECOND + CLOCK_SKEW_MS

    private fun logEntry(
        request: RemoteCommandRequest,
        grant: RemoteControllerGrant?,
        handler: RemoteVerbHandler?,
        answer: RemoteCommandAnswer,
    ) = RemoteCommandLogEntry(
        timeMillis = now(),
        controllerName = grant?.deviceName,
        deviceId = request.fromDeviceId,
        verified = true,
        verb = request.verb.take(MAX_LOGGED_VERB_LENGTH),
        riskClass = handler?.spec?.riskClass,
        status = answer.status,
        reason = answer.reason,
    )

    companion object {
        const val CLOCK_SKEW_MS = 300_000L
        private const val MILLIS_PER_SECOND = 1_000L
        private const val MAX_LOGGED_VERB_LENGTH = 64

        /** Unknown members, a wrong kind, an enum value outside its list and a missing required member all fail. */
        internal fun argumentsValid(declared: List<RemoteVerbArg>, args: JsonObject): Boolean {
            val byName = declared.associateBy { it.name }
            val known = args.keySet().all { name -> byName[name]?.let { accepts(it, args.get(name)) } == true }
            return known && declared.filter { it.required }.all { args.has(it.name) }
        }

        private fun accepts(arg: RemoteVerbArg, value: JsonElement): Boolean {
            val primitive = value.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return false
            return when (arg.kind) {
                RemoteArgKind.STRING -> primitive.isString
                RemoteArgKind.INT -> primitive.isNumber && primitive.asDouble.let { it == floor(it) }
                RemoteArgKind.BOOL -> primitive.isBoolean
                RemoteArgKind.ENUM -> primitive.isString && primitive.asString in arg.values
            }
        }
    }
}

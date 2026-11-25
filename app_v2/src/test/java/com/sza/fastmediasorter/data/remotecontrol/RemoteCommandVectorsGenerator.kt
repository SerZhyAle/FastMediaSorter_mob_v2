package com.sza.fastmediasorter.data.remotecontrol

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeEnvelope
import com.sza.fastmediasorter.data.remote.exchange.ExchangeSealing
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eOpenResult
import com.sza.fastmediasorter.data.security.e2e.E2eRandom
import com.sza.fastmediasorter.data.security.e2e.E2eSealedBox
import com.sza.fastmediasorter.data.security.e2e.E2eVectorsGenerator
import com.sza.fastmediasorter.data.security.e2e.FixedRandom
import com.sza.fastmediasorter.domain.exchange.RemoteCommandAnswerManager
import com.sza.fastmediasorter.domain.remotecontrol.DecideRemoteCommandUseCase
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandAnswer
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandLogEntry
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConfirm
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConsentMode
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConsentPrompter
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControlRepository
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControllerGrant
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbHandler
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.time.Instant

/** The device-only state of a target, in memory: the ring keeps each answer with the moment it may be dropped. */
internal class InMemoryRemoteControlRepository : RemoteControlRepository {
    override val listening = MutableStateFlow(false)
    override val grants = MutableStateFlow<List<RemoteControllerGrant>>(emptyList())
    override val recentLog = MutableStateFlow<List<RemoteCommandLogEntry>>(emptyList())
    val ring = linkedMapOf<String, Pair<RemoteCommandAnswer, Long>>()

    override suspend fun setListening(on: Boolean) {
        listening.value = on
    }

    override suspend fun grant(deviceId: String, deviceName: String) {
        grants.value = grants.value + RemoteControllerGrant(deviceId, deviceName)
    }

    override suspend fun revoke(deviceId: String) {
        grants.value = grants.value.filterNot { it.deviceId == deviceId }
    }

    override suspend fun setClassMode(deviceId: String, riskClass: RemoteRiskClass, mode: RemoteConsentMode?) {
        grants.value = grants.value.map { grant ->
            when {
                grant.deviceId != deviceId -> grant
                mode == null -> grant.copy(classes = grant.classes - riskClass)
                else -> grant.copy(classes = grant.classes + (riskClass to mode))
            }
        }
    }

    override suspend fun findAnswered(controllerId: String, requestId: String) = ring["$controllerId/$requestId"]?.first

    override suspend fun recordAnswered(
        controllerId: String,
        requestId: String,
        answer: RemoteCommandAnswer,
        keepUntilMillis: Long,
    ) {
        ring["$controllerId/$requestId"] = answer to keepUntilMillis
    }

    override suspend fun appendLog(entry: RemoteCommandLogEntry) {
        recentLog.value = recentLog.value + entry
    }
}

/** A verb whose answer never depends on the build, so a transcript stays the same from one version to the next. */
internal class FixedRemoteVerb(name: String, riskClass: RemoteRiskClass) : RemoteVerbHandler {
    var runs = 0
    override val spec = RemoteVerbSpec(name, "Fixed answer for the transcripts.", riskClass, RemoteConfirm.GRANT)

    override fun describe(context: Context, args: JsonObject): String = spec.summary

    override suspend fun run(args: JsonObject): RemoteCommandAnswer {
        runs++
        return RemoteCommandAnswer.done(JsonObject().apply { addProperty("value", spec.name) })
    }
}

/** A target with fixed keys and a fixed clock: `phone` answers, `tablet` asks (the devices of `e2e-v1.json`). */
internal class RemoteCommandBench(random: E2eRandom = FixedRandom("remote-command")) {
    val clockMs = CLOCK_MS
    val repository = InMemoryRemoteControlRepository()
    val safeVerb = FixedRemoteVerb("safe-verb", RemoteRiskClass.SAFE)
    val readVerb = FixedRemoteVerb("read-verb", RemoteRiskClass.READS_USER_DATA)
    private val declining = object : RemoteConsentPrompter {
        override suspend fun ask(controllerName: String, handler: RemoteVerbHandler, args: JsonObject) = false
    }
    private val decide = DecideRemoteCommandUseCase(repository, setOf(safeVerb, readVerb), declining) { clockMs }
    val manager = RemoteCommandAnswerManager(decide, repository, random) { clockMs }
    val target = E2eVectorsGenerator.self(TARGET)
    val controller = E2eVectorsGenerator.self(CONTROLLER)
    private val sealRandom = random

    /** The target trusts the controller's vector key; [trustController] false models a key that changed. */
    var trustController = true

    val sealing: suspend () -> ExchangeSealing = {
        val trusted = if (trustController) {
            mapOf(controller.deviceId to E2eBytes.b64url(E2eVectorsGenerator.deviceKey(CONTROLLER).publicKey))
        } else {
            emptyMap()
        }
        ExchangeSealing(target, emptyList(), emptyMap(), trusted)
    }

    fun listenAndGrant() = runBlocking {
        repository.setListening(true)
        repository.grant(controller.deviceId, "Tablet")
    }

    fun payload(
        verb: String = safeVerb.spec.name,
        issuedAtMs: Long = clockMs,
        args: JsonObject = JsonObject(),
        version: Int = 1,
    ) = JsonObject().apply {
        addProperty("v", version)
        addProperty("verb", verb)
        add("args", args)
        addProperty("issuedAt", Instant.ofEpochMilli(issuedAtMs).toString())
        addProperty("ttlSeconds", TTL_SECONDS)
    }

    /** The frame as the server forwards it: the controller's request with `fromDeviceId` added. */
    fun frame(
        payload: JsonObject,
        requestId: String = REQUEST_ID,
        sealedFor: String = requestId,
        clearFrom: String = controller.deviceId,
    ) = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_COMMAND_REQUEST) {
        addProperty(RemoteCommandAnswerManager.KEY_REQUEST_ID, requestId)
        addProperty(RemoteCommandAnswerManager.KEY_FROM_DEVICE_ID, clearFrom)
        add(
            RemoteCommandAnswerManager.KEY_BOX,
            E2eSealedBox.seal(
                payload,
                E2eSealedBox.commandContext(sealedFor),
                controller,
                listOf(E2eVectorsGenerator.recipient(TARGET)),
                sealRandom,
            ),
        )
    }

    fun answer(frame: DeviceExchangeEnvelope) = runBlocking { manager.answer(frame, sealing) }

    /** The controller's view of an answer frame: the opened payload, or null when the box is not for it. */
    fun open(result: DeviceExchangeEnvelope, requestId: String = REQUEST_ID): JsonObject? {
        val opened = E2eSealedBox.open(
            result.members.get(RemoteCommandAnswerManager.KEY_BOX),
            E2eSealedBox.commandResultContext(requestId),
            controller,
        ) { from, key -> from == target.deviceId && key.contentEquals(E2eVectorsGenerator.deviceKey(TARGET).publicKey) }
        return (opened as? E2eOpenResult.Opened)?.payload
    }

    companion object {
        const val TARGET = "phone"
        const val CONTROLLER = "tablet"
        const val REQUEST_ID = "req-0001"
        const val TTL_SECONDS = 120
        const val CLOCK_MS = 1_800_000_000_000L
    }
}

/**
 * Producer of the frame and ring transcripts owed by the first target (DEVICE-EXCHANGE 1.18 item GF, REMOTE-CONTROL
 * section 4): every random input is [FixedRandom] and the clock is fixed, so a run reproduces the file byte for byte.
 */
object RemoteCommandVectorsGenerator {

    private const val SECONDS_AHEAD_OUT_OF_WINDOW = 301L
    private const val MILLIS_PER_SECOND = 1_000L

    fun generate(): JsonObject = JsonObject().apply {
        addProperty("contract", "DEVICE-EXCHANGE 1.18 items GC-GF, REMOTE-CONTROL 0.4")
        addProperty("producer", "FastMediaSorter Android, ticket S4701")
        addProperty("note", "Keys are the vector devices of e2e-v1.json; the target clock is clockMs.")
        addProperty("clockMs", RemoteCommandBench.CLOCK_MS)
        add("cases", JsonArray().apply { cases().forEach(::add) })
    }

    private fun cases(): List<JsonObject> = listOf(
        case("R1", "switch off: not-permitted, logged") { bench -> listOf(bench.frame(bench.payload())) },
        case("R2", "granted safe runs, an ungranted class is not-permitted", grant = true) { bench ->
            listOf(
                bench.frame(bench.payload(), requestId = "req-r2-safe"),
                bench.frame(bench.payload(verb = bench.readVerb.spec.name), requestId = "req-r2-read"),
            )
        },
        case("R4", "a verb in no manifest is unknown-verb", grant = true) { bench ->
            listOf(bench.frame(bench.payload(verb = "format-disk")))
        },
        case("R5", "an unknown argument is bad-argument and nothing runs", grant = true) { bench ->
            listOf(bench.frame(bench.payload(args = JsonObject().apply { addProperty("extra", 1) })))
        },
        case("R6", "the same box twice is run once and answered twice alike", grant = true) { bench ->
            bench.frame(bench.payload()).let { listOf(it, it) }
        },
        case("R7", "outside the window and ahead of it are expired", grant = true) { bench ->
            val windowMs = (RemoteCommandBench.TTL_SECONDS + SECONDS_AHEAD_OUT_OF_WINDOW) * MILLIS_PER_SECOND
            listOf(
                bench.frame(bench.payload(issuedAtMs = bench.clockMs - windowMs), requestId = "req-r7-old"),
                bench.frame(
                    bench.payload(issuedAtMs = bench.clockMs + SECONDS_AHEAD_OUT_OF_WINDOW * MILLIS_PER_SECOND),
                    requestId = "req-r7-ahead",
                ),
            )
        },
        case("R8", "a moved box and a changed key are never run; a wrong clear id is answered to the box", true) {
            listOf(
                it.frame(it.payload(), requestId = "req-r8-moved", sealedFor = "req-r8-other"),
                it.frame(it.payload(), requestId = "req-r8-clear", clearFrom = "dev-stranger"),
            )
        },
    )

    private fun case(
        id: String,
        title: String,
        grant: Boolean = false,
        frames: (RemoteCommandBench) -> List<DeviceExchangeEnvelope>,
    ): JsonObject {
        val bench = RemoteCommandBench(FixedRandom("remote-command $id"))
        if (grant) bench.listenAndGrant()
        val exchanges = JsonArray()
        frames(bench).forEach { frame ->
            val reply = bench.answer(frame)
            val requestId = frame.string(RemoteCommandAnswerManager.KEY_REQUEST_ID).orEmpty()
            exchanges.add(
                JsonObject().apply {
                    add("request", envelopeJson(frame))
                    add("result", reply?.envelope?.let(::envelopeJson))
                    add("resultClear", reply?.envelope?.let { bench.open(it, requestId) })
                },
            )
        }
        return JsonObject().apply {
            addProperty("case", id)
            addProperty("title", title)
            add("exchanges", exchanges)
            add("ring", ring(bench.repository))
            add("log", log(bench.repository))
            addProperty("verbRuns", bench.safeVerb.runs + bench.readVerb.runs)
        }
    }

    private fun envelopeJson(envelope: DeviceExchangeEnvelope): JsonElement =
        JsonParser.parseString(envelope.encode())

    private fun ring(repository: InMemoryRemoteControlRepository) = JsonArray().apply {
        repository.ring.forEach { (key, value) ->
            add(
                JsonObject().apply {
                    addProperty("key", key)
                    addProperty("status", value.first.status)
                    value.first.reason?.let { addProperty("reason", it) }
                    addProperty("keepUntilMs", value.second)
                },
            )
        }
    }

    private fun log(repository: InMemoryRemoteControlRepository) = JsonArray().apply {
        repository.recentLog.value.forEach { entry ->
            add(
                JsonObject().apply {
                    addProperty("deviceId", entry.deviceId)
                    addProperty("verified", entry.verified)
                    entry.verb?.let { addProperty("verb", it) }
                    addProperty("status", entry.status)
                    entry.reason?.let { addProperty("reason", it) }
                },
            )
        }
    }
}

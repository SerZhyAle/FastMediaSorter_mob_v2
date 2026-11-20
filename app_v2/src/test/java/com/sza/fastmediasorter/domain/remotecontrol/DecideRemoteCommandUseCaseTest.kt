package com.sza.fastmediasorter.domain.remotecontrol

import android.content.Context
import com.google.gson.JsonObject
import com.sza.fastmediasorter.domain.remotecontrol.verbs.CommandsRemoteVerb
import com.sza.fastmediasorter.domain.remotecontrol.verbs.StatusRemoteVerb
import com.sza.fastmediasorter.domain.remotecontrol.verbs.VersionRemoteVerb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.inject.Provider

/** Cases R1-R7, R10 and R12 of REMOTE-CONTROL section 4 against in-memory fakes. */
class DecideRemoteCommandUseCaseTest {

    private val clock = 1_800_000_000_000L
    private val repository = FakeRepository()
    private val prompter = FakePrompter()
    private val membership = mutableMapOf(CONTROLLER to RemoteControllerMembership.PRESENT)
    private val directory = RemoteControllerDirectory { membership[it] ?: RemoteControllerMembership.ABSENT }

    private val safeVerb = CountingVerb(RemoteVerbSpec("safe-verb", "s", RemoteRiskClass.SAFE, RemoteConfirm.GRANT))
    private val readVerb = CountingVerb(
        RemoteVerbSpec("read-verb", "r", RemoteRiskClass.READS_USER_DATA, RemoteConfirm.GRANT),
    )
    private val deviceVerb = CountingVerb(
        RemoteVerbSpec(
            "device-verb",
            "d",
            RemoteRiskClass.SAFE,
            RemoteConfirm.DEVICE,
            args = listOf(RemoteVerbArg("mode", RemoteArgKind.ENUM, "m", values = listOf("audio", "video"))),
        ),
    )
    private lateinit var handlers: Set<RemoteVerbHandler>
    private val decider by lazy {
        handlers = setOf(
            safeVerb,
            readVerb,
            deviceVerb,
            StatusRemoteVerb(repository),
            VersionRemoteVerb(),
            CommandsRemoteVerb(Provider { handlers }),
        )
        DecideRemoteCommandUseCase(repository, handlers, prompter) { clock }
    }

    private fun request(
        verb: String,
        id: String = "req-1",
        issuedAt: Long = clock,
        args: JsonObject = JsonObject(),
        from: String = CONTROLLER,
    ) = RemoteCommandRequest(id, from, verb, args, issuedAt, TTL_SECONDS)

    private fun decide(request: RemoteCommandRequest) = runBlocking { decider(request, directory) }

    private fun listenWithController() {
        repository.listening.value = true
        repository.grants.value = listOf(RemoteControllerGrant(CONTROLLER, "Laptop"))
    }

    @Test
    fun `R1 switch off answers not-permitted and logs the request`() {
        repository.grants.value = listOf(RemoteControllerGrant(CONTROLLER, "Laptop"))
        val answer = decide(request("status")).answer
        assertEquals(RemoteCommandAnswer.REASON_NOT_PERMITTED, answer.reason)
        assertEquals(RemoteCommandAnswer.EXIT_PERMISSION, answer.exit)
        assertEquals(1, repository.log.size)
        assertEquals("status", repository.log.single().verb)
    }

    @Test
    fun `R2 untouched controller is refused, ticked defaults run safe and refuse reads`() {
        repository.listening.value = true
        assertEquals(RemoteCommandAnswer.REASON_NOT_PERMITTED, decide(request("status")).answer.reason)

        listenWithController()
        val status = decide(request("status", id = "req-2")).answer
        assertEquals(RemoteCommandAnswer.STATUS_DONE, status.status)
        assertEquals(true, status.data?.get("listening")?.asBoolean)
        assertEquals(0, prompter.asked)
        assertEquals(RemoteCommandAnswer.REASON_NOT_PERMITTED, decide(request("read-verb", id = "req-3")).answer.reason)

        runBlocking {
            repository.setClassMode(
                CONTROLLER,
                RemoteRiskClass.READS_USER_DATA,
                RemoteControllerGrant.defaultMode(RemoteRiskClass.READS_USER_DATA),
            )
        }
        prompter.accept = true
        assertEquals(RemoteCommandAnswer.STATUS_DONE, decide(request("read-verb", id = "req-4")).answer.status)
        assertEquals(1, prompter.asked)
        assertEquals("Laptop", prompter.lastController)
    }

    @Test
    fun `R3 a device-confirmed verb asks under always and is declined unanswered`() {
        listenWithController()
        prompter.accept = false
        val answer = decide(request("device-verb")).answer
        assertEquals(RemoteCommandAnswer.REASON_DECLINED, answer.reason)
        assertEquals(RemoteCommandAnswer.EXIT_STOPPED, answer.exit)
        assertEquals(1, prompter.asked)
        assertEquals(0, deviceVerb.runs)
    }

    @Test
    fun `R4 an unknown verb is unknown-verb and commands lists only remote handlers`() {
        listenWithController()
        assertEquals(RemoteCommandAnswer.REASON_UNKNOWN_VERB, decide(request("delete-all")).answer.reason)

        val manifest = decide(request("commands", id = "req-2")).answer.data
        val names = manifest?.getAsJsonArray("commands")?.map { it.asJsonObject.get("name").asString }.orEmpty()
        assertEquals(handlers.map { it.spec.name }.sorted(), names)
        assertTrue(manifest?.getAsJsonArray("commands")?.all { it.asJsonObject.has("remote") } == true)
    }

    @Test
    fun `R5 an unknown argument member or a value outside the enum runs nothing`() {
        listenWithController()
        prompter.accept = true
        val unknown = JsonObject().apply { addProperty("path", "/sdcard") }
        val unknownAnswer = decide(request("safe-verb", args = unknown)).answer
        assertEquals(RemoteCommandAnswer.REASON_BAD_ARGUMENT, unknownAnswer.reason)
        val badValue = JsonObject().apply { addProperty("mode", "screen") }
        assertEquals(
            RemoteCommandAnswer.REASON_BAD_ARGUMENT,
            decide(request("device-verb", id = "req-2", args = badValue)).answer.reason,
        )
        assertEquals(0, safeVerb.runs)
        assertEquals(0, deviceVerb.runs)
        assertEquals(0, prompter.asked)
    }

    @Test
    fun `R6 the same box twice runs once and answers twice alike`() {
        listenWithController()
        val ahead = request("safe-verb", issuedAt = clock + DecideRemoteCommandUseCase.CLOCK_SKEW_MS - 1)
        val first = decide(ahead)
        val second = decide(ahead)
        assertEquals(1, safeVerb.runs)
        assertEquals(first.answer, second.answer)
        assertTrue(second.replayed)
        assertTrue(repository.keepUntil.single() >= ahead.issuedAtMillis + TTL_SECONDS * 1_000L)
        assertEquals(2, repository.log.size)
    }

    @Test
    fun `R7 a request older than its window or too far ahead is expired`() {
        listenWithController()
        val old = clock - TTL_SECONDS * 1_000L - DecideRemoteCommandUseCase.CLOCK_SKEW_MS - 1
        assertEquals(RemoteCommandAnswer.REASON_EXPIRED, decide(request("safe-verb", issuedAt = old)).answer.reason)
        val future = clock + DecideRemoteCommandUseCase.CLOCK_SKEW_MS + 1
        val answer = decide(request("safe-verb", id = "req-2", issuedAt = future)).answer
        assertEquals(RemoteCommandAnswer.REASON_EXPIRED, answer.reason)
        assertEquals(RemoteCommandAnswer.EXIT_NOT_PROVEN, answer.exit)
        assertEquals(0, safeVerb.runs)
    }

    @Test
    fun `R10 a controller gone from the account loses its grants at its next request`() {
        listenWithController()
        membership[CONTROLLER] = RemoteControllerMembership.ABSENT
        assertEquals(RemoteCommandAnswer.REASON_NOT_PERMITTED, decide(request("status")).answer.reason)
        assertTrue(repository.grants.value.isEmpty())
    }

    @Test
    fun `an unreadable directory refuses for now and keeps the grants`() {
        listenWithController()
        membership[CONTROLLER] = RemoteControllerMembership.UNKNOWN
        assertEquals(RemoteCommandAnswer.REASON_BUSY, decide(request("status")).answer.reason)
        assertEquals(1, repository.grants.value.size)
    }

    @Test
    fun `R12 no base verb changes the switch or the grants`() {
        listenWithController()
        listOf("commands", "version", "status").forEachIndexed { index, verb ->
            assertEquals(RemoteCommandAnswer.STATUS_DONE, decide(request(verb, id = "req-$index")).answer.status)
        }
        assertTrue(repository.listening.value)
        assertEquals(listOf(RemoteControllerGrant(CONTROLLER, "Laptop")), repository.grants.value)
        assertFalse(handlers.any { it.spec.args.any { arg -> arg.name == "path" } })
    }

    private class CountingVerb(override val spec: RemoteVerbSpec) : RemoteVerbHandler {
        var runs = 0
        override fun describe(context: Context, args: JsonObject): String = spec.summary

        override suspend fun run(args: JsonObject): RemoteCommandAnswer {
            runs++
            return RemoteCommandAnswer.done()
        }
    }

    private class FakePrompter : RemoteConsentPrompter {
        var accept = false
        var asked = 0
        var lastController: String? = null
        override suspend fun ask(controllerName: String, handler: RemoteVerbHandler, args: JsonObject): Boolean {
            asked++
            lastController = controllerName
            return accept
        }
    }

    private class FakeRepository : RemoteControlRepository {
        override val listening = MutableStateFlow(false)
        override val grants = MutableStateFlow<List<RemoteControllerGrant>>(emptyList())
        override val recentLog: Flow<List<RemoteCommandLogEntry>> = MutableStateFlow(emptyList())
        val log = mutableListOf<RemoteCommandLogEntry>()
        val keepUntil = mutableListOf<Long>()
        private val ring = mutableMapOf<String, RemoteCommandAnswer>()

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
            grants.value = grants.value.map {
                if (it.deviceId != deviceId) it else it.copy(classes = it.classes + (riskClass to requireNotNull(mode)))
            }
        }

        override suspend fun findAnswered(controllerId: String, requestId: String) = ring["$controllerId/$requestId"]

        override suspend fun recordAnswered(
            controllerId: String,
            requestId: String,
            answer: RemoteCommandAnswer,
            keepUntilMillis: Long,
        ) {
            ring["$controllerId/$requestId"] = answer
            keepUntil += keepUntilMillis
        }

        override suspend fun appendLog(entry: RemoteCommandLogEntry) {
            log += entry
        }
    }

    private companion object {
        const val CONTROLLER = "dev-laptop"
        const val TTL_SECONDS = 30
    }
}

package com.sza.fastmediasorter.data.remote.exchange

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnection
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeConnector
import com.sza.fastmediasorter.data.remote.sftp.anywhere.ExchangeTrust
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import com.sza.fastmediasorter.data.security.e2e.E2eRecipient
import com.sza.fastmediasorter.data.security.e2e.E2eSelf
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Replays the four DEVICE-EXCHANGE server frame transcript corpora:
 * - server-frames-v2.json
 * - fmsw-224/frames-v2.json
 * - fmsw-229/frames-v2.json
 * - fmsw-234/frames-v2.json
 *
 * S4365 / DEVICE-EXCHANGE 1.3 amendment D6: each affected product replays applicable old and new
 * server cases, verifying exact wire framing, legacy schema refusal and clipboard raw boundaries.
 */
class ServerTranscriptFrameReplayTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `all four frame corpora and provenance files match their pinned SHA-256 hashes`() {
        val pinned = listOf(
            "device-exchange/server-frames-v2.json" to
                "AFA1A68273670F98C5DD615261759D1853A4CAFD9086254E23ACBDC7D0AEA107",
            "device-exchange/fmsw-224/frames-v2.json" to
                "F5C6AE0DB7DD47454467184C5DF3378104E35BFE09609ACFD37F703E833541D8",
            "device-exchange/fmsw-224/PROVENANCE.txt" to
                "E140FE9AEB8A180DA06EF7712D74EB09F41CAF4B675999B70C79A10E359090A4",
            "device-exchange/fmsw-229/frames-v2.json" to
                "578ED7BA8A04D6158F2BE142FF0C4324B859A5AF46048681E73DC31825042691",
            "device-exchange/fmsw-229/PROVENANCE.txt" to
                "724DDA12B45F444C3387B84AC4B11A834480621C1EAE5DC3FDFB92E6C72DB9D8",
            "device-exchange/fmsw-234/frames-v2.json" to
                "7DDF9FB472B825EFCC77D54405681CDBF0F2E2B485E0B58BC7782BDDFCE5D2FD",
            "device-exchange/fmsw-234/PROVENANCE.txt" to
                "3D84D79F00FBFD7406B5A92D4246008B47C0C3BDA2897C4A7EE56D415BD0A4F9",
        )

        for ((resourcePath, expectedHash) in pinned) {
            val bytes = requireNotNull(javaClass.classLoader?.getResource(resourcePath)) {
                "missing resource $resourcePath"
            }.readBytes()
            val actualHash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02X".format(it) }
            assertEquals("hash mismatch for $resourcePath", expectedHash, actualHash)
        }
    }

    @Test
    fun `replays all wire frames across the four corpora`() {
        val corpora = listOf(
            "device-exchange/server-frames-v2.json",
            "device-exchange/fmsw-224/frames-v2.json",
            "device-exchange/fmsw-229/frames-v2.json",
            "device-exchange/fmsw-234/frames-v2.json",
        )
        val counts = corpora.map(::replayCorpus)
        val totalFrames = counts.sumOf { it.first }
        val totalRaw = counts.sumOf { it.second }

        assertTrue("must have replayed multiple frames", totalFrames > 500)
        assertTrue("must have verified raw-byte steps", totalRaw > 0)
    }

    private fun replayCorpus(resource: String): Pair<Int, Int> {
        val text = requireNotNull(javaClass.classLoader?.getResource(resource)) {
            "missing corpus $resource"
        }.readText()
        val root = JsonParser.parseString(text).asJsonObject
        var frames = 0
        var raw = 0
        for (t in root.getAsJsonArray("transcripts")) {
            val transcript = t.asJsonObject
            val name = transcript.get("name").asString
            for (s in transcript.getAsJsonArray("steps")) {
                val step = s.asJsonObject
                if (isIgnoredStep(step)) continue
                if (step.has("hex") && !step.has("json")) {
                    raw++
                    assertRawStepRefused(step, name)
                } else if (step.has("hex") && step.has("json")) {
                    frames++
                    replayFrameStep(step, name)
                }
            }
        }
        return frames to raw
    }

    private fun isIgnoredStep(step: JsonObject): Boolean =
        (step.has("closed") && step.get("closed").asBoolean) || step.has("http") || step.has("chunk")

    private fun assertRawStepRefused(step: JsonObject, transcriptName: String) {
        val rawBytes = E2eBytes.fromHex(step.get("hex").asString)
        assertThrows(
            "raw bytes in $transcriptName must not decode as envelope",
            DeviceExchangeWireException::class.java,
        ) {
            DeviceExchangeFrameCodec.read(
                ByteArrayInputStream(rawBytes),
                maxBody = DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED,
            )
        }
    }

    private fun replayFrameStep(step: JsonObject, transcriptName: String) {
        val hexBytes = E2eBytes.fromHex(step.get("hex").asString)
        val expectedJson = JsonParser.parseString(step.get("json").asString).asJsonObject
        val schema = expectedJson.get("schemaVersion")?.asInt ?: 0
        val direction = step.get("direction").asString

        if (schema == DeviceExchangeEnvelope.SCHEMA_VERSION_LEGACY) {
            assertThrows(
                "legacy schemaVersion 1 in $transcriptName must throw legacy exception",
                DeviceExchangeLegacyPeerException::class.java,
            ) {
                DeviceExchangeFrameCodec.read(
                    ByteArrayInputStream(hexBytes),
                    maxBody = DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED,
                )
            }
        } else {
            val decoded = DeviceExchangeFrameCodec.read(
                ByteArrayInputStream(hexBytes),
                maxBody = DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED,
            )
            assertNotNull("decoded frame in $transcriptName must not be null", decoded)
            assertEquals("type mismatch in $transcriptName", expectedJson.get("type").asString, decoded?.type)

            if (direction == "client->server") {
                val reEncoded = JsonParser.parseString(checkNotNull(decoded).encode()).asJsonObject
                assertEquals("re-encoded client frame must match recorded json", expectedJson, reEncoded)
            }
        }
    }

    @Test
    fun `recorded sealed get bytes are decrypted to expected clear text`() = runBlocking {
        val (valueEnvelope, rawBytes) = loadSealedClipboardRecordedBytes()
        val keySource = buildTabletKeySource()
        val server = ServerSocket(0)
        try {
            val (client, entry) = buildClipboardClient(server.localPort)
            val queue = ArrayBlockingQueue<Boolean>(1)
            thread(isDaemon = true) {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()
                    checkNotNull(DeviceExchangeFrameCodec.read(input))
                    sendWelcome(output)
                    handleDirectoryList(input, output)
                    val getReq = DeviceExchangeFrameCodec.read(
                        input,
                        DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED,
                    )
                    if (getReq?.type == DeviceExchangeEnvelope.TYPE_CLIPBOARD_GET) {
                        DeviceExchangeFrameCodec.write(
                            output,
                            valueEnvelope,
                            DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED,
                        )
                        output.write(rawBytes)
                        output.flush()
                        queue.put(true)
                    }
                }
            }
            val targetFile = folder.newFile("clipboard_received.txt")
            val answer = client.get(entry, null, targetFile, keySource) {}
            assertTrue(checkNotNull(queue.poll(3, TimeUnit.SECONDS)))
            assertTrue("result should be Received", answer.result is ExchangeClipboardGetResult.Received)
            assertEquals(
                "decrypted text must match fmsw-234 sealed-clipboard",
                "A sealed clipboard text: only the devices of the account read it.",
                targetFile.readText(),
            )
        } finally {
            server.close()
        }
    }

    @Test
    fun `legacy clear clipboard is refused as Unsealed`() = runBlocking {
        val keySource = buildTabletKeySource()
        val server = ServerSocket(0)
        try {
            val (client, entry) = buildClipboardClient(server.localPort)
            val queue = ArrayBlockingQueue<Boolean>(1)
            thread(isDaemon = true) {
                server.accept().use { socket ->
                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()
                    checkNotNull(DeviceExchangeFrameCodec.read(input))
                    sendWelcome(output)
                    handleDirectoryList(input, output)
                    val getReq = DeviceExchangeFrameCodec.read(
                        input,
                        DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED,
                    )
                    if (getReq?.type == DeviceExchangeEnvelope.TYPE_CLIPBOARD_GET) {
                        val clearEnvelope = DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_CLIPBOARD_VALUE) {
                            addProperty("clipId", "legacy-clip")
                            addProperty("kind", "text")
                            addProperty("size", 12)
                            addProperty("sha256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                            addProperty("storedAt", "2026-10-14T10:00:00Z")
                        }
                        DeviceExchangeFrameCodec.write(
                            output,
                            clearEnvelope,
                            DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED,
                        )
                        queue.put(true)
                    }
                }
            }
            val clearAnswer = client.get(entry, null, folder.newFile("clear.txt"), keySource) {}
            assertTrue(checkNotNull(queue.poll(3, TimeUnit.SECONDS)))
            assertEquals(ExchangeClipboardGetResult.Unsealed, clearAnswer.result)
        } finally {
            server.close()
        }
    }

    private fun loadSealedClipboardRecordedBytes(): Pair<DeviceExchangeEnvelope, ByteArray> {
        val text234 = requireNotNull(
            javaClass.classLoader?.getResource("device-exchange/fmsw-234/frames-v2.json"),
        ).readText()
        val root234 = JsonParser.parseString(text234).asJsonObject
        val transcript = root234.getAsJsonArray("transcripts").map { it.asJsonObject }
            .first { it.get("name").asString == "sealed-clipboard" }
        val steps = transcript.getAsJsonArray("steps").map { it.asJsonObject }
        val valueStep = steps.first { it.has("json") && it.get("json").asString.contains("clipboard-value") }
        val rawStep = steps.first { it.has("hex") && !it.has("json") }
        val valueEnvelope = DeviceExchangeEnvelope.decode(valueStep.get("json").asString)
        val rawBytes = E2eBytes.fromHex(rawStep.get("hex").asString)
        return valueEnvelope to rawBytes
    }

    private fun buildTabletKeySource(): ExchangeClipboardKeySource {
        val e2eText = requireNotNull(javaClass.classLoader?.getResource("device-exchange/e2e-v1.json")).readText()
        val devices = JsonParser.parseString(e2eText).asJsonObject.getAsJsonArray("devices").map { it.asJsonObject }
        val phoneKem = devices.first { it.get("name").asString == "phone" }
            .getAsJsonObject("e2eKey").get("kem").asString
        val tabletKey = E2eDeviceKey.fromPrivate(
            E2eBytes.fromHex(devices.first { it.get("name").asString == "tablet" }.get("privateKey").asString),
        )
        val phoneId = "RQn8NLAhmFNNqWqpCIhNeA"
        val tabletId = "r2TuNtu53QEYWle9anKdsA"
        val tabletSelf = E2eSelf(tabletId, tabletKey)
        return ExchangeClipboardKeySource {
            ExchangeClipboardKeys(
                self = tabletSelf,
                recipients = listOf(E2eRecipient(phoneId, E2eDeviceKey.decodeKem(phoneKem) ?: ByteArray(0))),
            ) { from, key ->
                from == phoneId && (E2eDeviceKey.decodeKem(phoneKem)?.contentEquals(key) == true)
            }
        }
    }

    private fun buildClipboardClient(port: Int): Pair<DeviceExchangeClipboardClient, DeviceExchangeAccountEntry> {
        val tokens = object : DeviceExchangeTokenStore {
            override suspend fun token(entry: DeviceExchangeAccountEntry): String = "tok3n"
            override suspend fun save(entry: DeviceExchangeAccountEntry, token: String): Boolean = true
            override suspend fun forget(entry: DeviceExchangeAccountEntry) = Unit
        }
        val connector = object : ExchangeConnector {
            override fun connect(host: String, port: Int, trust: ExchangeTrust): ExchangeConnection =
                ExchangeConnection(Socket(host, port), "SHA256:fake")
        }
        val entry = DeviceExchangeAccountEntry("127.0.0.1", port, "alice", "r2TuNtu53QEYWle9anKdsA", null)
        val accounts = DeviceExchangeAccountClient(connector, tokens, InMemoryE2eKeyStore(), RecordingPinRecorder())
        return DeviceExchangeClipboardClient(accounts) to entry
    }

    private fun sendWelcome(output: java.io.OutputStream) {
        DeviceExchangeFrameCodec.write(
            output,
            DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_WELCOME) {
                val feats = JsonArray().apply {
                    add("clipboard")
                    add("sealed")
                }
                add("features", feats)
            },
        )
    }

    private fun handleDirectoryList(input: java.io.InputStream, output: java.io.OutputStream) {
        val listReq = DeviceExchangeFrameCodec.read(input, DeviceExchangeFrameCodec.MAX_FRAME_BODY_AUTHENTICATED)
        if (listReq?.type == DeviceExchangeEnvelope.TYPE_LIST) {
            DeviceExchangeFrameCodec.write(
                output,
                DeviceExchangeEnvelope.of(DeviceExchangeEnvelope.TYPE_DIRECTORY) {
                    addProperty("revision", 1)
                },
            )
        }
    }
}

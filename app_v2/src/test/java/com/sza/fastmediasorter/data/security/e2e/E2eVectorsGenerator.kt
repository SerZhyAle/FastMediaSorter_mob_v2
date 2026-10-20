package com.sza.fastmediasorter.data.security.e2e

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sza.fastmediasorter.data.security.e2e.E2eBytes.b64url
import com.sza.fastmediasorter.data.security.e2e.E2eBytes.hex
import java.io.ByteArrayOutputStream

/**
 * Producer of the contract artifact `vectors/e2e-v1.json` (DEVICE-EXCHANGE section 27, item DR's
 * conformance paragraph). Every random input comes from [FixedRandom], so the file is reproduced byte
 * for byte by [E2eVectorsTest]; a second product replays it before its first sealed send.
 */
object E2eVectorsGenerator {

    const val BROADCAST_ID = "b7c1d2e3-0000-4000-8000-00000000e2e1"
    const val RESOURCE_ID = "r7c1d2e3-0000-4000-8000-00000000e2e1"
    const val GRANT_ID = "g7c1d2e3-0000-4000-8000-00000000e2e1"
    const val CLEAR_PATTERN_MODULUS = 251
    private const val SEGMENT = E2eSegmentStream.SEGMENT_SIZE
    private const val ONE_SEGMENT_SIZE = 1000
    private const val TWO_SEGMENT_TAIL = 100
    private const val HEADER_FLAG = 0x02
    private const val JOINABLE_FLAG = 0x01
    private const val PLAIN_FLAG = 0x00
    private const val FIRST_EPOCH = 1L
    private const val SECOND_EPOCH = 2L
    private const val MEDIA_SAMPLE_LENGTH = 48

    val deviceNames = listOf("phone", "tablet", "watch", "guest", "stranger")

    fun deviceId(name: String): String = "dev-$name"

    fun deviceKey(name: String): E2eP256.KeyPair =
        E2eHpke.deriveKeyPair(E2eBytes.ascii("fmsx-e2e-v1 vector device $name"))

    fun self(name: String): E2eSelf = E2eSelf(deviceId(name), E2eDeviceKey.fromPrivate(deviceKey(name).privateKey))

    fun recipient(name: String): E2eRecipient = E2eRecipient(deviceId(name), deviceKey(name).publicKey)

    fun clearPattern(size: Int): ByteArray = ByteArray(size) { (it % CLEAR_PATTERN_MODULUS).toByte() }

    fun generate(): JsonObject = JsonObject().apply {
        addProperty("contract", "DEVICE-EXCHANGE")
        addProperty("section", "27")
        addProperty("version", "0.29")
        addProperty("producer", "FastMediaSorter Android, ticket S4240")
        addProperty(
            "note",
            "privateKey members exist only so a replaying product can open the boxes; " +
                "a real key never leaves its device.",
        )
        add("devices", devices())
        add("boxes", boxes())
        add("failing", failing())
        add("clipboard", clipboard())
        add("relay", relay())
    }

    fun render(root: JsonObject): String =
        GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n"

    private fun devices(): JsonArray = JsonArray().apply {
        deviceNames.forEach { name ->
            val pair = deviceKey(name)
            add(
                JsonObject().apply {
                    addProperty("name", name)
                    addProperty("deviceId", deviceId(name))
                    addProperty("privateKey", hex(pair.privateKey))
                    add(
                        "e2eKey",
                        JsonObject().apply {
                            addProperty("v", E2eDeviceKey.VERSION)
                            addProperty("kem", b64url(pair.publicKey))
                        },
                    )
                    addProperty("checkCode", E2eDeviceKey.checkCode(pair.publicKey))
                },
            )
        }
    }

    private class BoxCase(val name: String, val context: String, val payload: JsonObject, val to: List<String>)

    private fun boxCases(): List<BoxCase> = listOf(
        BoxCase(
            "access",
            E2eSealedBox.accessContext(RESOURCE_ID),
            JsonObject().apply {
                addProperty("type", "sftp")
                addProperty("username", "fms")
                addProperty("password", "vector-password")
                addProperty("hostKeyFingerprint", "SHA256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                addProperty("root", "/")
            },
            listOf("tablet", "watch"),
        ),
        BoxCase(
            "grant",
            E2eSealedBox.grantContext(GRANT_ID),
            JsonObject().apply {
                add(
                    "access",
                    JsonObject().apply {
                        addProperty("type", "sftp")
                        addProperty("username", "grant-login")
                        addProperty("password", "grant-password")
                    },
                )
            },
            listOf("guest"),
        ),
        BoxCase(
            "broadcast",
            E2eSealedBox.broadcastContext(BROADCAST_ID),
            JsonObject().apply {
                addProperty("relayKey", b64url(groupKey("own", FIRST_EPOCH)))
                addProperty("tunnelPin", "SHA256:BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
                add(
                    "credentials",
                    JsonObject().apply {
                        addProperty("username", "listener")
                        addProperty("password", "broadcast-password")
                    },
                )
            },
            listOf("tablet", "watch"),
        ),
        BoxCase(
            "clipboard",
            E2eSealedBox.CLIPBOARD_CONTEXT,
            clipboardMeta("text", "text/plain", null, ONE_SEGMENT_SIZE),
            listOf("tablet", "watch"),
        ),
    )

    private fun seal(case: BoxCase, seed: String): Pair<JsonObject, ByteArray> =
        E2eSealedBox.sealWithKey(
            case.payload,
            case.context,
            self("phone"),
            case.to.map(::recipient),
            FixedRandom(seed),
        )

    private fun boxes(): JsonArray = JsonArray().apply {
        boxCases().forEach { case ->
            val (box, _) = seal(case, "box ${case.name}")
            add(
                JsonObject().apply {
                    addProperty("name", case.name)
                    addProperty("context", case.context)
                    addProperty("from", "phone")
                    add("to", JsonArray().apply { case.to.forEach(::add) })
                    add("payload", case.payload)
                    add("box", box)
                },
            )
        }
    }

    private fun failing(): JsonArray {
        val access = boxCases().first { it.name == "access" }
        val (box, _) = seal(access, "box access")
        val tampered = box.deepCopy().apply {
            val ct = E2eBytes.fromB64url(get("ct").asString)
            ct[0] = (ct[0].toInt() xor 1).toByte()
            addProperty("ct", b64url(ct))
        }
        return JsonArray().apply {
            add(
                failure(
                    "wrong-to",
                    "access",
                    access.context,
                    "stranger",
                    "phone",
                    null,
                    E2eOpenFailure.NO_ENTRY,
                ),
            )
            add(
                failure(
                    "untrusted-fromKey",
                    "access",
                    access.context,
                    "tablet",
                    "stranger",
                    null,
                    E2eOpenFailure.UNTRUSTED_SENDER,
                ),
            )
            add(
                failure(
                    "moved-context",
                    "access",
                    E2eSealedBox.accessContext("another-resource"),
                    "tablet",
                    "phone",
                    null,
                    E2eOpenFailure.BAD_TAG,
                ),
            )
            add(
                failure(
                    "bad-tag",
                    "access",
                    access.context,
                    "tablet",
                    "phone",
                    tampered,
                    E2eOpenFailure.BAD_TAG,
                ),
            )
        }
    }

    @Suppress("LongParameterList") // One row of the failing table, each argument a column of it.
    private fun failure(
        name: String,
        box: String,
        context: String,
        reader: String,
        trustedFromKeyOf: String,
        replacedBox: JsonObject?,
        expected: E2eOpenFailure,
    ): JsonObject = JsonObject().apply {
        addProperty("name", name)
        addProperty("box", box)
        addProperty("openAs", reader)
        addProperty("context", context)
        addProperty("readerTrustsForPhone", trustedFromKeyOf)
        replacedBox?.let { add("replacedBox", it) }
        addProperty("expected", expected.name)
    }

    private fun clipboardMeta(kind: String, mimeType: String, name: String?, size: Int): JsonObject {
        val meta = JsonObject()
        meta.addProperty("kind", kind)
        meta.addProperty("mimeType", mimeType)
        name?.let { meta.addProperty("name", it) }
        meta.addProperty("size", size)
        meta.addProperty("sha256", hex(E2eBytes.sha256(clearPattern(size))))
        return meta
    }

    private fun clipboard(): JsonArray = JsonArray().apply {
        listOf(
            "one-segment" to ONE_SEGMENT_SIZE,
            "two-segments" to SEGMENT + TWO_SEGMENT_TAIL,
            "exact-multiple" to 2 * SEGMENT,
        ).forEach { (name, size) -> add(clipboardCase(name, size)) }
    }

    private fun clipboardCase(name: String, size: Int): JsonObject {
        val meta = clipboardMeta("file", "application/octet-stream", "vector-$name.bin", size)
        val case = BoxCase(name, E2eSealedBox.CLIPBOARD_CONTEXT, meta, listOf("tablet"))
        val (box, contentKey) = seal(case, "clipboard $name")
        val valueKey = E2eSegmentStream.valueKey(contentKey)
        val sealed = ByteArrayOutputStream().also {
            E2eSegmentStream.seal(clearPattern(size).inputStream(), it, valueKey)
        }.toByteArray()
        val firstTagStart = minOf(SEGMENT, size)
        return JsonObject().apply {
            addProperty("name", name)
            addProperty("clear", "byte i of the value is i mod $CLEAR_PATTERN_MODULUS")
            add("box", box)
            addProperty("contentKey", hex(contentKey))
            addProperty("valueKey", hex(valueKey))
            addProperty("clearSize", size)
            addProperty("clearSha256", hex(E2eBytes.sha256(clearPattern(size))))
            addProperty("segments", E2eSegmentStream.segmentCount(size.toLong()))
            addProperty("sealedSize", sealed.size)
            addProperty("sealedSha256", hex(E2eBytes.sha256(sealed)))
            addProperty("firstSegmentTag", hex(sealed.copyOfRange(firstTagStart, firstTagStart + E2eAead.TAG_LENGTH)))
            add(
                "failing",
                JsonArray().apply {
                    add("truncated: the sealed bytes without their last segment")
                    if (E2eSegmentStream.segmentCount(size.toLong()) > 1) {
                        add("reordered: the first two sealed segments swapped")
                    }
                },
            )
        }
    }

    fun groupKey(name: String, epoch: Long): ByteArray =
        E2eBytes.sha256(E2eBytes.ascii("fmsx-e2e-v1 vector group $name $epoch"))

    fun epochKey(epoch: Long): ByteArray = E2eBytes.sha256(E2eBytes.ascii("fmsx-e2e-v1 vector epoch $epoch"))

    fun media(label: String): ByteArray {
        val digest = E2eBytes.sha256(E2eBytes.ascii(label))
        return E2eBytes.concat(digest, digest).copyOf(MEDIA_SAMPLE_LENGTH)
    }

    private fun relay(): JsonObject {
        val random = FixedRandom("relay")
        val firstGroups = listOf(
            groupKey("own", FIRST_EPOCH),
            groupKey("guest", FIRST_EPOCH),
            groupKey("link", FIRST_EPOCH),
        )
        val secondGroups = listOf(groupKey("own", SECOND_EPOCH), groupKey("link", FIRST_EPOCH))
        val chunks = JsonArray()
        listOf(FIRST_EPOCH to firstGroups, SECOND_EPOCH to secondGroups).forEach { (epoch, groups) ->
            val key = epochKey(epoch)
            val ring = E2eRelayRecords.encodeKeyRing(BROADCAST_ID, epoch, "video/mp2t", key, groups, random)
            val header = E2eRelayRecords.encodeMedia(
                BROADCAST_ID,
                epoch,
                key,
                HEADER_FLAG,
                media("pat-pmt $epoch"),
                random,
            )
            chunks.add(chunk(HEADER_FLAG, E2eBytes.concat(ring, header), epoch))
            listOf(JOINABLE_FLAG, PLAIN_FLAG).forEach { flag ->
                val body = E2eRelayRecords.encodeMedia(
                    BROADCAST_ID,
                    epoch,
                    key,
                    flag,
                    media("media $epoch $flag"),
                    random,
                )
                chunks.add(chunk(flag, body, epoch))
            }
        }
        val epochKeys = JsonObject()
        listOf(FIRST_EPOCH, SECOND_EPOCH).forEach { epochKeys.addProperty("$it", hex(epochKey(it))) }
        return JsonObject().apply {
            addProperty("broadcastId", BROADCAST_ID)
            addProperty("contentType", E2eRelayRecords.CONTENT_TYPE)
            addProperty("mediaType", "video/mp2t")
            add("groupKeys", groupKeysJson())
            add("epochKeys", epochKeys)
            addProperty("epochChange", "epoch 2 drops the guest group key and replaces the own one")
            add("chunks", chunks)
        }
    }

    private fun groupKeysJson(): JsonObject = JsonObject().apply {
        addProperty("own-1", hex(groupKey("own", FIRST_EPOCH)))
        addProperty("guest-1", hex(groupKey("guest", FIRST_EPOCH)))
        addProperty("link-1", hex(groupKey("link", FIRST_EPOCH)))
        addProperty("own-2", hex(groupKey("own", SECOND_EPOCH)))
    }

    private fun chunk(flag: Int, payload: ByteArray, epoch: Long): JsonObject = JsonObject().apply {
        addProperty("flag", flag)
        addProperty("epoch", epoch)
        addProperty("payload", b64url(payload))
        add(
            "clearMedia",
            JsonArray().apply {
                E2eRelayRecords.split(payload).orEmpty()
                    .filter { it.type == E2eRelayRecords.TYPE_MEDIA }
                    .forEach { record ->
                        val opened = E2eRelayRecords.openMedia(BROADCAST_ID, record.body, flag) { epochKey(it) }
                        add(hex((opened as E2eRelayMedia.Clear).bytes))
                    }
            },
        )
    }
}

/** A reproducible byte stream: SHA-256 of the seed and a counter, block after block. */
class FixedRandom(seed: String) : E2eRandom {
    private val seedBytes = E2eBytes.ascii("fmsx-e2e-v1 vector random $seed")
    private var counter = 0L
    private var buffer = ByteArray(0)

    override fun next(length: Int): ByteArray {
        while (buffer.size < length) {
            buffer = E2eBytes.concat(buffer, E2eBytes.sha256(seedBytes, E2eBytes.u32be(counter++)))
        }
        val out = buffer.copyOf(length)
        buffer = buffer.copyOfRange(length, buffer.size)
        return out
    }
}

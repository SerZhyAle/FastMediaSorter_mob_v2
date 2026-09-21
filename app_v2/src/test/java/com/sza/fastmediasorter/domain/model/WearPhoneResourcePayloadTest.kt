package com.sza.fastmediasorter.domain.model

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * S1697: these payloads are the whole vocabulary the watch has for phone-owned resources, so their
 * serialised shape is a cross-module wire format - the phone writes it, a separately compiled Wear
 * binary reads it. A dropped field name or a leaked secret does not fail a build; it either silently
 * empties the watch browser or ships phone credentials over the Data Layer.
 */
class WearPhoneResourcePayloadTest {

    private val gson = Gson()
    private val envelopeCodec = WearEventEnvelopeCodec()

    /**
     * Anything whose name suggests a credential, a phone-local path, or raw exception text.
     * Plain "token" is absent on purpose: the opaque browse tokens are the contract's own vocabulary.
     */
    private val forbiddenFieldPatterns = listOf(
        "password", "credential", "secret", "authtoken", "username",
        "path", "absolute", "uri", "stacktrace", "exception", "throwable", "cause", "message"
    )

    @Test
    fun `request survives a serialisation round trip`() {
        val request = WearPhoneResourceRequest(
            requestId = "req-1",
            kind = WearPhoneResourceRequestKind.CHILDREN,
            parentToken = "folder-7",
            pageToken = "page-2"
        )

        val restored = gson.fromJson(gson.toJson(request), WearPhoneResourceRequest::class.java)

        assertEquals(request, restored)
        assertEquals(WEAR_PHONE_RESOURCE_SCHEMA_VERSION, restored.schemaVersion)
    }

    @Test
    fun `a media type survives the round trip and its absence stays absent`() {
        val filtered = WearPhoneResourceRequest(
            requestId = "req-2",
            kind = WearPhoneResourceRequestKind.CHILDREN,
            parentToken = "folder-7",
            mediaType = "documents"
        )

        val restored = gson.fromJson(gson.toJson(filtered), WearPhoneResourceRequest::class.java)

        assertEquals("documents", restored.mediaType)

        val unfiltered = WearPhoneResourceRequest(
            requestId = "req-3",
            kind = WearPhoneResourceRequestKind.ROOT
        )

        assertNull(
            "an absent media type must stay absent - it is what the unfiltered entrance sends",
            gson.fromJson(gson.toJson(unfiltered), WearPhoneResourceRequest::class.java).mediaType
        )
    }

    @Test
    fun `page survives a serialisation round trip`() {
        val page = WearPhoneResourcePage(
            requestId = "req-2",
            status = WearPhoneResourceResponseStatus.OK,
            items = listOf(
                WearPhoneResourceItem(token = "t-1", name = "Camera", isDirectory = true),
                WearPhoneResourceItem(
                    token = "t-2",
                    name = "clip.mp4",
                    mimeType = "video/mp4",
                    sizeBytes = 2048L,
                    isDirectory = false
                )
            ),
            nextPageToken = "page-3"
        )

        val restored = gson.fromJson(gson.toJson(page), WearPhoneResourcePage::class.java)

        assertEquals(page, restored)
    }

    @Test
    fun `full page thumbnail budget remains below data item limit when envelope encoded`() {
        val page = WearPhoneResourcePage(
            requestId = "req-thumbnail-budget",
            status = WearPhoneResourceResponseStatus.OK,
            items = listOf(
                WearPhoneResourceItem(
                    token = "t-thumbnail",
                    name = "thumbnail.jpg",
                    isDirectory = false,
                    thumbnailBase64 = "x".repeat(24 * 1024)
                )
            )
        )
        val encoded = envelopeCodec.encode(
            WearEventEnvelope(
                eventType = "phone_resource_page",
                sentAt = 1_700_000_000_000L,
                data = gson.toJson(page).toByteArray(Charsets.UTF_8)
            )
        )

        assertTrue(encoded.size <= MAX_DATA_ITEM_BYTES)
    }

    @Test
    fun `response correlation keeps the request id of its request`() {
        val request = WearPhoneResourceRequest(requestId = "req-3", kind = WearPhoneResourceRequestKind.ROOT)
        val page = WearPhoneResourcePage(
            requestId = request.requestId,
            status = WearPhoneResourceResponseStatus.EMPTY
        )

        val restored = gson.fromJson(gson.toJson(page), WearPhoneResourcePage::class.java)

        assertEquals(request.requestId, restored.requestId)
        assertEquals(request.schemaVersion, restored.schemaVersion)
    }

    @Test
    fun `failure mapping carries a status instead of exception text`() {
        val failures = listOf(
            WearPhoneResourceResponseStatus.PHONE_UNAVAILABLE,
            WearPhoneResourceResponseStatus.SOURCE_UNAVAILABLE,
            WearPhoneResourceResponseStatus.ACCESS_DENIED,
            WearPhoneResourceResponseStatus.UNSUPPORTED_MEDIA,
            WearPhoneResourceResponseStatus.TRANSFER_REJECTED,
            WearPhoneResourceResponseStatus.NOT_FOUND
        )

        failures.forEach { status ->
            val json = JsonParser
                .parseString(gson.toJson(WearPhoneResourcePage(requestId = "req-4", status = status)))
                .asJsonObject

            assertEquals(status.name, json["status"].asString)
            // S2885: `items` is nullable now, and Gson omits a null field, so a failure page carries
            // no `items` key at all rather than an empty array. Both readings mean the same thing to
            // the watch - it reads the field through `.orEmpty()` - so the assertion accepts either
            // and refuses only a page that actually names some content.
            val items = json["items"]
            assertTrue(
                "failure page must carry no items",
                items == null || items.isJsonNull || items.asJsonArray.isEmpty
            )
        }
    }

    @Test
    fun `delete request carries the token and the length the watch measured`() {
        val request = WearPhoneResourceDeleteRequest(
            requestId = "req-del-1",
            token = "7:DCIM/Camera/clip.mp4",
            expectedSizeBytes = 2_048L
        )

        val json = JsonParser.parseString(gson.toJson(request)).asJsonObject
        val restored = gson.fromJson(gson.toJson(request), WearPhoneResourceDeleteRequest::class.java)

        assertEquals(setOf("schemaVersion", "requestId", "token", "expectedSizeBytes"), json.keySet())
        assertEquals(request, restored)
        assertEquals(WEAR_PHONE_RESOURCE_SCHEMA_VERSION, restored.schemaVersion)
    }

    @Test
    fun `every delete outcome travels under its own member name`() {
        WearPhoneResourceDeleteOutcome.entries.forEach { outcome ->
            val ack = WearPhoneResourceDeleteAck(requestId = "req-del-2", outcome = outcome)
            val json = JsonParser.parseString(gson.toJson(ack)).asJsonObject

            // The watch has no @SerializedName of its own and resolves the member by name, so the
            // serialised token is the whole contract - a renamed member reads as null there, and a
            // null answer is "not confirmed", which silently withholds a move that did happen.
            assertEquals(outcome.name, json["outcome"].asString)
            assertEquals(
                ack,
                gson.fromJson(gson.toJson(ack), WearPhoneResourceDeleteAck::class.java)
            )
        }
    }

    @Test
    fun `schema version is the one both modules declare`() {
        // S3359: the delete pair joined this contract, so the stamp moved on both sides in one change.
        assertEquals(7, WEAR_PHONE_RESOURCE_SCHEMA_VERSION)
    }

    @Test
    fun `forbidden data cannot be serialised by any payload type`() {
        val types = listOf(
            WearPhoneResourceRequest::class.java,
            WearPhoneResourceItem::class.java,
            WearPhoneResourcePage::class.java,
            WearPhoneResourceDeleteRequest::class.java,
            WearPhoneResourceDeleteAck::class.java
        )

        types.forEach { type ->
            // Static fields are skipped for the same reason Gson skips them: they are not instance state.
            val leaking = type.declaredFields
                .filter { !it.isSynthetic && !Modifier.isStatic(it.modifiers) }
                .map { it.name }
                .filter { name -> forbiddenFieldPatterns.any { name.lowercase().contains(it) } }

            assertEquals("${type.simpleName} exposes forbidden fields", emptyList<String>(), leaking)
        }
    }

    @Test
    fun `item metadata serialises only the documented keys`() {
        val json = JsonParser
            .parseString(
                gson.toJson(
                    WearPhoneResourceItem(
                        token = "t-9",
                        name = "shot.jpg",
                        mimeType = "image/jpeg",
                        sizeBytes = 64L,
                        isDirectory = false
                    )
                )
            )
            .asJsonObject

        assertEquals(setOf("token", "name", "mimeType", "sizeBytes", "isDirectory"), json.keySet())
    }

    private companion object {
        const val MAX_DATA_ITEM_BYTES = 100 * 1024
    }
}

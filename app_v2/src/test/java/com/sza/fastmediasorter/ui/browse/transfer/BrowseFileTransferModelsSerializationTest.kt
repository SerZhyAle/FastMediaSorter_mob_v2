package com.sza.fastmediasorter.ui.browse.transfer

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.sza.fastmediasorter.domain.model.FileOperationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/**
 * S0957: the browse-transfer models are Gson field-reflection persisted to disk (active_request.json /
 * terminal_event.json) and read back by a worker that can outlive the process across a Play auto-update
 * (new R8 mapping). Every persisted field must carry an explicit @SerializedName so the wire format is
 * pinned and R8-independent.
 *
 * A plain "expected keys are present" assertion is worthless here: on a non-obfuscated JVM the field name
 * already equals the wire name, so it would pass even if the annotation were removed. This guard reflects
 * the annotation directly - it fails if a persisted field loses @SerializedName or a new field is added
 * without one, which is exactly the regression that reintroduces the R8 rename desync.
 *
 * S1638 covers the other half. Pinning the wire format cannot stop a blob written before the pin from
 * parsing: Gson has no no-arg constructor to call, so it allocates the instance and leaves unmatched keys
 * null, including under properties Kotlin declares non-null. The intactness checks assert that such an
 * object is recognised at the read boundary rather than travelling on to throw somewhere else.
 *
 * S1671 covers the third: the terminal payload used to carry the operation as a String filled by Enum.name
 * and read by Enum.valueOf, both of which ignore @SerializedName, so the pin never reached that value. The
 * field is the enum now, and the tests below hold the wire format still across the change - the literal a
 * previous release left on disk is the one Gson writes and the one it accepts back.
 */
class BrowseFileTransferModelsSerializationTest {

    private val persistedTypes = listOf(
        BrowseFileTransferRequest::class.java,
        BrowseFileTransferSource::class.java,
        BrowseFileTransferTerminalPayload::class.java,
    )

    @Test
    fun `every persisted field pins its wire name via SerializedName`() {
        persistedTypes.forEach { type ->
            // Instance backing fields only: skip the Compose-compiler `$stable` static field and any synthetics.
            type.declaredFields
                .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
                .forEach { field ->
                    val ann = requireNotNull(field.getAnnotation(SerializedName::class.java)) {
                        "${type.simpleName}.${field.name} must carry @SerializedName (R8 renames unpinned fields)"
                    }
                    assertEquals(
                        "${type.simpleName}.${field.name} @SerializedName value must equal the field name",
                        field.name,
                        ann.value,
                    )
                }
        }
    }

    @Test
    fun `request round-trips through Gson preserving values`() {
        val gson = Gson()
        val request = BrowseFileTransferRequest(
            operationType = FileOperationType.COPY,
            sourceResourceId = 42L,
            sourceResourceName = "Camera",
            sourceCredentialsId = "cred-1",
            currentBrowsePath = "/dcim",
            destinationPath = "/backup",
            destinationName = "Backup",
            overwriteFiles = true,
            sources = listOf(BrowseFileTransferSource("/dcim/a.jpg", "a.jpg", 100L, false)),
        )
        assertEquals(request, gson.fromJson(gson.toJson(request), BrowseFileTransferRequest::class.java))
    }

    @Test
    fun `delete request round-trips with explicit soft delete policy`() {
        val gson = Gson()
        val request = BrowseFileTransferRequest(
            operationType = FileOperationType.DELETE,
            sourceResourceId = 42L,
            sourceResourceName = "Remote",
            sourceCredentialsId = "cred-1",
            currentBrowsePath = "sftp://host/media",
            destinationPath = "sftp://host/media",
            destinationName = "Remote",
            overwriteFiles = false,
            sources = listOf(BrowseFileTransferSource("sftp://host/media/a", "a", 100L, false)),
            softDelete = true,
        )

        assertEquals(request, gson.fromJson(gson.toJson(request), BrowseFileTransferRequest::class.java))
    }

    @Test
    fun `legacy request without soft delete decodes to hard delete`() {
        val legacyJson = """
            {"operationType":"DELETE","sourceResourceId":42,"sourceResourceName":"Remote",
             "sourceCredentialsId":null,"currentBrowsePath":null,"destinationPath":"/remote",
             "destinationName":"Remote","overwriteFiles":false,"sources":[]}
        """.trimIndent()

        val decoded = Gson().fromJson(legacyJson, BrowseFileTransferRequest::class.java)

        assertEquals(false, decoded.softDelete)
    }

    @Test
    fun `staged request round-trips preserving source ownership`() {
        val gson = Gson()
        val request = BrowseFileTransferRequest(
            operationType = FileOperationType.COPY,
            sourceResourceId = -1L,
            sourceResourceName = "Shared",
            sourceCredentialsId = null,
            currentBrowsePath = null,
            destinationPath = "cloud://GOOGLE_DRIVE/root",
            destinationName = "Drive",
            overwriteFiles = false,
            sources = listOf(BrowseFileTransferSource("/cache/temp_share/a.jpg", "a.jpg", 10L, false)),
            sourcesOwnedByOperation = true,
            stagingDirectoryPath = "/cache/temp_share",
        )

        assertEquals(request, gson.fromJson(gson.toJson(request), BrowseFileTransferRequest::class.java))
    }

    @Test
    fun `legacy request without source ownership decodes as caller-owned`() {
        val legacyJson = """
            {"operationType":"COPY","sourceResourceId":42,"sourceResourceName":"Camera",
             "sourceCredentialsId":null,"currentBrowsePath":null,"destinationPath":"/backup",
             "destinationName":"Backup","overwriteFiles":false,"sources":[]}
        """.trimIndent()

        val decoded = Gson().fromJson(legacyJson, BrowseFileTransferRequest::class.java)

        assertEquals(false, decoded.sourcesOwnedByOperation)
        assertEquals(null, decoded.stagingDirectoryPath)
    }

    @Test
    fun `terminal payload round-trips through Gson preserving values`() {
        val gson = Gson()
        val payload = BrowseFileTransferTerminalPayload(
            kind = "success",
            workId = "w-1",
            operationType = FileOperationType.COPY,
            processedCount = 3,
            undoSourceFiles = listOf("/a", "/b"),
            undoDestinationFolder = "/dst",
            undoCopiedFiles = listOf("/dst/a"),
        )
        assertEquals(payload, gson.fromJson(gson.toJson(payload), BrowseFileTransferTerminalPayload::class.java))
    }

    // S1638: the annotation guard above pins the wire format, but it cannot stop a blob written by a build
    // with a different mapping from parsing into a half-built object. These assert the second half of the
    // contract - that such an object is recognised as incomplete instead of reaching the worker.

    @Test
    fun `request parsed from an unrelated json is not intact`() {
        val renamedByR8 = """{"a":"COPY","b":42,"c":"Camera","f":"/backup","g":"Backup","i":[]}"""

        val decoded = Gson().fromJson(renamedByR8, BrowseFileTransferRequest::class.java)

        assertFalse(decoded.isStructurallyIntact())
    }

    @Test
    fun `request missing its sources list is not intact`() {
        val truncated = """
            {"operationType":"COPY","sourceResourceId":42,"sourceResourceName":"Camera",
             "destinationPath":"/backup","destinationName":"Backup","overwriteFiles":false}
        """.trimIndent()

        val decoded = Gson().fromJson(truncated, BrowseFileTransferRequest::class.java)

        assertFalse(decoded.isStructurallyIntact())
    }

    @Test
    fun `request whose source entry lost its path is not intact`() {
        val brokenSource = """
            {"operationType":"COPY","sourceResourceId":42,"sourceResourceName":"Camera",
             "destinationPath":"/backup","destinationName":"Backup","overwriteFiles":false,
             "sources":[{"size":10,"isDirectory":false}]}
        """.trimIndent()

        val decoded = Gson().fromJson(brokenSource, BrowseFileTransferRequest::class.java)

        assertFalse(decoded.isStructurallyIntact())
    }

    @Test
    fun `fully populated request survives the intactness check`() {
        val gson = Gson()
        val request = BrowseFileTransferRequest(
            operationType = FileOperationType.COPY,
            sourceResourceId = 42L,
            sourceResourceName = "Camera",
            sourceCredentialsId = null,
            currentBrowsePath = null,
            destinationPath = "/backup",
            destinationName = "Backup",
            overwriteFiles = false,
            sources = listOf(BrowseFileTransferSource("/dcim/a.jpg", "a.jpg", 100L, false)),
        )

        val decoded = gson.fromJson(gson.toJson(request), BrowseFileTransferRequest::class.java)

        assertTrue(decoded.isStructurallyIntact())
    }

    @Test
    fun `terminal payload parsed from an unrelated json is not intact`() {
        val decoded = Gson().fromJson("""{"a":"success","b":"w-1"}""", BrowseFileTransferTerminalPayload::class.java)

        assertFalse(decoded.isStructurallyIntact())
    }

    @Test
    fun `fully populated terminal payload survives the intactness check`() {
        val gson = Gson()
        val payload = BrowseFileTransferTerminalPayload(
            kind = "success",
            workId = "w-1",
            operationType = FileOperationType.COPY,
            processedCount = 3,
        )

        val decoded = gson.fromJson(gson.toJson(payload), BrowseFileTransferTerminalPayload::class.java)

        assertTrue(decoded.isStructurallyIntact())
    }

    // S1671: the operation used to be persisted as `operationType.name`. Retyping the field to the enum is
    // only safe while Gson keeps writing that same literal, so these hold the wire value itself, not the
    // round trip - a round trip stays green even if both ends move together.

    @Test
    fun `terminal payload writes the operation type as the literal Enum name wrote before`() {
        val gson = Gson()
        FileOperationType.entries.forEach { operation ->
            val written = JsonParser.parseString(gson.toJson(terminalPayloadOf(operation)))
                .asJsonObject
                .get("operationType")

            assertTrue(
                "operationType must stay a JSON string for $operation",
                written.isJsonPrimitive && written.asJsonPrimitive.isString,
            )
            // `.name` is exactly what the field emitted while it was declared String.
            assertEquals(operation.name, written.asString)
        }
    }

    @Test
    fun `terminal payload written by an earlier release decodes into the enum`() {
        val onDisk = """
            {"kind":"success","workId":"w-1","operationType":"MOVE","processedCount":3,"failedCount":0,
             "undoSourceFiles":["/a"],"undoDestinationFolder":"/dst","undoCopiedFiles":["/dst/a"]}
        """.trimIndent()

        val decoded = Gson().fromJson(onDisk, BrowseFileTransferTerminalPayload::class.java)

        assertEquals(FileOperationType.MOVE, decoded.operationType)
        assertTrue(decoded.isStructurallyIntact())
    }

    @Test
    fun `every operation type round-trips through the terminal payload`() {
        val gson = Gson()
        FileOperationType.entries.forEach { operation ->
            val payload = terminalPayloadOf(operation)

            val decoded = gson.fromJson(gson.toJson(payload), BrowseFileTransferTerminalPayload::class.java)

            assertEquals(payload, decoded)
            assertEquals(operation, decoded.operationType)
        }
    }

    @Test
    fun `terminal event survives the payload round trip preserving its operation`() {
        val gson = Gson()
        val event = BrowseFileTransferTerminalEvent.Success(
            workId = "w-1",
            operationType = FileOperationType.MOVE,
            processedCount = 3,
            undoOperation = null,
        )

        val json = gson.toJson(event.toPayload())
        val decoded = gson.fromJson(json, BrowseFileTransferTerminalPayload::class.java)

        assertEquals(event, decoded.toEvent())
    }

    @Test
    fun `terminal payload naming no known operation is rejected at the read boundary`() {
        // Every other persisted key is present, so the intactness verdict can only come from the operation.
        // Gson leaves an unmatched enum null instead of throwing, which is what moves the failure off the
        // consumer and onto the read where a bad blob is dropped.
        val renamedByR8 = """
            {"kind":"success","workId":"w-1","operationType":"a","processedCount":3,"failedCount":0,
             "undoSourceFiles":[],"undoCopiedFiles":[]}
        """.trimIndent()

        val decoded = Gson().fromJson(renamedByR8, BrowseFileTransferTerminalPayload::class.java)

        assertFalse(decoded.isStructurallyIntact())
    }

    private fun terminalPayloadOf(operation: FileOperationType) = BrowseFileTransferTerminalPayload(
        kind = "success",
        workId = "w-1",
        operationType = operation,
        processedCount = 3,
        undoSourceFiles = listOf("/a"),
        undoDestinationFolder = "/dst",
        undoCopiedFiles = listOf("/dst/a"),
    )
}

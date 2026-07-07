package com.sza.fastmediasorter.ui.browse.transfer

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.sza.fastmediasorter.domain.model.FileOperationType
import org.junit.Assert.assertEquals
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
    fun `terminal payload round-trips through Gson preserving values`() {
        val gson = Gson()
        val payload = BrowseFileTransferTerminalPayload(
            kind = "success",
            workId = "w-1",
            operationType = "COPY",
            processedCount = 3,
            undoSourceFiles = listOf("/a", "/b"),
            undoDestinationFolder = "/dst",
            undoCopiedFiles = listOf("/dst/a"),
        )
        assertEquals(payload, gson.fromJson(gson.toJson(payload), BrowseFileTransferTerminalPayload::class.java))
    }
}

package com.sza.fastmediasorter.data.local.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the Room schema-export invariant (S0731): the schema JSON for the current
 * AppDatabase version must be exported and committed under app_v2/schemas/. Catches a
 * version bump that forgot to commit its schema, or exportSchema being turned back off -
 * the foundation that lets future migrations be validated. Pure JVM (no emulator), so it
 * gates in the existing testStandardDebugUnitTest CI job.
 *
 * It does NOT validate migration correctness (that needs an instrumented MigrationTestHelper
 * run on a device); historical 1->35 migrations are untestable because those versions were
 * never exported. Going forward each version bump commits its N.json and adds an (N-1)->N test.
 */
class AppDatabaseSchemaExportTest {

    @Test
    fun exportedSchemaExistsForCurrentVersion() {
        val schemaDir = resolveSchemaDir()
        assertTrue(
            "Room schema export dir missing: ${schemaDir.absolutePath}. " +
                "exportSchema must stay true with room.schemaLocation set.",
            schemaDir.isDirectory
        )
        val schemaFile = File(schemaDir, "$CURRENT_VERSION.json")
        assertTrue(
            "Missing exported schema $CURRENT_VERSION.json under ${schemaDir.absolutePath}. " +
                "Build to regenerate it and commit the file.",
            schemaFile.isFile
        )
        val version = Regex("\"version\"\\s*:\\s*(\\d+)")
            .find(schemaFile.readText())
            ?.groupValues?.get(1)?.toInt()
        assertEquals(
            "Exported schema version must match @Database(version) in AppDatabase.",
            CURRENT_VERSION,
            version
        )
    }

    /**
     * The unit-test working dir varies (Gradle sets it to the module dir; the IDE may use the
     * repo root), so walk a few levels up looking for the committed schema dir under either layout.
     */
    private fun resolveSchemaDir(): File {
        val rel = "schemas/$DB_QUALIFIED_NAME"
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(4) {
            File(dir, rel).takeIf { it.isDirectory }?.let { return it }
            File(dir, "app_v2/$rel").takeIf { it.isDirectory }?.let { return it }
            dir = dir?.parentFile
        }
        return File(rel)
    }

    private companion object {
        // Keep in lockstep with AppDatabase @Database(version = ..).
        const val CURRENT_VERSION = 36
        const val DB_QUALIFIED_NAME = "com.sza.fastmediasorter.data.local.db.AppDatabase"
    }
}

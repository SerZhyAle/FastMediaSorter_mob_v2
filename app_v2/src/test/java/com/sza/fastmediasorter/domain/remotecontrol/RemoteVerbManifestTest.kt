package com.sza.fastmediasorter.domain.remotecontrol

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Keeps `docs/cli-commands.json` equal to the rendering of the handlers' own specs; never edited by hand. */
class RemoteVerbManifestTest {

    @Test
    fun `the committed manifest is exactly what the generator produces`() {
        val rendered = RemoteVerbManifestGenerator.render()
        File("build/remote-verbs").mkdirs()
        File("build/remote-verbs/cli-commands.json").writeText(rendered)

        val committed = File(COMMITTED)
        assertTrue("missing $COMMITTED - copy build/remote-verbs/cli-commands.json after a first run", committed.isFile)
        assertEquals(rendered, committed.readText().replace("\r\n", "\n"))
    }

    @Test
    fun `every verb is remote, has a class and takes no path`() {
        val commands = JsonParser.parseString(RemoteVerbManifestGenerator.render())
            .asJsonObject.getAsJsonArray("commands").map { it.asJsonObject }
        assertEquals(RemoteVerbManifestGenerator.specs.size, commands.size)
        commands.forEach { entry ->
            val confirm = entry.getAsJsonObject("remote").get("confirm").asString
            assertTrue(confirm in RemoteConfirm.entries.map { it.wire })
            assertTrue(RemoteRiskClass.fromWire(entry.get("risk").asString) != null)
            assertTrue(entry.getAsJsonArray("args").none { it.asJsonObject.get("kind").asString == "path" })
        }
    }

    private companion object {
        const val COMMITTED = "../docs/cli-commands.json"
    }
}

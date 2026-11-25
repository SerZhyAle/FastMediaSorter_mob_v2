package com.sza.fastmediasorter.domain.remotecontrol

import com.google.gson.GsonBuilder
import com.sza.fastmediasorter.domain.remotecontrol.verbs.CommandsRemoteVerb
import com.sza.fastmediasorter.domain.remotecontrol.verbs.PlaybackStopRemoteVerb
import com.sza.fastmediasorter.domain.remotecontrol.verbs.QuitRemoteVerb
import com.sza.fastmediasorter.domain.remotecontrol.verbs.StatusRemoteVerb
import com.sza.fastmediasorter.domain.remotecontrol.verbs.VersionRemoteVerb

/**
 * Producer of `docs/cli-commands.json` (REMOTE-CONTROL rule 6): the remote verbs of every flavor in one manifest.
 * The verbs bound in flavor source sets are declared here as data, so the file does not depend on the flavor
 * the unit test happens to run under; [RemoteVerbManifestTest] fails when the committed file drifts.
 */
object RemoteVerbManifestGenerator {

    private val castStop = RemoteVerbSpec(
        name = "cast-stop",
        summary = "Stop casting from this phone to a screen; done also when nothing is cast.",
        riskClass = RemoteRiskClass.SAFE,
        confirm = RemoteConfirm.GRANT,
    )

    private val broadcastStop = RemoteVerbSpec(
        name = "broadcast-stop",
        summary = "End the live broadcast of this phone; done also when nothing is on air.",
        riskClass = RemoteRiskClass.SAFE,
        confirm = RemoteConfirm.GRANT,
    )

    private val broadcastStart = RemoteVerbSpec(
        name = "broadcast-start",
        summary = "Start the prepared broadcast of this phone after the person on the phone confirms it.",
        riskClass = RemoteRiskClass.LEAVES_MACHINE,
        confirm = RemoteConfirm.DEVICE,
        args = listOf(
            RemoteVerbArg(
                name = "mode",
                kind = RemoteArgKind.ENUM,
                description = "what the broadcast sends",
                required = true,
                values = listOf("audio", "video-audio", "video"),
            ),
        ),
        idempotent = false,
    )

    val specs: List<RemoteVerbSpec> = listOf(
        CommandsRemoteVerb.SPEC,
        VersionRemoteVerb.SPEC,
        StatusRemoteVerb.SPEC,
        QuitRemoteVerb.SPEC,
        PlaybackStopRemoteVerb.SPEC,
        castStop,
        broadcastStop,
        broadcastStart,
    )

    fun render(): String =
        GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(RemoteVerbManifest.render(specs)) + "\n"
}

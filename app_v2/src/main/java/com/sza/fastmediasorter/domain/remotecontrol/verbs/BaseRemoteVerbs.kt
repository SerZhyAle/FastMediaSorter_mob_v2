package com.sza.fastmediasorter.domain.remotecontrol.verbs

import android.content.Context
import com.google.gson.JsonObject
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.remotecontrol.RemoteArgKind
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandAnswer
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConfirm
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControlRepository
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbHandler
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbManifest
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbReturn
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbSpec
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Provider

/** `commands` asked from outside: the manifest of the registered handlers, every one of them `remote` (rule 7). */
class CommandsRemoteVerb @Inject constructor(
    // A Provider, because this handler is itself a member of the set it lists.
    private val handlers: Provider<Set<@JvmSuppressWildcards RemoteVerbHandler>>,
) : RemoteVerbHandler {
    override val spec: RemoteVerbSpec = SPEC

    override fun describe(context: Context, args: JsonObject): String =
        context.getString(R.string.remote_command_verb_commands)

    override suspend fun run(args: JsonObject): RemoteCommandAnswer =
        RemoteCommandAnswer.done(RemoteVerbManifest.render(handlers.get().map { it.spec }))

    companion object {
        val SPEC = RemoteVerbSpec(
            name = "commands",
            summary = "List the commands this phone accepts from the account's other devices.",
            riskClass = RemoteRiskClass.SAFE,
            confirm = RemoteConfirm.GRANT,
            coldStart = RemoteVerbSpec.COLD_START_ONESHOT,
        )
    }
}

class VersionRemoteVerb @Inject constructor() : RemoteVerbHandler {
    override val spec: RemoteVerbSpec = SPEC

    override fun describe(context: Context, args: JsonObject): String =
        context.getString(R.string.remote_command_verb_version)

    override suspend fun run(args: JsonObject): RemoteCommandAnswer = RemoteCommandAnswer.done(
        JsonObject().apply {
            addProperty("product", RemoteVerbManifest.PRODUCT)
            addProperty("version", BuildConfig.VERSION_NAME)
            addProperty("cliVersion", RemoteVerbManifest.CLI_VERSION)
        },
    )

    companion object {
        val SPEC = RemoteVerbSpec(
            name = "version",
            summary = "Tell the product name, its version and the version of its command set.",
            riskClass = RemoteRiskClass.SAFE,
            confirm = RemoteConfirm.GRANT,
            returns = listOf(
                RemoteVerbReturn("product", RemoteArgKind.STRING, "the product name"),
                RemoteVerbReturn("version", RemoteArgKind.STRING, "the installed version"),
                RemoteVerbReturn("cliVersion", RemoteArgKind.STRING, "the version of the command set"),
            ),
            coldStart = RemoteVerbSpec.COLD_START_ONESHOT,
        )
    }
}

/** Only the listening state and the version: what plays, casts or broadcasts is user data (APP-COMMANDS item W). */
class StatusRemoteVerb @Inject constructor(
    private val repository: RemoteControlRepository,
) : RemoteVerbHandler {
    override val spec: RemoteVerbSpec = SPEC

    override fun describe(context: Context, args: JsonObject): String =
        context.getString(R.string.remote_command_verb_status)

    override suspend fun run(args: JsonObject): RemoteCommandAnswer = RemoteCommandAnswer.done(
        JsonObject().apply {
            addProperty("ready", true)
            addProperty("version", BuildConfig.VERSION_NAME)
            addProperty("listening", repository.listening.first())
        },
    )

    companion object {
        val SPEC = RemoteVerbSpec(
            name = "status",
            summary = "Tell whether the app is running and listening to commands from the account's devices.",
            riskClass = RemoteRiskClass.SAFE,
            confirm = RemoteConfirm.GRANT,
            returns = listOf(
                RemoteVerbReturn("ready", RemoteArgKind.BOOL, "the app is running and takes commands"),
                RemoteVerbReturn("version", RemoteArgKind.STRING, "the running version"),
                RemoteVerbReturn("listening", RemoteArgKind.BOOL, "whether listening to commands is on"),
            ),
        )
    }
}

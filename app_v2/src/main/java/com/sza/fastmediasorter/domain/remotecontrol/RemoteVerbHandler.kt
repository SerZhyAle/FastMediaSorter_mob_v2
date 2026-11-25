package com.sza.fastmediasorter.domain.remotecontrol

import android.content.Context
import com.google.gson.JsonObject

/** Argument kinds of a remote verb; `path` of APP-COMMANDS rule 18 is left out by REMOTE-CONTROL rule 6. */
enum class RemoteArgKind(val wire: String) {
    STRING("string"),
    INT("int"),
    BOOL("bool"),
    ENUM("enum"),
}

data class RemoteVerbArg(
    val name: String,
    val kind: RemoteArgKind,
    val description: String,
    val required: Boolean = false,
    val values: List<String> = emptyList(),
)

data class RemoteVerbReturn(val name: String, val kind: RemoteArgKind, val description: String)

data class RemoteVerbExit(val code: Int, val meaning: String)

/** One manifest entry in the shape of APP-COMMANDS rule 18, plus the `remote` member of REMOTE-CONTROL rule 7. */
data class RemoteVerbSpec(
    val name: String,
    val summary: String,
    val riskClass: RemoteRiskClass,
    val confirm: RemoteConfirm,
    val args: List<RemoteVerbArg> = emptyList(),
    val returns: List<RemoteVerbReturn> = emptyList(),
    val coldStart: String = COLD_START_REFUSE,
    val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    val idempotent: Boolean = true,
    val exitCodes: List<RemoteVerbExit> = listOf(RemoteVerbExit(RemoteCommandAnswer.EXIT_OK, "done")),
) {
    companion object {
        const val COLD_START_REFUSE = "refuse"
        const val COLD_START_ONESHOT = "oneshot"
        const val DEFAULT_TIMEOUT_MS = 5_000
    }
}

/**
 * One remote verb. A new verb is a handler contributed into the injected set, with no change to the decider.
 * No handler takes a path, a file name, a shell command, a script or a secret (REMOTE-CONTROL rules 6 and 7),
 * and none changes the listening switch, the grants or the local APP-COMMANDS setting (case R12).
 */
interface RemoteVerbHandler {
    val spec: RemoteVerbSpec

    /** The request in the phone's own words for the consent prompt, arguments included (REMOTE-CONTROL rule 10). */
    fun describe(context: Context, args: JsonObject): String

    /** Runs with arguments the decider already checked against [RemoteVerbSpec.args]. */
    suspend fun run(args: JsonObject): RemoteCommandAnswer

    /** Work that must wait until [answer] is on the wire, such as ending the process for `quit`. */
    suspend fun afterAnswer(answer: RemoteCommandAnswer) = Unit
}

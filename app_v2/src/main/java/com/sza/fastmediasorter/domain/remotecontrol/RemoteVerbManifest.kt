package com.sza.fastmediasorter.domain.remotecontrol

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * The APP-COMMANDS manifest (rule 18) of this product's remote verbs. The phone has no command line, so the
 * manifest holds the remote verbs alone and carries no `exe` (REMOTE-CONTROL rule 6). Every entry is `remote`:
 * `commands` asked from outside lists only those, and the handler set holds nothing else.
 */
object RemoteVerbManifest {
    const val SCHEMA_VERSION = 1
    const val PRODUCT = "FastMediaSorter"
    const val CLI_VERSION = "1.0"

    fun render(specs: List<RemoteVerbSpec>): JsonObject = JsonObject().apply {
        addProperty("schemaVersion", SCHEMA_VERSION)
        addProperty("product", PRODUCT)
        addProperty("cliVersion", CLI_VERSION)
        add("commands", JsonArray().apply { specs.sortedBy { it.name }.forEach { add(entry(it)) } })
    }

    private fun entry(spec: RemoteVerbSpec): JsonObject = JsonObject().apply {
        addProperty("name", spec.name)
        add("aliases", JsonArray())
        addProperty("summary", spec.summary)
        addProperty("synopsis", synopsis(spec))
        add("args", JsonArray().apply { spec.args.forEach { add(arg(it)) } })
        add("returns", JsonArray().apply { spec.returns.forEach { add(returned(it)) } })
        addProperty("risk", spec.riskClass.wire)
        addProperty("coldStart", spec.coldStart)
        addProperty("wait", "done")
        addProperty("timeoutMs", spec.timeoutMs)
        addProperty("interactive", false)
        addProperty("idempotent", spec.idempotent)
        add("exitCodes", JsonArray().apply { spec.exitCodes.forEach { add(exit(it)) } })
        add("examples", JsonArray())
        addProperty("since", CLI_VERSION)
        add(
            "remote",
            JsonObject().apply {
                addProperty("confirm", spec.confirm.wire)
                addProperty("since", CLI_VERSION)
            },
        )
    }

    private fun synopsis(spec: RemoteVerbSpec): String =
        (listOf(spec.name) + spec.args.map { if (it.required) "--${it.name} <${it.kind.wire}>" else "[--${it.name}]" })
            .joinToString(" ")

    private fun arg(arg: RemoteVerbArg): JsonObject = JsonObject().apply {
        addProperty("name", arg.name)
        addProperty("kind", arg.kind.wire)
        addProperty("required", arg.required)
        if (arg.values.isNotEmpty()) add("values", JsonArray().apply { arg.values.forEach { add(it) } })
        addProperty("description", arg.description)
    }

    private fun returned(value: RemoteVerbReturn): JsonObject = JsonObject().apply {
        addProperty("name", value.name)
        addProperty("kind", value.kind.wire)
        addProperty("description", value.description)
    }

    private fun exit(exit: RemoteVerbExit): JsonObject = JsonObject().apply {
        addProperty("code", exit.code)
        addProperty("meaning", exit.meaning)
    }
}

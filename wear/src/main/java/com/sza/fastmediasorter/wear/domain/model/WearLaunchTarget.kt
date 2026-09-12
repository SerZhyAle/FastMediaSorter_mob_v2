package com.sza.fastmediasorter.wear.domain.model

import android.content.Intent

/**
 * S1955: what an outside caller can ask the watch app to open.
 *
 * The module's only exported Activity used to ignore its launch intent completely and always open the home
 * screen, so "open this resource" had nowhere to be said. This is that contract, and it is deliberately not
 * private to tiles: S1944, S1884 and S1961 need the same entry, and four private versions of it would drift
 * (strategic §5.1, §7).
 *
 * Both ends of the wire format live in this file for the reason written at the top of `WearRoutes.kt` - a
 * writer and a reader kept apart are two independent string literals, and a mismatch between them is a
 * silent no-op that nothing in this module can catch.
 */
sealed interface WearLaunchTarget {

    /** Open the target the tile was assigned. */
    data class Open(val ref: WearTileTargetRef) : WearLaunchTarget

    /** Open a file the phone has just delivered to this watch. */
    data class File(val path: String, val mimeType: String) : WearLaunchTarget

    /** Open the app where a target for [kind] is chosen, because the tile has none yet. */
    data class Pick(val kind: WearTileKind) : WearLaunchTarget

    /**
     * S2511: open a named destination of the app - a home section or a mini-program.
     *
     * Distinct from [Open] because a shortcut grid pins nothing: there is no stored assignment to look up
     * and no target that can go missing, so the id alone is the whole address.
     */
    data class Destination(val id: WearDestinationId) : WearLaunchTarget
}

/**
 * S2511: one extra of the wire format, carrying its own type.
 *
 * An `Intent` erases the type of what was put into it, and the only way back is a deprecated untyped read
 * that answers `Any?`. A tile does not launch through an `Intent` at all - it hands the platform a key-to-
 * value mapping built per type - so the tile builder used to reconstruct those types by inspecting a
 * throwaway `Intent`, and dropped every value it did not recognise as a `String`. The resource port is an
 * `Int`; it was the value that got dropped, which is why a pinned resource opened the home screen. Naming
 * the type here makes both consumers read the same declared shape instead of guessing at one.
 */
sealed interface WearLaunchExtra {
    data class Text(val value: String) : WearLaunchExtra
    data class Number(val value: Int) : WearLaunchExtra
}

/**
 * The full wire form of [this], in the shape [readWearLaunchTarget] reads back.
 *
 * A null-valued field is omitted rather than written as a null, because both readers ask for it with a
 * getter that answers null for "absent" anyway - so the two spellings are the same answer.
 */
fun WearLaunchTarget.extras(): Map<String, WearLaunchExtra> = buildMap {
    when (this@extras) {
        is WearLaunchTarget.Pick -> {
            put(EXTRA_MODE, WearLaunchExtra.Text(MODE_PICK))
            put(EXTRA_KIND, WearLaunchExtra.Text(kind.name))
        }
        is WearLaunchTarget.Open -> {
            put(EXTRA_MODE, WearLaunchExtra.Text(MODE_OPEN))
            put(EXTRA_KIND, WearLaunchExtra.Text(ref.kind().name))
            putAll(ref.extras())
        }
        is WearLaunchTarget.File -> {
            put(EXTRA_MODE, WearLaunchExtra.Text(MODE_FILE))
            put(EXTRA_FILE_PATH, WearLaunchExtra.Text(path))
            put(EXTRA_FILE_MIME_TYPE, WearLaunchExtra.Text(mimeType))
        }
        is WearLaunchTarget.Destination -> {
            put(EXTRA_MODE, WearLaunchExtra.Text(MODE_DESTINATION))
            put(EXTRA_DESTINATION, WearLaunchExtra.Text(id.name))
        }
    }
}

/** Writes [this] into [intent] in the shape [readWearLaunchTarget] reads back. */
fun WearLaunchTarget.writeTo(intent: Intent) {
    extras().forEach { (key, extra) ->
        when (extra) {
            is WearLaunchExtra.Text -> intent.putExtra(key, extra.value)
            is WearLaunchExtra.Number -> intent.putExtra(key, extra.value)
        }
    }
}

/**
 * Reads back what [writeTo] wrote, or null.
 *
 * Null covers three cases that must not be told apart by the caller: no extras at all, an unreadable value,
 * and a partially written set. A half-filled target would resolve to some address rather than to none, and
 * landing on the wrong screen is worse than landing on the usual one.
 */
fun readWearLaunchTarget(intent: Intent): WearLaunchTarget? {
    val mode = intent.getStringExtra(EXTRA_MODE)
    return when {
        mode == MODE_FILE -> intent.readFileTarget()
        mode == MODE_DESTINATION -> intent.readDestinationTarget()
        mode == null -> null
        else -> readTileTarget(mode, intent)
    }
}

/** An unrecognised name is a target this build does not have, which is the same answer as none at all. */
private fun Intent.readDestinationTarget(): WearLaunchTarget.Destination? =
    getStringExtra(EXTRA_DESTINATION)
        ?.let { name -> WearDestinationId.entries.firstOrNull { it.name == name } }
        ?.let(WearLaunchTarget::Destination)

private fun readTileTarget(mode: String, intent: Intent): WearLaunchTarget? {
    val kind = intent.getStringExtra(EXTRA_KIND)?.let(::tileKindOrNull) ?: return null
    return when (mode) {
        MODE_PICK -> WearLaunchTarget.Pick(kind)
        MODE_OPEN -> intent.readRef(kind)?.let(WearLaunchTarget::Open)
        else -> null
    }
}

private fun Intent.readFileTarget(): WearLaunchTarget.File? {
    val path = getStringExtra(EXTRA_FILE_PATH)
    val mimeType = getStringExtra(EXTRA_FILE_MIME_TYPE)
    return if (path.isNullOrBlank() || mimeType.isNullOrBlank()) null else WearLaunchTarget.File(path, mimeType)
}

private fun WearTileTargetRef.kind(): WearTileKind = when (this) {
    is WearTileTargetRef.Resource -> WearTileKind.RESOURCE
    is WearTileTargetRef.Stream -> WearTileKind.STREAM
    WearTileTargetRef.Favourites -> WearTileKind.FAVOURITES
}

private fun WearTileTargetRef.extras(): Map<String, WearLaunchExtra> = buildMap {
    when (this@extras) {
        WearTileTargetRef.Favourites -> Unit
        is WearTileTargetRef.Stream -> put(EXTRA_STREAM_URL, WearLaunchExtra.Text(normalizedUrl))
        is WearTileTargetRef.Resource -> {
            put(EXTRA_RESOURCE_ID, WearLaunchExtra.Text(id))
            put(EXTRA_RESOURCE_TYPE, WearLaunchExtra.Text(type.name))
            put(EXTRA_RESOURCE_SERVER, WearLaunchExtra.Text(server))
            put(EXTRA_RESOURCE_PORT, WearLaunchExtra.Number(port))
            shareName?.let { put(EXTRA_RESOURCE_SHARE_NAME, WearLaunchExtra.Text(it)) }
            put(EXTRA_RESOURCE_BASE_PATH, WearLaunchExtra.Text(basePath))
        }
    }
}

private fun Intent.readRef(kind: WearTileKind): WearTileTargetRef? = when (kind) {
    WearTileKind.FAVOURITES -> WearTileTargetRef.Favourites
    WearTileKind.STREAM -> getStringExtra(EXTRA_STREAM_URL)?.let(WearTileTargetRef::Stream)
    WearTileKind.RESOURCE -> readResourceRef()
    // S2511: a shortcut grid pins nothing, so it never writes an Open target and cannot be read back as
    // one. An intent claiming otherwise was not written by this app, and null sends it to a plain launch.
    WearTileKind.PROGRAMS, WearTileKind.SECTIONS -> null
}

private fun Intent.readResourceRef(): WearTileTargetRef.Resource? {
    val id = getStringExtra(EXTRA_RESOURCE_ID)
    val type = getStringExtra(EXTRA_RESOURCE_TYPE)?.let(::sourceTypeOrNull)
    val server = getStringExtra(EXTRA_RESOURCE_SERVER)
    val basePath = getStringExtra(EXTRA_RESOURCE_BASE_PATH)
    val port = getIntExtra(EXTRA_RESOURCE_PORT, PORT_ABSENT)
    // shareName is absent from every non-SMB resource, so its nullity is data rather than a missing field.
    val complete = id != null && type != null && server != null && basePath != null && port != PORT_ABSENT
    return if (!complete) {
        null
    } else {
        WearTileTargetRef.Resource(
            id = requireNotNull(id),
            type = requireNotNull(type),
            server = requireNotNull(server),
            port = port,
            shareName = getStringExtra(EXTRA_RESOURCE_SHARE_NAME),
            basePath = requireNotNull(basePath)
        )
    }
}

private fun tileKindOrNull(name: String): WearTileKind? = WearTileKind.entries.firstOrNull { it.name == name }

private fun sourceTypeOrNull(name: String): NetworkSourceType? =
    NetworkSourceType.entries.firstOrNull { it.name == name }

private const val PREFIX = "com.sza.fastmediasorter.wear.launch."

private const val EXTRA_MODE = PREFIX + "mode"
private const val EXTRA_KIND = PREFIX + "kind"
private const val EXTRA_DESTINATION = PREFIX + "destination"
private const val EXTRA_STREAM_URL = PREFIX + "stream_url"
private const val EXTRA_FILE_PATH = PREFIX + "file_path"
private const val EXTRA_FILE_MIME_TYPE = PREFIX + "file_mime_type"
private const val EXTRA_RESOURCE_ID = PREFIX + "resource_id"
private const val EXTRA_RESOURCE_TYPE = PREFIX + "resource_type"
private const val EXTRA_RESOURCE_SERVER = PREFIX + "resource_server"
private const val EXTRA_RESOURCE_PORT = PREFIX + "resource_port"
private const val EXTRA_RESOURCE_SHARE_NAME = PREFIX + "resource_share_name"
private const val EXTRA_RESOURCE_BASE_PATH = PREFIX + "resource_base_path"

private const val MODE_OPEN = "open"
private const val MODE_PICK = "pick"
private const val MODE_FILE = "file"
private const val MODE_DESTINATION = "destination"

/** No port is valid, so this stands for "the extra was never written". */
private const val PORT_ABSENT = -1

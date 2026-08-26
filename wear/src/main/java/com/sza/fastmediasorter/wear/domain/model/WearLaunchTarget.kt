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

    /** Open the app where a target for [kind] is chosen, because the tile has none yet. */
    data class Pick(val kind: WearTileKind) : WearLaunchTarget
}

/** Writes [this] into [intent] in the shape [readWearLaunchTarget] reads back. */
fun WearLaunchTarget.writeTo(intent: Intent) {
    when (this) {
        is WearLaunchTarget.Pick -> {
            intent.putExtra(EXTRA_MODE, MODE_PICK)
            intent.putExtra(EXTRA_KIND, kind.name)
        }
        is WearLaunchTarget.Open -> {
            intent.putExtra(EXTRA_MODE, MODE_OPEN)
            intent.putExtra(EXTRA_KIND, ref.kind().name)
            ref.writeInto(intent)
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
    val kind = intent.getStringExtra(EXTRA_KIND)?.let(::tileKindOrNull)
    return when {
        mode == null || kind == null -> null
        mode == MODE_PICK -> WearLaunchTarget.Pick(kind)
        mode == MODE_OPEN -> intent.readRef(kind)?.let(WearLaunchTarget::Open)
        else -> null
    }
}

private fun WearTileTargetRef.kind(): WearTileKind = when (this) {
    is WearTileTargetRef.Resource -> WearTileKind.RESOURCE
    is WearTileTargetRef.Stream -> WearTileKind.STREAM
    WearTileTargetRef.Favourites -> WearTileKind.FAVOURITES
}

private fun WearTileTargetRef.writeInto(intent: Intent) {
    when (this) {
        WearTileTargetRef.Favourites -> Unit
        is WearTileTargetRef.Stream -> intent.putExtra(EXTRA_STREAM_URL, normalizedUrl)
        is WearTileTargetRef.Resource -> {
            intent.putExtra(EXTRA_RESOURCE_ID, id)
            intent.putExtra(EXTRA_RESOURCE_TYPE, type.name)
            intent.putExtra(EXTRA_RESOURCE_SERVER, server)
            intent.putExtra(EXTRA_RESOURCE_PORT, port)
            intent.putExtra(EXTRA_RESOURCE_SHARE_NAME, shareName)
            intent.putExtra(EXTRA_RESOURCE_BASE_PATH, basePath)
        }
    }
}

private fun Intent.readRef(kind: WearTileKind): WearTileTargetRef? = when (kind) {
    WearTileKind.FAVOURITES -> WearTileTargetRef.Favourites
    WearTileKind.STREAM -> getStringExtra(EXTRA_STREAM_URL)?.let(WearTileTargetRef::Stream)
    WearTileKind.RESOURCE -> readResourceRef()
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
private const val EXTRA_STREAM_URL = PREFIX + "stream_url"
private const val EXTRA_RESOURCE_ID = PREFIX + "resource_id"
private const val EXTRA_RESOURCE_TYPE = PREFIX + "resource_type"
private const val EXTRA_RESOURCE_SERVER = PREFIX + "resource_server"
private const val EXTRA_RESOURCE_PORT = PREFIX + "resource_port"
private const val EXTRA_RESOURCE_SHARE_NAME = PREFIX + "resource_share_name"
private const val EXTRA_RESOURCE_BASE_PATH = PREFIX + "resource_base_path"

private const val MODE_OPEN = "open"
private const val MODE_PICK = "pick"

/** No port is valid, so this stands for "the extra was never written". */
private const val PORT_ABSENT = -1

package com.sza.fastmediasorter.domain.remotecontrol

import com.google.gson.JsonObject

/** Asks the person on this device whether a controller's request may run (REMOTE-CONTROL rule 10). */
interface RemoteConsentPrompter {

    /**
     * True only on the person's explicit acceptance. False when the prompt cannot be shown (notifications
     * refused) or when [CONSENT_WINDOW_MS] pass unanswered - an unshown prompt is never an acceptance.
     * [controllerName] comes from this device's own grant list, never from the request.
     */
    suspend fun ask(controllerName: String, handler: RemoteVerbHandler, args: JsonObject): Boolean

    /**
     * Called once the accepted verb has run. The prompt's own window stays up until then, because a visible
     * window is what lets a verb start a foreground service (camera, microphone) from the person's tap.
     */
    fun ran() = Unit

    companion object {
        const val CONSENT_WINDOW_MS = 60_000L
    }
}

/** How the sender of an opened request stands in the account directory right now. */
enum class RemoteControllerMembership { PRESENT, ABSENT, UNKNOWN }

/** The account directory as the decider sees it, scoped to the exchange entry the request arrived through. */
fun interface RemoteControllerDirectory {
    suspend fun membership(deviceId: String): RemoteControllerMembership
}

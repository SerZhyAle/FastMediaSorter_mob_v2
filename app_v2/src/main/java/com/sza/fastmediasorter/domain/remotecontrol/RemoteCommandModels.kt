package com.sza.fastmediasorter.domain.remotecontrol

import com.google.gson.JsonObject

/** Risk classes of APP-COMMANDS rule 13; [wire] is the manifest token. */
enum class RemoteRiskClass(val wire: String) {
    SAFE("safe"),
    READS_USER_DATA("reads-user-data"),
    WRITES_USER_DATA("writes-user-data"),
    LEAVES_MACHINE("leaves-machine"),
    ;

    companion object {
        fun fromWire(value: String?): RemoteRiskClass? = entries.firstOrNull { it.wire == value }
    }
}

enum class RemoteConsentMode(val wire: String) {
    ASK("ask"),
    ALWAYS("always"),
    ;

    companion object {
        /** An unreadable or missing mode reads as the stricter one (REMOTE-CONTROL rule 10). */
        fun fromWire(value: String?): RemoteConsentMode = entries.firstOrNull { it.wire == value } ?: ASK
    }
}

/** `remote.confirm` of a manifest verb: `device` asks on the target whatever the class mode says. */
enum class RemoteConfirm(val wire: String) {
    GRANT("grant"),
    DEVICE("device"),
}

/** A request read only from an opened box; the controller id is the box's verified sender. */
data class RemoteCommandRequest(
    val requestId: String,
    val fromDeviceId: String,
    val verb: String,
    val args: JsonObject,
    val issuedAtMillis: Long,
    val ttlSeconds: Int,
)

/** An answer in the shape of REMOTE-CONTROL section 2.2; statuses and exits follow APP-COMMANDS rules 11 and 12. */
data class RemoteCommandAnswer(
    val status: String,
    val exit: Int,
    val reason: String? = null,
    val message: String = "",
    val data: JsonObject? = null,
) {
    val isRefusal: Boolean get() = status != STATUS_DONE && status != STATUS_ACCEPTED

    companion object {
        const val STATUS_DONE = "done"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_REFUSED = "refused"
        const val STATUS_BUSY = "busy"
        const val STATUS_FAILED = "failed"

        const val EXIT_OK = 0
        const val EXIT_FAILED = 1
        const val EXIT_NOT_PROVEN = 2
        const val EXIT_USAGE = 10
        const val EXIT_NOT_READY = 11
        const val EXIT_PERMISSION = 12
        const val EXIT_STOPPED = 16

        const val REASON_NOT_PERMITTED = "not-permitted"
        const val REASON_DECLINED = "declined"
        const val REASON_EXPIRED = "expired"
        const val REASON_UNKNOWN_VERB = "unknown-verb"
        const val REASON_BAD_ARGUMENT = "bad-argument"
        const val REASON_BUSY = "busy"
        const val REASON_TARGET_MISSING = "target-missing"
        const val REASON_SCHEMA = "schema"

        fun done(data: JsonObject? = null) = RemoteCommandAnswer(STATUS_DONE, EXIT_OK, data = data)

        fun notPermitted() = refused(EXIT_PERMISSION, REASON_NOT_PERMITTED, "The target does not permit this request.")

        fun declined() = refused(EXIT_STOPPED, REASON_DECLINED, "The person on the target declined the request.")

        fun expired() = refused(EXIT_NOT_PROVEN, REASON_EXPIRED, "The request is outside its time window.")

        fun unknownVerb() = refused(EXIT_USAGE, REASON_UNKNOWN_VERB, "The verb is not a remote verb of this product.")

        fun badArgument() = refused(EXIT_USAGE, REASON_BAD_ARGUMENT, "An argument is unknown or of the wrong kind.")

        /** A payload `v` above the one this target reads (REMOTE-CONTROL section 3, APP-COMMANDS rule 12). */
        fun schema() = refused(EXIT_NOT_PROVEN, REASON_SCHEMA, "The request is of a newer schema than the target.")

        fun busy() = RemoteCommandAnswer(STATUS_BUSY, EXIT_NOT_READY, REASON_BUSY, "The target is busy.")

        fun targetMissing() =
            RemoteCommandAnswer(STATUS_FAILED, EXIT_NOT_READY, REASON_TARGET_MISSING, "The named thing does not exist.")

        private fun refused(exit: Int, reason: String, message: String) =
            RemoteCommandAnswer(STATUS_REFUSED, exit, reason, message)
    }
}

/**
 * What one controller may ask. A class absent from [classes] is not granted; a new grant carries
 * only [RemoteRiskClass.SAFE] with [RemoteConsentMode.ALWAYS] (REMOTE-CONTROL rules 9 and 10).
 */
data class RemoteControllerGrant(
    val deviceId: String,
    val deviceName: String,
    val classes: Map<RemoteRiskClass, RemoteConsentMode> = mapOf(RemoteRiskClass.SAFE to RemoteConsentMode.ALWAYS),
) {
    fun modeFor(riskClass: RemoteRiskClass): RemoteConsentMode? = classes[riskClass]

    companion object {
        /** Default mode when a class is granted later: only `safe` defaults to `always`. */
        fun defaultMode(riskClass: RemoteRiskClass): RemoteConsentMode =
            if (riskClass == RemoteRiskClass.SAFE) RemoteConsentMode.ALWAYS else RemoteConsentMode.ASK
    }
}

/** One line of the on-device command log; arguments are never stored (CLI-EVENT-STREAM redaction). */
data class RemoteCommandLogEntry(
    val timeMillis: Long,
    val controllerName: String?,
    val deviceId: String,
    val verified: Boolean,
    val verb: String?,
    val riskClass: RemoteRiskClass?,
    val status: String,
    val reason: String?,
) {
    companion object {
        const val STATUS_SEALED_FAILED = "sealed-failed"
    }
}

package com.sza.fastmediasorter.domain.model.networkmonitor

/**
 * S1433: why a Monitor section has nothing to show.
 *
 * Modelled rather than decided in the UI. Strategic §11 criterion 2 requires the screen to say honestly
 * why data is missing, and a blank field cannot tell "this device has no Bluetooth radio" apart from "you
 * did not grant the permission" - the first is permanent, the second is one tap from being fixed, and the
 * user is owed the difference.
 */
sealed interface SectionAvailability {

    /** Real data stands behind the section. */
    data object Available : SectionAvailability

    /** No such hardware on this device, so no permission or setting will ever populate the section. */
    data object NoHardware : SectionAvailability

    /**
     * The hardware exists but its runtime permission was not granted.
     *
     * Carries the manifest name so the UI can offer the one permission that would fix this section,
     * instead of sending the user to a settings screen to work out which of several it meant.
     */
    data class NoPermission(val manifestName: String) : SectionAvailability

    /**
     * The permission is granted but the system location switch is off.
     *
     * Told apart from [NoPermission] because the recovery differs: a permission dialog changes nothing
     * here, and the platform withholds the same Wi-Fi facts in both states (S1853).
     */
    data object NoLocationService : SectionAvailability

    /** Hardware and permission are both fine; nothing is connected at this moment. */
    data object NoNetwork : SectionAvailability
}

/**
 * S1433: a Monitor section paired with the reason it may be empty.
 *
 * The pairing is a type rather than a convention: the tactical plan requires every section-bearing field
 * of the snapshot to carry a [SectionAvailability], and expressing that as a wrapper makes a field that
 * forgets one fail to compile rather than fail in review.
 *
 * [data] is null whenever [availability] is not [SectionAvailability.Available]; a section that is
 * available always has its payload.
 */
data class MonitorSection<out T>(
    val availability: SectionAvailability,
    val data: T?,
) {

    companion object {

        /** Section with real content behind it. */
        fun <T> available(data: T): MonitorSection<T> = MonitorSection(SectionAvailability.Available, data)

        /** Section with no content, and the reason why. */
        fun <T> absent(reason: SectionAvailability): MonitorSection<T> = MonitorSection(reason, null)
    }
}

package com.sza.fastmediasorter.wear.domain.motion

/**
 * The five live readings the Motion Monitor session can offer.
 *
 * The set deliberately spans two permission families, which is the whole reason S2458 exists as its own
 * ticket: the three motion ids are normal-protection sensors needing no permission on any level this
 * module supports, while the two step ids need ACTIVITY_RECOGNITION, which S2458 ADR-2 confines to the
 * noLegal edition after a Play-distributed edition carrying it was rejected under S1614.
 */
enum class WearSensorStreamId {
    ACCELEROMETER,
    GYROSCOPE,
    ROTATION_VECTOR,
    STEP_COUNTER,
    STEP_DETECTOR
}

/**
 * Why a stream has no reading, kept as four distinct answers rather than one absent value.
 *
 * S2458 §5.4: a missing sensor, a refused permission and a capability this edition does not ship are
 * three different sentences to the user, and none of them may be rendered as a zero reading - a zero is
 * a legitimate accelerometer value, so it cannot double as "nothing arrived".
 *
 * [NotInThisEdition] is the merged-manifest answer - the app's own manifest declares no such permission -
 * and never a flavor check, which CLAUDE.md Rule 14 bans in shared code.
 */
sealed interface WearSensorAvailability {

    data object Available : WearSensorAvailability

    data object NoHardware : WearSensorAvailability

    data object PermissionDenied : WearSensorAvailability

    data object NotInThisEdition : WearSensorAvailability
}

/**
 * One stream's whole live state.
 *
 * [values] carries the sensor's own axes verbatim; nothing here smooths, derives or re-bases a reading.
 */
data class WearSensorStreamState(
    val id: WearSensorStreamId,
    val availability: WearSensorAvailability,
    val values: List<Float> = emptyList(),
    val delivery: WearSensorDelivery = WearSensorDelivery.EMPTY
)

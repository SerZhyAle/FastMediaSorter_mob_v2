package com.sza.fastmediasorter.wear.domain.motion

/**
 * How well a stream is delivering, in the three figures S2458 §5.3 allows.
 *
 * Nothing here survives the screen: the session that produced these numbers ends when the collector
 * detaches, and no history, graph or cross-session accumulation is kept (S2458 Non-goals).
 *
 * The event time is stored absolute rather than as an age, because an age computed once at emission is
 * always near zero and would therefore never show the case the diagnostic exists for - a sensor that
 * accepted the registration and then stopped delivering.
 */
data class WearSensorDelivery(
    val eventCount: Int,
    val hertz: Double,
    val lastEventAtMillis: Long
) {

    companion object {
        val EMPTY = WearSensorDelivery(eventCount = 0, hertz = 0.0, lastEventAtMillis = 0L)
    }
}

private const val MILLIS_PER_SECOND = 1000.0

/**
 * Folds one sensor event into a stream's delivery figures.
 *
 * Every clock value is a parameter and no time API is called here: the platform source this feeds cannot
 * run on the plain JVM the wear unit suite uses, so the arithmetic has to live where a test can reach it.
 */
fun accumulate(
    previous: WearSensorDelivery,
    eventAtMillis: Long,
    sessionStartMillis: Long
): WearSensorDelivery {
    val count = previous.eventCount + 1
    val elapsedMillis = eventAtMillis - sessionStartMillis
    // A first event can land in the same millisecond the session started; dividing by that elapsed time
    // yields infinity, which would reach the screen as a rate.
    val hertz = if (elapsedMillis <= 0L) {
        0.0
    } else {
        count * MILLIS_PER_SECOND / elapsedMillis
    }
    return WearSensorDelivery(
        eventCount = count,
        hertz = hertz,
        lastEventAtMillis = eventAtMillis
    )
}

/**
 * How long ago the last event arrived, or null while a stream has delivered nothing at all.
 *
 * Null rather than zero for the same reason [WearSensorAvailability] has four cases: "no event yet" and
 * "an event just now" must not render alike.
 */
fun deliveryAgeMillis(delivery: WearSensorDelivery, nowMillis: Long): Long? = if (delivery.eventCount == 0) {
    null
} else {
    (nowMillis - delivery.lastEventAtMillis).coerceAtLeast(0L)
}

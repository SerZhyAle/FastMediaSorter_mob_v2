package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.sensors.SensorCapability

/**
 * S1179: does this device carry the hardware a sensor gadget needs.
 *
 * One answer shared by the gadget picker and the gadgets themselves, so "no sensor - no gadget in
 * the list" is a rule of this layer rather than five copies inside five tiles (strategic ADR-3).
 *
 * Hardware only, never permissions. A refused permission leaves the tile in place in an honest empty
 * state (strategic §11.5); an absent sensor removes it from the picker entirely (§11.2). Answering
 * both questions here would collapse those two outcomes into one.
 */
interface SensorAvailabilityRepository {

    fun isAvailable(capability: SensorCapability): Boolean
}

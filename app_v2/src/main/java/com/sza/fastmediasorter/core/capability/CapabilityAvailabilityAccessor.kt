package com.sza.fastmediasorter.core.capability

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Reaches [CapabilityAvailability] from a class Hilt cannot inject.
 *
 * Exists for two shapes the framework constructs itself - an `AppWidgetProvider` and the
 * stateless dialog objects in `ui/dialog` - plus the player helper managers, which are built by
 * hand-written factories several levels below the activity that holds the graph.
 *
 * This is plumbing, not a second gate (S1625): every caller still asks the ONE contract, so the
 * translation decision has exactly one implementation. Prefer constructor injection wherever the
 * class has a constructor Hilt owns; use this only where it does not.
 */
object CapabilityAvailabilityAccessor {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CapabilityEntryPoint {
        fun capabilityAvailability(): CapabilityAvailability
    }

    fun get(context: Context): CapabilityAvailability =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            CapabilityEntryPoint::class.java,
        ).capabilityAvailability()

    /** Shorthand for the common question, so a call site needs one import rather than two. */
    fun isTranslationAvailable(context: Context): Boolean = get(context).isTranslationAvailable(context)
}

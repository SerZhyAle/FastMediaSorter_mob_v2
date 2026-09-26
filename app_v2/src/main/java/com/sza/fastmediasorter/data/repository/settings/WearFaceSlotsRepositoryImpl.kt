package com.sza.fastmediasorter.data.repository.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sza.fastmediasorter.domain.model.WearFaceSlot
import com.sza.fastmediasorter.domain.model.WearFaceSlotAssignment
import com.sza.fastmediasorter.domain.model.WearFaceSlotOption
import com.sza.fastmediasorter.domain.repository.WearFaceSlotsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3558: its own DataStore file rather than a field of AppSettings - the assignment is read only by the
 * companion window and the watch publisher, and keeping it out of the main settings store keeps it out
 * of device profiles and presets that have no meaning for a watch face.
 *
 * Stored as wire ids, so an id this build no longer knows (a downgrade after a newer build wrote one)
 * reads as the slot's default instead of failing the whole flow.
 */
private val Context.wearFaceSlotsDataStore by preferencesDataStore("wear_face_slots")

@Singleton
class WearFaceSlotsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : WearFaceSlotsRepository {

    override fun observe(): Flow<WearFaceSlotAssignment> = context.wearFaceSlotsDataStore.data
        .map { prefs -> prefs.toAssignment() }
        .distinctUntilChanged()

    override suspend fun set(slot: WearFaceSlot, option: WearFaceSlotOption) {
        context.wearFaceSlotsDataStore.edit { prefs -> prefs[keyOf(slot)] = option.wireId }
    }

    private fun Preferences.toAssignment() = WearFaceSlotAssignment(
        WearFaceSlot.entries.associateWith { slot ->
            WearFaceSlotOption.fromWireIdOrNull(this[keyOf(slot)]) ?: slot.defaultOption
        }
    )

    private fun keyOf(slot: WearFaceSlot) = stringPreferencesKey("slot${slot.ordinal + 1}")
}

package com.sza.fastmediasorter.wear.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sza.fastmediasorter.wear.data.wear.WearFaceSlotsCodec
import com.sza.fastmediasorter.wear.domain.model.WearFaceSlots
import com.sza.fastmediasorter.wear.domain.repository.WearFaceSlotsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.faceSlotsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "wear_face_slots")

/**
 * S3558: its own store for [WearClockStyleStore]'s reason - the choice is made on the phone only, and
 * the settings exchange must neither report it back nor carry it through its six-file parity.
 *
 * Kept as the wire JSON so the codec that reads the packet also reads the store; a stored value an
 * older build cannot read falls back to [WearFaceSlots.DEFAULT], the face as it looked before.
 */
@Singleton
class WearFaceSlotsStore @Inject constructor(
    @ApplicationContext private val context: Context
) : WearFaceSlotsRepository {

    override val slots: Flow<WearFaceSlots> = context.faceSlotsDataStore.data
        .map { prefs -> prefs[SLOTS_KEY]?.let { WearFaceSlotsCodec.decode(it) } ?: WearFaceSlots.DEFAULT }
        .distinctUntilChanged()

    override suspend fun save(slots: WearFaceSlots) {
        context.faceSlotsDataStore.edit { prefs ->
            prefs[SLOTS_KEY] = WearFaceSlotsCodec.encode(slots)
        }
    }

    private companion object {
        val SLOTS_KEY = stringPreferencesKey("face_slots_json")
    }
}

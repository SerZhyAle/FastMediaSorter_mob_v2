package com.sza.fastmediasorter.wear.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sza.fastmediasorter.wear.data.wear.WearClockStyleCodec
import com.sza.fastmediasorter.wear.domain.model.WearClockStyle
import com.sza.fastmediasorter.wear.domain.repository.WearClockStyleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.clockStyleDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "wear_clock_style")

/**
 * S3557: its own store rather than keys in `wear_settings`. The style is not a watch setting - the
 * owner never edits it here and the settings exchange must not report it back to the phone - and the
 * settings mirrors are held to parity across six files for every key they carry.
 *
 * Kept as the wire JSON so the codec that reads the packet also reads the store; a stored value an
 * older build cannot read falls back to [WearClockStyle.DEFAULT] rather than blanking the clock.
 */
@Singleton
class WearClockStyleStore @Inject constructor(
    @ApplicationContext private val context: Context
) : WearClockStyleRepository {

    override val style: Flow<WearClockStyle> = context.clockStyleDataStore.data
        .map { prefs -> prefs[STYLE_KEY]?.let { WearClockStyleCodec.decode(it) } ?: WearClockStyle.DEFAULT }
        .distinctUntilChanged()

    override suspend fun save(style: WearClockStyle) {
        context.clockStyleDataStore.edit { prefs ->
            prefs[STYLE_KEY] = WearClockStyleCodec.encode(style)
        }
    }

    private companion object {
        val STYLE_KEY = stringPreferencesKey("clock_style_json")
    }
}

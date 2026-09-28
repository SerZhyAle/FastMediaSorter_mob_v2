package com.sza.fastmediasorter.wear.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sza.fastmediasorter.wear.data.wear.PhoneBatteryReportCodec
import com.sza.fastmediasorter.wear.domain.model.PhoneBatteryReport
import com.sza.fastmediasorter.wear.domain.repository.WearPhoneBatteryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.phoneBatteryDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "wear_phone_battery")

/**
 * S3764: its own store rather than keys in `wear_settings`, for [WearClockStyleStore]'s reasons:
 * the phone's charge is not a watch setting - the owner never edits it here and the settings
 * exchange must not report it back to the phone. Persisting at all is what keeps the face fed
 * across a watch reboot: the face provider reads this store in a process the listener service is
 * not part of, so an in-memory holder would empty the bar on every restart.
 */
@Singleton
class WearPhoneBatteryStore @Inject constructor(
    @ApplicationContext private val context: Context
) : WearPhoneBatteryRepository {

    override val report: Flow<PhoneBatteryReport?> = context.phoneBatteryDataStore.data
        .map { prefs -> prefs[REPORT_KEY]?.let { PhoneBatteryReportCodec.decode(it) } }
        .distinctUntilChanged()

    override suspend fun save(report: PhoneBatteryReport) {
        context.phoneBatteryDataStore.edit { prefs ->
            prefs[REPORT_KEY] = PhoneBatteryReportCodec.encode(report)
        }
    }

    private companion object {
        val REPORT_KEY = stringPreferencesKey("phone_battery_json")
    }
}

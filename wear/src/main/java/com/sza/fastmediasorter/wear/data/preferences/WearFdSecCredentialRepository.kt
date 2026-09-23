package com.sza.fastmediasorter.wear.data.preferences

import android.content.SharedPreferences
import com.sza.fastmediasorter.wear.di.EncryptedPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3397: the one FileDO credential the wearer asked this watch to remember for viewing containers.
 *
 * Lives in the module's existing Keystore-backed preferences under a key no other store reads. The
 * value is never logged, and neither is the fact that one exists. The watch keeps its own copy; the
 * phone's is never synced here.
 */
@Singleton
class WearFdSecCredentialRepository @Inject constructor(
    @EncryptedPrefs private val prefs: SharedPreferences
) {

    suspend fun read(): CharArray? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_CREDENTIAL, null)?.toCharArray()
    }

    suspend fun save(credential: CharArray) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_CREDENTIAL, String(credential)).apply()
    }

    suspend fun forget() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_CREDENTIAL).apply()
    }

    private companion object {
        const val KEY_CREDENTIAL = "fdsec_remembered_credential"
    }
}

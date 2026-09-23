package com.sza.fastmediasorter.data.security.fdsec

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3397: the one FileDO credential the owner asked this phone to remember for viewing containers.
 *
 * Kept in its own Keystore-backed file so that forgetting it can never touch another store. The
 * value is never logged, and neither is the fact that one exists.
 */
@Singleton
class FdSecCredentialRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun read(): CharArray? = prefs.getString(KEY_CREDENTIAL, null)?.toCharArray()

    fun save(credential: CharArray) {
        prefs.edit().putString(KEY_CREDENTIAL, String(credential)).apply()
    }

    fun forget() {
        prefs.edit().remove(KEY_CREDENTIAL).apply()
    }

    private companion object {
        const val FILE_NAME = "fdsec_remembered_credential"
        const val KEY_CREDENTIAL = "credential"
    }
}

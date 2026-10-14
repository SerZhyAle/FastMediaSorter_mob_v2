package com.sza.fastmediasorter.core.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Opens an [EncryptedSharedPreferences] file for a store whose content the user can obtain again by signing in.
 *
 * All encrypted stores share one Keystore master key. When it stops decrypting the keysets they hold (app data
 * kept across an uninstall, a Keystore reset, a device transfer), `create(..)` throws on every call, and a
 * `by lazy` around it re-runs and re-throws on every access: the store is dead until the app data is cleared by
 * hand. The unreadable file is therefore reset once and created again. There is never a plaintext fallback.
 */
object EncryptedPrefsOpener {

    fun open(context: Context, fileName: String): SharedPreferences = try {
        create(context, fileName)
    } catch (error: GeneralSecurityException) {
        reset(context, fileName, error)
        create(context, fileName)
    } catch (error: IOException) {
        reset(context, fileName, error)
        create(context, fileName)
    }

    private fun create(context: Context, fileName: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // clear() rather than deleteSharedPreferences: the legacy flavor runs on API 23, which lacks the latter.
    private fun reset(context: Context, fileName: String, error: Exception) {
        Timber.w(error, "EncryptedPrefsOpener: unreadable keyset, resetting %s", fileName)
        context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().commit()
    }
}

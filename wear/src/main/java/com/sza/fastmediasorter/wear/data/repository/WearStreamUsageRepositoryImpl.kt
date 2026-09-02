package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.wear.domain.model.WearStreamUsage
import com.sza.fastmediasorter.wear.domain.model.recordWearStreamPlay
import com.sza.fastmediasorter.wear.domain.repository.WearStreamUsageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2146: the play counter, held in a plain preferences file of its own.
 *
 * Plain rather than the encrypted file the favourites store uses: a count over public catalogue
 * addresses is not a secret, and an encrypted store would pay a master-key init on first read for
 * nothing.
 */
@Singleton
class WearStreamUsageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WearStreamUsageRepository {

    /**
     * S2146 ADR-7: the singleton owns the scope its fire-and-forget write runs on, instead of
     * `GlobalScope`, which Rule 19 bans, and instead of a new Hilt qualifier, of which the watch
     * module currently has none.
     */
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun usageByIdentity(): Map<String, WearStreamUsage> = withContext(Dispatchers.IO) {
        read().associateBy { it.identity }
    }

    override fun recordPlay(identity: String) {
        if (identity.isBlank()) return
        writeScope.launch {
            val current = read().associateBy { it.identity }
            val updated: List<WearStreamUsage> =
                recordWearStreamPlay(current, identity, System.currentTimeMillis()).values.toList()
            prefs.edit().putString(KEY_USAGE, gson.toJson(updated)).apply()
        }
    }

    /**
     * The stored shape is a list, not a map keyed by identity: every entry already carries its own
     * [WearStreamUsage.identity], so a keyed object would write that address to disk twice and let
     * the two copies disagree. The map the projection wants is built on the way out instead.
     */
    private fun read(): List<WearStreamUsage> {
        return try {
            val json = prefs.getString(KEY_USAGE, null) ?: return emptyList()
            val type = TypeToken.getParameterized(List::class.java, WearStreamUsage::class.java).type
            gson.fromJson<List<WearStreamUsage>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            // A store this build cannot parse reads as "nothing played yet": the counter only orders
            // a list, so starting it over costs order, while rethrowing would cost the screen.
            Timber.e(e, "WearStreamUsageRepositoryImpl: failed to read stream usage")
            emptyList()
        }
    }

    private companion object {
        const val PREFS_NAME = "wear_stream_usage"
        const val KEY_USAGE = "wear_stream_usage_by_identity"
    }
}

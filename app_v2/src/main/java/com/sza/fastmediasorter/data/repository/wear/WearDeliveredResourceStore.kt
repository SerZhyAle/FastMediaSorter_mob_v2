package com.sza.fastmediasorter.data.repository.wear

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S2909: which resources this phone has put on the watch and not yet taken back.
 *
 * S2882 withdraws every registered resource the selection does not hold, which makes the push leg's
 * "nothing to send" branch unreachable while the registry is not empty: an empty selection always
 * declares the whole registry withdrawn, so a batch of pure withdrawals crosses the wire on every
 * visit to the selection list. A withdrawal only means something for a resource the watch could be
 * carrying, and that is the question this store answers.
 *
 * Stored beside the `resources` table rather than inside it, for the reason [WearResourceStampStore]
 * records: a column would demand a database version bump and a migration for a value nothing outside
 * the watch exchange reads.
 *
 * The entries are resource ids in their string form, exactly as they travel on the wire.
 *
 * S2515: every member suspends and the implementation moves itself to IO, matching the three stores
 * beside it.
 */
interface WearDeliveredResourceStore {

    /**
     * The ids this phone believes the watch carries, or null when it has never completed a push.
     *
     * Null and the empty set must stay distinguishable. Empty means "the watch holds nothing this
     * phone gave it", so there is nothing to withdraw; null means the answer is unknown, which is
     * what a build that predates this store leaves behind, and treating that as empty would drop the
     * withdrawal S2882 exists to deliver.
     */
    suspend fun read(): Set<String>?

    suspend fun write(ids: Set<String>)
}

class SharedPreferencesWearDeliveredResourceStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WearDeliveredResourceStore {

    private val preferences
        get() = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun read(): Set<String>? = withContext(Dispatchers.IO) {
        val stored = preferences.getString(KEY_DELIVERED, null) ?: return@withContext null
        // An unreadable value degrades to "unknown" rather than to "nothing delivered": the first
        // shape keeps S2882's registry-wide withdrawal for one batch and then rewrites the value,
        // the second would silently strand whatever the watch is holding.
        runCatching { gson.fromJson<Set<String>>(stored, DELIVERED_SET_TYPE) }
            .onFailure { Timber.w(it, "Stored delivered wear resource ids unreadable, ignoring them") }
            .getOrNull()
    }

    override suspend fun write(ids: Set<String>) {
        withContext(Dispatchers.IO) {
            preferences.edit().putString(KEY_DELIVERED, gson.toJson(ids)).apply()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "wear_delivered_resources"
        const val KEY_DELIVERED = "delivered_resource_ids"

        // The token is what keeps the deserialized shape a Set of String rather than Gson's default
        // LinkedTreeMap guess for a bare generic.
        val DELIVERED_SET_TYPE = object : TypeToken<Set<String>>() {}.type
    }
}

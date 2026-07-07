package com.sza.fastmediasorter.domain.usecase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.sza.fastmediasorter.data.link.auth.AccountIdentityExtractor
import com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

/**
 * S0211: one-shot cleanup of duplicate auth accounts.
 *
 * For each host, groups active accounts by computed identity. In each group with >1
 * entries, keeps the record with `max(lastUsedAt ?: savedAt)` and deletes the rest.
 * Records without a computable identity are left untouched.
 *
 * Idempotent: stores a done-flag in DataStore and returns immediately on subsequent
 * calls. On a clean DB it scans then no-ops.
 */
class DedupAuthAccountsUseCase @Inject constructor(
    private val store: EncryptedCookieStore,
    private val dataStore: DataStore<Preferences>,
) {

    companion object {
        private val KEY_DONE = booleanPreferencesKey("dedup_s0211_done")
    }

    suspend operator fun invoke() {
        val isDone = dataStore.data.first()[KEY_DONE] ?: false
        if (isDone) return

        var deleted = 0
        withContext(Dispatchers.IO) {
            val active = store.listAllAccounts()
                .filter { (_, entry) ->
                    entry.type == EncryptedCookieStore.TYPE_ACTIVE && entry.cookieCount > 0
                }
            val byHost = active.groupBy { (host, _) -> host }

            byHost.forEach { (_, pairs) ->
                val withIdentity = pairs.mapNotNull { (h, entry) ->
                    val cookies = store.loadForAccount(h, entry.accountId)
                    val identity = AccountIdentityExtractor.extract(h, cookies)
                    if (identity != null) Triple(h, entry, identity) else null
                }
                withIdentity
                    .groupBy { it.third }
                    .filter { (_, group) -> group.size > 1 }
                    .forEach { (identity, group) ->
                        val keep = group.maxByOrNull { (_, e, _) ->
                            e.lastUsedAt ?: e.savedAt ?: Instant.MIN
                        } ?: return@forEach
                        group
                            .filter { it.second.accountId != keep.second.accountId }
                            .forEach { (h, entry, _) ->
                                store.deleteForAccount(h, entry.accountId)
                                deleted += 1
                                Timber.i(
                                    "cleanup: deleted dup host=%s accountId=%s identity=%s keepId=%s",
                                    h, entry.accountId, identity, keep.second.accountId,
                                )
                            }
                    }
            }
        }

        dataStore.edit { it[KEY_DONE] = true }
        Timber.i("cleanup completed: deleted=%d", deleted)
    }
}

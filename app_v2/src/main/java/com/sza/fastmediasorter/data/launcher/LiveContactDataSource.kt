package com.sza.fastmediasorter.data.launcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.sza.fastmediasorter.domain.model.launcher.LiveContactDetails
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1206: reads a pinned contact's current name and photo under `READ_CONTACTS`, so a cell follows the
 * address book instead of the snapshot it was pinned with.
 *
 * Sibling of [ContactSnapshotDataSource] and deliberately not part of it: that class reads under the
 * one-time grant the system picker attaches to a picked record and needs no permission at all, while
 * every read here is gated on a permission the user may refuse or revoke at any moment.
 *
 * **Every "cannot answer" case is one null.** A blank lookup key, a missing grant, a deleted contact
 * and a provider that refuses the query all return null, so the caller has one fallback branch rather
 * than four. Nothing about the person is logged - not the name, the lookup key, nor the photo URI;
 * only the failure kind ever reaches Timber.
 */
@Singleton
class LiveContactDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Answers already read, including the negative ones.
     *
     * Mandatory rather than an optimisation: the desktop re-resolves every cell on each emission of
     * the flow it is combined into - including emissions caused by an unrelated radio switching - so
     * an uncached read would query the address book once per cell per Wi-Fi toggle. Concurrent because
     * resolution runs per cell on [Dispatchers.IO]. A missing contact is cached too, so a person who is
     * genuinely gone is not looked up again on every emission.
     */
    private val cache = ConcurrentHashMap<String, CachedContact>()

    /** Null when the address book cannot answer for this key - see the class KDoc. */
    suspend fun read(lookupKey: String): LiveContactDetails? {
        val details = if (lookupKey.isBlank() || !isGranted()) null else cached(lookupKey).details
        val photo = details?.photoUri != null
        return details
    }

    /**
     * Emits once on collection and again whenever the address book changes.
     *
     * The immediate first emission is load-bearing, not cosmetic: this flow is combined with the
     * stored cell list, and `combine` withholds its first value until every input has emitted, so an
     * observer-only flow would leave the desktop blank until somebody edited a contact.
     */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                cache.clear()
                trySend(Unit)
            }
        }
        // `true` is notifyForDescendants: a renamed person changes a row below the contacts URI, and
        // without descendants the observer would never hear about the very edit this ticket exists for.
        val registered = runCatching {
            context.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                observer,
            )
        }.onFailure { error ->
            Timber.i("Launcher contacts: live observer refused (%s)", error.javaClass.simpleName)
        }.isSuccess
        trySend(Unit)
        awaitClose {
            if (registered) {
                runCatching { context.contentResolver.unregisterContentObserver(observer) }
            }
        }
    }

    private suspend fun cached(lookupKey: String): CachedContact =
        cache[lookupKey] ?: withContext(Dispatchers.IO) {
            CachedContact(queryDetails(lookupKey)).also { cache[lookupKey] = it }
        }

    private fun queryDetails(lookupKey: String): LiveContactDetails? {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
        return queryOrNull(uri)?.use { cursor ->
            if (cursor.moveToFirst()) {
                LiveContactDetails(
                    displayName = cursor.getString(INDEX_NAME).orEmpty(),
                    photoUri = cursor.getString(INDEX_PHOTO_THUMBNAIL)?.takeIf { it.isNotBlank() },
                )
            } else {
                null
            }
        }
    }

    /**
     * A revoked permission, a vanished contact and a provider that simply refuses are all normal
     * states of the world here, so the read degrades to "no data" with the failure kind logged.
     */
    private fun queryOrNull(uri: Uri): Cursor? = runCatching {
        context.contentResolver.query(uri, PROJECTION, null, null, null)
    }.getOrElse { error ->
        Timber.i("Launcher contacts: live read unavailable (%s)", error.javaClass.simpleName)
        null
    }

    private fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Wrapper so "looked up, found nothing" is a cache hit rather than an absent key. */
    private class CachedContact(val details: LiveContactDetails?)

    private companion object {
        val PROJECTION = arrayOf(
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
        )
        const val INDEX_NAME = 0
        const val INDEX_PHOTO_THUMBNAIL = 1
    }
}

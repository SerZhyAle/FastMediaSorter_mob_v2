package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands the auth fields of a network stream from the caller to [NetworkAwareMediaSourceFactory]
 * inside this process, keyed by the media URI string.
 *
 * They used to ride in the played `MediaItem`'s metadata extras, but the audio session is exported
 * and Media3 ships those extras to every connected controller, so any installed app could read the
 * password of the playing track (S3890). The caller and the factory share one process, so the
 * credentials never need to cross a session boundary.
 *
 * Bounded so a long listening session does not keep every password it ever played in memory; a
 * handful of entries covers the player re-opening a recent source after an error or a seek.
 */
@Singleton
class StreamCredentialHolder @Inject constructor() {

    private val lock = Any()
    private val entries = object : LinkedHashMap<String, StreamCredentials>(CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StreamCredentials>?): Boolean =
            size > CAPACITY
    }

    fun put(uriKey: String, credentials: StreamCredentials) {
        synchronized(lock) { entries[uriKey] = credentials }
    }

    /** Credentials stored for [uriKey], or null when none were handed over for this address. */
    fun get(uriKey: String): StreamCredentials? = synchronized(lock) { entries[uriKey] }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HolderEntryPoint {
        fun streamCredentialHolder(): StreamCredentialHolder
    }

    companion object {
        internal const val CAPACITY = 8
        private const val LOAD_FACTOR = 0.75f

        /** For classes Hilt does not construct, such as [AudioServiceController]. */
        fun from(context: Context): StreamCredentialHolder =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                HolderEntryPoint::class.java,
            ).streamCredentialHolder()
    }
}

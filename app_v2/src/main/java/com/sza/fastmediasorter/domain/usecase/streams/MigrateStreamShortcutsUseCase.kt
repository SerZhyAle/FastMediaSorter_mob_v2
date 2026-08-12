package com.sza.fastmediasorter.domain.usecase.streams

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.sza.fastmediasorter.ui.streams.StreamsActivity
import com.sza.fastmediasorter.ui.streams.helpers.StreamShortcutPinManager
import com.sza.fastmediasorter.widget.StreamPlayLaunchActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S1471: rewrites the tap intent of stream shortcuts pinned before this ticket. A pinned shortcut keeps
 * the intent it was created with for as long as it lives and the user has no gesture to refresh it, so
 * without this pass the screen-less start would reach only shortcuts pinned after the update.
 */
class MigrateStreamShortcutsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Deliberately not gated on `isRequestPinShortcutSupported`: that answers whether the *current*
     * launcher will accept a new pin, not whether pins already exist. A user who pinned under one
     * launcher and moved to one that refuses new pins still has the old shortcut, and would never get
     * the migration. Enumerating an empty set costs a single binder call.
     */
    suspend operator fun invoke() {
        val migrated = ShortcutManagerCompat
            .getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
            .mapNotNull(::rebuild)
        Timber.d("S1471: pinned stream shortcut migration found %d stale pin(s)", migrated.size)
        if (migrated.isNotEmpty()) {
            ShortcutManagerCompat.updateShortcuts(context, migrated)
            Timber.i("MigrateStreamShortcutsUseCase: rewrote %d pinned stream shortcuts", migrated.size)
        }
    }

    /**
     * The rewritten shortcut, or null when the entry is not a stale stream pin.
     *
     * The icon survives because `updateShortcuts` leaves fields the update does not set alone - NOT
     * because the Builder copy constructor carries it: the platform hands a publisher its own shortcuts
     * back with the icon stripped, so the copy holds none. The distinction matters, since the icon a
     * shortcut was pinned with is the channel's favicon tile (S1067) and cannot be rebuilt from the id.
     * Anything that replaces rather than updates - `addDynamicShortcuts`, `pushDynamicShortcut` - would
     * therefore blank it.
     */
    private fun rebuild(shortcut: ShortcutInfoCompat): ShortcutInfoCompat? =
        shortcut.takeIf { it.id.startsWith(StreamShortcutPinManager.SHORTCUT_ID_PREFIX) }
            ?.intent
            ?.takeIf(::targetsStreamsScreen)
            ?.getStringExtra(StreamsActivity.EXTRA_STREAM_URL)
            ?.takeIf { it.isNotBlank() }
            ?.let { url ->
                ShortcutInfoCompat.Builder(shortcut)
                    .setIntent(StreamPlayLaunchActivity.createIntent(context, url))
                    .build()
            }

    /**
     * Matched on the component rather than on the action, because the trampoline reuses no action of
     * its own: an already-migrated shortcut would otherwise be rewritten on every start.
     */
    private fun targetsStreamsScreen(intent: Intent): Boolean =
        intent.component?.className == StreamsActivity::class.java.name
}

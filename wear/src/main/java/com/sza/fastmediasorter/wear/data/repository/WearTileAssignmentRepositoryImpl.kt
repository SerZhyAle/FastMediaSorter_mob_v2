package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.model.WearTileTargetRef
import com.sza.fastmediasorter.wear.domain.repository.WearTileAssignmentRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.tileAssignmentDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "wear_tile_assignments"
)

/**
 * Stores tile target assignments in DataStorePreferences, serialized with Gson.
 */
class WearTileAssignmentRepositoryImpl @Inject constructor(
    private val context: Context,
    private val gson: Gson
) : WearTileAssignmentRepository {

    private object PreferencesKeys {
        val RESOURCE_ASSIGNMENT = stringPreferencesKey("wear_tile_assignment_resource")
        val STREAM_ASSIGNMENT = stringPreferencesKey("wear_tile_assignment_stream")
    }

    /**
     * The store key of [kind], or null when the kind carries no target at all.
     *
     * S2511: null is not "nothing has been chosen yet" - it is "there is nothing here to choose". The two
     * shortcut grids draw a fixed catalog, so they own no key, and `FAVOURITES` addresses the whole list.
     * Before this the key was picked by an `if RESOURCE else STREAM`, under which any further kind would
     * have silently read and written the stream's assignment.
     */
    private fun keyFor(kind: WearTileKind) = when (kind) {
        WearTileKind.RESOURCE -> PreferencesKeys.RESOURCE_ASSIGNMENT
        WearTileKind.STREAM -> PreferencesKeys.STREAM_ASSIGNMENT
        WearTileKind.FAVOURITES, WearTileKind.PROGRAMS, WearTileKind.SECTIONS -> null
    }

    override suspend fun assignmentFor(kind: WearTileKind): WearTileTargetRef? = when (kind) {
        WearTileKind.FAVOURITES -> WearTileTargetRef.Favourites
        else -> keyFor(kind)?.let { key -> readAssignment(kind, key) }
    }

    private suspend fun readAssignment(
        kind: WearTileKind,
        key: Preferences.Key<String>
    ): WearTileTargetRef? {
        val json = context.tileAssignmentDataStore.data.map { prefs -> prefs[key] }.firstOrNull()
        return json?.let {
            runCatching {
                when (kind) {
                    WearTileKind.RESOURCE -> gson.fromJson(it, WearTileTargetRef.Resource::class.java)
                    WearTileKind.STREAM -> gson.fromJson(it, WearTileTargetRef.Stream::class.java)
                    // Unreachable - a kind with no key never gets here. Named rather than sent to an else
                    // so a future kind still has to be classified.
                    WearTileKind.FAVOURITES,
                    WearTileKind.PROGRAMS,
                    WearTileKind.SECTIONS -> null
                }
            }.getOrNull()
        }
    }

    override suspend fun assign(kind: WearTileKind, ref: WearTileTargetRef) {
        val key = keyFor(kind) ?: return
        val json = jsonFor(ref) ?: return
        context.tileAssignmentDataStore.edit { prefs ->
            prefs[key] = json
        }
    }

    /**
     * Null for the favourites list, which is addressed as a whole and so has nothing to serialize.
     *
     * The named locals are not ceremony: `assert-gson-persistence-contract.ps1` resolves the serialized
     * type at each `toJson` call site statically, and a smart-cast sealed-interface receiver reads to it as
     * an unresolvable type - which is how it fails, since a model it cannot name is a model it cannot check
     * for a keep rule.
     */
    private fun jsonFor(ref: WearTileTargetRef): String? = when (ref) {
        is WearTileTargetRef.Resource -> {
            val resourceTarget: WearTileTargetRef.Resource = ref
            gson.toJson(resourceTarget)
        }
        is WearTileTargetRef.Stream -> {
            val streamTarget: WearTileTargetRef.Stream = ref
            gson.toJson(streamTarget)
        }
        WearTileTargetRef.Favourites -> null
    }
}

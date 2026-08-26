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

    override suspend fun assignmentFor(kind: WearTileKind): WearTileTargetRef? {
        if (kind == WearTileKind.FAVOURITES) {
            return WearTileTargetRef.Favourites
        }
        val key = if (kind == WearTileKind.RESOURCE) {
            PreferencesKeys.RESOURCE_ASSIGNMENT
        } else {
            PreferencesKeys.STREAM_ASSIGNMENT
        }
        val json = context.tileAssignmentDataStore.data.map { prefs -> prefs[key] }.firstOrNull()
        val result: WearTileTargetRef? = if (json == null) {
            null
        } else {
            runCatching {
                when (kind) {
                    WearTileKind.RESOURCE -> {
                        val resourceTarget: WearTileTargetRef.Resource = gson.fromJson(
                            json,
                            WearTileTargetRef.Resource::class.java
                        )
                        resourceTarget
                    }
                    WearTileKind.STREAM -> {
                        val streamTarget: WearTileTargetRef.Stream = gson.fromJson(
                            json,
                            WearTileTargetRef.Stream::class.java
                        )
                        streamTarget
                    }
                    WearTileKind.FAVOURITES -> WearTileTargetRef.Favourites
                }
            }.getOrNull()
        }
        return result
    }

    override suspend fun assign(kind: WearTileKind, ref: WearTileTargetRef) {
        if (kind == WearTileKind.FAVOURITES || ref is WearTileTargetRef.Favourites) {
            return
        }
        val key = if (kind == WearTileKind.RESOURCE) {
            PreferencesKeys.RESOURCE_ASSIGNMENT
        } else {
            PreferencesKeys.STREAM_ASSIGNMENT
        }
        val json = when (ref) {
            is WearTileTargetRef.Resource -> {
                val resourceTarget: WearTileTargetRef.Resource = ref
                gson.toJson(resourceTarget)
            }
            is WearTileTargetRef.Stream -> {
                val streamTarget: WearTileTargetRef.Stream = ref
                gson.toJson(streamTarget)
            }
            WearTileTargetRef.Favourites -> ""
        }
        if (json.isEmpty()) return

        context.tileAssignmentDataStore.edit { prefs ->
            prefs[key] = json
        }
    }
}

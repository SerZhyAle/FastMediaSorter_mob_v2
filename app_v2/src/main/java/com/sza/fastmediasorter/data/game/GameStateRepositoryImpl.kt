package com.sza.fastmediasorter.data.game

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.sza.fastmediasorter.domain.game.GameBoardGenerator
import com.sza.fastmediasorter.domain.game.GameLevelConfig
import com.sza.fastmediasorter.domain.game.GameMode
import com.sza.fastmediasorter.domain.game.GameStateRepository
import com.sza.fastmediasorter.domain.game.GameStateSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameStateRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson,
    private val generator: GameBoardGenerator
) : GameStateRepository {

    override fun observeSnapshot(): Flow<GameStateSnapshot?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Timber.w(exception, "GameStateRepository: failed to read game state")
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> parseSnapshot(preferences[KEY_GAME_STATE]).snapshotOrNull() }

    override suspend fun loadSnapshot(): GameStateSnapshot? {
        val preferences = dataStore.data.first()
        return parseSnapshot(preferences[KEY_GAME_STATE]).snapshotOrNull()
    }

    override suspend fun loadOrCreate(config: GameLevelConfig): GameStateSnapshot {
        val preferences = dataStore.data.first()
        return when (val parsed = parseSnapshot(preferences[KEY_GAME_STATE])) {
            is SnapshotRead.Valid -> parsed.snapshot
            SnapshotRead.Missing -> createAndSave(config)
            is SnapshotRead.Invalid -> {
                Timber.w(parsed.cause, "GameStateRepository: resetting incompatible or malformed game state")
                createAndSave(config)
            }
        }
    }

    override suspend fun saveSnapshot(snapshot: GameStateSnapshot) {
        dataStore.edit { preferences ->
            preferences[KEY_GAME_STATE] = gson.toJson(snapshot)
        }
    }

    override suspend fun clearSnapshot() {
        dataStore.edit { preferences -> preferences.remove(KEY_GAME_STATE) }
    }

    override suspend fun loadMode(): GameMode {
        val preferences = dataStore.data.first()
        return GameMode.fromStorageName(preferences[KEY_GAME_MODE])
    }

    override suspend fun saveMode(mode: GameMode) {
        dataStore.edit { preferences -> preferences[KEY_GAME_MODE] = mode.name }
    }

    private suspend fun createAndSave(config: GameLevelConfig): GameStateSnapshot {
        val snapshot = GameStateSnapshot.fromLevelState(generator.createInitialState(config))
        saveSnapshot(snapshot)
        return snapshot
    }

    private fun parseSnapshot(raw: String?): SnapshotRead {
        if (raw.isNullOrBlank()) return SnapshotRead.Missing
        return try {
            val snapshot = gson.fromJson(raw, GameStateSnapshot::class.java)
                ?: return SnapshotRead.Invalid(IllegalStateException("empty game state payload"))
            if (snapshot.schemaVersion != GameStateSnapshot.CURRENT_SCHEMA_VERSION) {
                SnapshotRead.Invalid(IllegalStateException("unsupported game state schema ${snapshot.schemaVersion}"))
            } else {
                snapshot.toLevelState()
                SnapshotRead.Valid(snapshot)
            }
        } catch (exception: JsonSyntaxException) {
            SnapshotRead.Invalid(exception)
        } catch (exception: RuntimeException) {
            SnapshotRead.Invalid(exception)
        }
    }

    private sealed interface SnapshotRead {
        data object Missing : SnapshotRead
        data class Valid(val snapshot: GameStateSnapshot) : SnapshotRead
        data class Invalid(val cause: Throwable) : SnapshotRead
    }

    private fun SnapshotRead.snapshotOrNull(): GameStateSnapshot? = when (this) {
        is SnapshotRead.Valid -> snapshot
        SnapshotRead.Missing,
        is SnapshotRead.Invalid -> null
    }

    companion object {
        private val KEY_GAME_STATE = stringPreferencesKey("embedded_game_state")
        private val KEY_GAME_MODE = stringPreferencesKey("embedded_game_mode")
    }
}

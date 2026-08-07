package com.sza.fastmediasorter.data.game

import androidx.datastore.core.DataStore
import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.sza.fastmediasorter.domain.game.GameBoardGenerator
import com.sza.fastmediasorter.domain.game.GameLevelConfig
import com.sza.fastmediasorter.domain.game.GameStateSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GameStateRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: GameStateRepositoryImpl

    @Before
    fun setUp() {
        // S1449: okio storage, not File storage - File.renameTo cannot replace an existing
        // destination on Windows. See BrowseStateDataStoreTest for the full reasoning.
        dataStore = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                tempFolder.root.resolve("game_state.preferences_pb").toOkioPath()
            },
            scope = scope,
        )
        repository = GameStateRepositoryImpl(dataStore, Gson(), GameBoardGenerator())
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `loadSnapshot returns null when no game state exists`() = runTest {
        assertNull(repository.loadSnapshot())
    }

    @Test
    fun `save then load round-trips active level`() = runTest {
        val snapshot = GameStateSnapshot.fromLevelState(
            GameBoardGenerator().createInitialState(GameLevelConfig(width = 9, height = 9, seed = 8L)),
            updatedAtEpochMillis = 100L
        )

        repository.saveSnapshot(snapshot)

        assertEquals(snapshot, repository.loadSnapshot())
    }

    @Test
    fun `loadOrCreate creates default state for missing payload`() = runTest {
        val snapshot = repository.loadOrCreate(GameLevelConfig(width = 9, height = 9, seed = 1L))

        assertNotNull(snapshot)
        assertEquals(snapshot, repository.loadSnapshot())
    }

    @Test
    fun `loadOrCreate resets malformed payload`() = runTest {
        dataStore.edit { it[KEY_GAME_STATE] = "not-json" }

        val snapshot = repository.loadOrCreate(GameLevelConfig(width = 9, height = 9, seed = 2L))

        assertEquals(GameStateSnapshot.CURRENT_SCHEMA_VERSION, snapshot.schemaVersion)
        assertEquals(snapshot, repository.loadSnapshot())
    }

    @Test
    fun `loadOrCreate resets incompatible schema payload`() = runTest {
        val old = GameStateSnapshot.fromLevelState(
            GameBoardGenerator().createInitialState(GameLevelConfig(width = 9, height = 9, seed = 3L))
        ).copy(schemaVersion = -1)
        dataStore.edit { it[KEY_GAME_STATE] = Gson().toJson(old) }

        val snapshot = repository.loadOrCreate(GameLevelConfig(width = 9, height = 9, seed = 4L))

        assertEquals(GameStateSnapshot.CURRENT_SCHEMA_VERSION, snapshot.schemaVersion)
        assertEquals(4L, snapshot.config.seed)
    }

    companion object {
        private val KEY_GAME_STATE = stringPreferencesKey("embedded_game_state")
    }
}
# Phase 02 — Foundation

**Strategic spec:** [`../spec_player-keybinding-remapping.md`](../spec_player-keybinding-remapping.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03, 04, 05, 06, 07
**Steps done:** 0 / 8
**Started:** —
**Completed:** —

---

## Objective

Introduce the data-layer skeleton for key-binding remapping: unified `CommandId` namespace, `InputTrigger` model, `InputBinding` persistence, the Defaults Map File asset, resolution cache and Hilt wiring. **No engine migration, no UI.** After this phase the runtime can load defaults and user overrides into an in-memory lookup, but no dispatcher queries it yet.

---

## Prerequisites

- [ ] Phase 01 is `✅ Done`; all seven `temp/phase1/` artefacts reviewed.
- [ ] Strategic spec §10: merge policy, max bindings per command per device, and conflict policy are resolved in writing.
- [ ] Working tree clean or on feature branch for Phase 02.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/input/CommandId.kt` | New | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/input/InputTrigger.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/input/InputBinding.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/input/CommandGroup.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/input/DefaultsMapLoader.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/input/InputBindingDao.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/input/InputBindingEntity.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/input/InputBindingRepository.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/db/AppDatabase.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/db/migrations/Migration_<FROM>_<TO>.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/input/KeyBindingManager.kt` | New | ≤ 400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/InputBindingModule.kt` | New | ≤ 120 |
| `app_v2/src/main/assets/input/default_bindings.json` | New | ≤ 1500 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/input/KeyBindingManagerTest.kt` | New | ≤ 400 |

> Any file projected > 500 lines requires a timestamped backup in `temp/` before the edit.

---

## Steps

### Step 02.1 — Declare CommandId, CommandGroup, InputTrigger, InputBinding

**Files:** `domain/input/CommandId.kt`, `domain/input/CommandGroup.kt`, `domain/input/InputTrigger.kt`, `domain/input/InputBinding.kt`
**Depends on:** — start of phase (consumes `temp/phase1/commandid-candidates.md`)

**Prompt for developer:**

> Create four files under `app_v2/src/main/java/com/sza/fastmediasorter/domain/input/`:
>
> 1. `CommandId.kt` — a value class or typealias wrapping a `String`. Add a companion object with compile-time constants for every `CommandId` in `temp/phase1/commandid-candidates.md`. Use dotted lowercase (`playback.pause_play`). Group constants by `CommandGroup` comment banners so grep is easy.
> 2. `CommandGroup.kt` — `enum class CommandGroup { PLAYBACK_CORE, NAVIGATION, VIEW_ZOOM, AUDIO_SUBTITLES, SYSTEM_UI, SORTING_ACTIONS, VR_ONLY }` mirroring strategic §4.2.
> 3. `InputTrigger.kt` — `sealed class InputTrigger` with subclasses `Key(keyCode: Int, modifiers: Int = 0)`, `MouseButton(button: Int)`, `GamepadButton(button: Int)`, `GamepadAxis(axis: Int, direction: Int, threshold: Float)`, `VrEvent(xrEventType: Int)`. Include a `serialize(): String` and `deserialize(String)` pair — one round-trip format (e.g. `key:21:0`).
> 4. `InputBinding.kt` — `data class InputBinding(val commandId: CommandId, val trigger: InputTrigger, val source: BindingSource)` where `BindingSource = DEFAULT | OVERRIDE`.
>
> Zero business logic in any of these files. `CommandId` constant list MUST be 1-to-1 with `temp/phase1/commandid-candidates.md` row count.

**Verification:**

- `Glob` — all four files exist.
- `Grep -c "^    const val " app_v2/src/main/java/com/sza/fastmediasorter/domain/input/CommandId.kt` equals the `^| playback\.|^| nav\.|^| view\.|^| audio\.|^| system\.|^| sort\.|^| vr\.` row count in `temp/phase1/commandid-candidates.md` (exact match, off-by-one tolerated for header).
- `Grep "enum class CommandGroup"` matches exactly once in `CommandGroup.kt` and lists 7 values.
- `Grep "sealed class InputTrigger"` matches exactly once in `InputTrigger.kt`.
- `Grep "data class InputBinding"` matches exactly once in `InputBinding.kt`.
- `Grep -n "Log\.d\(" app_v2/src/main/java/com/sza/fastmediasorter/domain/input/` returns zero hits.

**Status:** `[ ]` not done

---

### Step 02.2 — Author the Defaults Map File asset

**Files:** `app_v2/src/main/assets/input/default_bindings.json` (new)
**Depends on:** Step 02.1 (consumes `temp/phase1/defaults-seed.md`)

**Prompt for developer:**

> Create `app_v2/src/main/assets/input/default_bindings.json`. Top-level JSON object:
>
> ```json
> {
>   "schema_version": 1,
>   "generated_at": "<YYYY-MM-DD>",
>   "bindings": [
>     {
>       "command_id": "playback.pause_play",
>       "group": "PLAYBACK_CORE",
>       "label_key": "cmd_playback_pause_play",
>       "flavor_gate": null,
>       "triggers": {
>         "keyboard": ["key:62:0", "key:66:0"],
>         "gamepad": ["gamepad_button:96"],
>         "mouse": [],
>         "vr": ["vr:0"]
>       }
>     }
>   ]
> }
> ```
>
> Every row from `temp/phase1/defaults-seed.md` becomes one entry. Empty device arrays are explicit `[]` — never omitted. `flavor_gate` values: `null`, `"photos_excluded"`, `"audio_required"`, `"vr_only"`. Maximum two triggers per device category per command (strategic §3.3).

**Verification:**

- `Glob` — asset file exists.
- Parse the file with `jq .schema_version default_bindings.json` (via Bash) — returns `1`.
- `jq '.bindings | length' default_bindings.json` equals the `CommandId` row count from Step 02.1.
- `jq '[.bindings[].triggers.keyboard | length] | max' default_bindings.json` ≤ 2.
- `jq '[.bindings[] | select(.flavor_gate == "vr_only")] | length' default_bindings.json` ≥ 10.

**Status:** `[ ]` not done

---

### Step 02.3 — Room entity, DAO, schema bump with migration

**Files:** `data/input/InputBindingEntity.kt`, `data/input/InputBindingDao.kt`, `data/db/AppDatabase.kt`, `data/db/migrations/Migration_<FROM>_<TO>.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Before editing `AppDatabase.kt`, create a timestamped backup in `temp/` (file is > 500 LOC). Read the current `@Database(version = N)` annotation — the new version is `N+1`.
>
> 1. `InputBindingEntity.kt` — `@Entity(tableName = "input_bindings", primaryKeys = ["command_id", "device"])` with columns `command_id: String`, `device: String` (`keyboard`/`gamepad`/`mouse`/`vr`), `slot: Int` (0 or 1), `trigger: String` (serialized form from Step 02.1), `updated_at: Long`.
> 2. `InputBindingDao.kt` — methods: `fun observeAll(): Flow<List<InputBindingEntity>>`, `suspend fun upsert(entity: InputBindingEntity)`, `suspend fun deleteByCommand(commandId: String)`, `suspend fun deleteAll()`.
> 3. `AppDatabase.kt` — add `InputBindingEntity::class` to the `entities = []` array; bump `version` to `N+1`; add the new `InputBindingDao` abstract accessor; register the new `Migration_<FROM>_<TO>` in `Room.databaseBuilder` where it is configured.
> 4. `Migration_<FROM>_<TO>.kt` — `object Migration_<N>_<N+1> : Migration(N, N+1) { override fun migrate(db) { db.execSQL("CREATE TABLE IF NOT EXISTS input_bindings (command_id TEXT NOT NULL, device TEXT NOT NULL, slot INTEGER NOT NULL, trigger TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (command_id, device, slot))") } }`. Do not rename prior migrations; append only.

**Verification:**

- `Glob` — all four files exist / modified.
- `Grep -n "version = " app_v2/src/main/java/com/sza/fastmediasorter/data/db/AppDatabase.kt` shows the new version number is exactly `N+1` — one hit.
- `Grep "object Migration_${N}_${N+1}"` matches exactly once in the new migration file.
- `Grep "CREATE TABLE IF NOT EXISTS input_bindings"` matches in migration.
- `Grep -n "Log\.d\(" app_v2/src/main/java/com/sza/fastmediasorter/data/input/ app_v2/src/main/java/com/sza/fastmediasorter/data/db/migrations/` returns zero hits.
- Backup exists at `temp/AppDatabase.kt.<timestamp>.backup`.

**Status:** `[ ]` not done

---

### Step 02.4 — Implement DefaultsMapLoader

**Files:** `data/input/DefaultsMapLoader.kt` (new)
**Depends on:** Step 02.2

**Prompt for developer:**

> Create class `DefaultsMapLoader` constructor-injected with `@ApplicationContext Context` and a JSON parser (`Gson` if already in DI, otherwise `kotlinx.serialization` — pick the one already registered in `di/NetworkModule.kt` or equivalent). Expose one method: `fun loadDefaults(): List<InputBinding>`. Reads `assets/input/default_bindings.json`, deserialises each trigger string via `InputTrigger.deserialize()`, yields one `InputBinding(..., source = BindingSource.DEFAULT)` per trigger slot. Must be synchronous and cheap (called once at app start) — no coroutines, no I/O beyond the single asset read.

**Verification:**

- `Glob` — `DefaultsMapLoader.kt` exists.
- `Grep "class DefaultsMapLoader"` matches exactly once.
- `Grep "fun loadDefaults"` matches exactly once in that file.
- `Grep "assets.open(\"input/default_bindings.json\")"` matches exactly once.
- `Grep -n "runBlocking|withContext|async|launch"` in this file returns zero hits (must be synchronous).

**Status:** `[ ]` not done

---

### Step 02.5 — Implement InputBindingRepository

**Files:** `data/input/InputBindingRepository.kt` (new)
**Depends on:** Step 02.3, Step 02.4

**Prompt for developer:**

> Create class `InputBindingRepository` constructor-injected with `InputBindingDao` and `DefaultsMapLoader`. Responsibilities:
>
> - `fun observeResolvedBindings(): Flow<List<InputBinding>>` — merges defaults with user overrides using the merge policy resolved in strategic §10. Overrides win per `(commandId, device, slot)` tuple. Result is the flat list of effective bindings.
> - `suspend fun setOverride(commandId: CommandId, device: String, slot: Int, trigger: InputTrigger)` — persists one row.
> - `suspend fun clearOverride(commandId: CommandId, device: String)` — removes all slots for one command/device.
> - `suspend fun clearAll()` — drops the whole override table (used by Phase 07 global reset).
>
> Dispatch handlers NEVER call this repository directly — they call `KeyBindingManager` (Step 02.6). Settings UI (Phase 06) calls the repository via a use-case layer.

**Verification:**

- `Glob` — file exists.
- `Grep "class InputBindingRepository"` matches exactly once.
- `Grep "fun observeResolvedBindings"` matches exactly once.
- `Grep "fun setOverride"` and `fun clearOverride` and `fun clearAll` each match exactly once.
- `Grep -n "Log\.d\(" app_v2/src/main/java/com/sza/fastmediasorter/data/input/InputBindingRepository.kt` returns zero hits.

**Status:** `[ ]` not done

---

### Step 02.6 — Implement KeyBindingManager (resolver cache)

**Files:** `core/input/KeyBindingManager.kt` (new)
**Depends on:** Step 02.5

**Prompt for developer:**

> Create class `KeyBindingManager` constructor-injected with `InputBindingRepository`. Responsibilities:
>
> - On construction: collect `observeResolvedBindings()` in a `CoroutineScope` provided by DI (application-scoped `CoroutineScope` with `SupervisorJob`). On every emission, rebuild two indexes:
>   - `triggerToCommand: Map<InputTrigger, CommandId>` for O(1) lookup on the hot path.
>   - `commandToTriggers: Map<CommandId, List<InputTrigger>>` for the UI (unused in this phase).
> - `fun resolve(trigger: InputTrigger, surface: InputSurface): CommandId?` — the hot-path method. Pure map lookup. Return `null` if no binding. Do **not** touch disk. Do **not** hop threads. `<1ms` p99 is the strategic §11 budget.
> - `fun resolveKeyAction(keyCode: Int, modifiers: Int, surface: InputSurface): CommandId?` — convenience wrapper that wraps `InputTrigger.Key(keyCode, modifiers)` and delegates to `resolve`. (Surface-scoping is applied in Phase 03; for now it is passed through as metadata.)
>
> No engine calls this in Phase 02 — only the unit test in Step 02.8.

**Verification:**

- `Glob` — file exists.
- `Grep "class KeyBindingManager"` matches exactly once.
- `Grep "fun resolve\\b"` matches exactly once in that file.
- `Grep -n "ioDispatcher|withContext(Dispatchers.IO)|runBlocking"` in this file returns zero hits (hot path must not hop threads).
- `Grep -n "Log\.d\(" app_v2/src/main/java/com/sza/fastmediasorter/core/input/KeyBindingManager.kt` returns zero hits.

**Status:** `[ ]` not done

---

### Step 02.7 — Hilt wiring for the input binding stack

**Files:** `di/InputBindingModule.kt` (new)
**Depends on:** Steps 02.3, 02.4, 02.5, 02.6

**Prompt for developer:**

> Create `InputBindingModule.kt`:
>
> - `@Module @InstallIn(SingletonComponent::class) object InputBindingModule`
> - `@Provides @Singleton fun provideInputBindingDao(db: AppDatabase): InputBindingDao = db.inputBindingDao()`
> - `@Provides @Singleton fun provideDefaultsMapLoader(@ApplicationContext ctx: Context, gson: Gson): DefaultsMapLoader = DefaultsMapLoader(ctx, gson)` (adjust to the project's JSON parser — see Step 02.4).
> - `@Provides @Singleton fun provideInputBindingRepository(dao, loader): InputBindingRepository = InputBindingRepository(dao, loader)`
> - `@Provides @Singleton fun provideKeyBindingManager(repo: InputBindingRepository, @ApplicationScope scope: CoroutineScope): KeyBindingManager = KeyBindingManager(repo, scope)`
>
> If the project does not yet have an application-scoped `CoroutineScope` with a qualifier, add the qualifier and provider in the same module.

**Verification:**

- `Glob` — file exists.
- `Grep "object InputBindingModule"` matches exactly once.
- `Grep -c "@Provides"` in this file returns 4 or 5 (depending on whether `@ApplicationScope` provider was added here).
- `./gradlew.bat compileStandardDebugKotlin` runs successfully (via `/build` skill — do not invoke gradle directly).

**Status:** `[ ]` not done

---

### Step 02.8 — Unit test: end-to-end default load + override merge

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/input/KeyBindingManagerTest.kt` (new)
**Depends on:** Step 02.6

**Prompt for developer:**

> Write a unit test that:
>
> 1. Fakes `InputBindingRepository` with three defaults and one override that collides with one of them.
> 2. Instantiates `KeyBindingManager` with a `TestCoroutineScope`.
> 3. Asserts that `resolve(<overridden trigger>)` returns the override's `CommandId`.
> 4. Asserts that `resolve(<non-overridden trigger>)` returns the default's `CommandId`.
> 5. Asserts that `resolve(<unknown trigger>)` returns `null`.
>
> Use JUnit4 + Turbine (already in project dependencies — see existing `*Test.kt` files).

**Verification:**

- `Glob` — test file exists.
- `Grep "class KeyBindingManagerTest"` matches exactly once.
- Run via `/build` skill: `./gradlew.bat :app_v2:testStandardDebugUnitTest --tests "*.KeyBindingManagerTest"` exits 0.
- `Grep -c "@Test"` returns ≥ 3.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles — `/build` skill reports green for `assembleStandardDebug`.
- [ ] Room migration round-trips: `./gradlew.bat :app_v2:testStandardDebugUnitTest` includes the existing migration tests and they all pass.
- [ ] `AppDatabase.kt` `@Database(version = ..)` is exactly the previous version + 1; a matching `Migration_<N>_<N+1>` object exists.
- [ ] Grep for `TODO(phase-02)` across `app_v2/` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`; new classes have `role` + `status` set via `set.ps1`.

---

## Handoff Notes to Next Phase

- `KeyBindingManager.resolve(...)` is the single entry point for engines in Phases 03/04/05. Engines MUST NOT read defaults directly, MUST NOT touch `InputBindingRepository`.
- `InputTrigger.Key(keyCode, modifiers)` is the canonical representation — engines must build it from `KeyEvent` the same way (a helper `InputTrigger.fromKeyEvent(event)` may be added in Phase 03 if needed).
- Merge policy is locked: overrides replace defaults per `(commandId, device, slot)` — confirm this matches strategic §10 resolution.
- The `default_bindings.json` asset is the single source of truth for defaults. Adding a new command in a later release means: add a `CommandId` constant + append a row to the JSON + bump `schema_version` if the structure changes.

---

## Rollback Plan

Phase 02 introduces a schema bump. Rollback:

1. Revert the phase commit(s).
2. If Room version was already bumped and a test run persisted the new schema on a dev device: `adb shell run-as com.sza.fastmediasorter rm /data/data/com.sza.fastmediasorter/databases/<db>` (dev devices only — never on user installs).
3. Restore `AppDatabase.kt` from `temp/AppDatabase.kt.<timestamp>.backup`.

No user-facing surface changed yet — Phase 02 is internal only.

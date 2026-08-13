# Phase 02 - Repository and Mapper

**Strategic spec:** [`../S0623_app-launch-panel-dialog.md`](../S0623_app-launch-panel-dialog.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, 04, 05
**Steps done:** 4 / 4
**Started:** 2026-06-23
**Completed:** 2026-06-23

---

## Objective

Expose the tile store through a repository abstraction with entity<->domain mapping and Hilt wiring. No package resolution, UseCase, or UI yet.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`AppLaunchPanelTileDao`, `AppLaunchPanelTile`, entity exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/AppLaunchPanelRepository.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AppLaunchPanelTileMapper.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AppLaunchPanelRepositoryImpl.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/AppLaunchPanelModule.kt` | New | ≤ 40 |

---

## Steps

### Step 02.1 - Define the repository interface

**Files:** `domain/repository/AppLaunchPanelRepository.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `interface AppLaunchPanelRepository` (domain layer, no Android imports) with: `fun observeTiles(): Flow<List<AppLaunchPanelTile>>`; `suspend fun setTile(tile: AppLaunchPanelTile)`; `suspend fun removeTile(slotIndex: Int)`; `suspend fun moveTile(fromSlot: Int, toSlot: Int)`; `suspend fun count(): Int`; `suspend fun replaceAll(tiles: List<AppLaunchPanelTile>)`. `moveTile` swaps the occupants of two slots (locked-view: positions are stable, move = reassign slotIndex). These cover the Edit-panel long-press operations (move/replace/remove, strategic §3.1.9).

**Verification:**

- `Glob` - file exists.
- `Grep` - `interface AppLaunchPanelRepository` matches once.
- `Grep` - `fun observeTiles(): Flow<List<AppLaunchPanelTile>>`, `suspend fun setTile(`, `suspend fun moveTile(`, `suspend fun removeTile(` present.
- `Grep -n "import android"` - zero hits.

**Status:** `[x] done`

---

### Step 02.2 - Add the entity<->domain mapper

**Files:** `data/repository/AppLaunchPanelTileMapper.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create an object or top-level functions `AppLaunchPanelTileEntity.toDomain(): AppLaunchPanelTile` and `AppLaunchPanelTile.toEntity(): AppLaunchPanelTileEntity`. Map `type` via `AppLaunchPanelTileType.fromName(entity.type, AppLaunchPanelTileType.RESERVED)` on the way in and `tile.type.name` on the way out. No business logic.

**Verification:**

- `Glob` - file exists.
- `Grep` - `fun AppLaunchPanelTileEntity.toDomain()` present.
- `Grep` - `fun AppLaunchPanelTile.toEntity()` present.
- `Grep` - `AppLaunchPanelTileType.fromName(` present.

**Status:** `[x] done`

---

### Step 02.3 - Implement the repository

**Files:** `data/repository/AppLaunchPanelRepositoryImpl.kt`
**Depends on:** Steps 02.1, 02.2

**Prompt for developer:**

> Create `class AppLaunchPanelRepositoryImpl @Inject constructor(private val dao: AppLaunchPanelTileDao) : AppLaunchPanelRepository`. `observeTiles` maps `dao.observeAll()` through `toDomain`. `setTile` upserts; `removeTile` calls `deleteBySlot`; `count` calls `dao.count()`; `replaceAll` clears then upserts each. `moveTile(from, to)` reads the current tiles via `dao.getAll()`, swaps the `slotIndex` of the two occupants (or moves one into an empty target), and persists - keep it a single suspend operation. Run no work on the main thread (DAO suspend funcs already dispatch on Room's executor).

**Verification:**

- `Glob` - file exists.
- `Grep` - `class AppLaunchPanelRepositoryImpl` and `: AppLaunchPanelRepository` present.
- `Grep` - `@Inject constructor(` present.
- `Grep` - `override fun observeTiles()` and `override suspend fun moveTile(` present.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[x] done`

---

### Step 02.4 - Bind the repository in Hilt

**Files:** `core/di/AppLaunchPanelModule.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Create `@Module @InstallIn(SingletonComponent::class) abstract class AppLaunchPanelModule` with `@Binds abstract fun bindAppLaunchPanelRepository(impl: AppLaunchPanelRepositoryImpl): AppLaunchPanelRepository`. This module also hosts later `@Binds`/`@Provides` for the panel UseCases if constructor injection is insufficient (Phase 03 reuses it).

**Verification:**

- `Glob` - file exists.
- `Grep` - `@Module` and `@InstallIn(SingletonComponent::class)` present.
- `Grep` - `bindAppLaunchPanelRepository` present.
- Build: `.\a.ps1 fk` exits 0 (Hilt graph resolves).

**Status:** `[x] done`

---

## Step Log

- 2026-06-23 - Steps 02.1-02.4 Verification PASS. New: AppLaunchPanelRepository.kt (domain), AppLaunchPanelTileMapper.kt, AppLaunchPanelRepositoryImpl.kt (data), AppLaunchPanelModule.kt (@Binds). `moveTile` is a locked-view slot swap. Build `.\a.ps1 fk` exit 0 - Hilt graph resolves.

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk`.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (deferred to Phase 07 - once per ticket).

---

## Handoff Notes to Next Phase

`AppLaunchPanelRepository` is injectable. Phase 03 consumes it from UseCases that add package resolution (label/icon/availability) and default seeding on top of the raw tile list.

---

## Rollback Plan

Revert phase commit(s). No schema or user-facing surface; pure DI + data wiring.

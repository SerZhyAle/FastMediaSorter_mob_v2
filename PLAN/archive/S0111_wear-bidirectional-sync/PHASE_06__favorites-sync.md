# Phase 06 — Favorites Sync (Watch → Phone)

**Strategic spec:** [`../S0111_wear-bidirectional-sync.md`](../S0111_wear-bidirectional-sync.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 07
**Steps done:** 11 / 11
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Watch players gain a favorite toggle button. Favorites marked or unmarked on the watch are sent to the phone as a delta payload via `WatchWearListenerService`; phone applies the delta to its `FavoritesRepository`. Watch stores its own favorites in a new SharedPreferences-backed repository.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearFavoritesPayload.kt` | New | ≤ 40 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/repository/WearFavoritesRepository.kt` | New | ≤ 40 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/data/repository/WearFavoritesRepositoryImpl.kt` | New | ≤ 120 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/domain/usecase/SendFavoritesDeltaUseCase.kt` | New | ≤ 80 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/di/WearAppModule.kt` | Modified | ≤ 230 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerViewModel.kt` | Modified | ≤ 330 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerScreen.kt` | Modified | ≤ 250 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/image/ImageViewerViewModel.kt` | Modified | ≤ 310 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/image/ImageViewerScreen.kt` | Modified | ≤ 240 |
| `wear/src/main/res/values/strings.xml` | Modified | — |
| `wear/src/main/res/values-ru/strings.xml` | Modified | — |
| `wear/src/main/res/values-uk/strings.xml` | Modified | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearFavoritesPayload.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ApplyWatchFavoritesDeltaUseCase.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/service/PhoneWearListenerService.kt` | Modified | ≤ 210 |

---

## Steps

### Step 6.1 — Create `WearFavoritesPayload` on both sides

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/domain/model/WearFavoritesPayload.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/WearFavoritesPayload.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create both files. In each, declare in the respective package:
> ```
> data class WearFavoriteDeltaItem(
>     val sourceId: String,
>     val filePath: String,
>     val isFavorite: Boolean,  // true = add, false = remove
>     val changedAt: Long
> )
> data class WearFavoritesDeltaPayload(
>     val items: List<WearFavoriteDeltaItem>
> )
> ```

**Verification:**

- `Glob` — both files exist.
- `Grep` — `data class WearFavoritesDeltaPayload` present in each file.
- `Grep` — `val isFavorite: Boolean` present in each file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: wear/.../model/WearFavoritesPayload.kt (+13 LOC), app_v2/.../model/WearFavoritesPayload.kt (+13 LOC). Dev log recorded.

---

### Step 6.2 — Create `WearFavoritesRepository` interface on watch

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/domain/repository/WearFavoritesRepository.kt`
**Depends on:** — start of phase (parallel with 6.1)

**Prompt for developer:**

> Create the listed file. Declare `interface WearFavoritesRepository` in package `com.sza.fastmediasorter.wear.domain.repository` with methods:
> - `suspend fun addFavorite(sourceId: String, filePath: String)`
> - `suspend fun removeFavorite(sourceId: String, filePath: String)`
> - `suspend fun isFavorite(sourceId: String, filePath: String): Boolean`
> - `suspend fun getPendingDelta(): List<WearFavoriteDeltaItem>`
> - `suspend fun clearPendingDelta()`

**Verification:**

- `Glob` — `wear/src/main/java/com/sza/fastmediasorter/wear/domain/repository/WearFavoritesRepository.kt` exists.
- `Grep` — `interface WearFavoritesRepository` matches.
- `Grep` — `suspend fun getPendingDelta` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: wear/.../repository/WearFavoritesRepository.kt (+11 LOC). Dev log recorded.

---

### Step 6.3 — Create `WearFavoritesRepositoryImpl`

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/data/repository/WearFavoritesRepositoryImpl.kt`
**Depends on:** Step 6.2

**Prompt for developer:**

> Create the listed file. Declare `class WearFavoritesRepositoryImpl @Inject constructor(@ApplicationContext private val context: Context, private val gson: Gson) : WearFavoritesRepository`.
>
> Use `EncryptedSharedPreferences` (same pattern as `NetworkSourceRepositoryImpl`) to store two JSON arrays under keys `"wear_favorites"` (current favorites: set of `"sourceId:filePath"` strings) and `"wear_favorites_delta"` (pending `List<WearFavoriteDeltaItem>` not yet sent to phone).
>
> Implement all five interface methods. `clearPendingDelta()` writes an empty list to `"wear_favorites_delta"`.

**Verification:**

- `Glob` — `wear/src/main/java/com/sza/fastmediasorter/wear/data/repository/WearFavoritesRepositoryImpl.kt` exists.
- `Grep` — `class WearFavoritesRepositoryImpl` matches.
- `Grep` — `wear_favorites_delta` key string present.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Files: wear/.../data/repository/WearFavoritesRepositoryImpl.kt (+95 LOC). Dev log recorded.

---

### Step 6.4 — Provide `WearFavoritesRepository` in `WearAppModule`

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/di/WearAppModule.kt`
**Depends on:** Step 6.3

**Prompt for developer:**

> In `WearAppModule`, add a `@Provides @Singleton` function that provides `WearFavoritesRepository` as `WearFavoritesRepositoryImpl`. Alternatively, use a `@Binds` abstract binding in a companion abstract module — follow whichever pattern the existing module uses (inspect `WearAppModule` to confirm).

**Verification:**

- `Grep` — `WearFavoritesRepository` provided in `WearAppModule.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 1/1 PASS. Files: wear/.../di/WearAppModule.kt (+5 LOC). Dev log recorded.

---

### Step 6.5 — Create `SendFavoritesDeltaUseCase` on watch

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/domain/usecase/SendFavoritesDeltaUseCase.kt`
**Depends on:** Steps 6.1, 6.3

**Prompt for developer:**

> Create the listed file. Declare `class SendFavoritesDeltaUseCase @Inject constructor(private val favoritesRepository: WearFavoritesRepository, private val context: Context, private val gson: Gson)`.
>
> Implement `suspend operator fun invoke(): Result<Int>` using `runCatching`:
> 1. Load delta via `favoritesRepository.getPendingDelta()`.
> 2. If empty, return `Result.success(0)`.
> 3. Build `WearFavoritesDeltaPayload(items = delta)`.
> 4. Build `WearEventEnvelope(eventType = WearDataLayerPaths.EVENT_FAVORITES, sentAt = ..., data = gson.toJson(payload).toByteArray())`.
> 5. Get connected nodes; if empty, error("No phone connected").
> 6. For each node: `Wearable.getMessageClient(context).sendMessage(node.id, WearDataLayerPaths.FAVORITES_DELTA, envelopeBytes).await()`.
> 7. Call `favoritesRepository.clearPendingDelta()`.
> 8. Return `Result.success(delta.size)`.
>
> Add `Timber.d("S0111: SendFavoritesDeltaUseCase — sending ${delta.size} favorite changes")`.

**Verification:**

- `Glob` — `wear/src/main/java/com/sza/fastmediasorter/wear/domain/usecase/SendFavoritesDeltaUseCase.kt` exists.
- `Grep` — `class SendFavoritesDeltaUseCase` matches.
- `Grep` — `Timber.d("S0111:` present.
- `Grep` — `clearPendingDelta` called.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Files: wear/.../usecase/SendFavoritesDeltaUseCase.kt (+46 LOC). Dev log recorded.

---

### Step 6.6 — Add favorite toggle to `AudioPlayerViewModel`

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerViewModel.kt`
**Depends on:** Steps 6.4, 6.5

**Prompt for developer:**

> Inject `WearFavoritesRepository` and `SendFavoritesDeltaUseCase` into `AudioPlayerViewModel`. Add `val isFavorite: StateFlow<Boolean>` (initialized `false`). Add `fun toggleFavorite()`:
> 1. Get current file's `sourceId` and `filePath` from `_uiState`.
> 2. If currently favorite: call `favoritesRepository.removeFavorite(...)`.
> 3. If not favorite: call `favoritesRepository.addFavorite(...)`.
> 4. Flip `_isFavorite` state.
> 5. Call `sendFavoritesDeltaUseCase()` in the same coroutine.
>
> On init, load initial favorite state via `favoritesRepository.isFavorite(...)` when `loadAudioFile` completes.

**Verification:**

- `Grep` — `toggleFavorite` present in `AudioPlayerViewModel.kt`.
- `Grep` — `WearFavoritesRepository` injected.
- `Grep` — `SendFavoritesDeltaUseCase` injected.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: wear/.../audio/AudioPlayerViewModel.kt (+22 LOC). Dev log recorded.

---

### Step 6.7 — Add favorite toggle button to `AudioPlayerScreen`

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/audio/AudioPlayerScreen.kt`
**Depends on:** Step 6.6

**Prompt for developer:**

> In `AudioPlayerContent` composable, observe `isFavorite` from the ViewModel. Add a `CompactChip` or `IconButton` with a heart icon (filled when favorite, outlined when not) alongside the existing playback controls. On tap: call `viewModel.toggleFavorite()`. Use `stringResource(R.string.wear_toggle_favorite)` for content description.

**Verification:**

- `Grep` — `toggleFavorite` called in `AudioPlayerScreen.kt`.
- `Grep` — `isFavorite` observed.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS. Files: wear/.../audio/AudioPlayerScreen.kt (+20 LOC). Dev log recorded.

---

### Step 6.8 — Add favorite toggle to `ImageViewerViewModel` and `ImageViewerScreen`

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/image/ImageViewerViewModel.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/player/image/ImageViewerScreen.kt`
**Depends on:** Steps 6.4, 6.5

**Prompt for developer:**

> Apply the same changes as Steps 6.6 and 6.7 to `ImageViewerViewModel` and `ImageViewerScreen`. The favorite toggle behavior is identical — same pattern, different player class. Use `mediaType = "IMAGE"` when labeling.

**Verification:**

- `Grep` — `toggleFavorite` present in `ImageViewerViewModel.kt`.
- `Grep` — `toggleFavorite` called in `ImageViewerScreen.kt`.
- `Grep` — `WearFavoritesRepository` injected in `ImageViewerViewModel.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: wear/.../image/ImageViewerViewModel.kt (+25 LOC), wear/.../image/ImageViewerScreen.kt (+20 LOC). Dev log recorded.

---

### Step 6.9 — Add favorite strings to watch locales

**Files:** `wear/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`
**Depends on:** Steps 6.7, 6.8

**Prompt for developer:**

> Add to all three watch locale files:
> - `wear_toggle_favorite` — "Toggle favorite" / "Избранное" / "Вибране"
> - `wear_favorite_sent` — "Favorite synced" / "Избранное синхронизировано" / "Вибране синхронізовано"

**Verification:**

- `Grep` — `wear_toggle_favorite` present in all three watch `strings.xml` files.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 1/1 PASS (3 files contain wear_toggle_favorite). Files: wear/.../values/strings.xml, values-ru/strings.xml, values-uk/strings.xml. Dev log recorded.

---

### Step 6.10 — Create `ApplyWatchFavoritesDeltaUseCase` on phone

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ApplyWatchFavoritesDeltaUseCase.kt`
**Depends on:** Step 6.1

**Prompt for developer:**

> Create the listed file. Declare `class ApplyWatchFavoritesDeltaUseCase @Inject constructor(private val favoritesRepository: FavoritesRepository)`.
>
> Implement `suspend operator fun invoke(payload: WearFavoritesDeltaPayload)`:
> For each `WearFavoriteDeltaItem` in `payload.items`:
> - If `isFavorite = true`: call `favoritesRepository.addFavorite(FavoritesEntity(uri = item.filePath, addedAt = item.changedAt))`. Inspect `FavoritesEntity` constructor before coding.
> - If `isFavorite = false`: call `favoritesRepository.removeFavorite(item.filePath)`.
>
> Add `Timber.d("S0111: ApplyWatchFavoritesDeltaUseCase — applied ${payload.items.size} changes")`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ApplyWatchFavoritesDeltaUseCase.kt` exists.
- `Grep` — `class ApplyWatchFavoritesDeltaUseCase` matches.
- `Grep` — `Timber.d("S0111:` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. Files: app_v2/.../usecase/ApplyWatchFavoritesDeltaUseCase.kt (+35 LOC). Dev log recorded.

---

### Step 6.11 — Wire favorites handler in `PhoneWearListenerService`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/service/PhoneWearListenerService.kt`
**Depends on:** Step 6.10

**Prompt for developer:**

> Inject `ApplyWatchFavoritesDeltaUseCase` and `Gson` into `PhoneWearListenerService`. Replace the stub `handleFavoritesDelta(data: ByteArray)` with a real implementation:
> 1. Deserialize `data` → `WearEventEnvelope` → inner data → `WearFavoritesDeltaPayload` via Gson.
> 2. Call `applyWatchFavoritesDeltaUseCase(payload)` in `serviceScope.launch`.
>
> On deserialization failure: log with `Timber.e` and swallow.

**Verification:**

- `Grep` — `handleFavoritesDelta` is non-stub in `PhoneWearListenerService.kt`.
- `Grep` — `ApplyWatchFavoritesDeltaUseCase` injected.
- `Grep` — `WearFavoritesDeltaPayload` imported.
- `Grep` — `Log\.d\(` returns zero hits in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Files: app_v2/.../service/PhoneWearListenerService.kt (+12 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every Step 06.* above is `[x] done`.
- [ ] Project compiles — run `/build` for both modules.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] String locale audit: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "wear_toggle_favorite"` exits 0.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] Catalogs regenerated for both modules.

---

## Handoff Notes to Next Phase

- Watch favorites are stored in `WearFavoritesRepositoryImpl` (EncryptedSharedPreferences).
- Delta is sent immediately after each toggle; no batching period.
- Phone `FavoritesRepository` receives changes via `ApplyWatchFavoritesDeltaUseCase`.
- `VideoPlayerViewModel` favorite toggle is deliberately deferred — video files on watch are edge-case; add in a follow-up if needed.

---

## Rollback Plan

Revert phase commit(s). No DB schema change on phone (favorites use existing Room entity). Watch favorites stored in SharedPreferences are orphaned but do not affect app stability.

# Specification: WEAR OS — Complete Development Master Plan

**Status:** Draft
**Date:** 2026-04-14
**Tier:** 4
**Scope:** All pending Wear OS companion (`wear/`) development + phone-side companion integration

> This document is the single source of truth for all Wear OS work.
> Phase 1 (Resource Sync) is further detailed in `PLAN/spec_wear_resource_sync.md`.
> All other phases are fully specified here.

---

## Progress Checklist

### Phase 1 — Resource Sync (Phone ↔ Watch Data Layer)
- [x] P1-01 · Add `play-services-wearable:18.1.0` to `app_v2/build.gradle.kts`
- [x] P1-02 · Create `WearSyncPayload.kt` (payload data classes)
- [x] P1-03 · Extend `NetworkSource` — add `domain` + `sshPrivateKey` fields
- [x] P1-04 · Add `upsertSource()` to `NetworkSourceRepository` interface
- [x] P1-05 · Implement `upsertSource()` in `NetworkSourceRepositoryImpl`
- [x] P1-06 · Create `WearableDataLayerRepository` interface + impl
- [x] P1-07 · Register `WearableDataLayerRepository` in `RepositoryModule`
- [x] P1-08 · Create `SendResourcesToWatchUseCase`
- [x] P1-09 · Create `ImportNetworkSourcesUseCase`
- [x] P1-10 · Create `PhoneWearListenerService`
- [x] P1-11 · Register `PhoneWearListenerService` in phone `AndroidManifest.xml`
- [x] P1-12 · Create `WatchWearListenerService`
- [x] P1-13 · Register `WatchWearListenerService` in wear `AndroidManifest.xml`
- [x] P1-14 · Create `WearSyncViewModel`
- [x] P1-15 · Create `BeamAnimationDialog`
- [x] P1-16 · Create `WearSyncSettingsFragment` + hook into `GeneralSettingsFragment`
- [x] P1-17 · Create `SyncTransferScreen`
- [x] P1-18 · Create `SyncResultScreen`
- [x] P1-19 · Extend `NetworkSourcesViewModel` — `requestSyncFromPhone()` + `syncState`
- [x] P1-20 · Extend `NetworkSourcesScreen` — "Sync from Phone" chip
- [x] P1-21 · Add `sync_transfer` + `sync_result` routes in `wear/MainActivity.kt`
- [x] P1-22 · Integrate haptic feedback (phone + watch)
- [x] P1-23 · Add string resources Phase 1 (EN / RU / UK)
- [x] P1-24 · Unit tests — `SendResourcesToWatchUseCaseTest` + `ImportNetworkSourcesUseCaseTest`
- [x] P1-25 · Update `docs/FEATURES.md` + RU + UK

### Phase 2 — Source CRUD Completeness
- [ ] P2-01 · Create `FtpConnectionTest.kt`
- [ ] P2-02 · Create `SftpConnectionTest.kt`
- [ ] P2-03 · Register FTP/SFTP stubs in `WearAppModule`
- [ ] P2-04 · Route FTP/SFTP in `NetworkSourceRepositoryImpl.testConnection`
- [ ] P2-05 · Add Phase 2 strings (EN / RU / UK)
- [ ] P2-06 · Add `deleteSource()` to `NetworkSourcesViewModel`
- [ ] P2-07 · Add delete confirmation dialog to `NetworkSourcesScreen`
- [ ] P2-08 · Create `AddNetworkSourceViewModel`
- [ ] P2-09 · Create `AddNetworkSourceScreen` (protocol picker)
- [ ] P2-10 · Add `add_network_source` route + alias in `wear/MainActivity.kt`

### Phase 3 — UX Polish
- [ ] P3-01 · Add `loading` / `no_network_sources` / `error` / `retry` strings (EN / RU / UK)
- [ ] P3-02 · Replace 4 hardcoded strings in `NetworkSourcesScreen`
- [ ] P3-03 · Add slideshow interval stepper to `SettingsScreen`
- [ ] P3-04 · Replace emoji with `Icon` in `HomeScreen`

### Phase 4 — Architecture Hardening
- [ ] P4-01 · Fix `SettingsViewModel` parallel-collect race → `combine()`
- [ ] P4-02 · Add `observeSources(): Flow<…>` to `NetworkSourceRepository`
- [ ] P4-03 · Implement `observeSources()` via `MutableSharedFlow` in repository impl
- [ ] P4-04 · Migrate `NetworkSourcesViewModel` to reactive `observeSources()`
- [ ] P4-05 · Fix retry in `NetworkSourcesScreen` to call `viewModel.retryLoad()`

---

## 1. Current State (AS-IS)

### 1.1 What Exists Today

| Area | File(s) | Status |
|------|---------|--------|
| Navigation | `wear/MainActivity.kt` — `SwipeDismissableNavHost` | 8 routes; functional |
| Home screen | `wear/ui/home/HomeScreen.kt` | Chips with emoji labels; no icons |
| Browse | `wear/ui/browse/BrowseScreen.kt` + `BrowseViewModel.kt` | Local + SMB; functional |
| Players | `AudioPlayerScreen`, `VideoPlayerScreen`, `ImageViewerScreen` | Functional |
| Network sources list | `wear/ui/network/NetworkSourcesScreen.kt` | M2; no delete/edit; no sync button |
| Add source | `wear/ui/network/AddSmbScreen.kt` | **SMB only**; manual keyboard entry |
| Settings | `wear/ui/settings/SettingsScreen.kt` | Toggles work; **no interval stepper** |
| Network source storage | `wear/data/preferences/NetworkSourceRepositoryImpl.kt` | CRUD via EncryptedSharedPreferences; **no upsert**; **testConnection SMB-only** |
| Network source model | `wear/domain/model/NetworkSource.kt` | SMB/FTP/SFTP/GOOGLE_DRIVE; **no `domain` or `sshPrivateKey` fields** |
| DI | `wear/di/WearAppModule.kt` | Hilt; covers current dependencies |
| Localization | `values/strings.xml`, `values-ru/`, `values-uk/` | **Done** (VIII.3, March 22) |
| Phone ↔ Watch bridge | — | **Zero integration**: no `play-services-wearable` in phone, no `WearableListenerService` on either side |

### 1.2 Hardcoded Strings (Tech Debt)

| File | Hardcoded text | Should be |
|------|---------------|-----------|
| `NetworkSourcesScreen.kt:84` | `"⏳ Loading..."` | `stringResource(R.string.loading)` |
| `NetworkSourcesScreen.kt:153` | `"No network sources configured"` | `stringResource(R.string.no_network_sources)` |
| `NetworkSourcesScreen.kt:183` | `"Error"` | `stringResource(R.string.error)` |
| `NetworkSourcesScreen.kt:208` | `"Retry"` | `stringResource(R.string.retry)` |

### 1.3 Architecture Issues

| Issue | Location | Impact |
|-------|----------|--------|
| Parallel `viewModelScope.launch { flow.collect {} }` × 7 | `SettingsViewModel.kt:40–73` | Race condition: multiple flows emit simultaneously, last writer wins on `copy()` — can silently drop updates |
| `testConnection` throws `UnsupportedOperationException` for FTP/SFTP | `NetworkSourceRepositoryImpl.kt:98` | FTP/SFTP sources can be stored but never tested |
| One-shot `getAllSources()` in ViewModel | `NetworkSourcesViewModel.kt:33` | Changes from `WatchWearListenerService` won't refresh the list without explicit reload |
| `NetworkSourcesViewModel.onRetry` navigates back | `NetworkSourcesScreen.kt:67` | Error state has no retry path |

---

## 2. Strategic Vision (TO-BE)

### 2.1 Experience Goal

The Wear OS companion should feel like a **first-class app**, not a demo. The user's experience arc:

1. **First launch**: Phone → Settings → "Wear Companion" → tap "Push to Watch" → all network sources appear on watch in ≤ 10 s with a satisfying animation.
2. **Daily use**: Open watch, tap Network Storage, browse SMB/FTP/SFTP shares seamlessly.
3. **Source management on watch**: Add, edit, or delete individual sources without touching the phone.
4. **Settings**: Adjust slideshow interval, toggle media types — all controls visible and interactive.

### 2.2 Technical Goal

- **No manual credential entry** on watch keyboard (solved by Phase 1 sync).
- **Full CRUD** for network sources on watch (solved by Phase 2).
- **Zero hardcoded strings** — fully localized EN/RU/UK (solved by Phase 3).
- **Reactive data layer** — source list updates automatically after sync (solved by Phase 4).

---

## 3. Scope Overview

| Phase | Theme | Priority | Ref |
|-------|-------|----------|-----|
| **Phase 1** | Resource Sync (Phone ↔ Watch Data Layer) | Must-have | `spec_wear_resource_sync.md` |
| **Phase 2** | Source CRUD Completeness (Delete + FTP/SFTP Add) | Should-have | §6 this doc |
| **Phase 3** | UX Polish (hardcoded strings, Settings controls) | Should-have | §7 this doc |
| **Phase 4** | Architecture Hardening (reactive repo, SettingsVM fix) | Nice-to-have | §8 this doc |

Phase 1 is the hard dependency for phases 2–4 in terms of `upsertSource` and `NetworkSource` model changes; phases 2–4 are otherwise independent of each other.

---

## 4. Flavor & API Level Scope

### 4.1 Affected Flavors

| Flavor | Phases affected | Notes |
|--------|:--------------:|-------|
| `standard` | 1, 2, 3, 4 | Primary target; full feature set |
| `lite` | 1, 2, 3, 4 | Has SMB/FTP/SFTP browsing; no cloud — safe |
| `photos` | 3 only | No network sources; only UI polish applies |
| `legacy` | ❌ | API 23–25 cannot pair with Wear OS 2.0+ companion |

### 4.2 API Levels

| API level | Constraint |
|-----------|-----------|
| 26+ (standard minSdk) | Default path for all phone-side changes |
| 28+ (Wear OS minSdk) | All watch-side code runs here |
| 33+ (Android 13) | `POST_NOTIFICATIONS` guard before sync-result notification |

---

## 5. Phase 1 — Resource Sync (Reference)

**Status:** Fully specified.  
**Full specification:** `PLAN/spec_wear_resource_sync.md`  
**Summary of deliverables:**

| Deliverable | Side | Key File |
|-------------|------|----------|
| `WearSyncPayload` + `WearNetworkSourcePayload` | Watch | `wear/domain/model/WearSyncPayload.kt` (new) |
| `WearableDataLayerRepository` | Phone | `app_v2/data/wear/WearableDataLayerRepositoryImpl.kt` (new) |
| `SendResourcesToWatchUseCase` | Phone | `app_v2/domain/usecase/SendResourcesToWatchUseCase.kt` (new) |
| `PhoneWearListenerService` | Phone | `app_v2/service/PhoneWearListenerService.kt` (new) |
| `WearSyncSettingsFragment` + `BeamAnimationDialog` | Phone | `app_v2/ui/settings/fragments/` (new) |
| `ImportNetworkSourcesUseCase` | Watch | `wear/domain/usecase/ImportNetworkSourcesUseCase.kt` (new) |
| `WatchWearListenerService` | Watch | `wear/data/wear/WatchWearListenerService.kt` (new) |
| `SyncTransferScreen` + `SyncResultScreen` | Watch | `wear/ui/network/` (new) |
| `NetworkSource` — add `domain`, `sshPrivateKey` | Watch | `wear/domain/model/NetworkSource.kt` (extend) |
| `NetworkSourceRepository.upsertSource` | Watch | `wear/domain/repository/NetworkSourceRepository.kt` (extend) |
| "Sync from Phone" chip in `NetworkSourcesScreen` | Watch | `wear/ui/network/NetworkSourcesScreen.kt` (extend) |

**Phase 1 must be implemented first.** It establishes `upsertSource` and the extended `NetworkSource` model that phases 2–4 depend on.

---

## 6. Phase 2 — Source CRUD Completeness

### 6.1 Problem

`deleteSource(id)` exists in the repository but has **no UI entry point** on the watch. There is no way to remove a misconfigured source without uninstalling the app or using ADB. Additionally, `AddSmbScreen` only supports SMB — users with FTP or SFTP shares must use phone sync (Phase 1) or have no option at all on the watch.

### 6.2 Deliverables

1. **Delete source**: Long-press (or swipe + action chip) on a source in `NetworkSourcesScreen` → confirmation dialog → `NetworkSourceRepository.deleteSource`.
2. **Multi-protocol Add screen**: Rename `AddSmbScreen` → `AddNetworkSourceScreen`; add a protocol picker chip row (SMB / FTP / SFTP) that shows/hides protocol-specific fields.
3. **`testConnection` for FTP/SFTP**: Add `FtpDataSource` and `SftpDataSource` stubs to `wear/data/network/` or reuse the phone-side libraries (SMBJ/SSHJ/Apache Commons Net are already in `wear/build.gradle.kts` transitively via the SMB dep).
4. **Navigation**: Add `add_network_source` route (replaces `add_smb`); keep `add_smb` as alias to avoid breaking existing back-stack entries.

### 6.3 New / Changed Components

| Component | Change | Location |
|-----------|--------|----------|
| `AddNetworkSourceScreen` | New — replaces `AddSmbScreen`; protocol picker + dynamic fields | `wear/ui/network/AddNetworkSourceScreen.kt` |
| `AddNetworkSourceViewModel` | New — replaces `SmbConnectionViewModel`; protocol-aware validation | `wear/ui/network/viewmodel/AddNetworkSourceViewModel.kt` |
| `NetworkSourcesScreen` | Extend — add delete action (long-press → `AlertDialog` → confirm) | `wear/ui/network/NetworkSourcesScreen.kt` |
| `NetworkSourcesViewModel` | Extend — add `deleteSource(id: String)` | `wear/ui/network/viewmodel/NetworkSourcesViewModel.kt` |
| `SmbConnectionViewModel` | Keep as-is (backward compat); mark `@Deprecated` | `wear/ui/network/viewmodel/SmbConnectionViewModel.kt` |
| `FtpConnectionTest` | New stub — `suspend fun testFtp(source: NetworkSource): Result<Boolean>` | `wear/data/network/ftp/FtpConnectionTest.kt` |
| `SftpConnectionTest` | New stub — `suspend fun testSftp(source: NetworkSource): Result<Boolean>` | `wear/data/network/sftp/SftpConnectionTest.kt` |
| `NetworkSourceRepositoryImpl` | Extend — update `testConnection` to route FTP/SFTP to stubs | `wear/data/preferences/NetworkSourceRepositoryImpl.kt` |
| `WearAppModule` | Extend — bind `FtpConnectionTest`, `SftpConnectionTest` | `wear/di/WearAppModule.kt` |
| `MainActivity` | Extend — add `add_network_source` route | `wear/MainActivity.kt` |

### 6.4 Delete UX Flow

```
NetworkSourcesScreen — user long-presses a source chip
  → showDeleteConfirmation(source)
      ConfirmationOverlay (Wear M2 Dialog or custom Alert):
        Title: "Delete [source.name]?"
        Chip "Delete" (red) → NetworkSourcesViewModel.deleteSource(id)
        Chip "Cancel" → dismiss
  ← NetworkSourcesViewModel reloads list
  ← Success: source removed from list
```

### 6.5 Protocol Picker UX (AddNetworkSourceScreen)

```
ScalingLazyColumn:
  [Protocol]  [ SMB | FTP | SFTP ]   ← Row of ToggleChips, one selected
  [Server]    BasicTextField
  [Port]      BasicTextField (pre-filled: 445/21/22 based on protocol)
  [Username]  BasicTextField
  [Password]  BasicTextField (masked)
  [Share]     BasicTextField (SMB only — hidden for FTP/SFTP)
  [Domain]    BasicTextField (SMB only — hidden for FTP/SFTP)
  [Key Auth]  ToggleChip (SFTP only — "Use SSH Key")
              ↳ if enabled: [Private Key] multiline BasicTextField
  [Name]      BasicTextField (display name)
  [Test]  [Save]
```

### 6.6 Strings — Phase 2

New string keys needed in all three locales (`values/`, `values-ru/`, `values-uk/`):

| Key | EN |
|-----|----|
| `delete_source` | "Delete Source" |
| `delete_source_confirm` | "Delete \"%s\"?" |
| `protocol` | "Protocol" |
| `port` | "Port" |
| `domain` | "Domain" |
| `use_ssh_key` | "Use SSH Key" |
| `ssh_private_key` | "Private Key" |
| `add_network_source` | "Add Source" |
| `ftp_connection` | "FTP" |
| `sftp_connection` | "SFTP" |
| `connection_test_not_supported` | "Test not available for this protocol" |

---

## 7. Phase 3 — UX Polish

### 7.1 Fix Hardcoded Strings

All hardcoded English text in `wear/ui/` must use `stringResource(R.string.*)`. This is a **localization correctness** fix — the app is already translated, but these strings bypass translation.

| File | Line | Current | New key |
|------|------|---------|---------|
| `NetworkSourcesScreen.kt` | 84 | `"⏳ Loading..."` | `R.string.loading` |
| `NetworkSourcesScreen.kt` | 153 | `"No network sources configured"` | `R.string.no_network_sources` |
| `NetworkSourcesScreen.kt` | 183 | `"Error"` | `R.string.error` |
| `NetworkSourcesScreen.kt` | 208 | `"Retry"` | `R.string.retry` |

New string keys:

| Key | EN | RU | UK |
|-----|----|----|----|
| `loading` | "Loading…" | "Загрузка…" | "Завантаження…" |
| `no_network_sources` | "No network sources. Add one or sync from phone." | "Нет источников. Добавьте или синхронизируйте с телефоном." | "Немає джерел. Додайте або синхронізуйте з телефоном." |
| `error` | "Error" | "Ошибка" | "Помилка" |
| `retry` | "Retry" | "Повтор" | "Повторити" |

> Note: `no_network_sources` text is updated to hint at "sync from phone" (introduced in Phase 1). Phases 1 and 3 should be applied together for the best empty-state UX.

### 7.2 Slideshow Interval Stepper in SettingsScreen

**Current state**: `SettingsScreen.kt:147` shows `"Interval: %ds"` as text only. There is no control to change the value. `SettingsViewModel.setSlideshowInterval(seconds: Int)` exists but is never called from UI.

**Change**: Replace the static `Text` with a stepper row — two `CompactChip` buttons (`−` and `+`) flanking the current value. Allowed values: 3, 5, 10, 15, 20, 30, 60 s (step through array).

```
[ − ]  Interval: 10s  [ + ]
```

Files changed: `SettingsScreen.kt` only. No ViewModel change needed (`setSlideshowInterval` already exists).

### 7.3 HomeScreen Emoji → Icons

**Current state**: `HomeScreen.kt:35–38` prepends emoji to chip labels (`"🎵 Music"`, `"🎬 Videos"`, etc.).

**Change**: Remove emoji prefixes. Use `Chip(icon = { Icon(...) })` parameter instead. Map:
- Music → `Icons.Default.MusicNote`
- Videos → `Icons.Default.VideoLibrary`
- Photos → `Icons.Default.Image`
- Network Storage → `Icons.Default.Storage`
- Settings → `Icons.Default.Settings`

This is **cosmetic** but improves accessibility (TalkBack reads the emoji aloud as "Musical note sign") and looks more native on Wear OS.

### 7.4 Strings — Phase 3

Beyond the four hardcoded-string fixes above, check `SettingsScreen.kt` for any remaining hardcoded text (none found in current scan).

---

## 8. Phase 4 — Architecture Hardening

### 8.1 Fix SettingsViewModel Race Condition

**Problem**: `SettingsViewModel.loadSettings()` launches 7 independent coroutines, each collecting from a separate `Flow<Boolean/Int>`. When multiple preferences change simultaneously (e.g., on first load), the `copy()` calls race — one coroutine overwrites another's update.

**Fix**: Combine all preference flows into a single `combine()` emission:

```kotlin
// Replace 7 parallel launches with:
viewModelScope.launch {
    combine(
        preferencesRepository.isAudioEnabled,
        preferencesRepository.isVideoEnabled,
        preferencesRepository.isImagesEnabled,
        preferencesRepository.isSlideshowEnabled,
        preferencesRepository.slideshowIntervalSeconds,
        preferencesRepository.slideshowWaitForFinish,
        preferencesRepository.downloadAlbumArt
    ) { audio, video, images, slideshow, interval, waitForFinish, albumArt ->
        _uiState.value.copy(
            isAudioEnabled = audio,
            isVideoEnabled = video,
            isImagesEnabled = images,
            isSlideshowEnabled = slideshow,
            slideshowIntervalSeconds = interval,
            slideshowWaitForFinish = waitForFinish,
            downloadAlbumArt = albumArt,
            isLoading = false
        )
    }.collect { newState -> _uiState.value = newState }
}
```

> Note: `combine` with 7 args is at the limit of the overloaded extensions. If a future preference is added, switch to `combineTransform` or the `combine(flows: List)` overload.

**File**: `wear/ui/settings/SettingsViewModel.kt` only. No interface changes.

### 8.2 Reactive NetworkSourceRepository

**Problem**: `NetworkSourcesViewModel.loadSources()` is a one-shot `suspend fun` — it loads once and does not observe further changes. When `WatchWearListenerService` (Phase 1) imports new sources, the screen does not refresh unless the user navigates away and back.

**Fix**: Add `fun observeSources(): Flow<List<NetworkSource>>` to the repository interface and implement it using `MutableSharedFlow` in `NetworkSourceRepositoryImpl`. `NetworkSourcesViewModel` switches from one-shot load to `collectAsState` on the flow.

```kotlin
// NetworkSourceRepository interface:
fun observeSources(): Flow<List<NetworkSource>>

// NetworkSourceRepositoryImpl:
private val _sourcesFlow = MutableSharedFlow<List<NetworkSource>>(
    replay = 1, 
    onBufferOverflow = DROP_OLDEST
)
override fun observeSources(): Flow<List<NetworkSource>> = _sourcesFlow

// After every saveSources() call, emit:
_sourcesFlow.tryEmit(sources)
```

`WatchWearListenerService` doesn't need to call any ViewModel method — the repository emission propagates automatically.

**Files**: `NetworkSourceRepository.kt`, `NetworkSourceRepositoryImpl.kt`, `NetworkSourcesViewModel.kt`.

### 8.3 Error State — Add Retry

**Current**: `NetworkSourcesScreen.ErrorContent.onRetry` calls `navController.popBackStack()` — abandons the screen entirely.

**Fix**: `NetworkSourcesViewModel` exposes `fun retryLoad()` that re-calls `loadSources()`. `NetworkSourcesScreen.ErrorContent` calls `viewModel.retryLoad()` instead of `navController.popBackStack()`.

**Files**: `NetworkSourcesViewModel.kt`, `NetworkSourcesScreen.kt`.

---

## 9. Cross-Cutting: Architecture Compliance

| Rule | Phase 1 | Phase 2 | Phase 3 | Phase 4 |
|------|:-------:|:-------:|:-------:|:-------:|
| No business logic in Activities/Fragments | ✅ | ✅ | ✅ | ✅ |
| Naming: `VerbNounUseCase`, `NounViewModel`, `NounVerbManager` | ✅ | ✅ | N/A | N/A |
| Data flow `UI → ViewModel → UseCase → Repo → DS` | ✅ | ✅ | N/A | ✅ |
| Timber only (no `Log.d`) | ✅ | ✅ | ✅ | ✅ |
| Room schema increment | N/A | N/A | N/A | N/A |
| `StateFlow` for state, `SharedFlow` for events | ✅ | ✅ | N/A | ✅ |
| Hilt bindings declared in `@Module` | ✅ | ✅ | N/A | N/A |
| File size ≤ 1000 lines | ✅ | ✅ | ✅ | ✅ |

---

## 10. Cross-Cutting: Accessibility

**Phase 1 (SyncTransferScreen, SyncResultScreen, BeamAnimationDialog)**:
- All `Chip` elements: `Modifier.semantics { contentDescription = "…" }`
- Animations disabled when `Settings.Global.TRANSITION_ANIMATION_SCALE == 0`
- `SyncResultScreen`: call `LocalAccessibilityManager.current.announceForAccessibility("Sync complete: X added")` on completion

**Phase 2 (AddNetworkSourceScreen)**:
- Protocol picker chips: `contentDescription = "Select protocol: SMB/FTP/SFTP"`
- Delete confirmation dialog: focus-trapped, confirm/cancel reachable via rotary
- Password field: `contentDescription = "Password (hidden)"`

**Phase 3 (HomeScreen icon change)**:
- All `Icon` composables: `contentDescription = null` if chip label already describes the action (TalkBack reads the label)
- Remove emoji from strings — emoji are read as long Unicode descriptions by TalkBack

**Phase 4 (SettingsScreen stepper)**:
- `−` and `+` buttons: `contentDescription = "Decrease interval"` / `"Increase interval"`
- Current value `Text`: `Modifier.semantics { contentDescription = "Slideshow interval: Xs" }`

---

## 11. Cross-Cutting: Localization

All phases must maintain EN/RU/UK parity in:
- `wear/src/main/res/values/strings.xml`
- `wear/src/main/res/values-ru/strings.xml`
- `wear/src/main/res/values-uk/strings.xml`

New keys per phase:
- Phase 1: ~10 keys (sync states, result screen) — detailed in `spec_wear_resource_sync.md §13 step 23`
- Phase 2: ~11 keys (protocol picker, delete, port, domain) — listed in §6.6
- Phase 3: ~4 keys (loading, empty state, error, retry) — listed in §7.4
- Phase 4: ~2 keys (stepper accessibility labels)

---

## 12. Risk Analysis

| Risk | Phase | Likelihood | Mitigation |
|------|-------|:----------:|-----------|
| Data Layer setup fails (phone GMS absent) | 1 | Very low | Catch `ApiException`; show "Device not supported" |
| `combine(7 flows)` breaks if repo emits on init | 4 | Low | `replay = 1` on `MutableSharedFlow`; test with cold/hot flow mock |
| `AddNetworkSourceScreen` FTP/SFTP `testConnection` stub returns false positive | 2 | Med | Mark as "Basic connectivity check"; document that full browse test is on first use |
| Delete confirmation dialog not shown if chip long-press is intercepted by swipe-dismiss | 2 | Med | Use `SwipeToDismissBox` exclusion zone or a secondary "⋯" action chip instead of long-press |
| `observeSources()` `MutableSharedFlow` emits stale state on cold collector | 4 | Low | `replay = 1` ensures new collectors get last emission; initial `loadSources()` still needed on first subscription |
| Phase 3 empty-state string change ("sync from phone") confuses users on `photos`/`legacy` flavors | 3 | Low | The "sync from phone" hint is part of the string value, not a button — harmless for unsupported flavors |

---

## 13. Complete Tactical Implementation Plan

Steps are ordered by dependency. Each step ends with the mandatory dev log command.

---

### ◆ Phase 1 Steps (Resource Sync) — See `spec_wear_resource_sync.md §13`

Execute steps 1–25 from `spec_wear_resource_sync.md` in full before starting Phase 2.

**Phase 1 entry gate**: confirmed `upsertSource` working + `NetworkSource` has `domain` + `sshPrivateKey`.

---

### ◆ Phase 2 Steps (Source CRUD)

**Step P2-1** — Create `FtpConnectionTest.kt` at `wear/data/network/ftp/`:
```kotlin
class FtpConnectionTest {
    suspend fun test(source: NetworkSource): Result<Boolean> = withContext(Dispatchers.IO) {
        // Stub: TCP connect to server:port; no full FTP login (Apache Commons Net not yet in wear)
        runCatching { Socket(source.server, source.port).use { true } }
    }
}
```
```
.\scripts\add_to_dev_log.ps1 "wear/data/network/ftp/FtpConnectionTest.kt" "FtpConnectionTest" "Add FTP connection test stub"
```

**Step P2-2** — Create `SftpConnectionTest.kt` at `wear/data/network/sftp/`:
Similar stub — TCP connect to `server:port`.
```
.\scripts\add_to_dev_log.ps1 "wear/data/network/sftp/SftpConnectionTest.kt" "SftpConnectionTest" "Add SFTP connection test stub"
```

**Step P2-3** — Register `FtpConnectionTest` and `SftpConnectionTest` in `WearAppModule`:
```kotlin
@Provides @Singleton fun provideFtpConnectionTest() = FtpConnectionTest()
@Provides @Singleton fun provideSftpConnectionTest() = SftpConnectionTest()
```
```
.\scripts\add_to_dev_log.ps1 "wear/di/WearAppModule.kt" "WearAppModule" "Add FTP/SFTP connection test Hilt bindings"
```

**Step P2-4** — Extend `NetworkSourceRepositoryImpl.testConnection`:
Route `NetworkSourceType.FTP` → `FtpConnectionTest.test()`, `SFTP` → `SftpConnectionTest.test()`.
Inject both via constructor (update `WearAppModule.provideNetworkSourceRepository` signature).
```
.\scripts\add_to_dev_log.ps1 "wear/data/preferences/NetworkSourceRepositoryImpl.kt" "NetworkSourceRepositoryImpl" "Add FTP/SFTP routing in testConnection"
```

**Step P2-5** — Add delete action strings (EN/RU/UK) per §6.6 key list:
```
.\scripts\add_to_dev_log.ps1 "wear/src/main/res/values/strings.xml" "strings" "Add Phase 2 network source management strings EN"
.\scripts\add_to_dev_log.ps1 "wear/src/main/res/values-ru/strings.xml" "strings" "Add Phase 2 strings RU"
.\scripts\add_to_dev_log.ps1 "wear/src/main/res/values-uk/strings.xml" "strings" "Add Phase 2 strings UK"
```

**Step P2-6** — Extend `NetworkSourcesViewModel` with `deleteSource(id: String)`:
```kotlin
fun deleteSource(id: String) {
    viewModelScope.launch {
        networkSourceRepository.deleteSource(id)
        // Phase 4 reactive flow will refresh; until then call loadSources()
        loadSources()
    }
}
```
```
.\scripts\add_to_dev_log.ps1 "wear/ui/network/viewmodel/NetworkSourcesViewModel.kt" "NetworkSourcesViewModel" "Add deleteSource method"
```

**Step P2-7** — Extend `NetworkSourcesScreen` with delete UX:
- Add `showDeleteDialog: Boolean` + `sourceToDelete: SourceItem?` state
- Long-press on `Chip` sets `sourceToDelete` and shows `Dialog`
- Dialog: title "Delete [name]?", two `Chip` buttons (Delete / Cancel)
- On confirm: `viewModel.deleteSource(id)`
```
.\scripts\add_to_dev_log.ps1 "wear/ui/network/NetworkSourcesScreen.kt" "NetworkSourcesScreen" "Add delete source confirmation dialog"
```

**Step P2-8** — Create `AddNetworkSourceViewModel` at `wear/ui/network/viewmodel/`:
State: `protocol: NetworkSourceType`, `server`, `port`, `username`, `password`, `shareName`, `domain`, `sshPrivateKey`, `name`, `isTesting`, `isSaving`, `error`.
Methods: `setProtocol(type)` (auto-fills default port), `testConnection()`, `saveSource()`.
```
.\scripts\add_to_dev_log.ps1 "wear/ui/network/viewmodel/AddNetworkSourceViewModel.kt" "AddNetworkSourceViewModel" "Add multi-protocol source creation ViewModel"
```

**Step P2-9** — Create `AddNetworkSourceScreen` at `wear/ui/network/`:
Implement the protocol picker UX described in §6.5. Reuse `WearTextInput` for fields.
```
.\scripts\add_to_dev_log.ps1 "wear/ui/network/AddNetworkSourceScreen.kt" "AddNetworkSourceScreen" "Add multi-protocol network source add screen"
```

**Step P2-10** — Add `add_network_source` route in `MainActivity` + keep `add_smb` alias:
```kotlin
composable("add_network_source") { AddNetworkSourceScreen(navController) }
composable("add_smb") { AddNetworkSourceScreen(navController) }  // alias
```
Update `NetworkSourcesScreen` "Add" chip to navigate to `add_network_source`.
```
.\scripts\add_to_dev_log.ps1 "wear/MainActivity.kt" "MainActivity" "Add add_network_source route"
.\scripts\add_to_dev_log.ps1 "wear/ui/network/NetworkSourcesScreen.kt" "NetworkSourcesScreen" "Update Add chip to navigate to add_network_source"
```

---

### ◆ Phase 3 Steps (UX Polish)

**Step P3-1** — Add 4 missing keys to strings.xml (all locales) per §7.1 table:
`loading`, `no_network_sources`, `error`, `retry`.
```
.\scripts\add_to_dev_log.ps1 "wear/src/main/res/values/strings.xml" "strings" "Add loading/empty/error/retry localized strings EN"
.\scripts\add_to_dev_log.ps1 "wear/src/main/res/values-ru/strings.xml" "strings" "Add strings RU"
.\scripts\add_to_dev_log.ps1 "wear/src/main/res/values-uk/strings.xml" "strings" "Add strings UK"
```

**Step P3-2** — Replace 4 hardcoded strings in `NetworkSourcesScreen.kt` with `stringResource(R.string.*)`:
Lines 84, 153, 183, 208 — see §7.1 table.
```
.\scripts\add_to_dev_log.ps1 "wear/ui/network/NetworkSourcesScreen.kt" "NetworkSourcesScreen" "Replace 4 hardcoded strings with stringResource"
```

**Step P3-3** — Add slideshow interval stepper to `SettingsScreen`:
Replace `Text(slideshow_interval)` with a `Row` containing `CompactChip("−")`, `Text(value)`, `CompactChip("+")`.
Wire taps to `viewModel.setSlideshowInterval(newValue)` stepping through array `[3, 5, 10, 15, 20, 30, 60]`.
```
.\scripts\add_to_dev_log.ps1 "wear/ui/settings/SettingsScreen.kt" "SettingsScreen" "Add slideshow interval stepper control"
```

**Step P3-4** — Replace emoji with `Icon` in `HomeScreen`:
Remove `"🎵 "` etc. prefix strings. Add `icon` lambda to each `Chip` using `Icons.Default.*` (see §7.3 mapping).
Add `contentDescription = null` to icons (chip label describes action).
```
.\scripts\add_to_dev_log.ps1 "wear/ui/home/HomeScreen.kt" "HomeScreen" "Replace emoji with proper Wear icons in HomeScreen chips"
```

---

### ◆ Phase 4 Steps (Architecture Hardening)

**Step P4-1** — Fix `SettingsViewModel` race condition:
Replace 7 parallel `viewModelScope.launch { flow.collect {} }` with single `combine(7 flows)` — see §8.1 code snippet.
```
.\scripts\add_to_dev_log.ps1 "wear/ui/settings/SettingsViewModel.kt" "SettingsViewModel" "Fix parallel collect race condition using combine()"
```

**Step P4-2** — Add `observeSources(): Flow<List<NetworkSource>>` to `NetworkSourceRepository` interface:
```
.\scripts\add_to_dev_log.ps1 "wear/domain/repository/NetworkSourceRepository.kt" "NetworkSourceRepository" "Add observeSources reactive flow method"
```

**Step P4-3** — Implement `observeSources()` in `NetworkSourceRepositoryImpl` via `MutableSharedFlow(replay=1)`:
Emit after every `saveSources()` call — see §8.2 code snippet.
```
.\scripts\add_to_dev_log.ps1 "wear/data/preferences/NetworkSourceRepositoryImpl.kt" "NetworkSourceRepositoryImpl" "Implement observeSources with MutableSharedFlow"
```

**Step P4-4** — Migrate `NetworkSourcesViewModel` from one-shot `loadSources()` to `collectAsState` on `observeSources()`:
Keep `loadSources()` as a trigger for the initial emit; remove the one-shot pattern.
Also add `fun retryLoad()` that calls `loadSources()` (§8.3).
```
.\scripts\add_to_dev_log.ps1 "wear/ui/network/viewmodel/NetworkSourcesViewModel.kt" "NetworkSourcesViewModel" "Migrate to reactive observeSources flow + add retryLoad"
```

**Step P4-5** — Fix `NetworkSourcesScreen.ErrorContent` to call `viewModel.retryLoad()` instead of `navController.popBackStack()`:
```
.\scripts\add_to_dev_log.ps1 "wear/ui/network/NetworkSourcesScreen.kt" "NetworkSourcesScreen" "Fix retry to call viewModel.retryLoad instead of popBackStack"
```

---

### ◆ Final Checklist

- [ ] Phase 1 complete and verified (ref `spec_wear_resource_sync.md §13 checklist`)
- [ ] String resources added EN/RU/UK for Phase 2 (§6.6) and Phase 3 (§7.1, §7.4)
- [ ] `docs/FEATURES.md` + RU + UK updated (Phase 1 & Phase 2 are user-visible)
- [ ] No file exceeds 1000 lines after changes (`NetworkSourcesScreen.kt` — watch; after Phase 2 adds delete dialog it may approach 280 lines — safe)
- [ ] `.\scripts\add_to_dev_log.ps1` run for every modified file (steps P2-1 through P4-5)
- [ ] Build verified: `.\gradlew.bat :wear:assembleDebug` and `.\gradlew.bat assembleStandardDebug`

---

## 14. Architecture Decision Records (ADRs)

**ADR-M1: Phased delivery — Resource Sync before CRUD**
- **Decision:** Phase 1 (resource sync) ships before Phase 2 (CRUD UI), even though they are largely independent.
- **Alternatives considered:** Ship CRUD first; ship all phases together.
- **Reason:** The `upsertSource` method (Phase 1) is required by the `WatchWearListenerService`. If CRUD is built first without `upsertSource`, the repository has a gap. Delivering Phase 1 first also gives users an immediate payoff (no manual keyboard entry) which validates the Wear OS investment before polishing.

**ADR-M2: FTP/SFTP testConnection as TCP stub, not full protocol handshake**
- **Decision:** `FtpConnectionTest` and `SftpConnectionTest` only verify TCP reachability (socket connect), not full protocol authentication.
- **Alternatives considered:** Full Apache Commons Net / SSHJ integration in wear module.
- **Reason:** Apache Commons Net and SSHJ add ~1.5MB to the wear APK. The watch only *browses* via the phone-proxied connection (SMB/SmbDataSource handles the actual browse). FTP/SFTP on the watch is a secondary path — full credential validation is deferred to first actual browse attempt.

**ADR-M3: Delete UX via long-press + dialog (not swipe-to-dismiss)**
- **Decision:** Delete is triggered by long-press on the source chip, not by a swipe gesture.
- **Alternatives considered:** Swipe-left-to-delete (iOS-style); dedicated "⋯ More" chip.
- **Reason:** Wear OS `SwipeDismissableNavHost` uses the swipe gesture for back navigation. Overloading swipe for delete creates navigation conflicts. Long-press is the Wear OS convention for context actions. A dedicated "more" chip would take up screen real estate on the small round display.

**ADR-M4: Reactive repository via `MutableSharedFlow` not Room**
- **Decision:** `observeSources()` uses an in-memory `MutableSharedFlow`, not a Room `@Query` returning `Flow`.
- **Alternatives considered:** Migrate `NetworkSourceRepositoryImpl` from `EncryptedSharedPreferences` to a Room table; use Room's native `Flow` support.
- **Reason:** Migrating to Room requires a schema migration, Hilt wiring, and `@TypeConverter` for `EncryptedSharedPreferences` content. `MutableSharedFlow` achieves the same reactive semantics with zero migration risk. Room migration can be a future ADR when the wear module has more entities.

**ADR-M5: Keep `SmbConnectionViewModel` as deprecated alias**
- **Decision:** `SmbConnectionViewModel` is kept in the codebase (marked `@Deprecated`) after `AddNetworkSourceViewModel` is introduced.
- **Alternatives considered:** Delete `SmbConnectionViewModel` immediately.
- **Reason:** `AddSmbScreen` still exists as a navigation alias. If any third-party deep-link or test references the route `add_smb`, deleting the ViewModel causes a crash. The `@Deprecated` annotation signals to contributors that it should not be extended; it will be removed in a future cleanup sprint.

---

## 15. User-Facing Feature Updates

After Phase 1 and Phase 2 complete, update all three FEATURES docs:

- `docs/FEATURES.md` (EN): Under **Wear OS** — "One-tap sync of network sources (SMB/FTP/SFTP) from phone to watch; watch can also pull from phone. Full source management on watch: add (SMB/FTP/SFTP), edit, delete without touching the phone."
- `docs/FEATURES_RU.md` (RU): В разделе **Wear OS** — "Синхронизация источников (SMB/FTP/SFTP) с телефона на часы одним нажатием; часы могут запросить синхронизацию сами. Полное управление источниками на часах: добавление (SMB/FTP/SFTP), редактирование, удаление без обращения к телефону."
- `docs/FEATURES_UK.md` (UK): У розділі **Wear OS** — "Синхронізація джерел (SMB/FTP/SFTP) з телефону на годинник одним дотиком; годинник може запросити синхронізацію самостійно. Повне управління джерелами на годиннику: додавання (SMB/FTP/SFTP), редагування, видалення без звернення до телефону."

---

## 16. Out of Scope (deferred)

- **Watch → Phone sync**: Watch is a consumer, not a source of truth.
- **Cloud source support on watch** (CLOUD resources, OAuth tokens are phone-device-bound).
- **Multi-watch delivery** (Data Layer supports multiple nodes; UX design not scoped).
- **Full FTP/SFTP file browsing on watch** (network browse currently proxied via SMB/SmbDataSource only).
- **SFTP key-auth UI on watch** (key field added to model in Phase 1; dedicated key management UI is deferred).
- **Watch → Watch source sharing**.
- **Wear OS Material 3 migration** (Wear Compose M3 is in alpha/beta; migration deferred until stable).
- **Conflict picker UI** (last-write-wins is sufficient for now).
- **`photos` / `legacy` flavor Wear companion pairing** (see §4.1).

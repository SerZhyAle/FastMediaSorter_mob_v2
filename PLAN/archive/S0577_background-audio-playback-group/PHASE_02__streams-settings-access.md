# Phase 02 - Streams Settings Access

**Strategic spec:** [`../S0577_background-audio-playback-group.md`](../S0577_background-audio-playback-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Give `StreamsViewModel` read access to the relevant app settings and a write path for the exit-behavior preference, mirroring `PlayerViewModel`. No playback or exit-handling changes yet - this phase only exposes state and a setter.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done (none).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | ≤ 240 |

---

## Steps

### Step 02.1 - Inject SettingsRepository and expose settings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `private val settingsRepository: com.sza.fastmediasorter.domain.repository.SettingsRepository` to the `@Inject` constructor. Expose `val settings: StateFlow<AppSettings> = settingsRepository.getSettings().stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())` (mirror `PlayerViewModel.settings`). Do not fold settings into `StreamsUiState` - keep it a separate flow so the Activity can read `settings.value.enablePersistentAudioPlayback` and `settings.value.backgroundAudioExitBehavior` at play-time and exit-time without coupling list rendering to settings.

**Verification:**

- `Grep` - `settingsRepository: SettingsRepository` in the constructor of `StreamsViewModel.kt`.
- `Grep` - `val settings: StateFlow<AppSettings>` present.
- `Grep -n "Log\.d\("` on the file returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 3/3 PASS. Files: StreamsViewModel.kt (Modified). Injected SettingsRepository; exposed settings StateFlow (Eagerly, AppSettings() initial).

---

### Step 02.2 - Add updateExitBehavior setter

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `fun updateExitBehavior(behavior: BackgroundAudioExitBehavior)` that mirrors `PlayerViewModel.updateExitBehavior`: launch in `viewModelScope`, read `settingsRepository.getSettings().first()`, then `settingsRepository.updateSettings(settings.copy(backgroundAudioExitBehavior = behavior))`. This is the persistence path for the dialog's "Always stop" / "Always continue" choices on the streams screen.

**Verification:**

- `Grep` - `fun updateExitBehavior(behavior: BackgroundAudioExitBehavior)` present.
- `Grep` - `backgroundAudioExitBehavior = behavior` present.
- Compile: `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Verification 2/2 PASS. Files: StreamsViewModel.kt (Modified). Added updateExitBehavior mirroring PlayerViewModel. `.\a.ps1 fk` BUILD SUCCESSFUL.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fk` exits 0.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for the phase.

---

## Handoff Notes to Next Phase

`StreamsViewModel.settings` (StateFlow<AppSettings>) and `StreamsViewModel.updateExitBehavior(..)` are available. Phase 03 reads `settings.value.enablePersistentAudioPlayback` to pick the playback path; Phase 04 reads `backgroundAudioExitBehavior` and calls `updateExitBehavior` from the dialog.

---

## Rollback Plan

Revert the phase commit; `StreamsViewModel` returns to its prior constructor. No persisted data changed (the new setter is unused until Phase 04).

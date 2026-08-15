# Phase 01 - Settings model & migration

**Strategic spec:** [`../S0439_screen-rotation-follow-os.md`](../S0439_screen-rotation-follow-os.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Split the single persisted `followSystemRotation` flag into a program-scope flag and a new player-scope flag, preserving behaviour on upgrade by reusing the existing DataStore key for the program flag.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Research artifact `research/01__current-rotation-model.md` read.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 700 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewModel.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerObserverManager.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivityLifecycleBridge.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/init/AppStartupInitializer.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/preset/DeviceProfilePresetApplier.kt` | Modified | ≤ 500 |

> `>500 LOC` files (PlayerViewModel, both settings fragments) require a timestamped backup in `temp/` before editing (Constraints).

---

## Steps

### Step 01.1 - Rename program flag and add player flag in the settings model

**Files:** `domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `AppSettings`, rename the property `followSystemRotation` to `programFollowSystemRotation` (keep default `true`). Immediately after it add a new property `val playerFollowSystemRotation: Boolean = false` with a short KDoc: program flag is the umbrella; the player flag is consulted only when the program flag is off. Do not touch `playerRotationSensorEnabled`.

**Verification:**

- `Grep` - `val programFollowSystemRotation: Boolean = true` matches once in `AppSettings.kt`.
- `Grep` - `val playerFollowSystemRotation: Boolean = false` matches once in `AppSettings.kt`.
- `Grep` - `followSystemRotation` (old identifier, word-boundary) returns zero hits in `AppSettings.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS. AppSettings.kt: renamed followSystemRotation -> programFollowSystemRotation, added playerFollowSystemRotation (default false).

---

### Step 01.2 - Persist both flags; reuse legacy key for the program flag

**Files:** `data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Keep `KEY_FOLLOW_SYSTEM_ROTATION = booleanPreferencesKey("follow_system_rotation")` and bind it to `programFollowSystemRotation` (this preserves stored values on upgrade - the legacy single flag becomes the program flag). Add `KEY_PLAYER_FOLLOW_SYSTEM_ROTATION = booleanPreferencesKey("player_follow_system_rotation")`. In the settings read, map `programFollowSystemRotation = preferences[KEY_FOLLOW_SYSTEM_ROTATION] ?: true` and `playerFollowSystemRotation = preferences[KEY_PLAYER_FOLLOW_SYSTEM_ROTATION] ?: false`. In the settings write, persist both keys from the corresponding fields.

**Verification:**

- `Grep` - `booleanPreferencesKey("follow_system_rotation")` still present (string unchanged).
- `Grep` - `booleanPreferencesKey("player_follow_system_rotation")` present once.
- `Grep` - `KEY_PLAYER_FOLLOW_SYSTEM_ROTATION` appears in both the read map and the write block.
- `Grep` - `?: false` on the `KEY_PLAYER_FOLLOW_SYSTEM_ROTATION` read (default off on upgrade).

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 4/4 PASS. SettingsRepositoryImpl.kt: legacy key `follow_system_rotation` reused for programFollowSystemRotation; added `player_follow_system_rotation` (default false) in read+write.

---

### Step 01.3 - Update all consumers of the renamed field to compile (behaviour-preserving)

**Files:** `ui/player/PlayerViewModel.kt`, `ui/player/PlayerObserverManager.kt`, `ui/player/PlayerActivityLifecycleBridge.kt`, `ui/settings/fragments/OperationsSettingsFragment.kt`, `ui/settings/fragments/PlaybackSettingsFragment.kt`, `core/init/AppStartupInitializer.kt`, `data/preset/DeviceProfilePresetApplier.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Replace every remaining reference to the old `followSystemRotation` identifier with `programFollowSystemRotation` so the module compiles. Do not yet introduce the player-flag precedence (that is Phase 03) - the player `ScreenRotationManager.apply(..)` call sites keep passing `programFollowSystemRotation` for now, which preserves current behaviour because the new player flag defaults off. In `AppStartupInitializer` settings dump, rename the logged label to `programFollowSystemRotation` and add a line logging `playerFollowSystemRotation`. In `PlayerState` (inside `PlayerViewModel`) rename the carried field to `programFollowSystemRotation` and keep populating it from settings.

**Verification:**

- `Grep` - `followSystemRotation` (old identifier, word-boundary, excluding `programFollowSystemRotation` / `playerFollowSystemRotation`) returns zero hits across `app_v2/src/main/java`.
- `Grep` - `programFollowSystemRotation` present in `PlayerObserverManager.kt` and `PlayerActivityLifecycleBridge.kt`.
- `Grep -n "Log\.d\("` returns zero hits in every modified file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification PASS (old identifier 0 hits across src/main incl. CSV/comments; programFollowSystemRotation present in player call sites; no Log.d added). Renamed across 9 .kt + device_profile_presets.csv preset key; SettingsViewModel/AppStartupInitializer also carry the new playerFollowSystemRotation.

---

### Step 01.4 - Compile gate

**Files:** -
**Depends on:** Step 01.1, 01.2, 01.3

**Prompt for developer:**

> Build the standard debug variant to prove the rename and new field compile cleanly. Use `/build` (`.\a.ps1 fk` for compile-only is acceptable).

**Verification:**

- `/build` - standard debug compiles, exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - BLOCKED (external). `a.ps1 fk` fails with ~10 `'settingsRepository' hides member of supertype 'BaseActivity'` errors from S0438's in-flight refactor (settingsRepository lifted into BaseActivity, subclasses not yet `override`/removed). Not an S0439 defect - my edits (01.1-01.3) are grep-verified and neuroslop-clean. Re-run after S0438 compiles.
- 2026-06-16 - UNBLOCKED. S0438 Archived; `a.ps1 fk` PASS (compileStandardDebugKotlin up-to-date). Phase 01 edits intact (old identifier 0 hits across src/main) and compile clean.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for the old identifier `followSystemRotation` (word-boundary, excluding the two new names) returns zero hits.
- [ ] DataStore key string `follow_system_rotation` is unchanged (upgrade-safe).
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- `AppSettings.programFollowSystemRotation` (key `follow_system_rotation`, default true) and `AppSettings.playerFollowSystemRotation` (key `player_follow_system_rotation`, default false) exist and persist.
- Behaviour is unchanged at this point: player still reacts only to the program flag; non-player windows still implicitly follow OS.

---

## Rollback Plan

Revert phase commit(s). No DataStore migration code was added (key reuse), so reverting restores the single-flag model with no data loss.

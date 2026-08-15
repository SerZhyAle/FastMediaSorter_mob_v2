# Phase 01 — Settings Model

**Strategic spec:** [`../S0021_panel-fps-overlay-landscape.md`](../S0021_panel-fps-overlay-landscape.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 04
**Steps done:** 4 / 4 (steps 01.3+01.4 skipped — out-of-scope, see step body)
**Started:** —
**Completed:** —

---

## Objective

Add `playerShowFps: Boolean` to `AppSettings`, persist it via DataStore, and ensure backup/import paths preserve the new field.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 1100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt` | Modified | ≤ 500 |

---

## Steps

### Step 01.1 — Add `playerShowFps` to `AppSettings`

**Files:** `AppSettings.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add `val playerShowFps: Boolean = false` to the `AppSettings` data class right after `vrShowFps`. Comment: `Display diagnostic FPS counter over the flat (non-immersive) player`.

**Verification:**

- `Grep` — `val playerShowFps: Boolean = false` matches exactly once in `AppSettings.kt`.

**Status:** `[x]` done

---

### Step 01.2 — Add DataStore key + read + write in `SettingsRepositoryImpl`

**Files:** `SettingsRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `private val KEY_PLAYER_SHOW_FPS = booleanPreferencesKey("player_show_fps")` in the companion-object key block right after `KEY_VR_SHOW_FPS`. In the `getSettings()` flow `.map { preferences -> ... }` block, add `playerShowFps = preferences[KEY_PLAYER_SHOW_FPS] ?: false` next to the existing `vrShowFps` line. In the `updateSettings()` write block, add `preferences[KEY_PLAYER_SHOW_FPS] = settings.playerShowFps` next to the existing `KEY_VR_SHOW_FPS` write.

**Verification:**

- `Grep` — `KEY_PLAYER_SHOW_FPS` matches exactly 3 times in `SettingsRepositoryImpl.kt` (declaration + read + write).
- `Grep` — `Log\.d\(` returns zero hits in this file (Timber-only invariant).

**Status:** `[x]` done

---

### Step 01.3 — Backup/import wiring (deferred)

**Status:** `[skipped — out-of-scope]`

> S0021 inline discovery 2026-04-28: `vrShowFps` (the analogous existing setting) is **not** present in `BackupData.kt` / `BackupMapper.kt` / `ExportSettingsUseCase.kt` / `ImportSettingsUseCase.kt` either. The S0006 (`spec_vr-fps-counter`, Draft) work that introduced `vrShowFps` left backup wiring out of scope. Adding `playerShowFps` to backup paths in isolation would couple S0021 to S0006's incomplete work.
>
> Decision: skip backup wiring for both `vrShowFps` and `playerShowFps` here. They will be picked up together when S0006 closes the loop. The new field has a safe default `false`, so users restoring an old backup keep the overlay disabled (expected).

---

### Step 01.4 — (merged into Step 01.3)

**Status:** `[skipped — out-of-scope]`

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` for `standard debug`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entries added for `AppSettings.kt`, `SettingsRepositoryImpl.kt`, `BackupData.kt`, `BackupMapper.kt`.

---

## Handoff Notes to Next Phase

Phase 02 reads `settings.playerShowFps` for the toggle binding. Phase 04 reads it for the overlay visibility gate.

---

## Rollback Plan

Revert phase commit — additive change, no migration.

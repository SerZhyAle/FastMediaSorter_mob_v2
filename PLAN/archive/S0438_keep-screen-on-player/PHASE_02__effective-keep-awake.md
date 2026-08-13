# Phase 02 - Effective keep-screen-on application

**Strategic spec:** [`../S0438_keep-screen-on-player.md`](../S0438_keep-screen-on-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** -
**Steps done:** 3 / 3
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Make the window keep-screen-on flag settings-driven: ordinary activities follow the global `preventSleep`; player activities follow the effective rule `preventSleep || keepScreenOnPlayer`. This resolves the research finding that the base activity currently keeps the screen on unconditionally.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §6 item 1 reviewed (`research/01__keep-screen-on-current-wiring.md`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt` | Modified | ≤ 550 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/AudioStandaloneActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/ResourceLaunchWidgetConfigActivity.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraQuickCaptureConfigActivity.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameConfigActivity.kt` | Modified | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/CalculatorActivity.kt` | Modified | ≤ 250 |

> `BaseActivity.kt` may approach the backup threshold (>500 LOC) - take a timestamped backup in `temp/` before editing.

---

## Steps

### Step 02.1 - Drive base keep-awake from settings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Inject `SettingsRepository` into `BaseActivity` (member injection, same pattern as `tvKeyRouter`; concrete subclasses are already Hilt entry points). Replace the synchronous `shouldKeepScreenAwake(): Boolean` decision with a settings-driven one: add `protected open fun keepScreenAwakeFor(settings: AppSettings): Boolean = settings.preventSleep`. In a lifecycle-bound collector (use the existing `collectOnLifecycle` extension), observe `settingsRepository.getSettings()` and apply/clear `FLAG_KEEP_SCREEN_ON` from `keepScreenAwakeFor(settings)`. Keep the existing `onCreate`/`onResume` apply calls so the flag is correct before the first emission (use the last known value or default `preventSleep = true`). Remove the now-unused `shouldKeepScreenAwake()` hook only after all overrides are migrated in Steps 02.2-02.3.

**Verification:**

- `Grep` - `protected open fun keepScreenAwakeFor(settings: AppSettings): Boolean = settings.preventSleep` present.
- `Grep` - `collectOnLifecycle` present in `BaseActivity.kt`.
- `Grep` - `lateinit var keepScreenSettingsRepository` (`@Inject` field) present in `BaseActivity.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 3/3 PASS. Files: core/ui/BaseActivity.kt (+~16 LOC). shouldKeepScreenAwake() left for removal in 02.3. Dev log recorded.
- 2026-06-16 - Build-fix: injected field renamed settingsRepository -> keepScreenSettingsRepository to avoid hiding subclasses' own settingsRepository injections (compile error fk).

---

### Step 02.2 - Player activities apply the effective rule

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/AudioStandaloneActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> In each of the four player host activities - `PlayerActivity`, `StandalonePlayerActivity`, `AudioStandaloneActivity`, `PhotoVideoStandaloneActivity` (all four implement `PlayerHostCapabilities`) - override `keepScreenAwakeFor` to return `settings.preventSleep || settings.keepScreenOnPlayer`. This makes a player keep the screen on whenever either the global setting or the dependent player setting is on, while non-player activities (including the document/text standalone viewers) sleep when the global setting is off. Verify the override resolves the same `AppSettings` type imported in `BaseActivity`.

**Verification:**

- `Grep` - `override fun keepScreenAwakeFor` present in each of the 4 files.
- `Grep` - `settings.preventSleep || settings.keepScreenOnPlayer` present in each of the 4 files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification 4/4 PASS. Scope corrected during exec: all 4 PlayerHostCapabilities hosts (Player, StandalonePlayer, AudioStandalone, PhotoVideoStandalone) overridden. Dev log recorded.

---

### Step 02.3 - Migrate always-off config activities and reconcile player managers

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/widget/ResourceLaunchWidgetConfigActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/widget/CameraQuickCaptureConfigActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/widget/RandomPhotoFrameConfigActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/calculator/CalculatorActivity.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Replace each `override fun shouldKeepScreenAwake(): Boolean = false` with `override fun keepScreenAwakeFor(settings: AppSettings): Boolean = false` so these screens never keep the display awake regardless of settings. Confirm no remaining references to `shouldKeepScreenAwake` exist anywhere; if the base hook was removed in Step 02.1, the project must compile with zero references. Leave the transient slideshow keep-awake in `PlayerLifecycleManager` and the standalone `keepScreenOn` toggle as-is: they only force the flag on during active playback and are additive to the steady-state rule from Step 02.2.

**Verification:**

- `Grep` - `shouldKeepScreenAwake` returns zero hits across `app_v2/src/main` (hook fully removed/migrated).
- `Grep` - `override fun keepScreenAwakeFor(settings: AppSettings): Boolean = false` present in each of the 4 files.

**Status:** `[x]` done

**Step Log:**

- 2026-06-16 - Verification PASS: shouldKeepScreenAwake removed (0 refs across all source sets); 4 config activities migrated to keepScreenAwakeFor=false. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `Grep` for `shouldKeepScreenAwake` returns zero hits in `app_v2/src/main`.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

- Effective keep-awake is now `preventSleep` for ordinary activities and `preventSleep || keepScreenOnPlayer` for player hosts.
- With the global setting off and `keepScreenOnPlayer` on, only player hosts hold the screen awake; the dependent UI row in Phase 03 now produces a visible effect.

---

## Rollback Plan

Revert phase commit(s). No data migration. Restore the `temp/` backup of `BaseActivity.kt` if needed; the prior unconditional keep-awake behavior returns.

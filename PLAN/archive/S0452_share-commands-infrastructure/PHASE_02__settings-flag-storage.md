# Phase 02 - Settings flag storage

**Strategic spec:** [`../S0452_share-commands-infrastructure.md`](../S0452_share-commands-infrastructure.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-16
**Completed:** 2026-06-16

---

## Objective

Persist per-target enable state app-global in `AppSettings` as a `Set<String>` of explicitly-toggled target ids (DataStore), and expose an effective-enabled query that falls back to the registry default when a target id is absent. No UI, no Room change.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (`ShareTargetRegistry` available).
- [ ] Working tree clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/AppSettings.kt` | Modified | ≤ 330 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepositoryImpl.kt` | Modified | ≤ 780 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/IsShareTargetEnabledUseCase.kt` | New | ≤ 90 |

---

## Steps

### Step 02.1 - Add `enabledShareTargets` to `AppSettings`

**Files:** `domain/model/AppSettings.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `val enabledShareTargets: Set<String> = emptySet()` to `AppSettings` near the share/Lens fields. Semantics: a target id present in the set means the user explicitly toggled it ON; absent means "use the registry default". Add a brief WHY comment: empty set = all targets follow their registry default rule. Do not add one boolean per target - the set keeps the model stable as target tickets register new ids.

**Verification:**

- `Grep` - `enabledShareTargets: Set<String>` matches once in `AppSettings.kt`.
- `Grep -n "Log\.d\("` - zero hits in modified file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification PASS. Added `enabledShareTargets` + `disabledShareTargets` (`Set<String>`) to `AppSettings` with WHY comment (two-set tri-state: explicit ON / explicit OFF / registry default).

---

### Step 02.2 - Persist the set in `SettingsRepositoryImpl`

**Files:** `data/repository/SettingsRepositoryImpl.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a `stringSetPreferencesKey("enabled_share_targets")` DataStore key. Read it into `AppSettings.enabledShareTargets` in the settings mapping, and write it in `updateSettings()` alongside the other keys. Mirror the existing per-field read/write pattern exactly; default to `emptySet()` when the key is absent (this is the "migration" - existing installs start with all-defaults).

**Verification:**

- `Grep` - `enabled_share_targets` matches in `SettingsRepositoryImpl.kt`.
- `Grep` - `enabledShareTargets` referenced in both read and write paths (>= 2 hits).
- `Grep -n "Log\.d\("` - zero hits in modified file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification PASS. Added `KEY_ENABLED_SHARE_TARGETS` + `KEY_DISABLED_SHARE_TARGETS` (`stringSetPreferencesKey`) to main `DataStore<Preferences>`; read into `AppSettings` (default `emptySet()`); written in `updateSettings()`. assembleStandardDebug PASS.

---

### Step 02.3 - Add `IsShareTargetEnabledUseCase`

**Files:** `domain/usecase/IsShareTargetEnabledUseCase.kt`
**Depends on:** Step 02.1, Step 02.2

**Prompt for developer:**

> Create `IsShareTargetEnabledUseCase` (`@Inject constructor`, deps: `ShareTargetRegistry`, `ShareTargetAvailabilityResolver`, settings repository). `suspend operator fun invoke(targetId: String): Boolean` (or accept a pre-read `AppSettings` to stay synchronous for UI): resolve the target via `registry.byId(targetId)` (unknown id -> false); if `targetId in settings.enabledShareTargets` -> true; if the set contains an explicit-disabled marker, honor it; otherwise fall back to `resolver.isDefaultEnabled(target)`. Keep effective-enabled logic here so UI and gating share one source of truth. (Visibility = enabled AND `resolver.isAvailable`; availability is combined at the gating phase.)

**Verification:**

- `Glob` - `IsShareTargetEnabledUseCase.kt` exists.
- `Grep` - `class IsShareTargetEnabledUseCase` matches once.
- `Grep` - `operator fun invoke` present.

**Status:** `[x] done`

**Step Log:**

- 2026-06-16 - Verification PASS. `IsShareTargetEnabledUseCase` (New): `invoke(targetId, settings)` -> enabled set wins, then disabled set, else `resolver.isDefaultEnabled`. Synchronous (takes pre-read `AppSettings`) for UI use.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `assembleStandardDebug` PASS (2026-06-16, build_debug_20260616_110638.log).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

- Effective-enabled is `IsShareTargetEnabledUseCase`; the settings UI writes the set, gating reads enabled AND `ShareTargetAvailabilityResolver.isAvailable`.

---

## Rollback Plan

Revert phase commit(s). DataStore key is additive and defaults to empty - removing it is safe; no schema migration involved.

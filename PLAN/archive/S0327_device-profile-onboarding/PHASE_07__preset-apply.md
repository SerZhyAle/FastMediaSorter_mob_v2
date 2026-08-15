# Phase 07 - Preset Application

**Strategic spec:** [`../S0327_device-profile-onboarding.md`](../S0327_device-profile-onboarding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (matrix values PROVISIONAL - owner approval pending)
**Depends on:** Phase 03, 04, 05, 06
**Blocks:** Phase 08, 09
**Steps done:** 3 / 3
**Started:** 2026-06-02
**Completed:** 2026-06-02

> **Provisional matrix:** the per-profile preset values shipped here are an engineering best-guess, NOT the owner-approved matrix that strategic §6.1 / §11.15 gate on. The wiring (matrix -> use case -> SettingsRepository.applyBatchSettings -> DataStore) is complete and tested; only the values and the matrix-design doc remain owner-gated. Tracked in the strategic spec Last Audit.

---

## Objective

Implement profile preset matrix: map DeviceProfileType → settings values (content defaults, interaction defaults, safety, device behavior, command priority). Apply presets on first install and on explicit Settings change with confirmation.

---

## Prerequisites

- [ ] Phase 03, 04, 05, 06 are ✅ Done.
- [ ] Preset matrix v1 values approved by owner (see blocker in INDEX).
- [ ] Working tree clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PresetMatrix.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ApplyProfilePresetUseCase.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/SettingsRepository.kt` | Modified | ≤ 800 |

---

## Steps

### Step 07.1 - Define preset matrix

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/PresetMatrix.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create data class representing preset matrix:
> ```kotlin
> data class PresetMatrix(
>     val profiles: Map<DeviceProfileType, PresetValues>
> )
> 
> data class PresetValues(
>     val enableThumbnails: Boolean,
>     val enableFullscreen: Boolean,
>     val enableBackgroundAudio: Boolean,
>     val preventSleep: Boolean,
>     val showConfirmations: Boolean,
>     // ... add fields per owner-approved matrix
> )
> ```
> Include documentation comments linking to owner-approved matrix doc (future).

**Verification:**

- `Glob` - file exists.
- `Grep` - `data class PresetMatrix` and `PresetValues`.
> - Fields are a PROVISIONAL v1 subset (5 booleans), NOT the owner-approved matrix. KDoc marks them provisional; final field set + values gated on owner refinement (§5.3, §11.15).

**Status:** `[x]` done (structure present; values provisional)

---

### Step 07.2 - Implement ApplyProfilePresetUseCase

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ApplyProfilePresetUseCase.kt`
**Depends on:** Step 07.1

**Prompt for developer:**

> Create UseCase:
> ```kotlin
> class ApplyProfilePresetUseCase @Inject constructor(
>     private val presetMatrix: PresetMatrix,
>     private val settingsRepository: SettingsRepository,
>     private val profileRepository: DeviceProfileRepository
> ) {
>     suspend fun apply(profileType: DeviceProfileType, presetVersion: Int = 1): Result<Unit> {
>         val values = presetMatrix.profiles[profileType] ?: return Result.failure(...)
>         return settingsRepository.applyBatchSettings(values)
>             .onSuccess { profileRepository.updatePresetApplied(presetVersion) }
>     }
> }
> ```

**Verification:**

- `Glob` - file exists.
- `Grep` - class name and @Inject constructor.
- `Grep` - `suspend fun apply` method.
- `Grep` - calls presetMatrix.profiles and settingsRepository.

**Status:** `[x]` done

---

### Step 07.3 - Wire preset application into Welcome/Settings flows

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeProfileSelectorViewModel.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsProfileViewModel.kt`
**Depends on:** Step 07.2

**Prompt for developer:**

> (1) Update WelcomeProfileSelectorViewModel:
>     - onSelect() → call ApplyProfilePresetUseCase.apply(selectedProfile)
>     - onSkip() → call with recommendedProfile
> (2) Update SettingsProfileViewModel:
>     - onConfirmApply() → call ApplyProfilePresetUseCase.apply(selectedProfile)
> (3) Add error handling, loading state, success navigation

**Verification:**

- `Grep` - `ApplyProfilePresetUseCase` injected into both ViewModels.
- `Grep` - `onSelect()`, `onSkip()`, `onConfirmApply()` call the use case.
- `Grep` - Result handling with error/loading/success states.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done` (matrix values provisional - see header note).
- [x] Project compiles - `standardDebug` BUILD SUCCESSFUL.
- [x] Unit test: preset matrix applied correctly - `ApplyProfilePresetUseCaseTest` (apply + OTHER-skip paths, filtered run failures=0).
- [ ] Manual test (ON-DEVICE, pending): Welcome → Select profile → settings change per preset; Settings → Change profile → warning → apply → settings update.
- [x] `Grep` for `TODO(phase-07)` returns zero hits.
- [x] Dev log entries.

---

## Handoff Notes to Next Phase

Preset application complete and wired into UI flows. Phase 08 handles migration for existing installs (show "Other" profile, no auto-apply).

---

## Rollback Plan

Revert phase commits - preset application reverts to no-op; user settings rollback via Settings manual change.

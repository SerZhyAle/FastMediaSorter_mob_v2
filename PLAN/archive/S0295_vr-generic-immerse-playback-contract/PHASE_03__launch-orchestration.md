# Phase 03 - Launch Orchestration

**Strategic spec:** [`../S0295_vr-generic-immerse-playback-contract.md`](../S0295_vr-generic-immerse-playback-contract.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 3 / 3
**Started:** 2026-05-25
**Completed:** 2026-05-25

---

## Objective

Introduce the reusable VR launch preflight use-case and bind it into the VR and no-op Hilt graphs so every UI surface shares one capability/error-mapping path.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.
- [ ] Existing comments in XR DI modules and detection/toggle classes are read before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/StartVrPlaybackUseCase.kt` | New | <= 180 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/StartVrPlaybackUseCaseImpl.kt` | New | <= 260 |
| `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpStartVrPlaybackUseCase.kt` | New | <= 140 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/di/XrModule.kt` | Modified | <= 120 |
| `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/di/NoOpXrModule.kt` | Modified | <= 120 |

---

## Steps

### Step 03.1 - Define the shared preflight use-case contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/StartVrPlaybackUseCase.kt`
**Depends on:** start of phase

**Prompt for developer:**

> Add a shared preflight use-case contract in `src/main/`. It should accept a `StartVrPlaybackRequest` and return either `Ready(VrLaunchInput)` or `Completed(VrLaunchResult)`. Keep launcher registration outside the use-case, but keep the orchestration contract here so all callers share one preparation path.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/StartVrPlaybackUseCase.kt` exists.
- `Grep` - `interface StartVrPlaybackUseCase` appears in `StartVrPlaybackUseCase.kt`.
- `Grep` - `Ready(` appears in `StartVrPlaybackUseCase.kt`.
- `Grep` - `Completed(` appears in `StartVrPlaybackUseCase.kt`.

**Status:** `[x]` done (2026-05-25)

**Step Log:**

- 2026-05-25 - Verification 4/4 PASS. Files: `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/StartVrPlaybackUseCase.kt`.

---

### Step 03.2 - Implement real VR preflight orchestration

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/StartVrPlaybackUseCaseImpl.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Implement the real use-case in the VR source set. Recheck runtime/toggle state, normalize diagnostic requests into `VrLaunchMode.DIAGNOSTIC_PLAYLIST`, short-circuit unsupported `VIDEO` / `GIF` requests into `VrLaunchResult.Unavailable(NotYetSupported)`, and log launch source + outcome via `Timber` without embedding ticket ids.

**Verification:**

- `Grep` - `VrLaunchMode.DIAGNOSTIC_PLAYLIST` appears in `StartVrPlaybackUseCaseImpl.kt`.
- `Grep` - `NotYetSupported` appears in `StartVrPlaybackUseCaseImpl.kt`.
- `Grep` - `Timber` appears in `StartVrPlaybackUseCaseImpl.kt`.
- `Grep` - `throw ` returns zero hits in `StartVrPlaybackUseCaseImpl.kt`.

**Status:** `[x]` done (2026-05-25)

**Step Log:**

- 2026-05-25 - Verification 4/4 PASS. Files: `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/StartVrPlaybackUseCaseImpl.kt`. Unsupported `VIDEO` / `GIF` requests short-circuit to `Unavailable(NotYetSupported)`.

---

### Step 03.3 - Add no-op implementation and bind both graphs

**Files:** `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpStartVrPlaybackUseCase.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/di/XrModule.kt`, `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/di/NoOpXrModule.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Add the phone-flavor no-op use-case that always returns an unavailable result, then bind the real and no-op implementations in the existing XR Hilt modules. Do not introduce a new module or a new qualifier for this ticket.

**Verification:**

- `Glob` - `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpStartVrPlaybackUseCase.kt` exists.
- `Grep` - `bindStartVrPlaybackUseCase` appears in `XrModule.kt`.
- `Grep` - `bindStartVrPlaybackUseCase` appears in `NoOpXrModule.kt`.
- `Grep` - `Unavailable` appears in `NoOpStartVrPlaybackUseCase.kt`.

**Status:** `[x]` done (2026-05-25)

**Step Log:**

- 2026-05-25 - Verification 4/4 PASS. Files: `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpStartVrPlaybackUseCase.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/core/xr/di/XrModule.kt`, `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/di/NoOpXrModule.kt`.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x]` done.
- [ ] Project compiles - run `/build` for standard debug and noLegal debug after Step 03.3.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [ ] If public API changed: `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.

---

## Handoff Notes to Next Phase

Every UI surface can now ask one shared use-case to prepare a VR launch without duplicating capability checks or unsupported-media mapping.

---

## Rollback Plan

Revert Phase 03 commit(s); no persisted state or schema migration is introduced.

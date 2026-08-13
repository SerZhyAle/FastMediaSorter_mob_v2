# Phase 01 - Radio control contract

**Strategic spec:** [`../S1441_launcher-radio-toggle-tiles.md`](../S1441_launcher-radio-toggle-tiles.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Introduce the flavor-neutral radio-control seam - contract, kind enum, no-op implementation and its Hilt binding - so later phases have something to call on every flavor.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/radio/RadioKind.kt` | New | ≤ 25 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/radio/RadioControlContract.kt` | New | ≤ 60 |
| `app_v2/src/networkMonitorDisabled/java/com/sza/fastmediasorter/radio/NoOpRadioControlContract.kt` | New | ≤ 35 |
| `app_v2/src/networkMonitorDisabled/java/com/sza/fastmediasorter/di/NetworkMonitorModule.kt` | Modified | ≤ 15 added |

> **Flavor placement.** The contract is the only part that may live in `src/main`. The no-op belongs to `src/networkMonitorDisabled`, which `lite`, `photos`, `legacy` and `vr` mount; the real implementation arrives in Phase 02 under `src/networkMonitor`.

---

## Steps

### Step 01.1 - Add the radio kind enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/radio/RadioKind.kt`

**Depends on:** - start of phase

**Prompt for developer:**

> Create `enum class RadioKind { WIFI, BLUETOOTH }` in package `com.sza.fastmediasorter.domain.radio`, with a
> one-line KDoc naming it the set of radios this app may toggle directly.

**Why:**

Strategic §6.4 limits the ticket to Wi-Fi and Bluetooth, and a closed enum is what keeps a later target from
being added to the toggle path without a decision.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/domain/radio/RadioKind.kt` exists.
- `Grep` - `enum class RadioKind` matches exactly once.
- `Grep` - both `WIFI` and `BLUETOOTH` appear in that file.

**Status:** `[x]` done

---

### Step 01.2 - Add the contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/radio/RadioControlContract.kt`

**Depends on:** Step 01.1

**Prompt for developer:**

> Create `interface RadioControlContract` in the same package with exactly three members:
>
> - `val isToggleSupported: Boolean` - whether this build compiles a real radio controller.
> - `fun state(kind: RadioKind): Flow<Boolean?>` - current state, `null` when it cannot be established.
> - `suspend fun toggle(kind: RadioKind): Boolean` - `true` only when the observed state actually flipped.
>
> Document on `toggle` that the return value of the platform call is not proof and that the caller falls back to
> a system screen when this returns `false`. Model the KDoc on `NetworkMonitorContract`, which is the seam this
> mirrors.

**Why:**

ADR-1 makes the observed state the only proof of success, and ADR-2 requires the component to sit outside the
launcher so the future network monitor consumes the same contract instead of copying the logic.

**Verification:**

- `Grep` - `interface RadioControlContract` matches exactly once.
- `Grep` - `suspend fun toggle(kind: RadioKind): Boolean` present.
- `Grep` - `fun state(kind: RadioKind): Flow<Boolean?>` present.
- `Grep` - `val isToggleSupported: Boolean` present.

**Status:** `[x]` done

---

### Step 01.3 - Add the no-op implementation and bind it

**Files:** `app_v2/src/networkMonitorDisabled/java/com/sza/fastmediasorter/radio/NoOpRadioControlContract.kt`, `app_v2/src/networkMonitorDisabled/java/com/sza/fastmediasorter/di/NetworkMonitorModule.kt`

**Depends on:** Step 01.2

**Prompt for developer:**

> Create `NoOpRadioControlContract` in package `com.sza.fastmediasorter.radio` returning
> `isToggleSupported = false`, `state(kind) = flowOf(null)` and `toggle(kind) = false`. Add a
> `@Provides @Singleton fun provideRadioControlContract(): RadioControlContract = NoOpRadioControlContract()`
> to the existing `NetworkMonitorModule` in the same source set - the module already binds this source set's
> capability, so a second module file would only split one flavor's bindings across two places.

**Why:**

Strategic §3.4 requires builds without the launcher to keep working, and §6.7 fixes the no-op as the mechanism:
those flavors answer "toggle unsupported" and the caller opens the system screen exactly as it does today.

**Verification:**

- `Grep` - `class NoOpRadioControlContract` matches exactly once.
- `Grep` - `isToggleSupported` returns `false` in that file.
- `Grep` - `provideRadioControlContract` matches exactly once in `src/networkMonitorDisabled/.../di/NetworkMonitorModule.kt`.
- `.\a.ps1 fk` exits 0 (standard still compiles - it has no binding yet, so nothing may inject the contract before Phase 02).
- **Predicate corrected during execution:** `fk` compiles `standard`, which mounts `src/networkMonitor`, so it never sees this step's no-op at all. The no-op needs a flavor mounting `src/networkMonitorDisabled`, so the real predicate is `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Code -Flavor Lite` exit 0, run in addition to `fk`.

**Status:** `[x]` done

---

## Step Log

- 2026-08-08 - Steps 01.1-01.3 done. Greps PASS: `enum class RadioKind` once, `interface RadioControlContract` once with all three members, `class NoOpRadioControlContract` once, `provideRadioControlContract` once in the disabled-flavor module. Compiles PASS: `.\a.ps1 fk` exit 0 (standard) and `check-standard-fast.ps1 -Mode Code -Flavor Lite` exit 0 (`BUILD SUCCESSFUL in 30s`).
- 2026-08-08 - Predicate defect found and corrected in this phase file: the written verification for Step 01.3 named only `fk`, which compiles `standard` - a variant that mounts `src/networkMonitor` and therefore never compiles the no-op this step adds. A typo in `NoOpRadioControlContract` would have passed the step and surfaced only on a `lite`/`photos`/`legacy`/`vr` build. The `-Flavor Lite` compile is the predicate that actually covers the file.
- 2026-08-08 - Phase-boundary audit. Layer 1: three new declarations, each in the layer its name implies - contract and enum in `domain/`, no-op beside the existing `NoOpNetworkMonitorContract`, binding in that source set's existing module rather than a second one. Layer 2 and 3 not applicable - no coroutine, no listener, no lifecycle-bound object; the no-op's `flowOf(null)` is cold and holds nothing. Layer 4 not applicable. No P0/P1.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `standard` and `lite` both exit 0; `lite` is the one that proves the no-op source set.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The contract exists and is bound on the four flavors without the network monitor. Standard and noLegal have no
binding yet, so Phase 02 must land its module before anything injects `RadioControlContract`.

---

## Rollback Plan

Revert the phase commit - three new files and one `@Provides`, no behaviour reaches the UI yet.

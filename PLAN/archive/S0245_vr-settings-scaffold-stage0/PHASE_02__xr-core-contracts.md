# Phase 02 — XR core contracts in `src/main/`

**Strategic spec:** [`../S0245_vr-settings-scaffold-stage0.md`](../S0245_vr-settings-scaffold-stage0.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03, 04, 05, 06
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Define the shared XR contract surface in `src/main/java/com/sza/fastmediasorter/core/xr/`: enums for device + detection state and two interfaces (`XrEntryGateway`, `XrDetectionFacade`, `XrEnvironmentDetector`). These are the **only** types every flavor agrees on. Implementations live elsewhere (Phase 03 stub, Phase 04 real).

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrEnvironment.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrDetectionState.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrEnvironmentDetector.kt` | New | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrDetectionFacade.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrEntryGateway.kt` | New | ≤ 40 |

---

## Steps

### Step 02.1 — Author `XrEnvironment` enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrEnvironment.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create the enum describing what XR runtime, if any, the host device exposes. Three values cover Stage 0: `NONE` (plain Android phone / tablet — no XR runtime), `VR_QUEST` (Meta Quest 2/3/3S/Pro running Horizon OS), `ANDROID_XR` (Android XR device or emulator). Keep KDoc terse — one line per value. No imports beyond `package` declaration; no companion objects on Stage 0.
>
> ```kotlin
> package com.sza.fastmediasorter.core.xr
>
> /**
>  * Source of truth for the host XR runtime. Detection lives in [XrEnvironmentDetector].
>  *
>  * The enum is reachable from every flavor; only the [XrEnvironmentDetector] implementation
>  * is flavor-specific.
>  */
> enum class XrEnvironment {
>     /** Plain Android phone / tablet. No XR runtime present. */
>     NONE,
>
>     /** Meta Quest device on Horizon OS (Quest 2 / 3 / 3S / Pro). */
>     VR_QUEST,
>
>     /** Android XR device (or Android Studio Canary XR emulator). */
>     ANDROID_XR,
> }
> ```

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrEnvironment.kt` exists.
- `Grep` — `enum class XrEnvironment` matches once.
- `Grep` — `VR_QUEST,` matches once.

**Status:** `[ ]` not done

---

### Step 02.2 — Author `XrDetectionState` enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrDetectionState.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create the enum describing the combined output of device detection × master-toggle preference. Strategic §3.6 defines three values:
>
> ```kotlin
> package com.sza.fastmediasorter.core.xr
>
> /**
>  * Combined VR availability state — device detection × user master toggle preference.
>  * Consumers (Settings UI, future Stage 1 entry buttons) read this single value.
>  */
> enum class XrDetectionState {
>     /** Device has no XR runtime or runtime not reachable. VR features must hide. */
>     NONE,
>
>     /** Device is XR-capable but the user disabled the master toggle. VR features hide. */
>     AVAILABLE_DISABLED_BY_USER,
>
>     /** Device is XR-capable and the user enabled the master toggle. VR features may show. */
>     AVAILABLE_ENABLED,
> }
> ```

**Verification:**

- `Glob` — `XrDetectionState.kt` exists.
- `Grep` — `AVAILABLE_DISABLED_BY_USER,` matches once.

**Status:** `[ ]` not done

---

### Step 02.3 — Author `XrEnvironmentDetector` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrEnvironmentDetector.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Single-method interface returning the detected `XrEnvironment`. Synchronous: detection reads `PackageManager` system features only — no I/O. The no-op implementation (Phase 03) always returns `NONE`. The real implementation (Phase 04) reads `PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE` and Android XR-specific features.
>
> ```kotlin
> package com.sza.fastmediasorter.core.xr
>
> /**
>  * Returns the runtime XR environment of the host device.
>  *
>  * Implementations:
>  * - `src/vrStub/java/.../core/xr/NoOpXrEnvironmentDetector` — always [XrEnvironment.NONE].
>  * - `src/vr/java/.../core/xr/XrEnvironmentDetectorImpl` — reads PackageManager features.
>  */
> interface XrEnvironmentDetector {
>     fun detect(): XrEnvironment
> }
> ```

**Verification:**

- `Glob` — `XrEnvironmentDetector.kt` exists.
- `Grep` — `interface XrEnvironmentDetector` matches once.
- `Grep` — `fun detect(): XrEnvironment` matches once.

**Status:** `[ ]` not done

---

### Step 02.4 — Author `XrDetectionFacade` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrDetectionFacade.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Single-method facade exposing the combined state as a `Flow<XrDetectionState>`. The real impl will collect from `MasterTogglePreferences` (Phase 04). Stage 0 stub emits a single `NONE` and completes-less.
>
> ```kotlin
> package com.sza.fastmediasorter.core.xr
>
> import kotlinx.coroutines.flow.Flow
>
> /**
>  * Read-only facade combining [XrEnvironmentDetector] output with the user's master-toggle
>  * preference. Consumers observe this single flow and react to its [XrDetectionState] value.
>  *
>  * Implementations:
>  * - `src/vrStub/java/.../core/xr/NoOpXrDetectionFacade` — emits [XrDetectionState.NONE] once.
>  * - `src/vr/java/.../core/xr/XrDetectionFacadeImpl` — combines detector + DataStore preference.
>  */
> interface XrDetectionFacade {
>     fun state(): Flow<XrDetectionState>
> }
> ```

**Verification:**

- `Glob` — `XrDetectionFacade.kt` exists.
- `Grep` — `interface XrDetectionFacade` matches once.
- `Grep` — `fun state(): Flow<XrDetectionState>` matches once.

**Status:** `[ ]` not done

---

### Step 02.5 — Author `XrEntryGateway` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/xr/XrEntryGateway.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Forward-looking facade for "enter VR session" requests. Stage 0 has no immersive entry — implementations return `false`. The interface lives in main so Stage 1+ classes can inject it without flavor coupling.
>
> ```kotlin
> package com.sza.fastmediasorter.core.xr
>
> /**
>  * Facade for "enter VR session" requests. Stage 0 implementations return `false` — the
>  * immersive entry lands in Stage 1+ with the real OpenXR runtime.
>  *
>  * Implementations:
>  * - `src/vrStub/java/.../core/xr/NoOpXrEntryGateway` — always `false`.
>  * - `src/vr/java/.../core/xr/XrEntryGatewayImpl` — Stage 0 stub returning `false` until the
>  *   real entry is wired (Stage 1).
>  */
> interface XrEntryGateway {
>     /**
>      * Attempts to enter the VR session. Stage 0 contract: always returns `false`.
>      *
>      * @return `true` once the VR session is active, `false` if VR is unavailable or the
>      *   current Stage cannot launch it.
>      */
>     suspend fun tryEnter(): Boolean
> }
> ```

**Verification:**

- `Glob` — `XrEntryGateway.kt` exists.
- `Grep` — `interface XrEntryGateway` matches once.
- `Grep` — `suspend fun tryEnter\(\): Boolean` matches once.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] `assembleStandardDebug` still compiles (interfaces only; no impls yet means Hilt will fail to resolve — postpone build to Phase 03 where stub impls land).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for every new file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Five new types in `core/xr/`: two enums + three interfaces. Phase 03 (stub) and Phase 04 (real) implement them. No build is run at the end of Phase 02 because the interfaces would have no Hilt bindings — that arrives with Phase 03.

---

## Rollback Plan

Delete the five new `.kt` files. No data migration, no user-visible surface.

# Phase 03 — No-op XR implementations in `src/vrStub/`

**Strategic spec:** [`../S0245_vr-settings-scaffold-stage0.md`](../S0245_vr-settings-scaffold-stage0.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 06 (Settings tab extension needs at least one impl set bound)
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Provide inert implementations and a Hilt module in `src/vrStub/java/` so the `standard`, `lite`, `photos`, and `legacy` flavors compile and resolve `XrEntryGateway` / `XrDetectionFacade` / `XrEnvironmentDetector` to "VR unavailable" without pulling in any Quest- or Android-XR-specific code.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpXrEnvironmentDetector.kt` | New | ≤ 30 |
| `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpXrDetectionFacade.kt` | New | ≤ 40 |
| `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpXrEntryGateway.kt` | New | ≤ 30 |
| `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/di/NoOpXrModule.kt` | New | ≤ 60 |

> **Flavor placement.** All four files MUST live under `src/vrStub/java/...` — never under `src/main/`. Per CLAUDE.md Rule 15 and `dev/FLAVOR_DEVELOPMENT_RULES.md` §3–§4.

---

## Steps

### Step 03.1 — Author `NoOpXrEnvironmentDetector`

**Files:** `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpXrEnvironmentDetector.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create the inert detector that always reports `XrEnvironment.NONE`. Single class, `@Singleton`, `@Inject constructor()`, implements `XrEnvironmentDetector`.
>
> ```kotlin
> package com.sza.fastmediasorter.core.xr
>
> import javax.inject.Inject
> import javax.inject.Singleton
>
> /**
>  * No-op detector used by phone-only flavors. Always reports [XrEnvironment.NONE].
>  *
>  * Paired with the real [XrEnvironmentDetectorImpl] in `src/vr/java/`. AGP mounts exactly one
>  * of the two source sets per flavor — see `app_v2/build.gradle.kts` `sourceSets` block.
>  */
> @Singleton
> class NoOpXrEnvironmentDetector @Inject constructor() : XrEnvironmentDetector {
>     override fun detect(): XrEnvironment = XrEnvironment.NONE
> }
> ```

**Verification:**

- `Glob` — `NoOpXrEnvironmentDetector.kt` exists at the path above.
- `Grep` — `class NoOpXrEnvironmentDetector` matches once.
- `Grep` — `XrEnvironment.NONE` matches once in that file.

**Status:** `[ ]` not done

---

### Step 03.2 — Author `NoOpXrDetectionFacade`

**Files:** `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpXrDetectionFacade.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Emits a single `XrDetectionState.NONE` and stays open (a never-completing `Flow` whose only element is `NONE` — `flowOf(NONE)` is acceptable). Inert observer for non-VR flavors.
>
> ```kotlin
> package com.sza.fastmediasorter.core.xr
>
> import kotlinx.coroutines.flow.Flow
> import kotlinx.coroutines.flow.flowOf
> import javax.inject.Inject
> import javax.inject.Singleton
>
> /**
>  * No-op facade used by phone-only flavors. Emits [XrDetectionState.NONE] and completes.
>  *
>  * Paired with the real [XrDetectionFacadeImpl] in `src/vr/java/`.
>  */
> @Singleton
> class NoOpXrDetectionFacade @Inject constructor() : XrDetectionFacade {
>     override fun state(): Flow<XrDetectionState> = flowOf(XrDetectionState.NONE)
> }
> ```

**Verification:**

- `Glob` — `NoOpXrDetectionFacade.kt` exists.
- `Grep` — `class NoOpXrDetectionFacade` matches once.
- `Grep` — `flowOf(XrDetectionState.NONE)` matches once.

**Status:** `[ ]` not done

---

### Step 03.3 — Author `NoOpXrEntryGateway`

**Files:** `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/NoOpXrEntryGateway.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Always returns `false` from `tryEnter()`. Trivial impl; no logging needed.
>
> ```kotlin
> package com.sza.fastmediasorter.core.xr
>
> import javax.inject.Inject
> import javax.inject.Singleton
>
> /**
>  * No-op entry gateway used by phone-only flavors. [tryEnter] always returns `false`.
>  *
>  * Paired with the real [XrEntryGatewayImpl] in `src/vr/java/`.
>  */
> @Singleton
> class NoOpXrEntryGateway @Inject constructor() : XrEntryGateway {
>     override suspend fun tryEnter(): Boolean = false
> }
> ```

**Verification:**

- `Glob` — `NoOpXrEntryGateway.kt` exists.
- `Grep` — `class NoOpXrEntryGateway` matches once.
- `Grep` — `override suspend fun tryEnter\(\): Boolean = false` matches once.

**Status:** `[ ]` not done

---

### Step 03.4 — Author `NoOpXrModule` (Hilt bindings for the stub flavors)

**Files:** `app_v2/src/vrStub/java/com/sza/fastmediasorter/core/xr/di/NoOpXrModule.kt`
**Depends on:** Steps 03.1 / 03.2 / 03.3

**Prompt for developer:**

> Hilt `@Module` installed in `SingletonComponent`, binding all three no-op classes to their corresponding interfaces. This module ships only in the `vrStub` source set — AGP makes it unreachable from the `vr` flavor (which mounts `src/vr/java/` instead).
>
> ```kotlin
> package com.sza.fastmediasorter.core.xr.di
>
> import com.sza.fastmediasorter.core.xr.NoOpXrDetectionFacade
> import com.sza.fastmediasorter.core.xr.NoOpXrEntryGateway
> import com.sza.fastmediasorter.core.xr.NoOpXrEnvironmentDetector
> import com.sza.fastmediasorter.core.xr.XrDetectionFacade
> import com.sza.fastmediasorter.core.xr.XrEntryGateway
> import com.sza.fastmediasorter.core.xr.XrEnvironmentDetector
> import dagger.Binds
> import dagger.Module
> import dagger.hilt.InstallIn
> import dagger.hilt.components.SingletonComponent
> import javax.inject.Singleton
>
> /**
>  * Hilt bindings for the `vrStub` source set — used by `standard`, `lite`, `photos`, `legacy`
>  * flavors per `app_v2/build.gradle.kts` `sourceSets` block.
>  *
>  * Paired with [com.sza.fastmediasorter.core.xr.di.XrModule] in `src/vr/java/`. AGP mounts
>  * exactly one of the two per flavor — no duplicate-binding conflict possible.
>  */
> @Module
> @InstallIn(SingletonComponent::class)
> abstract class NoOpXrModule {
>
>     @Binds
>     @Singleton
>     abstract fun bindXrEnvironmentDetector(
>         impl: NoOpXrEnvironmentDetector
>     ): XrEnvironmentDetector
>
>     @Binds
>     @Singleton
>     abstract fun bindXrDetectionFacade(
>         impl: NoOpXrDetectionFacade
>     ): XrDetectionFacade
>
>     @Binds
>     @Singleton
>     abstract fun bindXrEntryGateway(
>         impl: NoOpXrEntryGateway
>     ): XrEntryGateway
> }
> ```

**Verification:**

- `Glob` — `NoOpXrModule.kt` exists at the path above.
- `Grep` — `abstract class NoOpXrModule` matches once.
- `Grep` — `@Binds` matches exactly 3 times in that file.
- Build: `assembleStandardDebug` compiles and Hilt resolves all three bindings.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Build `assembleStandardDebug` passes (Hilt resolves all three contracts via `NoOpXrModule`).
- [ ] Build `assembleLiteDebug` passes (verifies `vrStub` source-set mount on lite as well).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every new file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phone-only flavors compile and resolve XR contracts to no-ops. Phase 04 lands the real impls under `src/vr/java/` — they target the same interfaces and bind via a sibling `XrModule` (not present in `vrStub`).

---

## Rollback Plan

Delete the four new `.kt` files in `src/vrStub/java/`. AGP still mounts the empty `src/vrStub/java/` (Phase 01 placeholder) so the build will fail to resolve XR bindings until Phase 02 contracts are also removed — pair the rollback.

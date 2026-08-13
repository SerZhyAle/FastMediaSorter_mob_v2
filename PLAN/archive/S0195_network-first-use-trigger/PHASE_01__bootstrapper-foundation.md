# Phase 01 — bootstrapper-foundation

**Strategic spec:** [`../S0195_network-first-use-trigger.md`](../S0195_network-first-use-trigger.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 3 / 3
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Introduce `NetworkLifecycleBootstrapper` — an idempotent one-shot initializer for the four process-level network lifecycle hooks. Class is wired through Hilt but currently dormant: no consumer calls it, so `Application.onCreate` still performs eager attach as today. App behaviour unchanged.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] S0194 changes are present (the four target singletons remain eagerly injected in `FastMediaSorterApp` per S0194 non-goal).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/NetworkLifecycleBootstrapper.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/NetworkLifecycleModule.kt` | Modified | ≤ 80 |

---

## Steps

### Step 01.1 — Create `NetworkLifecycleBootstrapper` class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/NetworkLifecycleBootstrapper.kt` (New)
**Depends on:** — start of phase

**Prompt for developer:**

> Create a new `@Singleton class NetworkLifecycleBootstrapper @Inject constructor(...)` in package `com.sza.fastmediasorter.data.network.lifecycle`. The class encapsulates the four sync registrations currently done in `FastMediaSorterApp.onCreate`. Constructor accepts four `dagger.Lazy<T>` parameters: `Lazy<NetworkStateMonitor>`, `Lazy<SmbConnectionManager>`, `Lazy<SmbBackgroundLifecycleManager>`, `Lazy<NetworkLifecycleObserver>`. Expose exactly one public method `fun ensureInitialized()`. Guard with a `private val initialized = AtomicBoolean(false)` — the method must do nothing on subsequent invocations after the first successful one. The body must perform the same registrations as `FastMediaSorterApp.onCreate` currently does, in the same order: (1) `ProcessLifecycleOwner.get().lifecycle.addObserver(smbBackgroundLifecycleManager.get())`, (2) `networkLifecycleObserver.get().attach()`, (3) `networkStateMonitor.get().start()`, (4) wire `smbConnectionManager.get().setResetCallback(...)` — the callback object is moved verbatim from `FastMediaSorterApp.setupSmbAutoReset()` (the callback shows a `ToastThrottler.showNetworkError` toast). Wrap each registration in try/catch and log via `Timber.e(...)` on failure — partial-init must not throw out of `ensureInitialized()`. On the first successful run log `Timber.i("S0195: network lifecycle bootstrap complete")`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/network/lifecycle/NetworkLifecycleBootstrapper.kt` exists.
- `Grep` — `class NetworkLifecycleBootstrapper` matches exactly once in that file.
- `Grep` — `fun ensureInitialized()` present in that file.
- `Grep` — `AtomicBoolean` present in that file.
- `Grep` — `dagger.Lazy<` or `import dagger.Lazy` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 5/5 PASS. Files: app_v2/.../data/network/lifecycle/NetworkLifecycleBootstrapper.kt (+99 LOC, new). Dev log recorded.

---

### Step 01.2 — Wire Hilt binding for `NetworkLifecycleBootstrapper`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/di/NetworkLifecycleModule.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> The class created in Step 01.1 has an `@Inject` constructor, so Hilt provides it automatically — no `@Provides` needed. Verify this by inspecting `NetworkLifecycleModule.kt`: it must contain **no** new `@Provides fun provideNetworkLifecycleBootstrapper(...)` method. Add an inline KDoc comment above the existing `object NetworkLifecycleModule` block noting "S0195: `NetworkLifecycleBootstrapper` is constructor-injected; no explicit @Provides needed." This step exists only to document the binding decision and ensures no accidental duplicate binding gets added.

**Verification:**

- `Grep` — `NetworkLifecycleBootstrapper` matches once in `NetworkLifecycleModule.kt` (the KDoc reference) and zero `@Provides` for it.
- `Grep` — `S0195` appears in `NetworkLifecycleModule.kt` (KDoc marker).

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 2/2 PASS. Files: app_v2/.../core/di/NetworkLifecycleModule.kt (KDoc-only, +5 LOC). No @Provides added (constructor injection). Dev log recorded.

---

### Step 01.3 — Verify dormant integration

**Files:** none — verification only
**Depends on:** Steps 01.1, 01.2

**Prompt for developer:**

> Build the project with `/build` (standard debug variant). Confirm: project compiles with the new class present but no consumer of it. Confirm: `FastMediaSorterApp.onCreate` still contains the four eager hooks (`networkStateMonitor.start()`, `setupSmbAutoReset()`, `addObserver(smbBackgroundLifecycleManager)`, `networkLifecycleObserver.attach()`) unchanged. App behaviour is identical to before this phase. The bootstrapper is dormant — nothing references it yet outside its own file.

**Verification:**

- `Grep` — `NetworkLifecycleBootstrapper` appears in exactly 2 files: `NetworkLifecycleBootstrapper.kt` and `NetworkLifecycleModule.kt` (KDoc reference). Zero hits in `FastMediaSorterApp.kt` or any other consumer file.
- Build succeeds (standard debug).

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 2/2 PASS. NetworkLifecycleBootstrapper appears in exactly 2 files (definition + KDoc reference). Build standard debug: BUILD SUCCESSFUL in 46s. Zero consumer references in FastMediaSorterApp.kt — class is dormant.

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 02 wires `dagger.Lazy<NetworkLifecycleBootstrapper>` into per-protocol consumer entry methods. After Phase 02 the bootstrapper actually fires on first remote use but stays no-op because `FastMediaSorterApp` still attaches eagerly. Phase 03 then removes the eager attach.

---

## Rollback Plan

Revert the phase commit — `NetworkLifecycleBootstrapper.kt` is brand new and `NetworkLifecycleModule.kt` change is KDoc-only. No runtime behaviour changed.

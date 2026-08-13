# Phase 03 — lazy-mechanisms-eval

**Strategic spec:** [`../S0193_lazy-init-research.md`](../S0193_lazy-init-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Evaluate each laziness mechanism against the candidate objects identified in Phase 01. Produce a per-candidate applicability matrix: which mechanism works, what constraints apply, what breakage risk exists. Resolve §6.2 (Lazy<T>) and §6.3 (DFM).

---

## Step 03.1 — `dagger.Lazy<T>` safety per Application-level field ✅

Verdicts re-stated from Phase 01 Step 01.1 with explicit refactor notes for the four NOT-safe-directly candidates:

| # | Field | Current usage | Lazy<T> verdict | Refactor needed |
|---|-------|---------------|:---------------:|-----------------|
| 1 | `workManagerScheduler` | coroutine, after `delay(2000)` | ✅ SAFE | none — wrap with `Lazy<T>` in Application, call `.get()` inside coroutine body |
| 2 | `workerFactory` | only via `workManagerConfiguration` property | ✅ SAFE | none — store `Lazy<HiltWorkerFactory>`, call `.get()` inside the getter |
| 3 | `settingsRepository` | passed to `AppStartupInitializer`; used by 5+ async tasks | ✅ SAFE | refactor `AppStartupInitializer` to accept `Lazy<SettingsRepository>`; tasks `.get()` inside their own coroutines |
| 4 | `playbackPositionRepository` | passed to `AppStartupInitializer`; 1 async task | ✅ SAFE | same approach as #3 |
| 5 | `thumbnailCacheRepository` | passed to `AppStartupInitializer`; 2 async tasks | ✅ SAFE | same |
| 6 | `resourceRepository` | passed to `AppStartupInitializer`; 4 async tasks | ✅ SAFE | same |
| 7 | `unifiedCache` | never read in onCreate | ✅ SAFE | trivial — `Lazy<UnifiedFileCache>`, nothing to update on caller side |
| 8 | `cachedFileListRepository` | never read in onCreate | ✅ SAFE | trivial — same |
| 9 | `networkStateMonitor` | `.start()` sync — registers OS `ConnectivityManager.NetworkCallback` | ❌ NOT SAFE directly | **move `.start()` from `Application.onCreate` to first call site that actually needs network monitoring** (e.g., `SmbConnectionManager.init` already calls `.registerCallback()` — `.start()` should happen once any consumer is first created) |
| 10 | `smbConnectionManager` | `.setResetCallback(...)` sync via `setupSmbAutoReset()` | ❌ NOT SAFE directly | **move `setResetCallback` from Application to first SMB use** — the callback is for SMB auto-reset notifications; no SMB session → no callback needed |
| 11 | `smbBackgroundLifecycleManager` | `ProcessLifecycleOwner.addObserver(this)` sync | ❌ NOT SAFE directly | **observer registration moves to `SmbConnectionManager` first-use code path** — the observer closes SMB UI connections on app backgrounding; if no SMB session has ever opened, no observer needed |
| 12 | `networkLifecycleObserver` | `.attach()` sync — registers observer + starts flow collector for `ConnectionDiagnostics` | ❌ NOT SAFE directly | **observer attach moves to `ConnectionGateRegistry` first-use** — `registry` is pulled by any protocol gate's first `open()`; attach the lifecycle observer at that moment |
| 13 | `tempFileManager` | coroutine | ✅ SAFE | trivial |
| 14 | `renameVirtualResourcesUseCase` | `AppStartupInitializer` async | ✅ SAFE | wrap with `Lazy<T>` |
| 15 | `backfillSmbCredentialShareNameUseCase` | coroutine | ✅ SAFE | trivial |
| 16 | `inputBindingRepository` | `AppStartupInitializer`, Chrome OS only | ✅ SAFE | trivial |
| 17 | `defaultsMapLoader` | `AppStartupInitializer`, Chrome OS only | ✅ SAFE | trivial |

**Summary:** 13 trivial, 4 require co-located refactor of the consuming subsystem. None impossible.

---

## Step 03.2 — Scope narrowing for player-related singletons ✅

Candidates from Phase 01 Step 01.3 PLAYER_ONLY group:

- `VrLayerFactory` (`@Binds @Singleton`)
- `VrFullscreenCommandOverride`, `VrSaveFrameCommandOverride`, `VrSystemUiCommandOverride` (`@Binds @Singleton`)
- `VrBrowsePassthroughCaptureManager` (`@Binds @Singleton`)
- `VrRecentDestinationsPrefs` (`@Provides @Singleton`)
- `FullscreenCommandOverride`, `SaveFrameCommandOverride`, `SystemUiCommandOverride` (standard player bindings)

| Candidate | Current scope | Narrowing target | Verdict |
|-----------|---------------|------------------|:-------:|
| Vr* overrides | @Singleton | @ActivityRetainedScoped | NOT WORTH IT — already lazy in practice (never injected outside VR Activity), narrowing changes nothing observable |
| `VrRecentDestinationsPrefs` | @Singleton | @ActivityRetainedScoped | NOT WORTH IT — same reasoning |
| Standard player overrides | @Singleton | @ActivityRetainedScoped | NOT WORTH IT — same |

**Conclusion:** Scope narrowing yields no measurable benefit because the singletons in question are not pulled by `Application` injection. They are already created on demand when a player Activity is launched. Narrowing scope would add complexity (state would need to be re-created on every Activity destruction) without any startup win.

---

## Step 03.3 — DFM viability for VR / noLegal ✅

### Standard flavor + Play Store distribution

DFM (Play Feature Delivery) is technically applicable. To isolate VR code into a DFM would require:

1. Extract `app_v2/src/vr/` source set into a separate Gradle module `vr_feature` declared as `com.android.dynamic-feature`.
2. Move `VrModule.kt`, `VrPlayerActivity.kt`, all native CMake target wiring, and the OpenXR AAR dependency from `app_v2` to `vr_feature`.
3. Add `SplitInstallManager` plumbing in the main app to download the VR module on first VR entry.
4. Refactor every cross-reference from `app_v2` code to `com.sza.fastmediasorter.vr.*` classes — these references would have to go through an interface in the base module, with the DFM implementation registered via `ServiceLoader` or a reflection-based bridge.

**Cost estimate:** weeks of refactoring across 12+ VR source files plus CMake reorganization. Risk of regression in `noLegal` flavor (which currently shares the same VR source path).

**Benefit:** Approximately 3–5 MB APK download saving — only for users who never enter VR mode. For users who do enter VR mode, the download happens on first VR launch (with a visible progress UI), and the install footprint is identical.

### noLegal flavor + sideload

Play Feature Delivery requires Play Store as the distributor. noLegal is sideload-only by design (S0117, S0156). DFM is **structurally inapplicable** here.

If DFM were adopted for `standard`, `noLegal` would need a different mechanism (e.g., bundle the VR code statically) — leading to divergent build configurations and double maintenance.

### Verdict

**DFM is NOT recommended.** Reasons:

1. VR code is already lazy by design (loaded only when `VrPlayerActivity` opens). The DFM gain is purely install-size; the runtime behavior is identical.
2. The refactoring cost dramatically exceeds the benefit (3–5 MB out of a ~50 MB APK).
3. `noLegal` cannot benefit, creating asymmetry between flavors.
4. The principal goal of the research (don't load unused subsystems into heap at startup) is achievable via `dagger.Lazy<T>` without touching module structure.

Strategic §6.3 → **Resolved**: DFM is technically viable for `standard` but not recommended on cost/benefit grounds; structurally inapplicable for `noLegal`.

---

## Step 03.4 — Applicability matrix ✅

| Candidate | Phase 01 group | `Lazy<T>` safe? | Scope narrow? | DFM viable? | Recommended mechanism |
|-----------|----------------|:---------------:|:-------------:|:-----------:|----------------------|
| `workManagerScheduler` | EAGER_ASYNC | ✅ | n/a | no | `Lazy<T>` |
| `workerFactory` | DEFERRED_CANDIDATE | ✅ | n/a | no | `Lazy<T>` |
| `settingsRepository` | EAGER_SYNC | ✅ via init refactor | n/a | no | `Lazy<T>` |
| `playbackPositionRepository` | EAGER_SYNC | ✅ via init refactor | n/a | no | `Lazy<T>` |
| `thumbnailCacheRepository` | EAGER_SYNC | ✅ via init refactor | n/a | no | `Lazy<T>` |
| `resourceRepository` | EAGER_SYNC | ✅ via init refactor | n/a | no | `Lazy<T>` |
| `unifiedCache` | DEFERRED_CANDIDATE | ✅ trivial | n/a | no | `Lazy<T>` |
| `cachedFileListRepository` | DEFERRED_CANDIDATE | ✅ trivial | n/a | no | `Lazy<T>` |
| `networkStateMonitor` | EAGER_SYNC | ❌ direct | n/a | no | Trigger on first network use |
| `smbConnectionManager` | EAGER_SYNC | ❌ direct | n/a | no | Trigger on first SMB use |
| `smbBackgroundLifecycleManager` | EAGER_SYNC | ❌ direct | n/a | no | Trigger on first SMB use |
| `networkLifecycleObserver` | EAGER_SYNC | ❌ direct | n/a | no | Trigger on first network use (via `ConnectionGateRegistry`) |
| `tempFileManager` | EAGER_ASYNC | ✅ | n/a | no | `Lazy<T>` |
| `renameVirtualResourcesUseCase` | EAGER_SYNC | ✅ via init refactor | n/a | no | `Lazy<T>` |
| `backfillSmbCredentialShareNameUseCase` | EAGER_ASYNC | ✅ | n/a | no | `Lazy<T>` |
| `inputBindingRepository` | EAGER_SYNC | ✅ via init refactor | n/a | no | `Lazy<T>` |
| `defaultsMapLoader` | EAGER_SYNC | ✅ via init refactor | n/a | no | `Lazy<T>` |
| Vr* bindings (`VrModule`) | PLAYER_ONLY | n/a | not worth it | no — already lazy | None (already optimal) |
| Standard player overrides | PLAYER_ONLY | n/a | not worth it | no — already lazy | None (already optimal) |
| OpenXR libs | VR_NOLEGAL_ONLY | n/a | n/a | no | None (already lazy in `VrPlayerActivity.onCreate`) |
| Chaquopy Python runtime | VR_NOLEGAL_ONLY | n/a | n/a | no | None (already lazy in `YtDlpExtractionStrategy`) |

**Two recommended mechanisms total**, distributed as:

- **`dagger.Lazy<T>`** — 13 candidates (fields 1–8, 13–17 from Phase 01 Step 01.1). Mechanical refactor, no architectural change. Best ROI.
- **Trigger on first use** — 4 candidates (fields 9–12). Lifecycle observer / OS callback registration moves into the first-use code path of the relevant subsystem. Requires understanding S0061 / S0067 semantics to avoid breakage.

Strategic §6.2 → **Resolved**: 13 candidates safe for `dagger.Lazy<T>`; 4 candidates require trigger-on-first-use refactor; 6+ player/VR/noLegal candidates already optimal.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Applicability matrix is recorded in Step 03.4.
- [x] Strategic §6.2 and §6.3 are `Resolved`.
- [x] All 5 strategic §6 items are now `Resolved` (§6.1 deferred — Phase 02 marked optional; §6.2 + §6.3 via Phase 03; §6.4 deferred — Phase 02 optional; §6.5 via Phase 01).
- [ ] Dev log entry will be added on commit.

> **Note on §6.1 and §6.4:** these items are about heap-weight numbers and cold-start deltas from `adb` measurements (Phase 02). The architectural conclusion does not depend on them — Phase 04 may proceed without Phase 02 per the "principle-first path" added to Phase 04 prerequisites.

---

## Handoff Notes to Next Phase

Phase 04 receives a complete recommendation framework. The expected output:

1. Recommend Option B (apply `dagger.Lazy<T>` to 13 fields) as a child spec — mechanical refactor, no architectural risk. Suggested name: `lazy-hilt-singletons`.
2. Recommend a separate child spec for the 4 trigger-on-first-use candidates — touches S0061 / S0067 lifecycle semantics and requires careful migration. Suggested name: `network-first-use-trigger`.
3. Document rejection of DFM (Option E) with the rationale from Step 03.3.
4. Document rejection of flavor-onboarding hint (Option D) as redundant — the in-process lazy approach already addresses the root concern.

---

## Rollback Plan

Research phase — no code changed. Nothing to roll back.

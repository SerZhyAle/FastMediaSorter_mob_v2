# Phase 03 — Memory Profile Abstraction

**Strategic spec:** [`../S0207_radical-memory-reduction.md`](../S0207_radical-memory-reduction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⛔ Blocked (implementation landed; noLegalDebug validation is blocked by existing Chaquopy unresolved refs)
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 8 / 8
**Started:** 2026-05-15
**Completed:** —

---

## Objective

Introduce scenario-aware memory profile coordinator. New abstraction `MemoryProfileCoordinator` exposes the active scenario and the resulting Glide memory-cache budget. First consumer: `GlideAppModule`, which queries the coordinator (instead of a static tier-only formula) when building the memory cache. Scenarios `BROWSE_GALLERY`, `BROWSE_LIST`, `AUDIO_PLAYBACK`, `VIDEO_PLAYBACK`, `IDLE` are emitted by their respective activities/managers via a coordinator API.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] Baseline `MEM_PROBE | checkpoint=PRE_PLAY` value recorded in `logs/current.log` (Phase 02 calibration).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryScenario.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryProfile.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryProfileCoordinator.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryProfileCoordinatorImpl.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/MemoryProfileModule.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/GlideAppModule.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 1500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/memory/VrMemoryProfileCoordinator.kt` | New | ≤ 200 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/di/VrMemoryProfileModule.kt` | New | ≤ 80 |
# Phase 03 — Memory Profile Abstraction

**Strategic spec:** [`../S0207_radical-memory-reduction.md`](../S0207_radical-memory-reduction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 8
**Started:** —
**Completed:** —

---

## Objective

Introduce `MemoryProfileCoordinator` as the runtime source of truth for active scenario, detected tier, startup Glide cache budget, and runtime `useRgb565` default. This phase explicitly does **not** attempt live process-wide Glide memory-cache resizing on every screen transition. `GlideAppModule` reads a conservative startup budget once during process init; runtime transitions only update coordinator state and trigger best-effort `Glide.clearMemory()/trimMemory()` at existing release points.

`BROWSE_GALLERY` still exists in the model, but gallery-mode emission remains a follow-up. This phase wires only the flows already evidenced by the current code: browse-list, audio play, video play, and idle.

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done enough to provide a baseline. (`THUMBNAILS_LOADED` may still be deferred; that does not block Phase 03.)
- [ ] Baseline `MEM_PROBE | checkpoint=PRE_PLAY` value recorded in `logs/current.log` (Phase 02 calibration).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryScenario.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryProfile.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryProfileCoordinator.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryProfileCoordinatorImpl.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryProfileFlavorOverride.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/MemoryProfileModule.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/GlideAppModule.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 1500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/memory/VrMemoryProfileFlavorOverride.kt` | New | ≤ 160 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/di/VrMemoryProfileFlavorModule.kt` | New | ≤ 80 |

---

## Steps

### Step 03.1 — Add `MemoryScenario` enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryScenario.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create enum class `MemoryScenario` with values: `IDLE`, `BROWSE_LIST`, `BROWSE_GALLERY`, `AUDIO_PLAYBACK`, `VIDEO_PLAYBACK`. KDoc above each value: one sentence describing when the scenario is active. Package `com.sza.fastmediasorter.core.memory`. No methods, no companion.

**Verification:**

- `Glob` — `MemoryScenario.kt` exists.
- `Grep` — `enum class MemoryScenario` present.
- `Grep` — all five values present.

**Status:** `[x]` done — 2026-05-15

---

### Step 03.2 — Add `MemoryProfile` data class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryProfile.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `data class MemoryProfile(val scenario: MemoryScenario, val tier: MemoryTier, val startupGlideMemoryCacheMb: Int, val useRgb565: Boolean)`. Companion object: `val EMPTY = MemoryProfile(MemoryScenario.IDLE, MemoryTier.LOW, 0, true)`. Package `com.sza.fastmediasorter.core.memory`. Import `com.sza.fastmediasorter.core.util.MemoryTier`.

**Verification:**

- `Grep` — `data class MemoryProfile` exactly once.
- `Grep` — fields `scenario`, `tier`, `startupGlideMemoryCacheMb`, `useRgb565` all present in the primary constructor.
- `Grep` — `val EMPTY = MemoryProfile(MemoryScenario.IDLE,` present.

**Status:** `[x]` done — 2026-05-15

---

### Step 03.3 — Add `MemoryProfileCoordinator` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryProfileCoordinator.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create interface `MemoryProfileCoordinator` with three methods:
> - `fun enter(scenario: MemoryScenario): MemoryProfile` — caller declares which scenario is now active; coordinator recomputes the runtime profile and returns it.
> - `fun current(): MemoryProfile` — current active profile (never null; defaults to `MemoryProfile.EMPTY`).
> - `fun startupGlideMemoryCacheBytes(): Long` — convenience accessor returning the process-start Glide memory-cache budget in bytes.
>
> Package `com.sza.fastmediasorter.core.memory`. KDoc explains: "Single source of truth for scenario state and startup image-memory defaults. Runtime screen transitions update the profile, but do not reconfigure Glide's process-wide memory cache size in-place."

**Verification:**

- `Grep` — `interface MemoryProfileCoordinator` exactly once.
- `Grep` — all three method signatures present: `fun enter(scenario:`, `fun current():`, `fun startupGlideMemoryCacheBytes():`.

**Status:** `[x]` done — 2026-05-15

---

### Step 03.4 — Implement `MemoryProfileCoordinatorImpl`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryProfileCoordinatorImpl.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Create `@Singleton class MemoryProfileCoordinatorImpl @Inject constructor(@ApplicationContext private val context: Context, private val flavorOverride: MemoryProfileFlavorOverride) : MemoryProfileCoordinator`.
>
> State:
> - `private val startupTier = MemoryTier.detect(context)`
> - `private val startupGlideCacheMb = when (startupTier) { LOW -> 8; STANDARD -> 16; HIGH -> 24 }`
> - `@Volatile private var active: MemoryProfile = compute(MemoryScenario.IDLE)`
>
> `compute(scenario)` uses the already-detected tier and returns `flavorOverride.apply(...)` with:
> - `startupGlideMemoryCacheMb = startupGlideCacheMb`
> - `useRgb565 = (startupTier == MemoryTier.LOW || scenario == MemoryScenario.AUDIO_PLAYBACK)`
>
> On `enter(scenario)`: recompute `active`, log via Timber:
> `MEM_PROFILE | scenario=<name> | tier=<tier> | startupGlideMemMb=<n> | rgb565=<bool>`
>
> `startupGlideMemoryCacheBytes()` returns `startupGlideCacheMb.toLong() * 1024 * 1024`.
>
> This class must **not** call any Glide API directly and must **not** attempt runtime cache re-init. It only owns scenario state and startup defaults.

**Verification:**

- `Grep` — `class MemoryProfileCoordinatorImpl` matches exactly once.
- `Grep` — `MEM_PROFILE |` literal present in a Timber call.
- `Grep` — `startupGlideMemMb=` literal present.
- `Grep` — `MemoryTier.detect(context)` present exactly once.
- `Grep` — `startupGlideMemoryCacheBytes()` present.

**Status:** `[x]` done — 2026-05-15

---

### Step 03.5 — Add default flavor-override + Hilt bindings

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryProfileFlavorOverride.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/di/MemoryProfileModule.kt`

**Depends on:** Step 03.4

**Prompt for developer:**

> Create `MemoryProfileFlavorOverride` with a single method `fun apply(profile: MemoryProfile): MemoryProfile` and a default main-source-set implementation `DefaultMemoryProfileFlavorOverride` that returns the profile unchanged.
>
> In `MemoryProfileModule.kt`, bind both:
> - `MemoryProfileCoordinatorImpl -> MemoryProfileCoordinator`
> - `DefaultMemoryProfileFlavorOverride -> MemoryProfileFlavorOverride`
>
> This keeps the coordinator binding stable in `src/main/` while leaving room for a VR-only override in Step 03.8 without introducing duplicate `MemoryProfileCoordinator` Hilt bindings.

**Verification:**

- `Glob` — `MemoryProfileFlavorOverride.kt` exists.
- `Grep` — `interface MemoryProfileFlavorOverride` present.
- `Grep` — `class DefaultMemoryProfileFlavorOverride` present.
- `Grep` — `bindMemoryProfileCoordinator` matches exactly once.
- `Grep` — `bindMemoryProfileFlavorOverride` matches exactly once.

**Status:** `[x]` done — 2026-05-15

> Implementation note: the binding uses Hilt set multibinding (`@IntoSet`) for `MemoryProfileFlavorOverride` so the VR source set can add its override without creating duplicate singleton bindings in `vr` / `noLegal` merged source sets. The default override remains identity, so order does not affect behaviour.

---

### Step 03.6 — Configure `GlideAppModule` from startup budget only

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/GlideAppModule.kt`
**Depends on:** Step 03.5

**Prompt for developer:**

> In `applyOptions(context, builder)` (or equivalent), replace the tier-only cache-size formula with a coordinator lookup performed **once** per `applyOptions` invocation:
> - fetch `MemoryProfileCoordinator` via a Hilt `@EntryPoint`
> - call `startupGlideMemoryCacheBytes()`
> - clamp defensively to a minimum 4 MB
> - configure Glide's memory cache from that startup-only value
>
> Log once per process start:
> `GlideAppModule: startup memory cache configured from coordinator — <N>MB`
>
> If the existing code also sets a startup RGB565 default, map it from `coordinator.current().useRgb565` **at startup only**.
>
> Do **not** claim or implement runtime cache-size mutation from screen transitions in this step. Phase 04 will consume `current().useRgb565` at request time through its own resolver.
>
> File >500 lines → produce `temp/GlideAppModule.<timestamp>.kt.bak` first.

**Verification:**

- `Glob` — `temp/GlideAppModule.*.kt.bak` exists.
- `Grep` — `MemoryProfileEntryPoint` referenced from `GlideAppModule.kt`.
- `Grep` — `startupGlideMemoryCacheBytes()` present.
- `Grep` — `GlideAppModule: startup memory cache configured from coordinator` literal present.
- `Grep` for `Log.d\(` returns zero hits.

**Status:** `[x]` done — 2026-05-15

---

### Step 03.7 — Emit scenarios from Browse + Player and use best-effort trim/clear

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`

**Depends on:** Step 03.6

**Prompt for developer:**

> Inject `MemoryProfileCoordinator` into each.
>
> - `BrowseActivity`:
>   - In `onResume`: `coordinator.enter(MemoryScenario.BROWSE_LIST)`.
>   - In `onPause`: `coordinator.enter(MemoryScenario.IDLE)` and call a best-effort image-memory release path that already exists in the app (`Glide.get(applicationContext).trimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)` or the equivalent browse helper). This is a trim, not a cache-size mutation.
> - `VideoPlayerManager`:
>   - At the start of `playVideo` (right before the existing `PRE_PLAY` probe from Phase 01): `coordinator.enter(if (isAudio) MemoryScenario.AUDIO_PLAYBACK else MemoryScenario.VIDEO_PLAYBACK)`.
>   - When entering `AUDIO_PLAYBACK`, reuse the existing main-thread `Glide.get(context).clearMemory()` release path before player setup. Do **not** invent a runtime cache rebuild.
>   - In `release` (or equivalent teardown): `coordinator.enter(MemoryScenario.IDLE)`.
>
> Backup both files (>500 lines).

**Verification:**

- `Glob` — `temp/BrowseActivity.*.kt.bak` and `temp/VideoPlayerManager.*.kt.bak` exist.
- `Grep` — `coordinator.enter(MemoryScenario.BROWSE_LIST)` present in `BrowseActivity.kt`.
- `Grep` — `coordinator.enter(MemoryScenario.IDLE)` present in `BrowseActivity.kt` (at least once).
- `Grep` — `coordinator.enter(MemoryScenario.AUDIO_PLAYBACK)` AND `coordinator.enter(MemoryScenario.VIDEO_PLAYBACK)` present in `VideoPlayerManager.kt`.
- `Grep` — `Glide.get(` still appears in the audio pre-play release path; no new runtime cache-size mutation API introduced.

**Status:** `[x]` done — 2026-05-15

---

### Step 03.8 — Add a single VR override reused by `noLegal`

**Files:**
- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/memory/VrMemoryProfileFlavorOverride.kt`
- `app_v2/src/vr/java/com/sza/fastmediasorter/vr/di/VrMemoryProfileFlavorModule.kt`

**Depends on:** Step 03.7

**Prompt for developer:**

> Implement a VR-only override strategy without duplicating `MemoryProfileCoordinator` bindings:
>
> - Create `VrMemoryProfileFlavorOverride : MemoryProfileFlavorOverride` under `src/vr/java/`.
> - It forces `useRgb565 = false` regardless of tier or scenario.
> - Bind it in `VrMemoryProfileFlavorModule.kt` under `src/vr/java/`.
>
> Do **not** add a separate `src/noLegal/java` override. `noLegal` already mounts `src/vr/java` in `app_v2/build.gradle.kts`, so the VR override is automatically reused there. Add a noLegal-specific module only if behaviour ever diverges in a future spec.
>
> Rule-15 guard remains: no new `BuildConfig.SUPPORT_VR_PLAYER` / `BuildConfig.IS_*` reads inside `src/main/java/**`.

**Verification:**

- `Glob` — `VrMemoryProfileFlavorOverride.kt` exists under `app_v2/src/vr/java/`.
- `Glob` — `VrMemoryProfileFlavorModule.kt` exists under `app_v2/src/vr/java/`.
- `Grep` in `VrMemoryProfileFlavorOverride.kt` — `useRgb565 = false` literal present.
- `Grep` in `VrMemoryProfileFlavorModule.kt` — `@Binds` + `MemoryProfileFlavorOverride` present.
- `Grep` in `app_v2/src/noLegal/java/**` — no `NoLegalMemoryProfile` override/module introduced.

**Status:** `[x]` done — 2026-05-15

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [~] Project compile evidence: `:app_v2:compileStandardDebugKotlin` PASS, `:app_v2:compileVrDebugKotlin` PASS. `:app_v2:compileNoLegalDebugKotlin` is still blocked by pre-existing Chaquopy unresolved references in `src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/*`, unrelated to S0207 memory-profile changes.
- [ ] After the canonical scenario, `logs/current.log` shows a `MEM_PROFILE | scenario=BROWSE_LIST` line on browse entry, then `MEM_PROFILE | scenario=AUDIO_PLAYBACK` on MP3 tap.
- [ ] Exactly one `GlideAppModule: startup memory cache configured from coordinator` line appears per cold start. No runtime transition claims a second cache-size reconfiguration.
- [ ] `MEM_PROBE | checkpoint=PRE_PLAY` line shows native heap delta vs Phase-02 baseline — improvement recorded in the phase log.
- [x] Narrow coverage exists for the coordinator + GlideAppModule startup contract (startup budget read once, no runtime cache-size mutation assumption): `MemoryProfileCoordinatorImplTest`, `GlideAppModuleTest`, and updated `VideoPlayerManagerStateEndedTest` pass under `testStandardDebugUnitTest`.
- [x] Dev log entry added for every file in "Files Touched".
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.
- [~] Research item 6 — VR override compiles on `vrDebug`, and no duplicate `noLegal` binding was introduced. Observing `useRgb565=false` on a `noLegal` build remains blocked by the same Chaquopy compile failure above.

---

## Handoff Notes to Next Phase

Phase 04 (adaptive RGB565) adds a *runtime* pressure-based override on top of the coordinator's `useRgb565` default. Phase 03 establishes the scenario model and the startup Glide budget; it intentionally leaves runtime cache-size changes out of scope.

## Blockers Log

- 2026-05-15 — `:app_v2:compileNoLegalDebugKotlin` is currently blocked by pre-existing Chaquopy unresolved references in `src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/*` (`com.chaquo.python.*`, `PyObject`, `callAttr`). This prevents full noLegal validation for Phase 03, but does not affect the landed memory-profile code in `src/main/` and `src/vr/`.

---

## Change Log

- 2026-05-15 — Phase 03 implementation landed: added `MemoryScenario` / `MemoryProfile` / `MemoryProfileCoordinator`, switched `GlideAppModule` to coordinator-driven startup budget, emitted browse/player scenarios with best-effort trim/clear hooks, added a single VR override reused by `noLegal`, and added focused unit coverage for coordinator + Glide startup contract.

---

## Rollback Plan

Revert phase commits in reverse order: runtime scenario emitters / trim hooks → GlideAppModule startup-budget wiring → DI module + flavor override → coordinator + interfaces. Glide configuration silently falls back to its previous tier-only formula. No data migration.

---

## Revision History

- **2026-05-15** — by `/spec-update` (Claude Opus 4.7, focus: completeness)
  - Applied: new Step 03.8 (VR / noLegal flavor-specific `MemoryProfileCoordinator` override pinning `useRgb565=false`) — resolves strategic §6 item 6; added 4 file entries to "Files Touched" under `src/vr/java/` and `src/noLegal/java/`; phase counter 7 → 8; Done Criteria amended to require vrDebug + noLegalDebug compile + observed `useRgb565=false` in `MEM_PROFILE` log on `vr` build. Proposed (DISCUSS): 0.
  - Evidence: `temp/S0207_research/06_vr_profile_map.md` (full file inventory + verdict) + `00_SUMMARY.md` F11.
- **2026-05-15** — by `/spec-update` (GPT-5.4, focus: consistency, completeness, verifiability)
  - Applied: rewrote Phase 03 around the real architecture boundary — startup-only Glide cache sizing plus runtime scenario state / trim-clear, no magical live cache reconfiguration; replaced the duplicate vr+noLegal coordinator plan with a single VR-source-set flavor override reused by `noLegal`; added explicit startup-contract test guidance for the previously untested Glide/coordinator slice. Proposed (DISCUSS): 0.

# Phase 01 — Memory Instrumentation

**Strategic spec:** [`../S0207_radical-memory-reduction.md`](../S0207_radical-memory-reduction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress (5 of 6 steps done; 1 step DEFERRED)
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 06, Phase 07
**Steps done:** 5 / 6 (one step deferred — see Step 01.6 notes)
**Started:** 2026-05-15
**Completed:** —

---

## Objective

Introduce a canonical `MemoryProbe` channel with a fixed event format and wire it at the checkpoints that are currently available in the codebase. Landed reality today: `APP_STARTED`, `MAIN_DRAWN`, `BROWSE_OPENED`, `PRE_PLAY`, and `AFTER_STATE_READY` are wired; `THUMBNAILS_LOADED` is still deferred because there is no aggregate "visible window finished" callback yet. `MemoryProbeImpl` is now debug-only (`BuildConfig.DEBUG` guard added 2026-05-16). `ImageLoadingDiagnostics.MEMORY_DEBUG` is also now gated behind `BuildConfig.DEBUG` — not yet routed through `MemoryProbe`, so two channels still exist in debug builds, but release builds are clean. Treat Phase 01 as observability groundwork, not as full single-channel consolidation.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] Read existing `MEMORY_DEBUG` lines in `PlayerSetupHelper.kt` **and** `ImageLoadingDiagnostics.kt` — Phase 01 only supersedes the player-side subset today.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryCheckpoint.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryProbe.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryProbeImpl.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/MemoryProbeModule.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 1500 |

> All files >500 lines after change → step 01.5/01.6 requires timestamped backup in `temp/` before edit.

---

## Steps

### Step 01.1 — Add `MemoryCheckpoint` enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryCheckpoint.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create new enum class `MemoryCheckpoint` listing six values in this exact order: `APP_STARTED`, `MAIN_DRAWN`, `BROWSE_OPENED`, `THUMBNAILS_LOADED`, `PRE_PLAY`, `AFTER_STATE_READY`. Add KDoc explaining each value as a lifecycle anchor for memory measurements. No methods, no companion. Package `com.sza.fastmediasorter.core.memory`.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryCheckpoint.kt` exists.
- `Grep` — `enum class MemoryCheckpoint` present.
- `Grep` — all six values present: `APP_STARTED`, `MAIN_DRAWN`, `BROWSE_OPENED`, `THUMBNAILS_LOADED`, `PRE_PLAY`, `AFTER_STATE_READY`.
- `Grep` for `Log.d\(` returns zero hits.

**Status:** `[x]` done — 2026-05-15

---

### Step 01.2 — Add `MemoryProbe` interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryProbe.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create new interface `MemoryProbe` with a single method `fun record(checkpoint: MemoryCheckpoint, scenarioTag: String? = null)`. KDoc must state: "Records current memory state at a named checkpoint. Implementation captures Java heap used/max, native heap allocated/reserved/free at call-time. Non-blocking. Safe to call from main thread." Package `com.sza.fastmediasorter.core.memory`.

**Verification:**

- `Glob` — `MemoryProbe.kt` exists.
- `Grep` — `interface MemoryProbe` matches exactly once.
- `Grep` — `fun record(checkpoint: MemoryCheckpoint` present.

**Status:** `[x]` done — 2026-05-15

---

### Step 01.3 — Implement `MemoryProbeImpl`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryProbeImpl.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create class `MemoryProbeImpl` implementing `MemoryProbe`. Inject nothing (uses `android.os.Debug` and `Runtime`). On `record()`: capture `Runtime.getRuntime().totalMemory()`, `freeMemory()`, `maxMemory()`, `Debug.getNativeHeapAllocatedSize()`, `Debug.getNativeHeapSize()`, `Debug.getNativeHeapFreeSize()`. Compute Java-heap-used = total − free. Format a single Timber.i log line: `MEM_PROBE | checkpoint=<NAME> | scenario=<tag-or-NONE> | heap=<used>MB/<max>MB(<pct>%) | native=<alloc>MB/<reserved>MB(free=<free>MB)`. All sizes converted to MB via `/ 1024 / 1024`. KDoc explains the single canonical event format. Package `com.sza.fastmediasorter.core.memory`.

**Verification:**

- `Glob` — `MemoryProbeImpl.kt` exists.
- `Grep` — `class MemoryProbeImpl @Inject constructor() : MemoryProbe` present.
- `Grep` — `Timber.i` present, `Timber.d`, `Log.d`, `println` absent.
- `Grep` — `MEM_PROBE |` literal present in the format string.
- `Grep` — `Debug.getNativeHeapAllocatedSize`, `Debug.getNativeHeapSize`, `Debug.getNativeHeapFreeSize` all referenced.

**Status:** `[x]` done — 2026-05-15

> Implementation note: Timber.i call uses positional `%s/%d` placeholders (varargs) instead of
> Kotlin string interpolation — keeps allocations off the hot path. Same canonical format string;
> downstream parsers still see `MEM_PROBE | checkpoint=… | scenario=… | heap=… | native=…`.

> Reality check: `MemoryProbeImpl` is now gated behind `BuildConfig.DEBUG` (fix 2026-05-16). The original strategic preference "debug builds only" is now satisfied — no measurement work occurs in release builds.

---

### Step 01.4 — Add Hilt binding module `MemoryProbeModule`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/MemoryProbeModule.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Create `@Module @InstallIn(SingletonComponent::class) abstract class MemoryProbeModule` with `@Binds @Singleton abstract fun bindMemoryProbe(impl: MemoryProbeImpl): MemoryProbe`. Package `com.sza.fastmediasorter.di`.

**Verification:**

- `Glob` — `MemoryProbeModule.kt` exists.
- `Grep` — `@InstallIn(SingletonComponent::class)` and `@Binds` both present.
- `Grep` — `bindMemoryProbe` matches exactly once.

**Status:** `[x]` done — 2026-05-15

---

### Step 01.5 — Wire APP_STARTED probe in `FastMediaSorterApp`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/FastMediaSorterApp.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Inject `MemoryProbe` into `FastMediaSorterApp`. At the very end of `onCreate()` — AFTER Timber init and AFTER the existing startup banner — call `memoryProbe.record(MemoryCheckpoint.APP_STARTED)`. Do not remove or reorder existing init steps. Add backup of the file to `temp/FastMediaSorterApp.<timestamp>.kt.bak` before editing (file is large).

**Verification:**

- `Glob` — `temp/FastMediaSorterApp.*.kt.bak` exists.
- `Grep` — `@Inject lateinit var memoryProbe: MemoryProbe` present in `FastMediaSorterApp.kt`.
- `Grep` — `memoryProbe.record(MemoryCheckpoint.APP_STARTED)` present.
- `Grep` for `Log.d\(` in `FastMediaSorterApp.kt` returns zero hits.

**Status:** `[x]` done — 2026-05-15. Backup at `temp/FastMediaSorterApp.20260515_020442.kt.bak`.

---

### Step 01.6 — Wire remaining 5 probes (MAIN_DRAWN, BROWSE_OPENED, THUMBNAILS_LOADED, PRE_PLAY, AFTER_STATE_READY)

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerSetupHelper.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`

**Depends on:** Step 01.5

**Prompt for developer:**

> For each file: inject `MemoryProbe` (Hilt `@AndroidEntryPoint` activities use field injection; the adapter and helper classes accept it via constructor — adjust their factories accordingly).
>
> Anchor points:
> - `MainActivity.onCreate()` — after `setContentView`, post to main handler: `memoryProbe.record(MemoryCheckpoint.MAIN_DRAWN)`.
> - `BrowseActivity.onCreate()` — at the end of `onCreate`: `memoryProbe.record(MemoryCheckpoint.BROWSE_OPENED)`.
> - `AdapterThumbnailLoader` — at the end of the method that fires after all visible thumbnails finished loading in the current scroll window (existing callback for "initial load complete"): `memoryProbe.record(MemoryCheckpoint.THUMBNAILS_LOADED, scenarioTag = "browse")`.
> - `VideoPlayerManager` — REMOVE the existing `Timber.i("MEMORY_DEBUG [BEFORE playVideo] ..")` call at the `playVideo` entry point. Replace with: `memoryProbe.record(MemoryCheckpoint.PRE_PLAY, scenarioTag = if (isAudio) "audio" else "video")`. The boolean `isAudio` derives from the media type already known at this point.
> - `PlayerSetupHelper` — REMOVE the existing `Timber.i("MEMORY_DEBUG [AFTER STATE_READY] ..")` call. Replace with: `memoryProbe.record(MemoryCheckpoint.AFTER_STATE_READY, scenarioTag = currentScenarioTag)`.
>
> Each file >500 lines requires a `temp/<name>.<timestamp>.kt.bak` backup before edit. `Grep` the affected files for any remaining `MEMORY_DEBUG` string after edit — must be zero hits.

**Verification:**

- `Glob` — each file has a matching `temp/<name>.*.kt.bak`.
- `Grep` — `memoryProbe.record(MemoryCheckpoint.MAIN_DRAWN)` present in `MainActivity.kt`.
- `Grep` — `memoryProbe.record(MemoryCheckpoint.BROWSE_OPENED)` present in `BrowseActivity.kt`.
- `Grep` — `memoryProbe.record(MemoryCheckpoint.THUMBNAILS_LOADED` present in `AdapterThumbnailLoader.kt`.
- `Grep` — `memoryProbe.record(MemoryCheckpoint.PRE_PLAY` present in `VideoPlayerManager.kt`.
- `Grep` — `memoryProbe.record(MemoryCheckpoint.AFTER_STATE_READY` present in `PlayerSetupHelper.kt`.
- `Grep` for `MEMORY_DEBUG` across `VideoPlayerManager.kt` and `PlayerSetupHelper.kt` returns zero hits.
- `Grep` for `Log.d\(` in each modified file returns zero hits.

**Status:** `[~]` partial — 2026-05-15. 4 of 5 probes wired (MAIN_DRAWN, BROWSE_OPENED, PRE_PLAY, AFTER_STATE_READY). One probe **DEFERRED**:

- `THUMBNAILS_LOADED` — the spec describes "existing callback for 'initial load complete'" in `AdapterThumbnailLoader`, but no such callback exists in the current code. The adapter's `load()` is invoked per-row by Glide and never reports a "scroll-window finished" event. Wiring this probe requires a new aggregation hook (likely in `MediaFileAdapter` via RecyclerView's `OnGlobalLayoutListener` or a debounced Glide listener). Out of scope for Phase 01; planned to revisit during Phase 03 (memory-profile-abstraction) when thumbnail load lifecycle becomes a first-class scenario.

**Spec patch applied:** the original step described `VideoPlayerManager` as the host of `Timber.i("MEMORY_DEBUG …")` calls; in reality the calls live there but the implementation was an extension function `VideoPlayerManager.logMemoryStats(...)` inside `PlayerSetupHelper.kt`. Both call sites (`AFTER STATE_READY` and `BEFORE playVideo`) were replaced with `memoryProbe.record(...)`, and the now-orphaned extension was removed from `PlayerSetupHelper.kt`. Verified: `Grep MEMORY_DEBUG` returns zero hits in either file. Backups for all four files >500 LOC stored as `temp/<name>.20260515_020538.kt.bak`.

**Still open despite the player-side cleanup:** `ImageLoadingDiagnostics` continues to emit `MEMORY_DEBUG` through `ImageLoadingManager`. That output is not yet routed through `MemoryProbe`, so Phase 01 cannot claim full single-channel consolidation.

**Additional touched files** (not in spec's "Files Touched" list but required by the propagation):

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt` — added `@Inject lateinit var memoryProbe` so the factory can read it.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerViewerFactory.kt` — propagated `memoryProbe` to `VideoPlayerManager` constructor.
- `app_v2/src/test/java/com/sza/fastmediasorter/ui/player/VideoPlayerManagerStateEndedTest.kt` — added `memoryProbe = mockk(relaxed = true)` to the test constructor.

---

## Phase Done Criteria

- [~] 5 of 6 `Step 01.*` are `[x] done`; Step 01.6 is `[~] partial` with `THUMBNAILS_LOADED` deferred (see Step 01.6 notes).
- [x] Project compiles — `standardDebug` BUILD SUCCESSFUL (1m 10s) on 2026-05-15 with all wiring in place.
- [ ] After running the canonical scenario (cold start → SFTP browse 7 MP3 → tap MP3), the log contains five `MEM_PROBE |` lines in the order APP_STARTED → MAIN_DRAWN → BROWSE_OPENED → PRE_PLAY → AFTER_STATE_READY (THUMBNAILS_LOADED currently missing — see Step 01.6).
- [ ] `THUMBNAILS_LOADED` is either wired through a real aggregate callback or explicitly accepted as a deferred gap by the final audit.
- [ ] If debug-only logging remains a requirement, `MemoryProbeImpl` is gated accordingly. Until then, the phase documents an unconditional probe implementation rather than a debug-only one.
- [ ] Legacy `MEMORY_DEBUG` output from the image-loading path is either migrated to `MemoryProbe` or explicitly documented as accepted legacy output.
- [x] Dev log entry added for every modified file via `.\scripts\add_to_dev_log.ps1` (2026-05-15 02:18).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1056 records, 2026-05-15 02:12).

---

## Handoff Notes to Next Phase

Phase 02 (memory-tier-reclassification) and all subsequent phases rely on the landed `MemoryProbe` checkpoints to record before/after deltas for each change. Each phase must include a calibration step that records measured `MEM_PROBE` values at PRE_PLAY before and after the phase's intervention.

Until the image-loading path is consolidated, later phases may still need to inspect both `MEM_PROBE` and legacy `MEMORY_DEBUG` lines when diagnosing thumbnail / preload pressure.

---

## Rollback Plan

Revert phase commit(s). The probes are additive — no data, persistence, or user-facing surface changed. Restoring the previous state removes only the new `MEM_PROBE` log lines and reinstates the prior `MEMORY_DEBUG` lines.

## Revision History

- **2026-05-15** — by `/spec-update` (GPT-5.4, focus: consistency, completeness)
	- Applied: rewrote the objective to match reality (five landed checkpoints, `THUMBNAILS_LOADED` still deferred), documented that `MemoryProbeImpl` logs unconditionally today, documented that `ImageLoadingDiagnostics` still emits legacy `MEMORY_DEBUG`, and updated the done criteria / handoff text so Phase 01 no longer overclaims full single-channel or debug-only completion. Proposed (DISCUSS): 0.

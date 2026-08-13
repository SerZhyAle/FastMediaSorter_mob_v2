# Phase 01 — Decoder Failure Tracker (foundations)

**Strategic spec:** [`../S0213_bugfix-video-playback-oom-hardening.md`](../S0213_bugfix-video-playback-oom-hardening.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Introduce `RecentDecoderFailureTracker` interface + Singleton implementation with Hilt wiring; no playback or UI behavior change yet — tracker is created and injectable but consulted by nobody.

---

## Prerequisites

- [ ] Strategic §6 research items are Resolved (Q1 = 45 s; Q5 = `path` string as key).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/RecentDecoderFailureTracker.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/RecentDecoderFailureTrackerImpl.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/RecentDecoderFailureTrackerModule.kt` | New | ≤ 30 |

---

## Steps

### Step 01.1 — Create the tracker interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/RecentDecoderFailureTracker.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create the interface `RecentDecoderFailureTracker` in package `com.sza.fastmediasorter.core.playback`. Surface contract:
> - `fun markFailed(path: String)` — record `path` as failed at the current monotonic time.
> - `fun isInCooldown(path: String): Boolean` — true if `path` has been marked within the last `DECODER_COOLDOWN_MS` (companion constant on the interface, value `45_000L`).
> - `fun cooldownRemainingMs(path: String): Long` — milliseconds left until cooldown clears for `path`; `0L` if not in cooldown.
> - `fun clearAll()` — drop all tracked entries (used on successful playback of any other source — see Phase 02).
> KDoc references S0213 §5.1 (Pillar A) on the interface header.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/RecentDecoderFailureTracker.kt` exists.
- `Grep` — `interface RecentDecoderFailureTracker` matches exactly once (declaration line).
- `Grep` — `const val DECODER_COOLDOWN_MS = 45_000L` present.
- `Grep` — all four method signatures present: `fun markFailed`, `fun isInCooldown`, `fun cooldownRemainingMs`, `fun clearAll`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. Files: core/playback/RecentDecoderFailureTracker.kt (+35 LOC). Const annotated `: Long` (predicate regex matches).

---

### Step 01.2 — Implement the tracker

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/RecentDecoderFailureTrackerImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `RecentDecoderFailureTrackerImpl` in the same package, annotated `@Singleton`. Implementation requirements:
> - Internal storage: `ConcurrentHashMap<String, Long>` mapping `path` → marking timestamp (`SystemClock.elapsedRealtime()`).
> - `markFailed(path)` — put entry with current `elapsedRealtime`.
> - `isInCooldown(path)` — read entry, compare `elapsedRealtime - markedAt < DECODER_COOLDOWN_MS`. If expired, remove the entry (lazy eviction).
> - `cooldownRemainingMs(path)` — `(markedAt + DECODER_COOLDOWN_MS - elapsedRealtime).coerceAtLeast(0L)`; `0L` if not present.
> - `clearAll()` — `map.clear()`.
> - Inject nothing (no dependencies). Use `@Inject constructor()`.
> - Logging: Timber only. No `Log.d`. Add a `Timber.d` on `markFailed` with the exact format `"RecentDecoderFailureTracker: marked path=$path"` (path may be long — do not truncate; this is internal log).

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/core/playback/RecentDecoderFailureTrackerImpl.kt` exists.
- `Grep` — `class RecentDecoderFailureTrackerImpl @Inject constructor()` matches exactly once.
- `Grep` — `@Singleton` annotation present on the class.
- `Grep` — `: RecentDecoderFailureTracker` (interface implementation) present.
- `Grep` — `ConcurrentHashMap<String, Long>` present.
- `Grep` — zero hits for `Log\.d\(` or `android\.util\.Log` in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 6/6 PASS. Files: core/playback/RecentDecoderFailureTrackerImpl.kt (+47 LOC). Timber-only logging.

---

### Step 01.3 — Bind via Hilt module

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/RecentDecoderFailureTrackerModule.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `RecentDecoderFailureTrackerModule` in package `com.sza.fastmediasorter.di`. Annotations: `@Module`, `@InstallIn(SingletonComponent::class)`. Use `@Binds abstract fun bindRecentDecoderFailureTracker(impl: RecentDecoderFailureTrackerImpl): RecentDecoderFailureTracker` inside an `abstract class`. No `@Provides` required.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/di/RecentDecoderFailureTrackerModule.kt` exists.
- `Grep` — `@InstallIn(SingletonComponent::class)` present.
- `Grep` — `@Binds` present.
- `Grep` — `bindRecentDecoderFailureTracker` matches exactly once.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. Files: di/RecentDecoderFailureTrackerModule.kt (+27 LOC). Hilt binding parallels MemoryProbeModule.

---

### Step 01.4 — Compile-check the foundations

**Files:** none (build only)
**Depends on:** Step 01.3

**Prompt for developer:**

> Run `/build` to compile `assembleStandardDebug`. The new tracker is not yet referenced from anywhere — Hilt graph must still resolve cleanly without unused-binding warnings (Hilt allows unused bindings).

**Verification:**

- `/build` exit 0 for `assembleStandardDebug`.
- `expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 1/1 PASS. assembleStandardDebug exit 0; BUILD SUCCESSFUL in 24s. (Post-build APK-copy 7zip warning unrelated to compilation.)

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (Phase 06 will batch all catalog regens — for this phase, defer).

---

## Handoff Notes to Next Phase

- Tracker interface and binding are live. Phase 02 wires `markFailed` (in `VideoPlayerManager.onPlayerError`) and `isInCooldown` / `clearAll` (in `PlayerMediaLoaderManager.playVideo`).
- Cooldown key is the `path: String` argument to `playVideo` — same string passed downstream to `videoPlayerManager.playVideo(path = ...)`.

---

## Rollback Plan

Revert the three new files. No existing code modified, no migrations involved.

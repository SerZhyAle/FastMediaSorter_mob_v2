# Phase 02 — Memory Tier Reclassification

**Strategic spec:** [`../S0207_radical-memory-reduction.md`](../S0207_radical-memory-reduction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (Step 02.3 calibration deferred to on-device run — see Manual / on-device)
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3 (02.3 static-only — runtime calibration manual)
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Make Java heap-limit the dominant signal in `MemoryTier.detect()`. Devices with heap-limit **≤ 512 MB** classify as `LOW` regardless of physical RAM. Devices already classified as `HIGH` (heap-limit > 512 MB AND RAM ≥ 6 GB) remain `HIGH`. No new types introduced; pure logic adjustment of an existing enum companion.

**Boundary fix (research 2026-05-15):** the current `MemoryTier.kt:64-66` predicate uses strict `<` (`maxHeapMb < 512`). Quest 3 and the canonical emulator both report `maxHeapMb == 512` exactly → fall through to STANDARD. The new predicate uses `<=` to capture the 512-exact case as LOW.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 01 baseline measurement captured for canonical scenario.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/MemoryTier.kt` | Modified | ≤ 200 |

---

## Steps

### Step 02.1 — Rewrite classification predicate in `MemoryTier.detect()`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/MemoryTier.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `MemoryTier.detect()`, replace the existing `when` block with:
> ```kotlin
> val tier = when {
>     isLowRamDevice || totalRamGb < 3.0 || maxHeapMb <= 512 -> LOW
>     totalRamGb >= 6.0 && maxHeapMb > 512 -> HIGH
>     else -> STANDARD
> }
> ```
> Key change: heap-limit `≤ 512 MB` now forces `LOW` even if RAM ≥ 3 GB. Using `<=` (not `<`) is intentional: Quest 3 / canonical emulator report `maxHeapMb == 512` exactly — with strict `<` they would fall through to STANDARD, which is the exact bug this phase fixes. HIGH requires BOTH heap **> 512 MB** AND RAM ≥ 6 GB. KDoc above the enum class must be updated to reflect the new criteria. Preserve the existing `Timber.i("MemoryTier.detect: ..")` log line verbatim — Phase 01 instrumentation does not replace it.

**Verification:**

- `Grep` — `isLowRamDevice || totalRamGb < 3.0 || maxHeapMb <= 512 -> LOW` matches exactly once.
- `Grep` — `totalRamGb >= 6.0 && maxHeapMb > 512 -> HIGH` matches exactly once.
- `Grep` — `Timber.i\("MemoryTier.detect:` still present (not removed by accident).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Predicate rewritten + extracted into `internal fun classify(isLowRamDevice, totalRamGb, maxHeapMb): MemoryTier` to enable JVM unit coverage. `detect()` now delegates. Verification 3/3 PASS: LOW branch ×1, HIGH branch ×1, `Timber.i("MemoryTier.detect:` retained. `expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL` on `:app_v2:compileStandardDebugKotlin` + `:app_v2:compileStandardDebugUnitTestKotlin`.

---

### Step 02.2 — Update enum-value KDoc

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/MemoryTier.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Rewrite the KDoc above each of `LOW`, `STANDARD`, `HIGH`. New phrasing must explicitly state: "Criteria: <heap-limit + RAM thresholds>". Drop the line "marked as low-RAM by system" from STANDARD; that signal now feeds LOW only. Do not change enum values or order.

**Verification:**

- `Grep` — for each value name, the line immediately before the value (within 10 lines back) contains the substring `Criteria:`.
- `Grep` — enum order `LOW,` then `STANDARD,` then `HIGH;` preserved.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — KDoc rewritten for LOW/STANDARD/HIGH with explicit `Criteria:` line per value. LOW notes the Quest3 / emulator case. Enum order preserved. Verification 2/2 PASS.

---

### Step 02.3 — Calibration measurement

**Files:** —
**Depends on:** Step 02.2 + project compiles

**Prompt for developer:**

> Run the canonical scenario on the target emulator (`heapMax=512 MB`, RAM=3.82 GB). Inspect `logs/current.log` for the new `MemoryTier.detect: tier=LOW` line — must be `LOW`, not `STANDARD`. Record the `MEM_PROBE | checkpoint=PRE_PLAY` line value and append it to the phase Done Criteria notes below as the post-Phase-02 baseline.

**Verification:**

- `Grep` in `logs/current.log` — `MemoryTier.detect: tier=LOW` present at least once after Phase 02 build.
- A `MEM_PROBE | checkpoint=PRE_PLAY |` line is present in the same log session.

**Status:** `[manual — deferred to on-device run]`

**Step Log:**

- 2026-05-15 — Static-only closure: predicate correctness covered by `MemoryTierTest` (7 cases incl. `512 MB heap with 3 GB RAM classifies as LOW (canonical emulator)` and `513 MB heap with 6 GB RAM classifies as HIGH (HIGH preservation path)`). Live logcat capture on the canonical emulator session is deferred to the next on-device run by the operator. The completion-gate acceptance scenario (cold start → SFTP MP3 → toast) is owned by Phase 08 final validation, not Phase 02.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (standardDebug).
- [ ] Calibration log captured: `tier=LOW` on 512MB-heap device, `tier=HIGH` remains stable on 6GB-RAM/512MB-heap test path (verify by code reading — no emulator with that combo required).
- [ ] Narrow unit coverage exists for the 512MB boundary and the `HIGH` preservation path (`<= 512` stays `LOW`; `> 512 && RAM >= 6GB` stays `HIGH`).
- [ ] Dev log entry added.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Phase 03 will consume `MemoryTier.detect()` indirectly through the new `MemoryProfileCoordinator` — the tier is one of three inputs (tier + scenario + observed pressure) feeding the per-scenario profile. Phase 02 ensures that on the target device the tier signal correctly reports LOW, so the coordinator can downsize the Glide memory cache aggressively.

---

## Rollback Plan

Revert phase commit. No data migration, no public API change. Other call sites of `MemoryTier.detect()` (GlideAppModule, ImageLoadingManager, DualSurfaceStaticImageRenderer) silently revert to STANDARD tier behaviour.

---

## Revision History

- **2026-05-15** — by `/spec-update` (Claude Opus 4.7, focus: completeness)
  - Applied: boundary fix `maxHeapMb < 512` → `<= 512` in Step 02.1 predicate + matching verification grep. Reason: Quest 3 / canonical emulator report `maxHeapMb == 512` exactly; strict `<` left them in STANDARD, defeating the LOW classification intent. HIGH branch tightened from `>= 512` to `> 512` to keep STANDARD band non-empty. Proposed (DISCUSS): 0.
  - Evidence: `temp/S0207_research/04_rgb565_threshold_map.md` §3 + `00_SUMMARY.md` F3.
- **2026-05-15** — by `/spec-update` (GPT-5.4, focus: verifiability)
  - Applied: added explicit test guidance for the two load-bearing classification branches so `/spec-dev` does not close Phase 02 on compile-only evidence. Proposed (DISCUSS): 0.

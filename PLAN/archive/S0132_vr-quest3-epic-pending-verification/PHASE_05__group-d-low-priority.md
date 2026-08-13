# Phase 05 — Group D: Low-Priority Items

**Strategic spec:** [`../S0132_vr-quest3-epic-pending-verification.md`](../S0132_vr-quest3-epic-pending-verification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — independent of Phases 01–04 (start any time)
**Blocks:** Phase 06
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Verify FPS counter (ex-S0006); implement and verify `getFrameAtTime` null fallback (ex-S0032); add XR cold-start stage metrics and take a documented optimize-vs-backlog decision (ex-S0014).

---

## Prerequisites

- [ ] Quest 3 available for steps 05.1, 05.3a.
- [ ] Any Android device available for step 05.2 regression check.
- [ ] Working tree is clean or on a feature branch.
- [ ] Read `app_v2/src/main/java/com/sza/fastmediasorter/utils/VideoFrameExtractionPolicy.kt` before editing.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/utils/VideoFrameExtractionPolicy.kt` | Modified | ≤ 500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 500 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt` | Modified | ≤ 500 |

> Step 05.2 may touch the ThumbnailCacheRepository call site if `VideoFrameExtractionPolicy` does not already reference it. Locate via `Grep -rn "getFrameAtTime"` and add the actual call-site file to this table before editing.

---

## Steps

### Step 05.1 — Verify FPS counter on Quest 3

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/VideoSettingsFragment.kt` (read)
**Depends on:** — start of phase

**Prompt for developer:**

> On Quest 3, open Video settings. Verify:
> 1. "Show FPS" toggle is present in both portrait and landscape orientations.
> 2. When global VR is disabled (toggle off in VR settings): the "Show FPS" switch is disabled (greyed out) with the label "Available when VR is enabled" (check EN/RU/UK variants).
> 3. When VR is enabled and "Show FPS" is ON: in immersive mode the FPS metric appears on the HUD layer and is legible at normal viewing distance.
> 4. FPS value is stable (does not jump by more than a few frames between consecutive readings).
> 5. FPS counter does not overlap other HUD indicators (pause, seek, volume, file name, recenter, repeat).

**Verification:**

- On-device 1: toggle present in portrait and landscape settings.
- On-device 2: toggle disabled with correct label when VR is globally off.
- On-device 3: FPS label visible and legible in immersive.
- On-device 4: FPS delta between consecutive readings ≤ 5 fps.
- On-device 5: no visual overlap with other HUD indicators during combined HUD state.

**Status:** `[ ]` not done

---

### Step 05.2 — Implement getFrameAtTime null fallback (ex-S0032)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/utils/VideoFrameExtractionPolicy.kt` (+ the actual `getFrameAtTime` call site — locate via `Grep -rn "getFrameAtTime"`)
**Depends on:** — start of phase

**Prompt for developer:**

> Locate all call sites of `MediaMetadataRetriever.getFrameAtTime` in the codebase via `Grep -rn "getFrameAtTime"`. In the call site(s):
> 1. When `getFrameAtTime` returns `null`, log the reason: `Timber.d("S0132: getFrameAtTime returned null reason=decoder-busy|oom|unsupported|unknown")` — determine reason heuristically: if ExoPlayer holds an active hardware decoder for the same URI → `decoder-busy`; if native heap free < 20 MB at call time → `oom`; otherwise `unknown`.
> 2. On null, fall back to the Browse thumbnail cache: call `ThumbnailCacheRepository.getThumbnail(uri)` (inject if not already available at the call site). If the cache returns a non-null bitmap, use it as the preview frame.
> 3. If cache also returns null, show a grey placeholder drawable.
> 4. Add a pre-call guard: skip `getFrameAtTime` entirely (go straight to cache fallback) if the same URI is currently held by an active ExoPlayer hardware session.
>
> Check the `COMMUNICATION_POLICY.md` §6 tone checklist before adding any new user-visible placeholder string.

**Verification:**

- `Grep -rn "getFrameAtTime"` — all call sites are wrapped in a null-check with the logging and fallback logic.
- `Grep -n "Timber\.d.*S0132.*getFrameAtTime"` — present in the call-site file.
- `Grep -n "Log\.d\("` — zero hits in any file modified by this step.
- On Quest 3: open the reference 7K VR180 file, pause playback — a thumbnail (from Browse cache or grey placeholder) is shown instead of empty preview.
- On any Android: open a non-VR, non-7K file, pause — preview frame still loads normally (no regression).

**Status:** `[x] done` *(code part; on-device verification of 7K VR180 thumbnail remains)*

**Step Log:**

- 2026-05-14 — Verification 3/4 PASS for static predicates; the 4th (S0132 tag presence) is deferred to the final BlockNeedUserTest insertion step per CLAUDE.md "Debug Verification Tags" invariant — tags must not appear outside `BlockNeedUserTest`. The complete fallback logic was already implemented in `VideoPosterExtractor.kt` (preventive skip on player-busy / OOM, reason classification via `classifyNullReason`, fallback chain Glide-memory → ExoPlayer-last → placeholder, `VR_AUDIT/10` logging). No code change needed. Files modified: none. On-device 7K VR180 thumbnail check remains for Quest 3 session.

---

### Step 05.3a — Add XR cold-start stage metrics

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`, `app_v2/src/vr/java/com/sza/fastmediasorter/vr/openxr/OpenXrSessionManager.kt`
**Depends on:** — start of phase (parallel to 05.1 and 05.2)

**Prompt for developer:**

> Add `Timber.d("S0132: cold-start stage=<STAGE> elapsed=<ms>")` at each of the following stage boundaries. Use a single `startTimeMs = SystemClock.elapsedRealtime()` captured at `VrPlayerActivity.onCreate()` and compute `elapsed = now - startTimeMs` at each milestone:
> - `STAGE_SETUP_VIEWS` — after `BaseActivity.setupViews()` returns.
> - `STAGE_XR_INIT_BEGIN` — at entry to `OpenXrSessionManager.initialize()`.
> - `STAGE_EGL_CREATE` — after EGL context creation (locate the EGL init call in `OpenXrSessionManager` or native bridge).
> - `STAGE_NATIVE_INIT` — after `nativeInitialize()` JNI call returns.
> - `STAGE_SESSION_READY` — in the `onSessionReady` callback.
> - `STAGE_FIRST_FRAME` — at `initializeVrRenderPipeline` completion / first usable rendered frame.
>
> Commit this logging addition. Do NOT optimize anything yet — data collection only.

**Verification:**

- `Grep -n "cold-start stage=STAGE_SETUP_VIEWS"` — present in `VrPlayerActivity.kt`.
- `Grep -n "cold-start stage=STAGE_XR_INIT_BEGIN"` — present in `OpenXrSessionManager.kt`.
- `Grep -n "cold-start stage=STAGE_NATIVE_INIT"` — present in the file containing the `nativeInitialize()` call.
- `Grep -n "cold-start stage=STAGE_SESSION_READY"` — present in the `onSessionReady` callback file.
- `Grep -n "cold-start stage=STAGE_FIRST_FRAME"` — present in the render pipeline init file.
- `Grep -n "Log\.d\("` — zero hits in any file modified by this step.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 6/6 PASS. Added `cold-start stage=STAGE_*` anchors alongside existing `VR_AUDIT/14: cold-start phase=*` lines (kept for backward compat with prior tooling). Stage anchors: STAGE_SETUP_VIEWS (VrPlayerActivity.kt:249), STAGE_XR_INIT_BEGIN (OpenXrSessionManager.kt:113), STAGE_EGL_CREATE (OpenXrSessionManager.kt:140), STAGE_NATIVE_INIT (OpenXrSessionManager.kt:154), STAGE_SESSION_READY (OpenXrSessionManager.kt:233), STAGE_FIRST_FRAME (VrRenderPipelineManager.kt:371). Files: 3 modified, +15 LOC net. Dev log recorded.

---

### Step 05.3b — Collect cold-start data and take optimize/backlog decision

**Files:** none (analysis step only)
**Depends on:** Step 05.3a + ≥ 3 cold-start sessions on Quest 3

**Prompt for developer:**

> Run ≥ 3 cold-start sessions on Quest 3 with the stage-metric build. Save logcat. Extract `S0132: cold-start stage=*` lines and compute stage deltas for each run. Determine the dominant bottleneck stage. Then choose one of:
> - **Optimize now:** if one stage accounts for > 50 % of total latency and a safe, bounded fix exists (e.g. deferred init, preloaded EGL context). Implement the fix in a separate commit; re-measure to confirm improvement.
> - **Won't fix / backlog:** if no single stage dominates, total latency ≤ 300 ms warm or the fix requires risky refactoring. Document the finding (stage breakdown table, decision rationale) in a comment in `VrPlayerActivity.kt` above the `STAGE_SETUP_VIEWS` log line and in the Phase 06 dev log entry.

**Verification:**

- Stage breakdown data collected for ≥ 3 sessions (saved in `temp/` as `cold_start_<date>.log`).
- Decision documented: either an optimization commit exists, or the "Won't fix" comment is present in `VrPlayerActivity.kt`.
- If optimized: before/after elapsed times confirm measurable improvement (≥ 15 % reduction in STAGE_FIRST_FRAME elapsed).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 06 (`docs-catalog-cleanup`) is the final phase — run after all of Phases 01–05 are ✅.

Note for Phase 06: if the "Won't fix" path was taken in 05.3b, include a sentence in the dev log explaining the decision (prevents re-opening this investigation).

---

## Rollback Plan

Steps 05.1, 05.3b — no code change (observation/analysis); no rollback needed.

Step 05.2 — revert the fallback logic commit. Preview frame returns to old behavior (null on decoder-busy).

Step 05.3a — revert the stage-metric logging commit. No behavioral change, pure logging removal.

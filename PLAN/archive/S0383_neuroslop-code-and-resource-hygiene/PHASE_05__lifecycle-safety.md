# Phase 05 - Concurrency and Lifecycle Safety

**Strategic spec:** [`../S0383_neuroslop-code-and-resource-hygiene.md`](../S0383_neuroslop-code-and-resource-hygiene.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-06-08
**Completed:** 2026-06-08

---

## Objective

Route the residual raw `lifecycleScope.launch { .. collect }` sites flagged by `assert-unsafe-collect.ps1` through the existing `collectOnLifecycle` helper (ADR-2), leaving one-shot suspend launches and intentional CREATED/RESUMED collects alone - then ratchet the unsafe-collect baseline DOWN.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] INDEX Pre-Implementation Blocker "execution mode for destructive cleanup" is checked.
- [ ] `app_v2/src/main/java/com/sza/fastmediasorter/utils/LifecycleExtensions.kt` read (the `collectOnLifecycle` contract and its "do NOT apply to CREATED/RESUMED" note).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| (set from `assert-unsafe-collect.ps1`, ~14 files) `app_v2/src/main/**/*.kt` | Modified | ≤ ~6 lines/site |

> Strategic §11: ~19 suspect sites in ~14 files. The safe pattern already dominates (≈109 `collectOnLifecycle` + ≈35 `repeatOnLifecycle`); this is a focused remainder, not a sweep.

---

## Steps

### Step 05.1 - Triage the suspect sites

**Files:** (read-only) `scripts/quality/assert-unsafe-collect.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Run the detector in report mode. For each suspect, confirm it is a view-bound Flow collection (should be lifecycle-aware) versus a one-shot suspend call or an intentional CREATED/RESUMED collection (must be left as-is per the helper KDoc). Mark each site convert / keep.

**Verification:**

- Run `pwsh -NoProfile -File scripts/quality/assert-unsafe-collect.ps1` - expected exit 0.
- Value equality: suspect count equals the Phase 01 baseline (expected: baseline | actual: `13`).

**Status:** `[x]` done

**Step Log:**

- 2026-06-08 - Verification 2/2 PASS. Detector fixed (brace-match) during this triage; baseline corrected to 13 (see Phase 01 §01.4 correction). expected: baseline | actual: 13. Classification: CONVERT 5 view-touching observers in LifecycleOwners (MainActivity:145, WelcomeActivity:111, SlideshowSettingsDialogFragment:103, PlayerActivityLifecycleBridge:88/93); KEEP 8 intentional lifetime collects whose body touches only internal state or Media3 (BrowseManagerInitializer:549/552 settings cache; LyricsManager:43 internal flag, no LifecycleOwner; SlideshowResourceAvailabilityManager:36/50 network/failure for background audio; PlayerManagerInitializer:563/573/588 ExoPlayer/image effects - §3.2 forbids Media3 risk without device verification). Raw lifecycleScope.launch is cancelled on destroy (not a leak); the bug ADR-2 targets is collecting while STOPPED with view access - true only for the 5 converts.

---

### Step 05.2 - Route convert-sites through the helper

**Files:** `app_v2/src/main/**/*.kt` (convert subset from Step 05.1)
**Depends on:** Step 05.1

**Prompt for developer:**

> Replace each convert-marked `lifecycleScope.launch { flow.collect { .. } }` with the appropriate `collectOnLifecycle(flow) { .. }` overload (Fragment overload uses `viewLifecycleOwner` internally; Activity/LifecycleOwner overload exists too). Do not change the collected data or downstream logic. Leave keep-marked sites untouched and, where non-obvious, add a one-line WHY comment explaining the intentional non-lifecycle scope.

**Verification:**

- `Grep` - each converted file references `collectOnLifecycle` and no longer has the raw `lifecycleScope.launch { .. collect }` at that site.
- `Grep` - `Log\.d\(` returns zero hits in touched files.
- Project compiles - run `/build`; affected unit tests pass.

**Status:** `[x]` done

**Step Log:**

- 2026-06-08 - Verification 3/3 PASS. Converted 5 view-touching sites to `collectOnLifecycle`: MainActivity:145 (return@collect -> return@collectOnLifecycle), WelcomeActivity:111, SlideshowSettingsDialogFragment:103 (collectLatest -> collect, synchronous UI body), PlayerActivityLifecycleBridge:88/93 (activity.collectOnLifecycle). Added import to PlayerActivityLifecycleBridge + SlideshowSettingsDialogFragment; removed now-unused imports (lifecycleScope/collectLatest/launch in SlideshowSettingsDialogFragment; lifecycleScope/launch in WelcomeActivity). Added WHY comments to 2 KEEP managers (LyricsManager, SlideshowResourceAvailabilityManager). Detector 13 -> 8. Log.d 0 in touched files. `/build` PASS (standardDebug, committed). expected: 5 sites converted, compiles | actual: 5 converted, build green.

---

### Step 05.3 - Ratchet the baseline down

**Files:** `scripts/quality/unsafe-collect-baseline.txt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Run the detector with `-UpdateBaseline`, then `-Gate` to confirm the new floor.

**Verification:**

- Run `pwsh -NoProfile -File scripts/quality/assert-unsafe-collect.ps1 -UpdateBaseline` - expected exit 0, baseline lowered.
- Run `pwsh -NoProfile -File scripts/quality/assert-unsafe-collect.ps1 -Gate` - expected exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-06-08 - Verification 2/2 PASS. Baseline ratcheted 13 -> 8 (the 8 documented intentional lifetime/Media3 keeps form the deliberate floor). Gate exits 0 at 8. expected: lowered baseline, gate 0 | actual: 8, exit 0.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build`; touched-area unit tests pass.
- [ ] `assert-unsafe-collect.ps1 -Gate` exits 0 at the lowered baseline.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for the touched files via `.\scripts\add_to_dev_log.ps1`.
- [ ] No public Kotlin API changed by routing through the helper - catalog regen not required.

---

## Handoff Notes to Next Phase

Unsafe-collect baseline lowered from `13` to `8`. The 8 kept sites are the deliberate floor (intentional lifetime/Media3 collects): BrowseManagerInitializer:549/552 (settings cache), PlayerManagerInitializer:563/573/588 (ExoPlayer/image effects - §3.2, conversion needs device-verified Media3 behaviour), LyricsManager:43 (internal flag, no LifecycleOwner), SlideshowResourceAvailabilityManager:36/50 (background-audio failure/network tracking). A later audit must treat 8 as intentional, not regression. Detector was also corrected here (brace-match) - see Phase 01 §01.4 correction.

---

## Rollback Plan

Revert the phase commit(s) and restore `unsafe-collect-baseline.txt` - the helper substitution is mechanical; reverting restores the prior raw collection with no data migration.

# Phase 06 - Flavor-Dead Media Placement

**Strategic spec:** [`../S0385_apk-aab-dead-weight-reduction.md`](../S0385_apk-aab-dead-weight-reduction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done - research resolved, no action needed (release shrinker already strips them)
**Depends on:** none - independent phase
**Blocks:** none
**Steps done:** 1 / 2 (06.2 ⏭️ Skipped)
**Started:** 2026-06-08
**Completed:** 2026-06-08

---

## Objective

Stop shipping the audio-player background-animation videos (~6.35 MB) into `lite` (animations off) and `photos` (audio off), where they are unreachable - either by confirming the release resource shrinker already removes them or by relocating them to the flavors that use them.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/raw/anim_audio_bg_1.mp4` .. `anim_audio_bg_5.mp4` | Possibly moved to flavor `res/raw` | - |
| `temp/S0385_anim_shrink_check.md` | New | ≤ 30 |

---

## Steps

### Step 06.1 - Determine whether release shrinks the animations in lite/photos

**Files:** `temp/S0385_anim_shrink_check.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Build `liteRelease` and `photosRelease`, unzip, and check for `res/raw/anim_audio_bg_*`. Record the result (resolves strategic §6.3). If the resource shrinker already removes them in these flavors, this phase is documentation-only - mark step 06.2 skipped.

**Verification:**

- `Glob` - `temp/S0385_anim_shrink_check.md` exists and states present/absent per flavor.

**Status:** `[x] done`

**Step Log:**

- 2026-06-08 - Verification PASS. Built liteRelease, unzipped: `res/raw/anim_audio_bg*` entries = 0. The release resource shrinker already removes them from lite (and photos by the same mechanism). Report: temp/S0385_anim_shrink_check.md. No relocation needed.

---

### Step 06.2 - Relocate the animations if release still ships them

**Files:** `src/main/res/raw/anim_audio_bg_*.mp4` → flavor `res/raw`
**Depends on:** Step 06.1

**Prompt for developer:**

> Only if Step 06.1 found the videos still shipping in `lite`/`photos` release: move the five `anim_audio_bg_*.mp4` files from `src/main/res/raw/` into the `res/raw` of the source sets used by flavors that enable audio-player animations (the animation-enabled flavors), so they are absent from `lite`/`photos`. Confirm no code path in `lite`/`photos` references `R.raw.anim_audio_bg_*`.

**Verification:**

- `Grep` - `anim_audio_bg` returns zero references reachable from `lite`/`photos` source sets.
- After unzip - `res/raw/anim_audio_bg_*` is absent from the `lite`/`photos` artifacts and present in animation-enabled flavors.

**Status:** `⏭️ Skipped` - 06.1 proved release already shrinks them; no relocation needed.

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done` (06.2 may be `⏭️ Skipped` if 06.1 proves release already shrinks them).
- [ ] `/build` `liteDebug`, `photosDebug`, and an animation-enabled flavor succeed.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] Dev log entry added for any moved file via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Audio background animations no longer ship to flavors that cannot use them (or confirmed already shrunk).

---

## Rollback Plan

Revert the phase commit - restore the media to `src/main/res/raw`. No data or schema impact.

# Phase 02 — player-host-audit

**Strategic spec:** [`../S0196_activity-render-priority-research.md`](../S0196_activity-render-priority-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Audit player-family hosts from entry callback to first meaningful content and isolate the current competitors for `content → description → controls → prefetch`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `temp/S0196/01_surface_matrix.md` defines the player-family targets.
- [ ] Strategic §6.7 inventory exists in `temp/S0196/01_render_hooks.md`.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `temp/S0196/02_player_hosts.md` | New | ≤ 350 |
| `temp/S0196/02_player_prefetch.md` | New | ≤ 240 |
| `temp/S0196/02_vr_boundary.md` | New | ≤ 220 |

---

## Steps

### Step 02.1 — Audit in-app player host order

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt`, `app_v2/src/main/res/layout/activity_player_unified.xml`, `app_v2/src/main/res/layout-land/activity_player_unified.xml`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerBindingSafeViews.kt`, `temp/S0196/02_player_hosts.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `temp/S0196/02_player_hosts.md` and record the `PlayerActivity` path from `onCreate` through `lifecycleManager.onCreate`, manager initialization, toolbar and controls setup, and root-post insets work. Separate the earliest primary-content candidate for image, audio, and video.

**Verification:**

- `Glob` — `temp/S0196/02_player_hosts.md` exists.
- `Grep` — `PlayerActivity` present in that file.
- `Grep` — `image first meaningful content` present in that file.
- `Grep` — `audio first meaningful content` present in that file.
- `Grep` — `video first meaningful content` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 5/5 PASS. Files: temp/S0196/02_player_hosts.md §1 (in-app PlayerActivity audit). Dev log recorded.

---

### Step 02.2 — Audit external standalone player open order

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt`, `temp/S0196/02_player_hosts.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Append the standalone VIEW and SEND flow to `temp/S0196/02_player_hosts.md`. Record the pre-super theme overlay, `setupViews`, `StandaloneViewManager` construction, `parseIncomingIntent`, and the first branch that can emit visible content or sound.

**Verification:**

- `Grep` — `StandalonePlayerActivity` present in `temp/S0196/02_player_hosts.md`.
- `Grep` — `StandaloneViewManager` present in `temp/S0196/02_player_hosts.md`.
- `Grep` — `parseIncomingIntent` present in `temp/S0196/02_player_hosts.md`.
- `Grep` — `ACTION_VIEW` present in `temp/S0196/02_player_hosts.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. Files: temp/S0196/02_player_hosts.md §2 (StandalonePlayerActivity audit appended). Dev log recorded.

---

### Step 02.3 — Audit VR boundary and 2D or immersive split

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt`, `temp/S0196/02_vr_boundary.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> Create `temp/S0196/02_vr_boundary.md`. Record the order of `isXrRuntimeAvailable`, `super.onCreate`, fallback handling, the `STAGE_SETUP_VIEWS` marker, and the first handoff into XR-only rendering. Separate 2D overlay work from stereo-loop work and mark what remains in scope for S0196.

**Verification:**

- `Glob` — `temp/S0196/02_vr_boundary.md` exists.
- `Grep` — `VrPlayerActivity` present in that file.
- `Grep` — `STAGE_SETUP_VIEWS` present in that file.
- `Grep` — `out of scope` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS. Files: temp/S0196/02_vr_boundary.md (+99 LOC). Dev log recorded.

---

### Step 02.4 — Audit prefetch and auto-advance competitors

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/AudioSlideshowPhotoModeManager.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/callbacks/PlayerPlaybackCallbackImpl.kt`, `temp/S0196/02_player_prefetch.md`
**Depends on:** Step 02.3

**Prompt for developer:**

> Create `temp/S0196/02_player_prefetch.md`. Record every call site that can start preload or prefetch before the user perceives current content: `prefetchNextAudio`, `preloadNextPhoto`, slideshow auto-advance, and any prefetch overlay work. Note trigger moment and whether the work can compete with first frame.

**Verification:**

- `Glob` — `temp/S0196/02_player_prefetch.md` exists.
- `Grep` — `prefetchNextAudio` present in that file.
- `Grep` — `preloadNextPhoto` present in that file.
- `Grep` — `slideshow auto-advance` present in that file.
- `Grep` — `first-frame competition` present in that file.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 5/5 PASS. Files: temp/S0196/02_player_prefetch.md (+91 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `temp/S0196/02_player_hosts.md` covers image, audio, video, and standalone flows.
- [x] `temp/S0196/02_vr_boundary.md` resolves strategic §6.9.
- [x] `temp/S0196/02_player_prefetch.md` resolves the static-audit portion of strategic §6.4.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 04 measurements must reuse the same host labels and first-content definitions recorded here. If a player host requires a prototype to answer §6.8 or §6.10, carry that note into Phase 05 instead of patching production code here.

---

## Rollback Plan

Delete `temp/S0196/02_*` files — no production code or persisted app data changed.
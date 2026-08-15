# Phase 03 - Register new visualization assets in Set C

**Strategic spec:** [`../S0407_audio-player-extra-visualizations.md`](../S0407_audio-player-extra-visualizations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (code) - mirror upload pending under BlockNeedUserTest
**Depends on:** none in code - external asset blocker resolved (6 clips produced 2026-06-14)
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-14
**Completed:** 2026-06-14

> Late owner decisions baked in here: only 6 new clips this batch (total 11, below the 8-10 target - extensible later); ALL 11 re-encoded to H.264 Constrained Baseline / 1024x576 / 24fps / ~800k cap for low-power car & cheap audio decoders, so the previously "leave as-is" old 5 (§6.9) were re-encoded too and their hashes changed. Mirror re-upload of all 11 is the BlockNeedUserTest prerequisite.

---

## Objective

Add the 8-10 newly produced clips to the delivery descriptor (integrity anchors) and to the player's background filename list, extending Set C without touching delivery or playback logic.

---

## Prerequisites

- [x] **Pre-Implementation Blocker resolved:** 6 clips produced + boomerang-processed + SHA-256/size captured. Mirror publish deferred to the BlockNeedUserTest step (owner re-uploads all 11).
- [x] (Superseded) Old 5 were re-encoded too for the low-power-decoder profile, so they are NOT untouched - hashes changed; strategic §6.9 reversed by owner decision.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableDescriptorCatalog.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliveredAudioVisualizationSource.kt` | Modified | ≤ 80 |
| `delivery/INVENTORY.md` | Modified | n/a |

> The mirror-publish + hash capture is external/manual (the blocker) and not a code step here.

---

## Steps

### Step 03.1 - Extend the player background filename list

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliveredAudioVisualizationSource.kt`
**Depends on:** - start of phase (after blocker resolved)

**Prompt for developer:**

> Append the new filenames to `bgFileNames` in continuation of the existing numbering (`anim_audio_bg_6.mp4` .. for the count actually produced, 8-10 new). Use the exact published filenames. Do not remove or rename the existing five. The random-pick logic is unchanged - it already filters to files that exist on disk, so a partially-installed set degrades safely.

**Verification:**

- `Grep` - `anim_audio_bg_6.mp4` present in `bgFileNames`.
- `Grep` - count of `anim_audio_bg_` entries in `bgFileNames` equals 5 + (new count).
- `/build` compiles.

**Status:** `[x] done`

---

### Step 03.2 - Register integrity anchors in the descriptor + inventory

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/delivery/DeliverableDescriptorCatalog.kt`, `delivery/INVENTORY.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `audioVisualizations()`, add one `resource("anim_audio_bg_N.mp4", "<sha256>", <minSize>L)` entry per new file, using the captured SHA-256 and byte size from the published mirror assets. The descriptor filename list MUST match `bgFileNames` exactly (same names, same count). Update `delivery/INVENTORY.md` Set C line to reflect the new range (`anim_audio_bg_1-v1.mp4 .. anim_audio_bg_N-v1.mp4`). Do not change the existing five entries.

**Verification:**

- `Grep` - number of `resource("anim_audio_bg_` entries in `audioVisualizations()` equals the number of names in `bgFileNames` (Step 03.1).
- `Grep` - each new entry has a 64-char hex SHA-256 and a numeric `minSize`.
- `Grep` - `delivery/INVENTORY.md` Set C line names the new top index.
- `/build` compiles.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` BUILD SUCCESSFUL.
- [x] Descriptor filename count == `bgFileNames` count (11 == 11).
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for both `.kt` files + `INVENTORY.md` (close-and-log batch).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (close-and-log catalog scan).

---

## Handoff Notes to Next Phase

Set C now lists 5 + new clips with matching integrity anchors on both the player source and the delivery descriptor. Phase 04 finalizes user-facing docs + catalog.

---

## Rollback Plan

Revert the phase commit - removes the new descriptor/source entries; installed users keep whatever files they downloaded, the random-pick still filters to existing files. No migration.

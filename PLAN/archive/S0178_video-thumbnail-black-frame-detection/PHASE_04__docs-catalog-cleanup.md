# Phase 04 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0178_video-thumbnail-black-frame-detection.md`](../S0178_video-thumbnail-black-frame-detection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Update `docs/FEATURES.md` and its mirrors, regenerate the class catalog, and write dev-log entries for all files touched in this spec. This is the final gate before `/spec-check S0178`.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Phase 03 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (generated) | — |

---

## Steps

### Step 4.1 — Update docs/FEATURES.md and mirrors

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of final phase

**Prompt for developer:**

> In `docs/FEATURES.md`, find the section covering video thumbnails or media thumbnails (search for "thumbnail" or "Thumbnail"). Add a new bullet:
>
> `- Video thumbnails automatically skip to a later frame when the initial frame is black (black leader), trying offsets at 5 s, 15 s, and 30 s.`
>
> In `docs/FEATURES_RU.md`, add the equivalent Russian bullet in the same section:
>
> `- Миниатюры видео автоматически переключаются на более поздний кадр, если первый кадр оказался чёрным (чёрный лидер): проверяются отметки 5 с, 15 с и 30 с.`
>
> In `docs/FEATURES_UK.md`, add the equivalent Ukrainian bullet:
>
> `- Мініатюри відео автоматично переходять до пізнішого кадру, якщо перший кадр виявився чорним (чорна заставка): перевіряються відмітки 5 с, 15 с та 30 с.`

**Verification:**

- `Grep` — `black leader` present in `docs/FEATURES.md`.
- `Grep` — `чёрный лидер` present in `docs/FEATURES_RU.md`.
- `Grep` — `чорна заставка` present in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 3/3 PASS. black leader / чёрный лидер / чорна заставка present in all three FEATURES files.

---

### Step 4.2 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — (independent of step 4.1)

**Prompt for developer:**

> Run the following commands in order:
> ```
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Then set the `role` and `status` for the two new classes via `set.ps1`:
> ```
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class VideoFrameExtractionPolicy -Role "Policy object: seek offsets and retry limits for video frame extraction" -Status active
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class VideoFrameDarknessEvaluator -Role "Utility: computes mean luma of Bitmap and classifies frame as dark" -Status active
> ```

**Verification:**

- `Grep` — `VideoFrameExtractionPolicy` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `VideoFrameDarknessEvaluator` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `VideoFrameExtractionPolicy` present in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 3/3 PASS. Both new classes in app_v2.jsonl and app_v2.md; roles set via set.ps1.

---

### Step 4.3 — Dev log entries for all spec files

**Files:** `dev/CHANGELOG.md` (via script only)
**Depends on:** Steps 4.1, 4.2

**Prompt for developer:**

> Run the following `add_to_dev_log.ps1` calls (one per file touched across all phases):
>
> ```
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/utils/VideoFrameExtractionPolicy.kt" "S0178" "Add VideoFrameExtractionPolicy: seek offsets and retry limits"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/utils/VideoFrameDarknessEvaluator.kt" "S0178" "Add VideoFrameDarknessEvaluator: mean luma computation for black frame detection"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/NetworkVideoFrameDecoder.kt" "S0178" "Add dark-frame retry loop and lazy cache eviction to network video frame decoder"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0178" "Document black-frame detection for video thumbnails"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0178" "Document black-frame detection (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0178" "Document black-frame detection (UK)"
> ```
>
> (ThumbnailExtractorHelper log entry was added in Phase 03 Step 3.3.)

**Verification:**

- `Grep` — `VideoFrameExtractionPolicy` present in `dev/CHANGELOG.md`.
- `Grep` — `VideoFrameDarknessEvaluator` present in `dev/CHANGELOG.md`.
- `Grep` — `NetworkVideoFrameDecoder` present in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification 3/3 PASS. VideoFrameExtractionPolicy, VideoFrameDarknessEvaluator, NetworkVideoFrameDecoder all present in CHANGELOG (logged in earlier phases + this phase for docs).

---

### Step 4.4 — Advance spec status to Implemented

**Files:** `PLAN/spec-catalog.jsonl` (via `update.ps1` only)
**Depends on:** Steps 4.1, 4.2, 4.3

**Prompt for developer:**

> Run:
> ```
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0178 -Status Implemented
> ```
>
> Then update `**Status:**` in `PLAN/S0178_video-thumbnail-black-frame-detection.md` to `Implemented`. (Or run `/spec-check S0178` which advances to `Verified` if criteria pass.)

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0178 -Format json` returns `"status":"Implemented"` or `"status":"Verified"`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-12 — Verification PASS: select.ps1 returns status "Implemented".

---

## Phase Done Criteria

- [x] Every `Step 4.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] All three FEATURES files updated (EN, RU, UK).
- [x] Catalog regenerated and both new classes have `role` set.
- [x] Dev log entries present for all spec-touched files.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Run `/spec-check S0178` to finalize.

---

## Rollback Plan

Revert phase commit(s). Docs changes are additive (remove the bullets). Catalog regenerates automatically from source. Spec status can be reset via `update.ps1 -Id S0178 -Status In Progress`.

# Tactical Plan: S1558 - cast-single-eye-crop

**Strategic spec:** [`../S1558_cast-single-eye-crop.md`](../S1558_cast-single-eye-crop.md)
**Research inputs:** none
**Feature:** Single-eye stereo crop applied to Chromecast output
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 30
**Status:** Not started
**Phases:** 5 / 5 done
**Last updated:** 2026-08-14

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | crop-request-seam | - | ✅ Done | 4/4 | [PHASE_01__crop-request-seam.md](PHASE_01__crop-request-seam.md) |
| 02 | crop-transcoder | 01 | ✅ Done | 2/2 | [PHASE_02__crop-transcoder.md](PHASE_02__crop-transcoder.md) |
| 03 | cast-pipeline-wiring | 01, 02 | ✅ Done | 4/4 | [PHASE_03__cast-pipeline-wiring.md](PHASE_03__cast-pipeline-wiring.md) |
| 04 | crop-user-feedback | 03 | ✅ Done | 2/2 | [PHASE_04__crop-user-feedback.md](PHASE_04__crop-user-feedback.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 item 1 is Resolved (owner picked variant A on 2026-08-13).

---

## Planning notes carried into implementation

- **Geometry parity is by construction, not by re-detection.** The panel's crop is a `Matrix` on the backing `TextureView` (`PanelStereoCropApplier`, S0264), not a GL effect - strategic §0 predates that workaround and describes the earlier GL attempt. The eye it keeps is the **right** half for every SBS mode and the **bottom** half for every OU mode. Phase 01 exports that decision from the player instead of re-deriving it inside the Cast manager, so §11 criterion 1 cannot drift.
- **A second detection path would be a defect.** `StereoDetectionFacade.detectFromDimensions` is a dimension heuristic only, while the panel's effective mode also honours Matroska metadata and the per-file `StereoFormatOverrideEntity`. Deriving the Cast crop from the facade would show a different eye than the panel on any overridden file.
- **The crop applies to the proxy path only.** `resolveAndSend` returns early for `CastStreamDecision.Direct` (live URLs go straight to the receiver), so the transcode sits after that guard and live streams stay untouched - strategic §3.2 scope constraint.
- **Encode cost is bounded by duration, not by file size.** `MAX_VIDEO_CAST_BYTES` (50 MB) already guards network downloads but does not apply to local files, which is where stereo material lives. Phase 02 adds a duration ceiling instead; above it the crop is skipped and the original is cast, matching the existing "refuse loudly, keep casting" behaviour of the same method.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - update only if strategic §8 contains FEATURES sentence (not "Без изменений"); skip otherwise.
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1558` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1558`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-14 - Initial tactical plan authored by `/spec-tech`.

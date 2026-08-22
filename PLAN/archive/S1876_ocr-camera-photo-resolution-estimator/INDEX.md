# Tactical Plan: S1876 - ocr-camera-photo-resolution-estimator

**Strategic spec:** [`../S1876_ocr-camera-photo-resolution-estimator.md`](../S1876_ocr-camera-photo-resolution-estimator.md)
**Research inputs:** none as files - strategic §4 carries the EXIF-availability finding verified 2026-08-21 against the pinned `exifinterface` 1.3.7.
**Feature:** OCR - declare a computed resolution for a camera photo instead of the floor
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 45
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-08-21

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | scene-arithmetic-from-exif | - | ✅ Done | 4/4 | [PHASE_01__scene-arithmetic-from-exif.md](PHASE_01__scene-arithmetic-from-exif.md) |
| 02 | record-the-narrowed-question | 01 | ✅ Done | 3/3 | [PHASE_02__record-the-narrowed-question.md](PHASE_02__record-the-narrowed-question.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Ordering rationale

Strategic §5 splits the camera branch into a tier that is arithmetic and a tier that is not. Phase 01 ships the arithmetic tier, because it depends on nothing outside the file being recognised and so needs no corpus. Phase 02 then records what the arithmetic did *not* answer, so the residual question stays visible after this ticket closes - the same failure mode S1876 itself exists to prevent for S1715.

---

## Pre-Implementation Blockers

- [x] **EXIF tag availability** - `TAG_SUBJECT_DISTANCE`, `TAG_FOCAL_LENGTH_IN_35MM_FILM` and the focal-plane pair are present in the pinned `androidx.exifinterface:exifinterface:1.3.7`; verified 2026-08-21 by extracting `ExifInterface.class` from the cached artefact.
- [ ] **Research:** strategic §6.1 "оценщик для снимка без EXIF-дистанции" - Open, and deliberately **not** a blocker for Phase 01. It gates only the tier-B rule, which this ticket does not implement.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - no change; strategic §8 records the reason.
- [ ] `dev/CHANGELOG.md` has an entry for this ticket.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - this ticket adds no class but changes one signature.
- [ ] Strategic §6.1 carries a `Carrier: Sxxxx` token or is `Resolved` before the ticket reaches `Implemented`.
- [ ] `/spec-check S1876` returns `Verified`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1876`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-21 - Initial tactical plan authored by `/spec-do` Stage F2.
- 2026-08-21 - Steps 02.1 and 02.2 ran ahead of Phase 01's completion, during a `CODE.LOCK` wait held by a sibling session. Neither needs Phase 01's code: 02.2 only searches the catalog, and 02.1 documents a decision recorded in strategic §5.1 rather than a measured result. The dependency Phase 02 has on Phase 01 is step 02.3, the closure, which still runs last.

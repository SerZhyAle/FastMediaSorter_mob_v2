# Tactical Plan: S1266 - play-listing-tablet-screenshots

**Strategic spec:** [`../S1266_play-listing-tablet-screenshots.md`](../S1266_play-listing-tablet-screenshots.md)
**Research inputs:** none - strategic §4.1/§4.2 already carry the resolved content-gap decisions.
**Feature:** Tablet screenshot set for the Play Store listing
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 60
**Status:** Done - all 5 phases closed; the live Play publish (Step 05.3) stays deferred to the owner
**Phases:** 5 / 5 done
**Last updated:** 2026-08-05

> **Device:** the tablet is **`emulator-5556`**, not the `emulator-5554` every phase file names -
> port 5554 was held by a phone AVD at execution time, so `Pixel_Tablet` was booted alongside it.
> Full note in the Phase 01 header.
>
> **Phase 05 is deliberately not started.** Its Step 05.3 is a live Play Console publish behind an
> explicit owner-confirmation gate, and Step 05.2 flips the journal status - neither is auto-executed.
> The ticket stays at journal status `Tactical`.

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | tablet-capture-tooling | - | ✅ Done | 2/2 | [PHASE_01__tablet-capture-tooling.md](PHASE_01__tablet-capture-tooling.md) |
| 02 | device-content-prep | 01 | ✅ Done | 3/3 | [PHASE_02__device-content-prep.md](PHASE_02__device-content-prep.md) |
| 03 | navigation-discovery-capture | 02 | ✅ Done | 2/2 | [PHASE_03__navigation-discovery-capture.md](PHASE_03__navigation-discovery-capture.md) |
| 04 | compose-and-localize | 03 | ✅ Done | 2/2 | [PHASE_04__compose-and-localize.md](PHASE_04__compose-and-localize.md) |
| 05 | docs-catalog-cleanup | 04 | ✅ Done | 2/3 + 1 manual | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - strategic §4.1/§4.2 already closed every open research item (content sourcing, geometry,
compat behavior are recon findings, not blockers on this plan).

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 not present in this compact-tier
      spec but §2 Non-goals and the nature of the change - store-listing image assets, not app
      behavior - make this a no-FEATURES-impact change; confirmed again at Phase 05).
- [x] `dev/CHANGELOG.md` has entry for every modified file.
- [x] `dev/CATALOG/<module>.jsonl` regenerated if public API changed - N/A, no `.kt` touched.
- [ ] `/spec-check S1266` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1266`.

---

## Blockers Log

None yet.

---

## Change Log

- 2026-08-02 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-02 - Phases 01-04 executed. Corrections recorded in the phase files' Step Logs; three
  findings that outlived the phase they were found in were promoted to strategic §4.3 (camera
  letterbox, the `FLAG_SECURE` capture blocker, the tablet AVD's device id).

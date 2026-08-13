# Tactical Plan: S0497 - play-store-listing-aso-rewrite

**Strategic spec:** [`../S0497_play-store-listing-aso-rewrite.md`](../S0497_play-store-listing-aso-rewrite.md)
**Research inputs:** none
**Feature:** Play Store listing ASO rewrite + auto-publish
**Tier:** tooling / store-presence
**Priority:** 60
**Status:** In Progress
**Phases:** 4 / 4 done
**Last updated:** 2026-06-18

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | play-listing-source | - | ✅ Done | 2/2 | [PHASE_01__play-listing-source.md](PHASE_01__play-listing-source.md) |
| 02 | listing-uploader | 01 | ✅ Done | 3/3 | [PHASE_02__listing-uploader.md](PHASE_02__listing-uploader.md) |
| 03 | screenshots-captioned | 01 | ✅ Done | 3/3 | [PHASE_03__screenshots-captioned.md](PHASE_03__screenshots-captioned.md) |
| 04 | docs-catalog-cleanup | 02, 03 | ✅ Done | 1/1 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- none (no open §6 research items).

---

## Operational push (post-Phase 02, owner-gated)

Not a phase step (no static verification - it is a live Play API call):

- After Phase 02, run the uploader in `validate` mode (no commit) to confirm the service account
  holds "Edit store listing" rights and the payload validates.
- The live commit (text + images go public, possibly via Play review) is performed only on explicit
  owner go. Play has no separate "draft store listing preview" via the API - committing an edit
  publishes it. So the preview is the validated payload + the texts under `play/listing/`, reviewed
  before the owner authorizes commit.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 introduces no user-facing app feature; this is store tooling).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` - skip (no `.kt` touched).
- [ ] `/spec-check S0497` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0497`.

---

## Resume here (next session, paused 2026-06-18)

Done so far:

- Phase 01 ✅ - `play/listing/<locale>/` texts (EN/RU/UK, all within Play limits) + `README.md`.
- Phase 02 steps 02.1/02.2 ✅ - `publish-play-listing.{py,ps1}` written, `py_compile` passes.

Two owner decisions pending before continuing:

1. **Run the validate pass (Step 02.3)?** `pwsh -NoProfile -File scripts/release/publish-play-listing.ps1 -Mode validate`
   creates an edit + validates the listing payload, no commit. Confirms the service account holds
   "Edit store listing" rights. Owner has not yet authorized touching the live account.
2. **Phase 03 screenshots:** needs a connected emulator/device + confirmed screen set. Proposed 7 slots:
   `browse`, `image-viewer`, `video-player`, `slideshow`, `cloud-connect`, `reader`, `widgets`.

Note: Play API has no separate "draft store-listing preview" - a listing edit is either validated
(no publish) or committed (live, possibly via review). Live `commit` stays owner-gated.

## Blockers Log

- 2026-06-19 (Step 02.3, BlockExternal) - validate pass exited 1: `uk-UA` rejected by Play API
  with HTTP 400 "language is not currently supported". Ukrainian is not enabled as a store-listing
  language in the Play Console. Auth + "Edit store listing" rights confirmed OK (en-US + ru-RU
  updated in edit `14312239236686287521`). Unblock: owner enables Ukrainian under Play Console →
  Store presence → Main store listing → Manage translations, then re-run
  `scripts/release/publish-play-listing.ps1 -Mode validate`.
- 2026-06-19 (RESOLVED) - misdiagnosed: Ukrainian IS supported, its Play code is `uk` (not `uk-UA`,
  per owner Console screenshot). Fixed `publish-play-listing.py` folder->language map; validate
  re-run exit 0 (edit `00449176981496323933`, all 3 locales incl. `uk`). Not external. Phase 02 ✅.

---

## Change Log

- 2026-06-18 - Initial tactical plan authored by `/spec-tech`.

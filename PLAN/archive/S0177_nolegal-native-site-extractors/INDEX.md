# Tactical Plan: S0177 — nolegal-native-site-extractors

**Strategic spec:** [`../S0177_nolegal-native-site-extractors.md`](../S0177_nolegal-native-site-extractors.md)
**Feature:** noLegal native Kotlin extractors for ArtStation, DeviantArt, Vimeo, Dailymotion
**Tier:** 2 — Easy
**Priority:** 45
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-12

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | artstation-extractor | — | ✅ Done | 4/4 | [PHASE_01__artstation-extractor.md](PHASE_01__artstation-extractor.md) |
| 02 | deviantart-extractor | 01 | ✅ Done | 3/3 | [PHASE_02__deviantart-extractor.md](PHASE_02__deviantart-extractor.md) |
| 03 | vimeo-extractor | 02 | ✅ Done | 3/3 | [PHASE_03__vimeo-extractor.md](PHASE_03__vimeo-extractor.md) |
| 04 | dailymotion-extractor | 03 | ✅ Done | 3/3 | [PHASE_04__dailymotion-extractor.md](PHASE_04__dailymotion-extractor.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open blockers. Research items below are resolved at implementation time, not before.

**Research notes (no action required before Phase 01):**
- DeviantArt `window.__INITIAL_STATE__` JSON structure must be verified against a live deviation page during Phase 02 implementation. The fallback path (oEmbed API) is specified in step 02.1 if primary extraction fails.
- Vimeo password-protected fallback: **deferred to v2** (strategic §6.2 option c — skip in v1). Vimeo strategy returns `OpenResult.NotFound("vimeo_config_failed")` on 403/password errors; WebView-dynamic fallback in the registry handles it transparently.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES_noLegal.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0177` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0177`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-12 — Initial tactical plan authored by `/spec-tech`.

# Tactical Plan: S0528 - consolidate-savetodownloads

**Strategic spec:** [`../S0528_consolidate-savetodownloads.md`](../S0528_consolidate-savetodownloads.md)
**Research inputs:** none
**Feature:** Консолидация записи в Downloads
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 45
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-06-19

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | route-frame-capture | - | ✅ Done | 2/2 | [PHASE_01__route-frame-capture.md](PHASE_01__route-frame-capture.md) |
| 02 | route-link-download | - | ✅ Done | 2/2 | [PHASE_02__route-link-download.md](PHASE_02__route-link-download.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - strategic §6 has no Open research items.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip; strategic §8 is "Без изменений".
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (constructor signatures of `LinkDownloadWriter` and `SaveVideoFrameManager` changed).
- [ ] `/spec-check S0528` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0528`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-19 - Initial tactical plan authored by `/spec-tech`.

# Tactical Plan: S1803 - sitemap-one-url-per-record

**Strategic spec:** [`../S1803_sitemap-one-url-per-record.md`](../S1803_sitemap-one-url-per-record.md)
**Research inputs:** none - the single measurement the plan needs is recorded in strategic §6.1 and reproducible by the step that consumes it.
**Feature:** Per-page sitemap entries driven by the page's own address
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In progress
**Phases:** 3 / 3 done
**Last updated:** 2026-08-18

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | generator-page-expansion | - | ✅ Done | 4/4 | [PHASE_01__generator-page-expansion.md](PHASE_01__generator-page-expansion.md) |
| 02 | classify-unlisted-pages | 01 | ✅ Done | 4/4 | [PHASE_02__classify-unlisted-pages.md](PHASE_02__classify-unlisted-pages.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 4/4 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [ ] **Research:** name-by-name classification of the unlisted pages (strategic §6.2) - required inside Phase 02, which is where the pass is performed. It gates no earlier phase, because the exclusion mechanism Phase 01 builds is needed whichever way each page is classified.

Strategic §6.3 (language alternates for expanded addresses) is `Open` and deliberately out of scope, carried by S1211.

---

## Measurement this plan starts from

Taken 2026-08-18 against the working tree, reproducible by walking each indexable record's path globs and counting files whose front matter declares an address:

- Indexable records: 7.
- Addresses declared today: 19 - which is exactly the sitemap's entry count.
- Files carrying their own declared address under those records: 71.
- Therefore unlisted: 52, of which 44 sit in the user-guides group alone.

Phase 02 re-runs this count and drives it to zero-or-excluded; Phase 03 makes the count a standing check rather than a one-time cleanup.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - strategic §8 says "Без изменений", so no showcase edit.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/*.jsonl` - not regenerated: no Kotlin source changes in this ticket.
- [ ] `scripts/document_registry/validate.ps1` exits 0 and `generate.ps1 -Check` reports no drift.
- [ ] `/spec-check S1803` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1803`.

---

## Blockers Log

- none

---

## Change Log

- 2026-08-18 - Initial tactical plan authored by `/spec-tech`.

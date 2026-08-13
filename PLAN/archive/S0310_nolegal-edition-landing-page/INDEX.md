# Tactical Plan: S0310 - nolegal-edition-landing-page

**Strategic spec:** [`../S0310_nolegal-edition-landing-page.md`](../S0310_nolegal-edition-landing-page.md)
**Feature:** Standalone public GitHub Pages page for the noLegal edition (EN/RU/UK)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-05-30

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.
>
> **Task class:** static GitHub Pages site (root-level `*.html` + `styles.css` + `sitemap.xml`). No Kotlin, Hilt, Room, layout-land, or flavor source-set work - those constraints are N/A here. Root-level placement follows the existing `index*.html` site convention (the only sanctioned exception to "no writes to project root").

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | en-page | - | ✅ Done | 6/6 | [PHASE_01__en-page.md](PHASE_01__en-page.md) |
| 02 | ru-uk-pages | 01 | ✅ Done | 4/4 | [PHASE_02__ru-uk-pages.md](PHASE_02__ru-uk-pages.md) |
| 03 | sitemap-seo-isolation | 01, 02 | ✅ Done | 3/3 | [PHASE_03__sitemap-seo-isolation.md](PHASE_03__sitemap-seo-isolation.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open research items - both strategic §6 items are Resolved (feature depth = full with details; outbound links = index, VR materials, Downloads/FAQ, GitHub). Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - NOT updated (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified/new file.
- [ ] Catalog regen - N/A (no `.kt` changed).
- [ ] No inbound link from any `index*.html` to any `nolegal*.html`.
- [ ] Three noLegal pages present in `sitemap.xml`; hreflang group limited to the noLegal triplet.
- [ ] Pages published on `main` and live on GitHub Pages (git step - owner via `/git`).
- [ ] `/spec-check S0310` returns `Verified`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, set the journal status accordingly.
5. All done: flip `Status:` to `Done`, run `/spec-check S0310`.

---

## Blockers Log

- 2026-05-30 - Phase 04 step 04.3 blocked: publish to `main` is a git operation outside `/spec-dev`. All artifacts authored and verified on DEBUG-v009. Next: owner directs publish path via `/git`.

---

## Change Log

- 2026-05-30 - Initial tactical plan authored by `/spec-tech`.

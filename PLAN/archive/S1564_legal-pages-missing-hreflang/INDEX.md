# Tactical Plan: S1564 - legal-pages-missing-hreflang

**Strategic spec:** [`../S1564_legal-pages-missing-hreflang.md`](../S1564_legal-pages-missing-hreflang.md)
**Feature:** Localized legal-page sitemap cluster
**Tier:** Tier 2 - Easy (ad-hoc)
**Priority:** 60
**Status:** Done
**Phases:** 2 / 2 done
**Last updated:** 2026-08-11

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|---|---|---|---:|---|
| 01 | registry-localization | - | ✅ Done | 2/2 | [PHASE_01__registry-localization.md](PHASE_01__registry-localization.md) |
| 02 | docs-catalog-cleanup | 01 | ✅ Done | 2/2 | [PHASE_02__docs-catalog-cleanup.md](PHASE_02__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Actual privacy-policy permalinks and registry coverage resolved in strategic §6.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.
- [x] `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.
- [x] `/spec-check S1564` is the next closure action.

---

## Blockers Log

- None.

---

## Change Log

- 2026-08-11 - Initial tactical plan authored by `/spec-tech`.

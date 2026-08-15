# Tactical Plan: S0994 - companion-publish-folders-help-link

**Strategic spec:** [`../S0994_companion-publish-folders-help-link.md`](../S0994_companion-publish-folders-help-link.md)
**Research inputs:** none
**Feature:** Ссылка на companion-страницу «publish folders» в приложении и документации
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest (all phases done; awaiting on-device verification)
**Phases:** 5 / 5 done
**Last updated:** 2026-07-11

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | url-source-and-strings | - | ✅ Done | 2/2 | [PHASE_01__url-source-and-strings.md](PHASE_01__url-source-and-strings.md) |
| 02 | add-resource-help-link | 01 | ✅ Done | 2/2 | [PHASE_02__add-resource-help-link.md](PHASE_02__add-resource-help-link.md) |
| 03 | settings-useful-link | 01 | ✅ Done | 3/3 | [PHASE_03__settings-useful-link.md](PHASE_03__settings-useful-link.md) |
| 04 | docs-and-site-deeplinks | - | ✅ Done | 4/4 | [PHASE_04__docs-and-site-deeplinks.md](PHASE_04__docs-and-site-deeplinks.md) |
| 05 | docs-catalog-cleanup | 02, 03, 04 | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- None. Strategic §6 has no open research items; target URL and capability gate confirmed against code on 2026-07-11.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - NOT edited here (release-owned per CLAUDE.md §11); new capability recorded in `docs/ALL_FEATURES.jsonl` instead (Phase 05).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `docs/settings/settings-manifest.json` + `docs/SETTINGS_REFERENCE*.md` + `docs/settings/settings-annotations.json` regenerated for the new General link button (Rule 22).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (touched Kotlin).
- [ ] `/spec-check S0994` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/5 done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log and set the journal status accordingly.
5. All done: flip `Status:` to `Done`, run `/spec-check S0994`.

---

## Blockers Log

- None yet.

---

## Change Log

- 2026-07-11 - Initial tactical plan authored by `/spec-tech`.

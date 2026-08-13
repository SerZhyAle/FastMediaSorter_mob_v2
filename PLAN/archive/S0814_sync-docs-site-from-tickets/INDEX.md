# Tactical Plan: S0814 - sync-docs-site-from-tickets

**Strategic spec:** [`../S0814_sync-docs-site-from-tickets.md`](../S0814_sync-docs-site-from-tickets.md)
**Research inputs:** [`research/01__doc-freshness-reconciliation.md`](research/01__doc-freshness-reconciliation.md)
**Feature:** Narrative-guide reconciliation with shipped capabilities (EN/RU/UK) + release-runbook review anchor
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-07-05

> **Scope:** tactical, English, developer handoff. Every step has a static verification predicate. Rationale lives in strategic spec. Docs-only ticket - no Kotlin, no build, no debug tags, no BlockNeedUserTest device pass. After all phases: `/spec-check` -> Verified.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | howto-scenarios | - | ✅ Done | 7/7 | [PHASE_01__howto-scenarios.md](PHASE_01__howto-scenarios.md) |
| 02 | faq-entries | - | ✅ Done | 6/6 | [PHASE_02__faq-entries.md](PHASE_02__faq-entries.md) |
| 03 | quickstart-readme | - | ✅ Done | 2/2 | [PHASE_03__quickstart-readme.md](PHASE_03__quickstart-readme.md) |
| 04 | docs-map-dates | 01, 02, 03 | ✅ Done | 2/2 | [PHASE_04__docs-map-dates.md](PHASE_04__docs-map-dates.md) |
| 05 | release-runbook-checklist | - | ✅ Done | 1/1 | [PHASE_05__release-runbook-checklist.md](PHASE_05__release-runbook-checklist.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items are Resolved via `/spec-quiz` (2026-07-05). Phase 01 may start.

---

## Owner decisions carried into this plan (from strategic §6)

- **Timing:** reflect all 11 clear-gap candidates now (owner overrode the "wait for Verified" research recommendation; premature-documentation risk accepted).
- **Clusters (all four):** Screen Capture & recording; Statistics & cleanup; Camera & photo metadata; Streams & shortcuts.
- **Process anchor:** lightweight checklist step in the release runbook - no keyword gate.
- **Category C:** fix stale `DOCS_MAP` "Last Updated" dates + two-table inconsistency now.
- **Placement:** per best-fit recommendations in `research/01__doc-freshness-reconciliation.md` (HOW_TO / FAQ / QUICK_START).

## Out of scope (this pass)

- **Reconciliation report (strategic §2 goal 1):** already delivered - `research/01__doc-freshness-reconciliation.md`. Not a phase.
- **Section B "maybe/partial" candidates (9):** Send-To branded receivers, Read-Aloud/TTS, main-window panels, streams advanced filters, standalone "Open with" viewer, player 9-zone/auto-fullscreen variants, mini-game, per-widget how-tos, Settings reorganisation. Owner selected the four clusters = the 11 clear-gap (A) items; B items are lower-importance/partial and deferred. Revisit in a future doc pass if promoted.
- **Generated/gated docs:** `FEATURES*`, `SETTINGS_REFERENCE*`, `ICON_LEGEND*`, version-pins - own sources of truth and gates (strategic §2 non-goals).
- **Troubleshooting.md, WHATS_NEW.md:** flagged in report category C but out of edit scope (release-owned / not in the 5-doc narrative set).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*.md` - NOT touched (strategic §8 = "Без изменений"; release-owned).
- [ ] `dev/CHANGELOG.md` has an entry for every modified doc (one logical entry per phase acceptable).
- [ ] `dev/CATALOG/*.jsonl` - NOT regenerated (no code/public-API change).
- [ ] `howto-settings-paths-gate` green if any HOW_TO settings-path (`→`) line was added (Phase 01).
- [ ] Every new narrative section exists in EN + RU + UK (trilingual parity).
- [ ] `/spec-check S0814` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/6 done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log; if the whole spec blocks, set the journal status accordingly via `update.ps1`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0814`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-07-05 - Initial tactical plan authored by `/spec-tech`.

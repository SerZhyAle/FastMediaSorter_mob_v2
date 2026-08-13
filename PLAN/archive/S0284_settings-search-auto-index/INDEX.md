# Tactical Plan: S0284 - settings-search-auto-index

**Strategic spec:** [`../S0284_settings-search-auto-index.md`](../S0284_settings-search-auto-index.md)
**Feature:** Settings search auto-indexing
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Awaiting on-device verification (BlockNeedUserTest)
**Phases:** 5 / 5 done
**Last updated:** 2026-05-21

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundation-types | - | ✅ Done | 4/4 | [PHASE_01__foundation-types.md](PHASE_01__foundation-types.md) |
| 02 | layout-xml-source | 01 | ✅ Done | 3/3 | [PHASE_02__layout-xml-source.md](PHASE_02__layout-xml-source.md) |
| 03 | locale-keyword-collector | 01 | ✅ Done | 3/3 | [PHASE_03__locale-keyword-collector.md](PHASE_03__locale-keyword-collector.md) |
| 04 | registry-refactor-and-di | 02, 03 | ✅ Done | 5/5 | [PHASE_04__registry-refactor-and-di.md](PHASE_04__registry-refactor-and-di.md) |
| 05 | docs-catalog-cleanup | 04 | ✅ Done | 4/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research #1 (strategic §6.1):** Resolved 2026-05-21 — runtime via `Resources.getXml` (lazy init on first search-overlay open; build-time not justified).
- [x] **Research #2 (strategic §6.2):** Resolved 2026-05-21 — `android:hint` as fallback title for hint-only views.
- [x] **Research #3 (strategic §6.3):** Resolved 2026-05-21 — help-popup text (`str_helpTitle`/`str_helpMessage`/`csh_helpTitle`/`csh_helpMessage`) excluded from keyword pool.
- [x] **Research #4 (strategic §6.4):** Resolved 2026-05-21 — all action buttons with visible `android:text` are searchable entries.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0284` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0284`.

---

## Blockers Log

- 2026-05-21 - Phase 01 was blocked: 4 strategic §6 research items `Open`. Resolved 2026-05-21 with engineering defaults (runtime, hint-fallback, help-popup excluded, buttons included). All defaults preserved as plan presumed. Implementation unblocked.

---

## Change Log

- 2026-05-21 - Initial tactical plan authored by `/spec-tech`.

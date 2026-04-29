# Tactical Plan: S0003 — link-receive-download

**Strategic spec:** [`../S0003_link-receive-download.md`](../S0003_link-receive-download.md)
**Feature:** Ad-hoc — Auto-download a file from an incoming Share-sheet link
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 0 / 6 done
**Last updated:** 2026-04-29

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-foundation | — | ⬜ Not started | 0/6 | [PHASE_01__settings-foundation.md](PHASE_01__settings-foundation.md) |
| 02 | url-channel-branch | 01 | ⬜ Not started | 0/5 | [PHASE_02__url-channel-branch.md](PHASE_02__url-channel-branch.md) |
| 03 | direct-file-extractor | 01 | ⬜ Not started | 0/5 | [PHASE_03__direct-file-extractor.md](PHASE_03__direct-file-extractor.md) |
| 04 | html-page-extractor | 03 | ⬜ Not started | 0/4 | [PHASE_04__html-page-extractor.md](PHASE_04__html-page-extractor.md) |
| 05 | writer-progress-autoopen | 02, 03, 04 | ⬜ Not started | 0/6 | [PHASE_05__writer-progress-autoopen.md](PHASE_05__writer-progress-autoopen.md) |
| 06 | docs-catalog-cleanup | all | ⬜ Not started | 0/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 research items are Resolved or Partially Resolved with defaults set in the strategic spec. The remaining open detail (HEAD-request fan-out / candidate-selection timeout) is captured inside Phase 04 with concrete numeric defaults that may be tuned later without API changes.

- [x] §6.1 flavor coverage — Resolved (identical across all flavors).
- [x] §6.2 parsing approach — Resolved (HTML-only, no managed web context, no streaming).
- [x] §6.3 candidate selection policy — Resolved (≥1 MB priority, then largest known, then first).
- [x] §6.4 candidate size probing — Defaults set in Phase 04 (HEAD fan-out ≤ 8 per page, candidate-selection budget ≤ 4 s).
- [x] §6.5 size limit / network profile — Resolved (any network, no hard cap).
- [x] §6.6 unavailable resource fallback — Resolved (Downloads + Toast).
- [x] §6.7 settings placement — Resolved (Settings → "Поделиться/Приём" → "Поведение").
- [x] §6.8 URL detection — Resolved (first `http(s)` URL via mild regex).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (user-facing — see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0003` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0003`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-04-29 — Initial tactical plan authored by `/spec-tech` via `/spec-all`.

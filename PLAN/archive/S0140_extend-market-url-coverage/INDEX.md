# Tactical Plan: S0140 — extend-market-url-coverage

**Strategic spec:** [`../S0140_extend-market-url-coverage.md`](../S0140_extend-market-url-coverage.md)
**Feature:** Extend market URL coverage (structured metadata, shared-link batching, safer auth UX, dynamic-page extractor)
**Tier:** 4 — Strategic
**Priority:** 65
**Status:** In Progress
**Phases:** 2 / 4 done
**Last updated:** 2026-05-10

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | auth-share-polish | — | ✅ Done | 4/4 | [PHASE_01__auth-share-polish.md](PHASE_01__auth-share-polish.md) |
| 02 | structured-standards | 01 | ✅ Done | 3/3 | [PHASE_02__structured-standards.md](PHASE_02__structured-standards.md) |
| 03 | dynamic-extractor | 02 + research §6.1/§6.2/§6.3 | ⛔ Blocked | 3/4 | [PHASE_03__dynamic-extractor.md](PHASE_03__dynamic-extractor.md) |
| 04 | docs-catalog-cleanup | 01, 02 | 🚧 In Progress | 2/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research §6.1 + §6.2:** resolved 2026-05-18 via `/spec-update` (web-research + code audit). Default JS-render budget codified at `HARD_TIMEOUT_MS=22s` / `DOM_SETTLE_MS=4s` to match the shipped `InvisibleWebViewExtractionStrategy.kt` companion-object. SPA-coverage smoke-test downgraded from pre-impl gate to BlockNeedUserTest regression artefact (Step 03.1).
- [x] **Research §6.3:** resolved 2026-05-18 via `/spec-update`. Heuristic uses ≥ 2-of-N signal threshold (`MIN_LOGIN_WALL_SIGNALS=2` in `HtmlPageExtractionStrategy.kt`); empirical FPR-sweep merged into Step 03.1 regression artefact.

> Phases 01, 02, and Phase 04 steps 04.1/04.2 are landed. Phase 03 runtime is landed; only Step 03.1 regression baseline plus final `/build` + `/spec-check` are open. Spec journal status `BlockQuestions` is now stale — promote to `Tactical` (or `In Progress`) via `pwsh -File scripts/spec_catalog/update.ps1 -Id S0140 -Status <new>` when ready.

---

## Completion Gate

- [ ] All phases show ✅ Done (or ⏭️ Skipped with reason).
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated for the landed S0140 user-visible slice.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0140` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0140`.

---

## Blockers Log

- 2026-05-10 — Phase 03 blocked: missing smoke-test/FPR baseline for invisible WebView and login-wall heuristics. Next: collect `temp/S0140_smoketest/`, choose the default timeout, then unblock Phase 03.
- 2026-05-10 — Runtime slice landed ahead of the empirical baseline: the dynamic WebView extractor, analyzing-page progress, and persisted soft login-wall heuristic are implemented, but Step 03.1 plus final `/build` and `/spec-check` still block closure.
- 2026-05-18 — Research blockers §6.1 / §6.2 / §6.3 resolved via `/spec-update` (web-research + code audit). Cap values (`HARD_TIMEOUT_MS=22s`, `DOM_SETTLE_MS=4s`) and `MIN_LOGIN_WALL_SIGNALS=2` codified in strategic spec. Remaining open: Step 03.1 regression baseline artefact, then `/build` + `/spec-check`.

---

## Change Log

- 2026-05-10 — Initial tactical plan authored after implementation catch-up for Phases 01, 02, and Phase 04 steps 04.1/04.2.
- 2026-05-10 — Phase 03 runtime implementation advanced to 3/4 done; only the smoke-test/FPR baseline and final verification remain open.
- 2026-05-18 — `/spec-update`: resolved strategic §6.1/§6.2/§6.3 research items, reconciled strategic §3.2/§5.1/§11 with shipped cap constants, rewrote Phase 03 Step 03.1 as a BlockNeedUserTest regression artefact, snapshotted INDEX blockers as `[x]` Resolved.

---

## Revision History

- **2026-05-18** — by `/spec-update` (sonnet-4.5, focus: completeness + consistency)
  - Applied: 3. Proposed (DISCUSS): 0.
  - Pre-Implementation Blockers: оба пункта переведены в `[x]` Resolved со ссылкой на shipped cap-константы. Blockers Log и Change Log дополнены строкой 2026-05-18. INDEX Status `In Progress` оставлен как есть; strategic Status `BlockQuestions` — стало stale, оператору рекомендован переход на `Tactical` или `In Progress`.
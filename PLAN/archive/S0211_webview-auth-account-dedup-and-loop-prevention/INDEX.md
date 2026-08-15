# Tactical Plan: S0211 — webview-auth-account-dedup-and-loop-prevention

**Strategic spec:** [`../S0211_webview-auth-account-dedup-and-loop-prevention.md`](../S0211_webview-auth-account-dedup-and-loop-prevention.md)
**Feature:** WebView-auth account dedup + reauth-loop prevention (noLegal flavor, link-download umbrella S0156)
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 75
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-05-15

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | auth-identity-extractor | — | ✅ Done | 2/2 | [PHASE_01__auth-identity-extractor.md](PHASE_01__auth-identity-extractor.md) |
| 02 | auth-session-upsert | 01 | ✅ Done | 5/5 | [PHASE_02__auth-session-upsert.md](PHASE_02__auth-session-upsert.md) |
| 03 | auth-account-cleanup-once | 01, 02 | ✅ Done | 4/4 | [PHASE_03__auth-account-cleanup-once.md](PHASE_03__auth-account-cleanup-once.md) |
| 04 | reauth-dialog-account-resolve | 02 | ✅ Done | 3/3 | [PHASE_04__reauth-dialog-account-resolve.md](PHASE_04__reauth-dialog-account-resolve.md) |
| 05 | preview-only-no-login-notification | — | ✅ Done | 3/3 | [PHASE_05__preview-only-no-login-notification.md](PHASE_05__preview-only-no-login-notification.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 open items all have a recorded default that does not block tactical work:

- §6 #1 — identity persistence: decision is "compute on the fly" (ADR-3); revisit only after profiling.
- §6 #2 — cleanup marker: tactical decision is DataStore boolean key `dedup_s0211_done`, mirroring `MigrateS0059UseCase`.
- §6 #3 — records without computable identity: tactical decision is "leave untouched" (default per strategic spec).
- §6 #4 — different identity on reauth: tactical decision is "create new record" (current behavior; multi-account is non-goal).

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — skipped per strategic §8 ("Без изменений в публичных docs/FEATURES*.md").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0211` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0211`.

---

## Blockers Log

- _(empty)_

---

## Change Log

- 2026-05-15 — Initial tactical plan authored by `/spec-tech`.

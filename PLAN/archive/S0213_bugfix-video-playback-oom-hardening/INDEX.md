# Tactical Plan: S0213 — bugfix-video-playback-oom-hardening

**Strategic spec:** [`../S0213_bugfix-video-playback-oom-hardening.md`](../S0213_bugfix-video-playback-oom-hardening.md)
**Feature:** Video playback OOM hardening — three coordinated defenses (decoder retry cooldown, media3 OOM-safe logger, memory degradation snackbar)
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 95
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-05-15

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | decoder-failure-tracker | — | ✅ Done | 4/4 | [PHASE_01__decoder-failure-tracker.md](PHASE_01__decoder-failure-tracker.md) |
| 02 | cooldown-integration | 01 | ✅ Done | 4/4 | [PHASE_02__cooldown-integration.md](PHASE_02__cooldown-integration.md) |
| 03 | media3-log-oom-guard | — | ✅ Done | 3/3 | [PHASE_03__media3-log-oom-guard.md](PHASE_03__media3-log-oom-guard.md) |
| 04 | memory-alert-snackbar | — | ✅ Done | 5/5 | [PHASE_04__memory-alert-snackbar.md](PHASE_04__memory-alert-snackbar.md) |
| 05 | strings-localization | 02, 04 | ✅ Done | 2/2 | [PHASE_05__strings-localization.md](PHASE_05__strings-localization.md) |
| 06 | docs-catalog-cleanup | 01–05 | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All five strategic §6 research items are `Resolved` (Q1, Q4, Q5 — engineering defaults; Q2, Q3 — owner decisions 2026-05-15). No pre-implementation blockers.

- [x] **Q1** — cooldown duration = 45 s (constant `DECODER_COOLDOWN_MS = 45_000L`)
- [x] **Q2** — context-dependent: slideshow → auto-skip; manual single file → snackbar with "Skip" action
- [x] **Q3** — Snackbar with "Close player" action
- [x] **Q4** — `Timber.w` ≤ 256 chars, fixed prefix + tag + throwable class + original-string length
- [x] **Q5** — cooldown key = canonical `path` string passed to `playVideo(path)`

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — **skip** (strategic §8: "Без изменений в `docs/FEATURES`").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new classes introduced).
- [ ] `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0213"` returns exit 0.
- [ ] `/spec-check S0213` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0213`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-05-15 — Initial tactical plan authored by `/spec-tech`.

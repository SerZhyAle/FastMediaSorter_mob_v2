# Tactical Plan: S0413 - bugfix-audio-graceful-skip

**Strategic spec:** [`../S0413_bugfix-audio-graceful-skip.md`](../S0413_bugfix-audio-graceful-skip.md)
**Research inputs:** [`research/01__error-code-classification.md`](research/01__error-code-classification.md), [`research/02__background-message-channel.md`](research/02__background-message-channel.md), [`research/03__queue-exhaustion-guard.md`](research/03__queue-exhaustion-guard.md)
**Feature:** Graceful skip of undecodable audio instead of stopping the service
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 90
**Status:** Not started
**Phases:** 3 / 3 done
**Last updated:** 2026-06-13

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | skip-message-strings | - | ✅ Done | 1/1 | [PHASE_01__skip-message-strings.md](PHASE_01__skip-message-strings.md) |
| 02 | graceful-skip-error-handling | 01 | ✅ Done | 4/4 | [PHASE_02__graceful-skip-error-handling.md](PHASE_02__graceful-skip-error-handling.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 2/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved (see Research inputs). No open blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not required (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public surface of `AudioPlaybackService` unchanged - regen for safety).
- [ ] `/spec-check S0413` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status accordingly.
5. All done: flip `Status:` to `Done`, run `/spec-check S0413`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-13 - Initial tactical plan authored by `/spec-tech`.

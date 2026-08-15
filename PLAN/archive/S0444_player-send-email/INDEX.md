# Tactical Plan: S0444 - player-send-email

**Strategic spec:** [`../S0444_player-send-email.md`](../S0444_player-send-email.md)
**Foundation:** [`../S0452_share-commands-infrastructure.md`](../S0452_share-commands-infrastructure.md) (Verified) + [`../S0452_share-commands-infrastructure/research/01__architecture.md`](../S0452_share-commands-infrastructure/research/01__architecture.md)
**Feature:** "Send to Email" share target - settings toggle (auto-rendered by S0452 group) + player-menu command that attaches the current file to an email compose intent
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 0 / 4 done
**Last updated:** 2026-06-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | trilingual-strings | - | ⬜ Not started | 0/1 | [PHASE_01__trilingual-strings.md](PHASE_01__trilingual-strings.md) |
| 02 | email-send-action-and-registration | 01 | ⬜ Not started | 0/3 | [PHASE_02__email-send-action-and-registration.md](PHASE_02__email-send-action-and-registration.md) |
| 03 | player-menu-command-and-gating | 01, 02 | ⬜ Not started | 0/4 | [PHASE_03__player-menu-command-and-gating.md](PHASE_03__player-menu-command-and-gating.md) |
| 04 | docs-catalog-cleanup | 01, 02, 03 | ⬜ Not started | 0/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

These are the only genuine unknowns; the rest is mechanical (see strategic §6, all Resolved).

- Email command icon: no `ic_*mail*`/`ic_*email*` drawable exists. Decide at Step 03.2 whether to reuse the generic share icon (`R.drawable.ic_share`, as the Telegram overflow command does) or add a new envelope vector. Does not block Phase 01-02.
- Optional manifest `<queries>` entry: NOT required for the chosen `ACTION_SEND` + chooser launch with `availability = ALWAYS` (no package probe). Add an `ACTION_SEND`+`message/rfc822` `<intent>` query ONLY if Step 02.1 chooses to pre-resolve / prefer a specific mail package. Default: do not add. Captured as a conditional step (02.1 note), not a blocker.

---

## Completion Gate

- [ ] All phases ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (strategic §8: this is a user-facing command).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (via `add_to_dev_log.ps1`).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new send-action class + Hilt module).
- [ ] Trilingual string parity verified (`check_strings_localized.ps1` exit 0).
- [ ] `/spec-check S0444` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Implemented`, then run `/spec-check S0444`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-16 - Initial tactical plan authored by `/spec-tech`.

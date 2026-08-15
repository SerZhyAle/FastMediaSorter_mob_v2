# Tactical Plan: S0446 - messenger-share-settings

**Strategic spec:** [`../S0446_messenger-share-settings.md`](../S0446_messenger-share-settings.md)
**Foundation:** [`../S0452_share-commands-infrastructure.md`](../S0452_share-commands-infrastructure.md) (Verified) - registry, app-global flag storage, availability resolver, `IsShareTargetEnabledUseCase`, auto-rendered settings group.
**Feature:** Per-profile "allow send to Telegram/WhatsApp/Instagram" toggles + command gating across player & browse + WhatsApp/Instagram send.
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 0 / 6 done
**Last updated:** 2026-06-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | trilingual-strings | - | ⬜ Not started | 0/2 | [PHASE_01__trilingual-strings.md](PHASE_01__trilingual-strings.md) |
| 02 | register-targets-and-queries | 01 | ⬜ Not started | 0/3 | [PHASE_02__register-targets-and-queries.md](PHASE_02__register-targets-and-queries.md) |
| 03 | messenger-send-implementation | 02, research | ⛔ Blocked | 0/3 | [PHASE_03__messenger-send-implementation.md](PHASE_03__messenger-send-implementation.md) |
| 04 | player-command-gating | 02, 03 | ⬜ Not started | 0/4 | [PHASE_04__player-command-gating.md](PHASE_04__player-command-gating.md) |
| 05 | browse-overflow-gating | 02, 03 | ⬜ Not started | 0/3 | [PHASE_05__browse-overflow-gating.md](PHASE_05__browse-overflow-gating.md) |
| 06 | docs-catalog-cleanup | all | ⬜ Not started | 0/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Phase 03 (messenger send) is **blocked** until the genuine messenger-API unknowns from strategic §6 are resolved. These are real external constraints, not yet answered:

- **B1 - Direct recipient selection feasibility.** Does any sanctioned public API let the app pick a WhatsApp/Instagram recipient and send an attachment without handing off to the messenger UI? Working hypothesis: no (business APIs require server integration, out of scope per §2). Resolution decides whether Phase 03 implements "pick recipient" or only "open in app with attachment". Default if unresolved: open-in-app with attachment via the system invoker.
- **B2 - Instagram attachment support.** Does Instagram accept an arbitrary file via a share intent, and for which media types? Instagram is known to restrict accepted formats. Resolution sets the target behavior and the user message when a format is rejected.
- **B3 - WhatsApp client package list.** Confirm the candidate package ids and preference order (standard vs Business client) for `setPackage` targeting, mirroring the Telegram package catalogue.

Resolution path: a focused research item (`research/01__messenger-send-limits.md`) before starting Phase 03. Phases 01, 02, 04, 05 do **not** depend on these answers and can proceed - their command callbacks route to the Phase 03 send methods, so Phase 03 must land before 04/05 wire the callbacks.

**Recommendation carried from §6:** implement the send as `ACTION_SEND` with `setPackage(<messenger>)` and a system-chooser fallback (the existing invoker primitive), no separate "in-app recipient" setting. This is the safe default and is expected to be the final shape unless B1 surprises.

---

## Completion Gate

- [ ] All phases ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (new user-visible toggles + WhatsApp/Instagram send).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new messenger package-catalogue classes + DI changes).
- [ ] String locale audit clean (`scripts/check_strings_localized.ps1` for the new keys).
- [ ] `/spec-check S0446` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Implemented`, run `/spec-check S0446`.

---

## Blockers Log

- 2026-06-16 - Phase 03 set `⛔ Blocked` at authoring: messenger-send API unknowns B1/B2/B3 (see Pre-Implementation Blockers) must be researched before send implementation.

---

## Change Log

- 2026-06-16 - Initial tactical plan authored by `/spec-tech`.

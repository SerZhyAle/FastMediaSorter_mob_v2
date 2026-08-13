# Tactical Plan: S0754 - camera-orientation-send-settings-dialog

**Strategic spec:** [`../S0754_camera-orientation-send-settings-dialog.md`](../S0754_camera-orientation-send-settings-dialog.md)
**Research inputs:** [`research/01__settings-dialog-capabilities.md`](research/01__settings-dialog-capabilities.md), [`research/02__send-to-mechanism.md`](research/02__send-to-mechanism.md), [`research/03__fixed-orientation.md`](research/03__fixed-orientation.md)
**Feature:** Camera wave 2 - fixed orientation, Send-to, settings dialog, preset polish
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 0 / 6 done
**Last updated:** 2026-06-28

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | fixed-orientation | - | ⬜ Not started | 0/4 | [PHASE_01__fixed-orientation.md](PHASE_01__fixed-orientation.md) |
| 02 | presets-and-save-label | - | ⬜ Not started | 0/4 | [PHASE_02__presets-and-save-label.md](PHASE_02__presets-and-save-label.md) |
| 03 | send-to | - | ⬜ Not started | 0/3 | [PHASE_03__send-to.md](PHASE_03__send-to.md) |
| 04 | settings-capabilities | - | ⬜ Not started | 0/4 | [PHASE_04__settings-capabilities.md](PHASE_04__settings-capabilities.md) |
| 05 | settings-dialog | 04 | ⬜ Not started | 0/5 | [PHASE_05__settings-dialog.md](PHASE_05__settings-dialog.md) |
| 06 | docs-catalog-cleanup | all | ⬜ Not started | 0/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All strategic §6 research items are Resolved (settings = full pro-mode; send-to reuses `SendToMenuManager`; orientation = self-managed portrait lock + icon rotation + targetRotation). See `research/`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/ALL_FEATURES.jsonl` has a record for the delivered capability (via `scripts/all_features/add.ps1`). Do NOT edit `docs/FEATURES*.md` per-spec (`/skill-release`-owned).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file (batched via `close-and-log.ps1`).
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new helpers + dialog).
- [ ] New strings pass `scripts/check_strings_localized.ps1`.
- [ ] `/spec-check S0754` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/6 done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` only when its Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip the row to `✅ Done`, bump the counter.
4. If blocked: flip to `⛔ Blocked`, add a bullet to Blockers Log, set journal status via `update.ps1 -Status Block...` with a `-StatusNote`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0754`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-28 - Initial tactical plan authored by `/spec-tech` (3 research artifacts).

# Tactical Plan: S0445 - profile-share-to-setting

**Strategic spec:** [`../S0445_profile-share-to-setting.md`](../S0445_profile-share-to-setting.md)
**Foundation:** S0452 share-commands-infrastructure (Verified) - registry, resolver, `IsShareTargetEnabledUseCase`, auto-rendered settings group.
**Feature:** Per-profile "Allow Share" toggle gating the system-Share command across every surface.
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 0 / 5 done
**Last updated:** 2026-06-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | register-system-share-target | - | ⬜ Not started | 0/2 | [PHASE_01__register-system-share-target.md](PHASE_01__register-system-share-target.md) |
| 02 | gate-player-command-panel | 01 | ⬜ Not started | 0/3 | [PHASE_02__gate-player-command-panel.md](PHASE_02__gate-player-command-panel.md) |
| 03 | gate-browse-surfaces | 01 | ⬜ Not started | 0/2 | [PHASE_03__gate-browse-surfaces.md](PHASE_03__gate-browse-surfaces.md) |
| 04 | gate-standalone-players | 01 | ⬜ Not started | 0/2 | [PHASE_04__gate-standalone-players.md](PHASE_04__gate-standalone-players.md) |
| 05 | docs-catalog-cleanup | 01-04 | ⬜ Not started | 0/3 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Surface inventory (the completeness anchor)

The dominant risk is missing a system-Share surface. The audit (strategic §6) enumerated nine show-points across three host families; every one must end up gated by `IsShareTargetEnabledUseCase("system_share", settings)`:

**Player command panel (Phase 02):**

1. Command set builder - `PlayerCommand.SHARE` added unconditionally (gate the add).
2. Landscape layout - `btnShareCmd.isVisible = true` set directly, bypassing the planner (separate gate point).

**Browse (Phase 03):**

3. Single-file overflow - `PlayerCommand.SHARE` in the per-row extended-command builder.
4. Multi-select toolbar - the `btnShare` visibility, currently `isVisible = hasSelection`.

**Standalone players (Phase 04) - one show-point each:**

5. Photo/Video standalone.
6. Audio standalone.
7. Text standalone.
8. Document standalone.
9. Generic standalone player.

**Out of scope (NOT gated - verified non-system-Share):** incoming `ACTION_SEND` receivers (resource import, receive-share, standalone dispatcher); contextual Office fallback-dialog share; drawing/print export; the separate Google Lens / Telegram / Keep targets.

Completeness predicate (Phase 05): a repo grep for `btnShareCmd.isVisible`/`btnShare.isVisible`/`add(PlayerCommand.SHARE)` finds no remaining show-point that is not behind the gate.

---

## Pre-Implementation Blockers

- **PIB-1 (resolve in Phase 04):** the system-Share `ShareTarget` carries no icon today (registry `iconRes` optional); the settings row renders title-only. Confirm a title-only row is acceptable in the group, or supply the existing Share drawable. Non-blocking for registration; settle before closing Phase 01.
- **PIB-2 (resolve in Phase 04):** standalone hosts inject `SettingsRepository` and already observe `AppSettings`, but their file-ops handler that wires the Share button has no settings access. Decide the thinnest seam: gate at each host's button-setup point (host already has settings) vs. threading the use-case into the shared standalone file-ops handler. Phase 04 picks the host-side gate unless a reason emerges.

No strategic §6 items remain open at the foundation level - all resolved by S0452.

---

## Completion Gate

- [ ] All phases ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (new user-visible toggle).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Surface-completeness grep (Phase 05) returns zero ungated show-points.
- [ ] `/spec-check S0445` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Implemented`, run `/spec-check S0445`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-06-16 - Initial tactical plan authored by `/spec-tech`.

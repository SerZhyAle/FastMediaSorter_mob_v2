# Tactical Plan: S0391 - remote-source-runtime-toggles

**Strategic spec:** [`../S0391_remote-source-runtime-toggles.md`](../S0391_remote-source-runtime-toggles.md)
**Research inputs:** [`research/RESEARCH_FINDINGS.md`](research/RESEARCH_FINDINGS.md)
**Feature:** Optional remote sources runtime toggles
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest (all phases implemented; awaiting on-device verification)
**Phases:** 7 / 7 done
**Last updated:** 2026-06-13

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-state | - | ✅ Done | 3/3 | [PHASE_01__settings-state.md](PHASE_01__settings-state.md) |
| 02 | availability-gate | 01 | ✅ Done | 3/3 | [PHASE_02__availability-gate.md](PHASE_02__availability-gate.md) |
| 03 | new-entry-gating | 02 | ✅ Done | 5/5 | [PHASE_03__new-entry-gating.md](PHASE_03__new-entry-gating.md) |
| 04 | list-background-gating | 02 | ✅ Done | 5/5 | [PHASE_04__list-background-gating.md](PHASE_04__list-background-gating.md) |
| 05 | playback-fileops-gating | 02 | ✅ Done | 6/6 | [PHASE_05__playback-fileops-gating.md](PHASE_05__playback-fileops-gating.md) |
| 06 | settings-welcome-ux | 02, 04 | ✅ Done | 5/5 | [PHASE_06__settings-welcome-ux.md](PHASE_06__settings-welcome-ux.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items and UI/UX questions resolved by owner 2026-06-13 (strategic §6, research §7). Settings placement chosen at `/spec-tech`: **Variant A** - new collapsible "Remote sources" section in the General tab. No unchecked blockers remain.

- [x] **Research:** Fate of existing disabled-source resources - hidden and inert, never deleted. Strategic §6.1.
- [x] **Research:** "Not deleted, just hidden" - confirmation dialog on disable with existing resources.
- [x] **Research:** In-flight background work on disable - cancel running work (best-effort). Strategic §6.2.
- [x] **Research:** Cloud group when cloud unsupported - hide the whole block. Strategic §6.3.
- [x] **UI:** Group-toggle mixed state - ON if any member ON. Tab strip vanishes silently. Favorites player gated.
- [x] **Decision:** Settings placement - Variant A (collapsible section in General tab).

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated - strategic §8 mandates a FEATURES sentence.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (new public classes: `RemoteSourceId`, `RemoteSourceAvailabilityGate`, `RemoteSourceSettingsStore`, `RemoteSourceDisableCoordinator`, `WelcomeRemoteSourcesController`).
- [ ] On-device verification pass (status `BlockNeedUserTest`) - then `/spec-check S0391` removes the 5 debug tags and sets `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check` after device test.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0391`.

---

## Blockers Log

- 2026-06-13 - none open. Plan refreshed from research with all decisions resolved.

---

## Change Log

- 2026-06-11 - Initial tactical plan authored by `/spec-tech` (6 phases, unresolved blockers).
- 2026-06-13 - Research consolidated; owner decisions resolved; plan refreshed to 7 phases (split existing-resource gating into list/background + playback/fileops; added tab-strip vanish, Rule 14 remediation, `RemoteSourceId`, Favorites player gating, auth non-touch guard).
- 2026-06-13 - Owner UI refinement: settings shows THREE group toggles (SMB / (S)FTP / Cloud), not six, with explanation + "?" help each; placed between File Browser and Authorization; welcome uses the same three; storage stays six flags (Phase 01 unchanged), groups mass-write. Phase 06 rewritten; strategic §3.1/§3.3/§5.1/§11 and research §0/§5/§6 updated. Phase 01 done; not implementing further yet (owner hold).

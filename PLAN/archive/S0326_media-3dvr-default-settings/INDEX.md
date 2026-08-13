# Tactical Plan: S0326 - media-3dvr-default-settings

**Strategic spec:** [`../S0326_media-3dvr-default-settings.md`](../S0326_media-3dvr-default-settings.md)
**Feature:** Global 3D/VR default settings (Settings → Media → 3D/VR)
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest (awaiting on-device VR verification)
**Phases:** 6 / 6 done (Phase 04 superseded)
**Last updated:** 2026-06-01

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations-settings-fields | - | ✅ Done | 3/3 | [PHASE_01__foundations-settings-fields.md](PHASE_01__foundations-settings-fields.md) |
| 02 | detection-config | 01 | ✅ Done | 3/3 | [PHASE_02__detection-config.md](PHASE_02__detection-config.md) |
| 03 | coordinator-default-slot | 01 | ✅ Done | 3/3 | [PHASE_03__coordinator-default-slot.md](PHASE_03__coordinator-default-slot.md) |
| 04 | settings-screen-shared | 02, 03 | ⏭️ Superseded | 0/3 | [PHASE_04__settings-screen-shared.md](PHASE_04__settings-screen-shared.md) |
| 05 | settings-vr-block (all groups A–E) | 02, 03 | ✅ Done | 4/4 | [PHASE_05__settings-screen-vr.md](PHASE_05__settings-screen-vr.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research (strategic §6.1) - RESOLVED 2026-06-01:** the global "disable 3D/VR" switch and the existing VR master toggle are unified into a single user control; the second stored flag becomes subordinate (kept in sync), so the two can never contradict. No truth table needed.

Resolved-by-default (documented in strategic spec, no owner action needed unless changed):

- **§6.2 image/video defaults:** first iteration uses a single shared default for images and video. Split deferred.
- **§6.3 projection visibility:** the default-projection control and all VR-only groups are hidden on non-VR flavors.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - update per strategic §8 (new user-visible capability).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0326` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0326`.

---

## Blockers Log

- 2026-06-01 - Phase 05 gate CLEARED: §6.1 resolved (single unified 3D/VR switch, subordinate second flag).
- 2026-06-01 - UI-clarify reframe (owner): ALL 3D/VR settings UI is VR-only and lives in the existing VR media section (src/vr). Phase 04 (separate always-visible shared screen) SUPERSEDED; its groups A/D fold into Phase 05. No 3D/VR settings UI on non-VR flavors. Strategic §3.2/§3.3/§6.3 + ADR-3 updated. Phase 05 to be re-detailed via /spec-tech --phase 05 before implementation (now carries groups A–E + spinners for default layout/projection + unified 3D/VR switch).

---

## Change Log

- 2026-06-01 - Initial tactical plan authored by `/spec-tech`.

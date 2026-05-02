# Tactical Plan: S0028 — vr-multi-window-playback

**Strategic spec:** [`../S0028_vr-multi-window-playback.md`](../S0028_vr-multi-window-playback.md)
**Feature:** VR Multi-Window Playback (Quest 3 / HorizonOS)
**Tier:** 4 — Strategic
**Priority:** 75
**Status:** Not started
**Phases:** 0 / 5 done
**Last updated:** 2026-04-30

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | manifest-player-multi-instance | — | ⬜ Not started | 0/2 | [PHASE_01__manifest-player-multi-instance.md](PHASE_01__manifest-player-multi-instance.md) |
| 02 | per-window-resume-state | 01 | ⬜ Not started | 0/5 | [PHASE_02__per-window-resume-state.md](PHASE_02__per-window-resume-state.md) |
| 03 | window-id-intent-plumbing | 02 | ⬜ Not started | 0/4 | [PHASE_03__window-id-intent-plumbing.md](PHASE_03__window-id-intent-plumbing.md) |
| 04 | open-in-new-window-ui | 03 | ⬜ Not started | 0/3 | [PHASE_04__open-in-new-window-ui.md](PHASE_04__open-in-new-window-ui.md) |
| 05 | docs-catalog-cleanup | all | ⬜ Not started | 0/4 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Phase 01 must not start while any blocker is unchecked.

- [ ] **S0038 resolved:** `bugfix-vr-exit-immersive-new-window` must reach `Verified` (currently `BlockNeedUserTest`). Without S0038, accidental multi-window from `exitImmersive` is indistinguishable from intentional. Run `/spec-check S0038` after user test passes. See strategic §11.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed: repository interface, use-cases).
- [ ] `/spec-check S0028` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status via `update.ps1 -Status Block...`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0028`.

---

## Architecture Notes (read before Phase 01)

- **ResumeStateRepositoryImpl** uses SharedPreferences (`"resume_state_prefs"` file), not Room. Per-window keying: name the prefs file `"resume_state_prefs_${windowId}"`. No DB migration required.
- **AudioPlaybackService** already exists at `ui/player/AudioPlaybackService.kt` as a `MediaSessionService`. Background audio is already implemented for the standard single-window case. Phases 02–04 must not break its `isRunning` / `pendingDirection` static contract.
- **VrPlayerActivity** keeps `launchMode="singleTask"` — only one immersive VR session at a time. Multi-instance applies only to panel `PlayerActivity` windows.
- `WINDOW_ID_MAIN = "main"` is the conventional identifier for the primary Browse-rooted window. Pass it wherever existing callers read resume state without a new-window context.

---

## Blockers Log

*(none yet)*

---

## Change Log

- 2026-04-30 — Initial tactical plan authored by `/spec-tech`.

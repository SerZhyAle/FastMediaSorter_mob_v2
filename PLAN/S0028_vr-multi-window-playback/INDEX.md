# Tactical Plan: S0028 — vr-multi-window-playback

**Strategic spec:** [`../S0028_vr-multi-window-playback.md`](../S0028_vr-multi-window-playback.md)
**Feature:** Multi-Window Mode (Quest 3 / Samsung DeX / any Android multi-window device)
**Tier:** 4 — Strategic
**Priority:** 75
**Status:** Not started
**Phases:** 7 / 7 done
**Last updated:** 2026-05-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-platform-gate | — | ✅ Done | 4/4 | [PHASE_01__settings-platform-gate.md](PHASE_01__settings-platform-gate.md) |
| 02 | manifest-multi-instance | 01 | ✅ Done | 2/2 | [PHASE_02__manifest-multi-instance.md](PHASE_02__manifest-multi-instance.md) |
| 03 | per-window-resume-state | 02 | ✅ Done | 5/5 | [PHASE_03__per-window-resume-state.md](PHASE_03__per-window-resume-state.md) |
| 04 | window-id-plumbing | 03 | ✅ Done | 5/5 | [PHASE_04__window-id-plumbing.md](PHASE_04__window-id-plumbing.md) |
| 05 | browse-entry-points | 04 | ✅ Done | 4/4 | [PHASE_05__browse-entry-points.md](PHASE_05__browse-entry-points.md) |
| 06 | player-tear-off | 04 | ✅ Done | 3/3 | [PHASE_06__player-tear-off.md](PHASE_06__player-tear-off.md) |
| 07 | docs-catalog-cleanup | 05, 06 | ✅ Done | 4/4 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Phase 01 must not start while any blocker is unchecked.

- [x] **S0038 resolved:** `bugfix-vr-exit-immersive-new-window` is `Implemented` (2026-05-04). The fix that removed accidental `FLAG_ACTIVITY_NEW_TASK` from `exitImmersive` is in code. `Verified` user test can run in parallel with S0028. Blocker lifted.
- [ ] **Q6 resolved (optional, unblocks Phase 01.3):** Platform detection API for non-VR multi-window (Samsung DeX / `isInMultiWindowMode()`). See strategic §6 Q6. Phase 01 can ship with setting always visible while Q6 is open; runtime gate can be added in Phase 01.3 later.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (Phase 07).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed: `AppSettings`, `SettingsRepository`, `ResumeStateRepository`, use-cases).
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

- **Settings pipeline:** `AppSettings` (domain model) → `SettingsRepository` (interface) → `SettingsRepositoryImpl` (DataStore directly — no `SettingsManager` intermediary). New booleans: add `KEY_xxx = booleanPreferencesKey(...)` to `SettingsRepositoryImpl` companion object, wire in `getSettings()` and `updateSettings()`. `SettingsRepository` interface needs no new per-field methods.
- **Default by flavor:** `BuildConfig.SUPPORT_VR_PLAYER` is `true` for VR flavors, `false` for all others. Use it as the default value for `allowSeparateWindow`.
- **ResumeStateRepositoryImpl** uses SharedPreferences (`"resume_state_prefs"` file), not Room. Per-window keying: prefs file name becomes `"resume_state_prefs_${windowId}"`. No DB migration required.
- **AudioPlaybackService** already exists at `ui/player/AudioPlaybackService.kt`. Phases 03–06 must not break its static contract.
- **VrPlayerActivity** keeps `launchMode="singleTask"` — only one immersive VR session at a time. Multi-instance applies only to panel `PlayerActivity` and `BrowseActivity` windows.
- `WINDOW_ID_MAIN = "main"` is the conventional identifier for the primary Browse-rooted window.
- **Browse tear-off state** is passed entirely via Intent extras (resource, file, scroll) — NOT via SharedPreferences resume state. Resume state mechanism is for per-window playback position (Phase 03–04).

---

## Blockers Log

*(none yet)*

---

## Change Log

- 2026-05-04 — Tactical plan rewritten by `/spec-tech` (redesign: settings toggle + 3 entry points + Browse multi-instance; supersedes 2026-04-30 plan).

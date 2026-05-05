# Tactical Plan: S0057 — bugfix-vr-hud-autohide-timeout

**Strategic spec:** [`../S0057_bugfix-vr-hud-autohide-timeout.md`](../S0057_bugfix-vr-hud-autohide-timeout.md)
**Feature:** VR HUD 15 s idle auto-hide
**Tier:** 2 — Easy
**Priority:** 60
**Status:** Implemented
**Phases:** 3 / 3 done
**Last updated:** 2026-05-03

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | fps-keep-alive-fix | — | ✅ Done | 3/3 | [PHASE_01__fps-keep-alive-fix.md](PHASE_01__fps-keep-alive-fix.md) |
| 02 | motion-noise-filter | 01 | ✅ Done | 3/3 | [PHASE_02__motion-noise-filter.md](PHASE_02__motion-noise-filter.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None — strategic §6 lists code anchors only; no open research items.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed (this bugfix touches private internals only — likely no catalogue diff beyond hashes).
- [ ] On Quest 3 with `vrShowFps=true`: HUD fully disappears within 15 s after last input.
- [ ] On Quest 3 with `vrShowFps=false`: HUD fully disappears within 15 s after last input.
- [ ] Pause/seek/volume/file-next still wake HUD instantly and reset 15 s window.
- [ ] `/spec-check S0057` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0057`.

---

## Blockers Log

- _none_

---

## Change Log

- 2026-05-03 — Initial tactical plan authored by `/spec-tech`.

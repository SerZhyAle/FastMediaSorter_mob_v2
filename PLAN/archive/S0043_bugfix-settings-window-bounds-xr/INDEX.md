# Tactical Plan: S0043 — bugfix-settings-window-bounds-xr

**Strategic spec:** [`../S0043_bugfix-settings-window-bounds-xr.md`](../S0043_bugfix-settings-window-bounds-xr.md)
**Feature:** Correct window bounds for system Settings permission dialogs on XR / freeform / foldable
**Tier:** 1 — Quick Win
**Priority:** 90
**Status:** In Progress
**Phases:** 3 / 3 done
**Last updated:** 2026-05-01

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | settings-launcher | — | ✅ Done | 3/3 | [PHASE_01__settings-launcher.md](PHASE_01__settings-launcher.md) |
| 02 | wire-callers | 01 | ✅ Done | 4/4 | [PHASE_02__wire-callers.md](PHASE_02__wire-callers.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research:** Default window-size fraction (strategic §6.1) — Resolved 2026-05-01: 0.80 × 0.85 of display width × height; revisit on first XR test report.
- [x] **Research:** Re-bounds on orientation change mid-launch (strategic §6.2) — Resolved 2026-05-01: compute once at launch; do not handle rotation race.
- [x] **Research:** API 23 (`legacy` flavor) behaviour (strategic §6.3) — Resolved 2026-05-01: guard `setLaunchBounds` with `Build.VERSION.SDK_INT >= 24`; pre-API-24 falls through to plain `startActivity`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (if user-facing — see strategic §8). **N/A** — strategic §8 declares no FEATURES change.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public utility added).
- [ ] `/spec-check S0043` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0043`.

---

## Blockers Log

- (none yet)

---

## Change Log

- 2026-05-01 — Initial tactical plan authored by `/spec-tech`.

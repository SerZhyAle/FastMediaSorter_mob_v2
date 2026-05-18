---
name: project-functionality-log
description: dev/FUNCTIONALITY.log - developer journal of user-visible functionality lifecycle (ADD/CHANGE/DELETE/FIX), separate from dev/CHANGELOG.md and docs/FEATURES.md
metadata:
  type: project
---

`dev/FUNCTIONALITY.log` is a plain-text developer journal of user-visible functionality lifecycle, introduced 2026-05-14. It sits between the two existing journals:

- `dev/CHANGELOG.md` - every code/config touch (low-level, written via `scripts/add_to_dev_log.ps1`).
- `docs/FEATURES.md` (+ `_RU`/`_UK`) - public end-user catalogue of significant features only.
- `dev/FUNCTIONALITY.log` - internal history of *which* user-visible capability was created/changed/deleted/fixed, when, and under which `Sxxxx` ticket.

**Why:** the author wants a grep-friendly developer audit trail of feature lifecycle that survives even when `docs/FEATURES.md` updates are skipped (refactors, polish, internal capabilities, `/quick` tweaks). `CHANGELOG.md` is too noisy for this purpose; `FEATURES.md` is too curated.

**How to apply:**
- Write one line via `scripts/add_to_functionality_log.ps1 -Id Sxxxx -Op <ADD|CHANGE|DELETE|FIX> -Description "<english summary>"` when a task completes a user-visible behaviour change. Omit `-Id` if there is no spec (e.g. `/quick`).
- Line format is fixed: `[YYYY-MM-DD HH:MM] [Sxxxx|------] [OP    ] <english description>`. `OP` is padded to 6 chars. Description in English (like all code/docs/logs).
- Skip for pure refactors, internal optimisations, or anything a user cannot perceive - same skip criteria as `docs/FEATURES.md`, but broader (visible bug fixes still get a FIX line).
- Skills that own the write: `/spec-dev` (ADD/CHANGE on `Implemented`), `/spec-check` (fallback ADD/CHANGE if no entry from `/spec-dev` exists), `/spec-fix` (FIX on auto-fix that restores visible behaviour), `/spec-arc` (DELETE only when archived with `--removes-functionality`), `/quick` (CHANGE without `-Id` for visible tweaks), `/skill-fix-release` (one FIX per cherry-picked Sxxxx), `/skill-release` (sanity check only - never backfills).
- `/spec-all` orchestrator never writes the log itself; it only sanity-checks that downstream skills did and surfaces `[FUNC_LOG MISSED]` markers in its final report.
- CLAUDE.md Post-Change Steps §3 documents the rule. Direct edits to `dev/FUNCTIONALITY.log` are allowed but the script keeps formatting consistent.

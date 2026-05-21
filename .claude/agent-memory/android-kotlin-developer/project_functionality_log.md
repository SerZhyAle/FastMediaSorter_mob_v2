---
name: project_functionality_log
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
- After completing a code change whose effect a user can perceive (new button works, a bug is fixed, a capability is removed, an existing flow changes), call `pwsh -NoProfile -File scripts/add_to_functionality_log.ps1 -Id Sxxxx -Op <ADD|CHANGE|DELETE|FIX> -Description "<english summary>"`. Omit `-Id` only for unticketed `/quick` tweaks.
- Skip the call entirely for pure refactors, build/config changes invisible to the user, internal performance work, or anything that has no end-user-visible delta - same skip criteria as `docs/FEATURES.md`, but broader (visible bug fixes still get a `FIX` line).
- The skill in flight may have already called the script - check `dev/FUNCTIONALITY.log` last line before adding a duplicate. Skills that own the write: `/spec-dev`, `/spec-check`, `/spec-fix`, `/spec-arc`, `/quick`, `/skill-fix-release`. If a skill is driving the work, don't call the script manually.
- Line format is fixed: `[YYYY-MM-DD HH:MM] [Sxxxx|------] [OP    ] <english description>`. `OP` is padded to 6 chars. Description in English (like all code/docs/logs).

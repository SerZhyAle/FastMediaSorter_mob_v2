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

**How to apply:** When the doc-writer's polish introduces a user-visible behaviour clarification (not just rewording) - e.g. a help string newly admits that "video keeps playing on lock" or a release note documents a hidden gesture - call `scripts/add_to_functionality_log.ps1 -Id Sxxxx -Op <ADD|CHANGE|DELETE|FIX> -Description "<english summary>"` for that change. Pure copy polish without behavioural shift is logged in `dev/CHANGELOG.md` only via the standard post-change ritual. Line format is fixed by the script: `[YYYY-MM-DD HH:MM] [Sxxxx|------] [OP    ] <english description>`. Omit `-Id` for entries without a spec ticket.

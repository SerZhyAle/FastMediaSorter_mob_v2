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
- When researching the history of a user-visible capability, grep `dev/FUNCTIONALITY.log` first - it is faster than scrolling `dev/CHANGELOG.md` and more precise than `docs/FEATURES.md`.
- Line format is fixed: `[YYYY-MM-DD HH:MM] [Sxxxx|------] [OP    ] <english description>`. `OP` is padded to 6 chars. Use this to filter by ticket id or op type when assembling research evidence.
- Do NOT write to this file - the researcher is read-only. If a spec audit notices a missing entry, flag it in the research report under "Open Questions" rather than calling the CLI yourself.
- Cross-reference with `dev/CHANGELOG.md` when a single ticket touched both visible behaviour and internal refactors; both journals can be cited side by side in the report.

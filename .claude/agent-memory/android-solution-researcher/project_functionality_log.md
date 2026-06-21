---
name: capability-inventory-all-features
description: dev/FUNCTIONALITY.log RETIRED (S0489); capability history is now docs/ALL_FEATURES.jsonl + git/release diffs, read-only
metadata:
  type: project
---

The free-text functionality log was retired; capability history now lives in a structured inventory.

**Why:** S0489 migrated user-visible-capability tracking from the plain-text `dev/FUNCTIONALITY.log` to a schema-validated JSONL inventory (`docs/ALL_FEATURES.jsonl`, EN-only, one JSON object per line, upsert-by-id). The old log is no longer written and `scripts/add_to_functionality_log.ps1` hard-errors. Chronology now comes from git history + release diffs, not from a single time-ordered file.

**How to apply (researcher is read-only):**
- When researching whether/when a user-visible capability exists, grep `docs/ALL_FEATURES.jsonl` first - each line is `{id, area, name, description, flavors, spec, status}`; filter by `spec` (`Sxxxx`), `area`, or `status` (`active`/`removed`). noLegal-only capabilities are in gitignored `docs/ALL_FEATURES_noLegal.jsonl`.
- For current capability state, read `docs/ALL_FEATURES.jsonl` + the code - never git. Only when the question is **explicitly about timeline** ("when did X land") and the user asked for it may you fall back to `git log` over `docs/ALL_FEATURES.jsonl` / the touched source, or the per-release FEATURES diff; the inventory itself has no timestamps.
- Do NOT write the inventory or call any `*functionality_log*` script - read-only role. If an audit notices a missing/incorrect record, flag it under "Open Questions" / as a `/spec-draft` candidate for the caller, never mutate.
- Layer roles: `dev/CHANGELOG.md` = low-level code-touch journal; `docs/FEATURES*.md` = curated end-user showcase (edited only by `/skill-release`); `docs/ALL_FEATURES.jsonl` = the developer capability inventory.

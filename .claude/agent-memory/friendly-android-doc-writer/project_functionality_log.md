---
name: capability-inventory-all-features
description: dev/FUNCTIONALITY.log + add_to_functionality_log.ps1 RETIRED (S0489); capability records go to docs/ALL_FEATURES.jsonl; FEATURES*.md is /skill-release-owned
metadata:
  type: project
---

The free-text functionality log was retired and replaced by a structured capability inventory.

**Why:** S0489 migrated user-visible-capability tracking from the plain-text `dev/FUNCTIONALITY.log` to a schema-validated JSONL inventory (`docs/ALL_FEATURES.jsonl`, EN-only, one JSON object per line, upsert-by-id). `scripts/add_to_functionality_log.ps1` now hard-errors pointing at the replacement.

**How to apply:**
- Do NOT call `scripts/add_to_functionality_log.ps1` - retired, errors out.
- If doc-writing surfaces a genuinely new/changed user-visible *behaviour* (not just reworded copy), record the capability via `pwsh -NoProfile -File scripts/all_features/add.ps1 -Id "<area>.<feature>" -Area "<Area>" -Name "<Name>" -Description "<EN desc>" -Flavors "<list>" [-Spec Sxxxx]` (EN-only; `-NoLegal` for noLegal-only; `-ListAreas` to pick a valid area). Usually the owning spec skill already recorded it - check `docs/ALL_FEATURES.jsonl` before adding.
- Pure copy polish with no behavioural shift = `dev/CHANGELOG.md` only via the standard post-change ritual; no inventory entry.
- `docs/FEATURES*.md` (EN/RU/UK) is the curated public showcase, edited ONLY by `/skill-release` from the `ALL_FEATURES` diff since the last release - never write a per-spec/per-doc entry into FEATURES yourself. noLegal showcase lives in gitignored `docs/FEATURES_noLegal*.md`.

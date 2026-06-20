---
name: capability-inventory-all-features
description: dev/FUNCTIONALITY.log + add_to_functionality_log.ps1 RETIRED (S0489); record capabilities in docs/ALL_FEATURES.jsonl via scripts/all_features/add.ps1
metadata:
  type: project
---

The free-text functionality log was retired and replaced by a structured capability inventory.

**Why:** S0489 migrated user-visible-capability tracking from the plain-text `dev/FUNCTIONALITY.log` to a schema-validated JSONL inventory (`docs/ALL_FEATURES.jsonl`, EN-only, one JSON object per line, upsert-by-id). `scripts/add_to_functionality_log.ps1` now hard-errors pointing at the replacement.

**How to apply:**
- Do NOT call `scripts/add_to_functionality_log.ps1` - retired, errors out (`exit 1`).
- After delivering a shippable user-visible capability, record it: `pwsh -NoProfile -File scripts/all_features/add.ps1 -Id "<area>.<feature>" -Area "<Area>" -Name "<Name>" -Description "<EN desc>" -Flavors "standard,lite,photos,legacy[,vr,noLegal]" [-Spec Sxxxx] [-Status active|removed]`.
  - `-Id` is kebab `<area>.<feature>` lowercase (regex-validated); upsert replaces a same-id record in place. `-ListAreas` prints existing areas so you pick a valid one without an exploratory pass.
  - Name/Description ASCII/EN-only (non-ASCII rejected). Flavors validated against standard,lite,photos,legacy,vr,noLegal. noLegal-only: `-NoLegal` → gitignored `docs/ALL_FEATURES_noLegal.jsonl`.
- When a spec skill is driving the work (`/spec-dev`, `/spec-check`, `/spec-fix`), the record is written by `close-and-log.ps1 -FuncOp/-FuncDesc` (+ `-FeatArea`/`-FeatFlavors`/`-FeatName` for a meaningful entry) - do not double-write.
- Layer roles otherwise unchanged: `dev/CHANGELOG.md` = low-level code-touch journal (via `add_to_dev_log.ps1`); `docs/FEATURES*.md` = curated end-user showcase, edited ONLY by `/skill-release` from the inventory diff - never per-spec.
- A rendering/UI bug fix is NOT a capability - skip ALL_FEATURES, the CHANGELOG dev-log is the record. Reserve ALL_FEATURES for added/changed/removed user-visible capabilities.

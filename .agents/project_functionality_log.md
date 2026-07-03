---
name: capability-inventory-all-features
description: dev/FUNCTIONALITY.log + add_to_functionality_log.ps1 RETIRED (S0489); capability inventory is now docs/ALL_FEATURES.jsonl via scripts/all_features/add.ps1
metadata:
  type: project
---

The free-text functionality log was retired and replaced by a structured capability inventory.

**Why:** S0489 migrated user-visible-capability tracking from the plain-text `dev/FUNCTIONALITY.log` to a schema-validated JSONL inventory (`docs/ALL_FEATURES.jsonl`, EN-only, one JSON object per line, upsert-by-id). `scripts/add_to_functionality_log.ps1` now hard-errors pointing at the replacement.

**How to apply:**
- Do NOT call `scripts/add_to_functionality_log.ps1` - retired, errors out.
- Record a capability: `pwsh -NoProfile -File scripts/all_features/add.ps1 -Id "<area>.<feature>" -Area "<Area>" -Name "<Name>" -Description "<EN desc>" -Flavors "standard,lite,photos,legacy[,vr,noLegal]" [-Spec Sxxxx] [-Status active|removed]`.
  - `-Id` is kebab `<area>.<feature>` lowercase (regex-validated); upsert replaces a same-id record in place.
  - Name/Description ASCII/EN-only (non-ASCII rejected). Flavors validated against standard,lite,photos,legacy,vr,noLegal.
  - noLegal-only capability: `-NoLegal` → gitignored `docs/ALL_FEATURES_noLegal.jsonl`.
- `close-and-log.ps1` records the capability itself via the new tool from `-FuncOp`/`-FuncDesc` + optional `-FeatId`/`-FeatArea`/`-FeatName`/`-FeatFlavors` (defaults Area=General, Flavors=standard, auto-id `sXXXX.<slug>`). Pass `-FeatArea`/`-FeatFlavors`/`-FeatName` explicitly for a meaningful entry; the bare `-FuncOp/-FuncDesc` defaults produce a weak `General`/`standard` record.
- Layer roles unchanged otherwise: `dev/CHANGELOG.md` = low-level code-touch journal (via `add_to_dev_log.ps1`); `docs/FEATURES*.md` = curated end-user catalogue.
- A rendering/UI bug fix is NOT a capability - skip ALL_FEATURES, the CHANGELOG dev-log is the record. Reserve ALL_FEATURES for added/changed/removed user-visible capabilities.

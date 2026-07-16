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
- `close-and-log.ps1` records the capability from `-FuncOp`/`-FuncDesc`, and since **S1072 requires `-FeatArea`/`-FeatName`/`-FeatFlavors` alongside** - a missing one is exit 2 with nothing mutated. The old defaults (Area=General, Flavors=standard, Name=80-char cut of `-FuncDesc`) are gone: they were not a "weak" record but a false one - structurally valid, plausible, and passed by `validate.ps1`, so the lie surfaced only in the release showcase built off this inventory. `-FeatId` stays optional (derived `<area-slug>.<name-slug>`). Read `-FeatFlavors` off the real gate (`BuildConfig` flag in `app_v2/build.gradle.kts`, or the source set), never off a sibling record - siblings disagree. No capability to record -> `-SkipFuncLog` or omit `-FuncOp`.
- Layer roles unchanged otherwise: `dev/CHANGELOG.md` = low-level code-touch journal (via `add_to_dev_log.ps1`); `docs/FEATURES*.md` = curated end-user catalogue.
- A rendering/UI bug fix is NOT a capability - skip ALL_FEATURES, the CHANGELOG dev-log is the record. Reserve ALL_FEATURES for added/changed/removed user-visible capabilities.

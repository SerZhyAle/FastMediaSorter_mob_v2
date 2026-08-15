# Phase 05 - Strings, docs, catalog

**Strategic spec:** [`../S0390_standalone-command-panel-parity.md`](../S0390_standalone-command-panel-parity.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** 01-04

## Steps

### Step 05.1 - Strings EN/RU/UK

- New strings for the two overflow titles + crop/rotate contentDescriptions (if not reusing existing `crop`/`rotate` keys).
- Use `scripts/utils/set-android-string.ps1 -Action add` (EN/RU/UK lockstep). Verify with `scripts/check_strings_localized.ps1`.

**Verification:** `check_strings_localized.ps1 -KeyPrefix` exit 0.

### Step 05.2 - FEATURES EN/RU/UK

- One sentence: opening an image from another app now offers crop, crop-to-file, compress and a screen-rotate toggle - the same editing actions as the in-app player. (Draw deferred - do not mention.)

**Verification:** `Grep` - the sentence in `docs/FEATURES.md` + `_RU` + `_UK`.

### Step 05.3 - Catalog + changelog

- `scripts/catalog_sync.ps1 -Module app_v2`; fill role/status for `StandaloneImageEditController`.
- `add_to_dev_log.ps1` per modified file.

**Verification:** `Grep` - `StandaloneImageEditController` in `dev/CATALOG/app_v2.jsonl`.

## Phase Done Criteria

- [ ] Strings localized (EN/RU/UK parity).
- [ ] FEATURES trilingual.
- [ ] Catalog regenerated.

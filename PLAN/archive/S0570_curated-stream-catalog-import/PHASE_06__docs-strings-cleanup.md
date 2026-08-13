# Phase 06 - Docs, localisation, catalog cleanup

**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** Pending
**Depends on:** Phase 05

## Objective

Close out: localisation parity, capability inventory, dev log, class catalog sync. No new behaviour.

## Steps

### Step 06.1 - Localisation parity

> Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "streams_"`. Fix any EN/RU/UK gap. RU uses Ё where correct.

**Verification:** script exits 0.

### Step 06.2 - Capability inventory

> When the feature works end-to-end, record the shipped capability via `scripts/all_features/add.ps1` (EN-only): curated stream catalog one-tap import/update with topic/language filter, sort and search. `spec` field = `S0570`.

**Verification:** `Grep docs/ALL_FEATURES.jsonl` for `S0570` returns the record.

### Step 06.3 - Catalog sync + dev log

> `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (new Kotlin classes get role/status via `set.ps1`). One dev-log entry per logical change through `add_to_dev_log.ps1` / `close-and-log.ps1`.

**Verification:** new classes present in `dev/CATALOG/app_v2.jsonl`; dev log entry present.

### Step 06.4 - Release packaging note

> Confirm the maintainer step is documented (it is, in `delivery/stream-catalog/README.md`): zip `streams.csv` -> upload `stream-catalog.zip` to Release `delivery-so-v1`. No code.

**Verification:** README has the `Compress-Archive` + release-asset URL.

**Status:** `[ ]` pending (all steps)

## Phase Done Criteria

- [ ] Steps 06.1-06.4 done.
- [ ] `check_strings_localized.ps1 -KeyPrefix "streams_"` exits 0.
- [ ] `ALL_FEATURES.jsonl` has the S0570 record once the feature is verified.
- [ ] Device-test gate (S0570 -> BlockNeedUserTest) per `/spec-all` finalization.

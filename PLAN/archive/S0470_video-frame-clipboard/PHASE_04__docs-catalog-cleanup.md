# Phase 04 - docs-catalog-cleanup

**Goal:** Record the delivered capability in docs, feature inventory, dev changelog and class catalog.

**Depends on:** all

---

## Steps

- [ ] 1. `docs/FEATURES.md` + `_RU.md` + `_UK.md`: add one sentence in the video-frame block about the new "save extracted frames to clipboard" option.
  - **Verification:** sentence present in all three locales.

- [ ] 2. `docs/ALL_FEATURES.jsonl`: add a record for the capability via `scripts/all_features/add.ps1`; `validate.ps1` exit 0.
  - **Verification:** record present; validate PASS.

- [ ] 3. `dev/CHANGELOG.md`: one entry per modified file via `add_to_dev_log.ps1` (covered incrementally during phases).
  - **Verification:** entries present for all modified files.

- [ ] 4. `dev/CATALOG/app_v2.jsonl`: regenerate via `scripts/catalog_sync.ps1 -Module app_v2`.
  - **Verification:** catalog sync exit 0.

---

## Phase Done Criteria

- [ ] FEATURES trilingual + ALL_FEATURES record present.
- [ ] Catalog regenerated.

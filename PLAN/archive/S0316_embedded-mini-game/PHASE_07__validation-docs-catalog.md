# Phase 07 - Validation, Docs, Catalog Cleanup

Goal: finish required verification, public feature documentation, catalog metadata, and spec status hygiene.

## Files

Modify:
- `docs/FEATURES.md`
- `docs/FEATURES_RU.md`
- `docs/FEATURES_UK.md`
- `PLAN/S0316_embedded-mini-game.md`
- `PLAN/S0316_embedded-mini-game/INDEX.md`
- Phase files in `PLAN/S0316_embedded-mini-game/` as steps are completed.

## Steps

- [x] Mark each completed implementation step `[x]` only after its verification predicate is satisfied.
- [x] Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` and ensure new game classes have catalog entries.
- [x] If new classes are missing role/status metadata, use `dev/CATALOG/scripts/set.ps1` to fill it.
- [x] Run `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "game_"`.
- [x] Run focused unit tests for `domain.game`, `data.game`, and `ui.game` packages.
- [x] Run a project build through the repository build workflow for Standard debug.
- [x] Add the new user-facing feature bullet to all three feature inventory files.
- [x] Search for permanent `Timber.d("S0316:` probes and remove any accidental ticket-tag logs.
- [x] Update the strategic spec with implementation notes and set catalog status to `Implemented` only after code, docs, strings, and build checks pass.

## Verification

- `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` succeeds.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "game_"` succeeds.
- Focused unit tests for game packages succeed.
- Standard debug build succeeds or reports exact pre-existing unrelated failures.
- `rg "S0316:" app_v2/src/main/java app_v2/src/test/java` returns no matches.
- `rg "Kryvavitsa|mini-game|мини-иг" docs/FEATURES.md docs/FEATURES_RU.md docs/FEATURES_UK.md` finds the new feature entries.

## Done

- [x] All phase checklists reflect actual verified work.
- [x] Public docs are updated in EN/RU/UK.
- [x] S0316 is ready for `/spec-check` or on-device verification if requested.

## Step Log

- 2026-05-31: PASS - `scripts/catalog_sync.ps1 -Module app_v2`, `scripts/check_strings_localized.ps1 -KeyPrefix "game_"`, `rg "S0316:" app_v2/src/main/java app_v2/src/test/java`, and feature-doc `rg` checks passed.
- 2026-05-31: PASS - `./gradlew.bat :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.domain.game.*" --tests "com.sza.fastmediasorter.data.game.*" --tests "com.sza.fastmediasorter.ui.game.*"` passed.
- 2026-05-31: PASS - `./gradlew.bat assembleStandardDebug` passed.
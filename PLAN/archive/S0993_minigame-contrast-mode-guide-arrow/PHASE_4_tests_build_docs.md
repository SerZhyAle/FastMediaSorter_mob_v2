# PHASE 4 - Tests, build gate, docs, device gate

**Ticket:** S0993
Goal: prove the new behaviour, close mechanical gates, and hand off to on-device verification (visual feature).

## Steps

1. Unit tests (`app_v2/src/test/java/com/sza/fastmediasorter/ui/game/`):
   - Render-mapper test: nearest-exit selection for `guideArrow` (two exits -> closer one; single exit; no exit -> null; player already on exit still yields a target, view guards the zero-length draw).
   - Mode test: `GameBoard`/`GameMode` - `GameMode.entries` contains `CONTRAST`; `fromStorageName("CONTRAST")` resolves; theme `filledActors`/`filledExitColor`/`steppedMove` true for CONTRAST and false/null for CLASSIC.
   - Verification: `.\gradlew.bat testStandardDebugUnitTest --tests "*GameBoardRenderMapper*" --tests "*GameViewModelTest*"` green (respect pre-existing unrelated failures - scope with `--tests`).

2. Debug verification tags (visual feature -> BlockNeedUserTest). As the final code edits before the last build, insert `Timber.d("S0993: <entry>")` at the changed flow entries (one per changed flow):
   - `GameBoardView.applyTheme` when CONTRAST resolved (contrast skin active).
   - `GameBoardView.drawGuideArrow` entry (arrow drawn).
   - Verification: exactly the S0993 probes exist; no persistent `Timber.i/w/e` carry `S0993:`.

3. Build gate: `.\a.ps1 dq` (standard debug, quiet) must PASS - this build validates code + inserted tags in one pass.
   - Verification: `BUILD SUCCESSFUL`.

4. Mechanical closure via facade for the touched Kotlin/XML (one per logical change):
   - `scripts/post-change.ps1 -File <...> -Target game -Description "S0993 contrast mode + guide arrow" -ChangeType Mixed -ScopeToFile` (dirty-tree scoped detekt + advisory ratchets).
   - `scripts/check_strings_localized.ps1 -KeyPrefix game_mode_contrast` exit 0.
   - `scripts/catalog_sync.ps1 -Module app_v2` once.
   - No settings-manifest change (game mode picker is not a Settings entry) -> Rule 22 gate N/A.
   - Verification: gates green / advisory only.

5. Capability inventory: record the shipped capability in `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1` (EN-only) - one record for the contrast mode + guide arrow.
   - Verification: `scripts/all_features/validate.ps1` passes; grep `S0993` present.

6. Status -> `BlockNeedUserTest` with a `-StatusNote` describing what to test on device (contrast legibility on a small screen, move direction readable, arrow points to nearest exit for ~1 s at level start in all three modes). Then run the `/spec-all` device-test gate (auto `/spec-test-device` + `/spec-check` if a device is online; silent no-op otherwise).

## Done when

- Build PASS, tests green, gates satisfied, ALL_FEATURES recorded.
- Ticket at `BlockNeedUserTest` (or Verified if device auto-test ran and passed).

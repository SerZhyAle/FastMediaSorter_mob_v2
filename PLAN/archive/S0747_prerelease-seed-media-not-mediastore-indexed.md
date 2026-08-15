# S0747 - Prerelease seed-media not MediaStore-indexed -> Maestro image flows fail

**Ticket:** S0747
**Status:** Archived
**Priority:** 45
**Date:** 2026-06-27
**Tier:** Ad-hoc (harness defect, moderate)
**Source:** Parked from `/spec-prerelease` sweep on device RFCR110NBQJ (Galaxy S21+, API 35), run TS 20260627_060958.

## Goal

Прогон `/spec-prerelease` на устройстве, которое уже было засеяно ранее, давал 4 ложных падения Maestro: `prerelease-prepare.ps1` на present-пути делал голый `SKIP` сидинга и не запускал скан MediaStore, поэтому файлы фикстур лежали на диске, но не индексировались - и flow, которые ищут каноническую фикстуру `photo_001.jpg` по имени, ничего не находили. Цель: гарантировать, что после стадии seed-media каноническая фикстура реально queryable в MediaStore - принудительный скан + проверка на обоих путях (present и свежий сидинг), с фолбэком на полный пере-сидинг и явным FAIL, если индексации так и нет. Приложение корректно; правка целиком в harness-скрипте, без изменения app-кода.

## 0. Raw finding (evidence)

`/spec-prerelease` full Maestro suite: 12 flows, 4 failed - `browse_all_images.yaml`, `player_image.yaml`, `slideshow_basic.yaml`, `local_browse.yaml`. All four share one root cause: the seeded fixture `photo_001.jpg` is not visible in MediaStore.

Cause chain:
- `prerelease-prepare.ps1` `seed-media` stage reported `SKIP - present - /sdcard/Download/FastMediaSorter_Test exists`. The present branch neither re-copied fixtures nor forced a MediaStore scan.
- `setup_test_media.ps1` (the seeder) DOES create `photo_001.jpg` (DCIM/photo_001.jpg) and DOES scan (per-file `MEDIA_SCANNER_SCAN_FILE` broadcast, step 11) - but the prepare present-branch never calls it, so on an already-seeded device the on-disk files stay unindexed.
- App behaviour is correct: browse/player UI loads and navigates; the 8 non-image flows pass; 0 crashes / 0 ANR / 0 error toasts across the whole run. Harness/fixture gap, not an app defect.

Evidence:
- Maestro JSON: `temp/maestro_suite_20260627_060958.json`
- Run log: `temp/s0484_run_20260627_060958.log`
- Verdict: machine `pass=false` driven solely by these 4 maestro failures (`log.pass=true`, `perf.pass=true`).

## Phases

### Phase 1 - Force MediaStore index + verify after seed-media

- [x] In `scripts/devtest/prerelease-prepare.ps1`, immediately after the existing `seed-media` stage, add a `media-index` stage that runs on BOTH the present and freshly-seeded paths.
- [x] Define a local `Test-MediaIndexed` check: query `content://media/external/images/media` projecting `_display_name` via a `& $adb @adbTarget shell content query` call and return true iff output matches `photo_001.jpg` (canonical fixture). Avoid a `--where` clause to dodge cross-device shell-quoting fragility; match in PowerShell instead.
- [x] If not indexed: enumerate `find $mediaRoot -type f` and send a `MEDIA_SCANNER_SCAN_FILE` broadcast per file (mirror `setup_test_media.ps1` step 11). Poll the check (`Wait-MediaIndexed`, 4 attempts x 2s) instead of a single read, since broadcast scans are asynchronous - prevents a spurious heavy re-seed when MediaStore is merely slow.
- [x] If still not indexed: full re-seed fallback via `setup_test_media.ps1` (wipes + re-pushes + scans), re-poll.
- [x] Outcomes: indexed -> `Add-Stage 'media-index' 'OK'` with a short detail; still absent after scan + re-seed -> `Add-Stage 'media-index' 'FAIL'` + `Complete-Run 10` (gate the sweep so the operator knows fixtures are unusable rather than letting 4 image flows fail silently).
- [x] Keep the change harness-only; do not touch app code or Maestro yaml.
- **Verification:** `pwsh -NoProfile -Command "$null = [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path scripts/devtest/prerelease-prepare.ps1), [ref]$null, [ref]$null); 'parse-ok'"` prints `parse-ok` (no parse errors).

### Phase 2 - Validate scan+verify against the connected device

- [x] Exercise the new media-index logic standalone (non-destructive: scan + query only, NO uninstall/reinstall) against the connected device, confirming `Test-MediaIndexed` returns true after the scan loop.
- **Verification:** a standalone run of the scan + `content query` over `/sdcard/Download/FastMediaSorter_Test` returns a row containing `photo_001.jpg` (expected: queryable | actual: recorded in `## Last Audit`).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0307 (prerelease strategic), S0666 (prior Maestro-harness flakiness)

## Non-goals

- Changing app MediaStore behaviour (the app is correct).
- Rewriting the Maestro suite or decoupling image flows from the canonical filename (possible future hardening; out of scope here).

## Last Audit

**2026-06-27 (spec-all, device RFCR110NBQJ)** - Verified.

- Change: `scripts/devtest/prerelease-prepare.ps1` gains a `media-index` stage after `seed-media`, with helpers `Test-MediaIndexed` / `Invoke-MediaScan` / `Wait-MediaIndexed`. Runs on both present and freshly-seeded paths; scans + polls; full re-seed fallback; `Complete-Run 10` if the canonical fixture stays unqueryable. Harness-only, no app code.
- Phase 1 (parse): `[System.Management.Automation.Language.Parser]::ParseFile` -> `parse-ok` (expected parse-ok | actual parse-ok).
- Phase 2 (device, real PowerShell functions): standalone run -> `Wait-MediaIndexed -> True`, `PHASE2-PASS`. Query mechanism detects `photo_001.jpg`; scan loop executes clean (expected: queryable | actual: queryable, count=1).
- Root cause confirmed during validation: at the failing run's moment `photo_001.jpg` was on disk (8 MB, DCIM/) but not yet MediaStore-indexed (prepare SKIPped the scan and did not await it); it was indexed later. The fix forces the scan and gates progress on the verified-queryable predicate, closing the race that produced the 4 spurious Maestro image-flow failures.
- Build gate: N/A (no `src/` / app code edited; harness `.ps1` only).
